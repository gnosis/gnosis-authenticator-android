package pm.gnosis.heimdall.ui.safe.create

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.functions.Predicate
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.GnosisSafe
import pm.gnosis.heimdall.data.remote.RelayServiceApi
import pm.gnosis.heimdall.data.remote.models.RelaySafeCreation2
import pm.gnosis.heimdall.data.remote.models.RelaySafeCreation2Params
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.model.Solidity
import pm.gnosis.model.SolidityBase
import pm.gnosis.svalinn.accounts.base.models.Account
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import pm.gnosis.utils.*
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class CreateSafeConfirmRecoveryPhraseViewModelTest {
    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    private lateinit var accountsRepositoryMock: AccountsRepository

    @Mock
    private lateinit var bip39Mock: Bip39

    @Mock
    private lateinit var relayServiceApiMock: RelayServiceApi

    @Mock
    private lateinit var gnosisSafeRepositoryMock: GnosisSafeRepository

    @Mock
    private lateinit var tokenRepositoryMock: TokenRepository

    private lateinit var viewModel: CreateSafeConfirmRecoveryPhraseViewModel

    @Before
    fun setup() {
        viewModel = CreateSafeConfirmRecoveryPhraseViewModel(
            accountsRepositoryMock,
            bip39Mock,
            relayServiceApiMock,
            gnosisSafeRepositoryMock,
            tokenRepositoryMock
        )
        // Setup parent class
        viewModel.setup(RECOVERY_PHRASE)
    }

    private fun calculateSafeAddress(setupData: String, saltNonce: Long): Solidity.Address {
        val setupDataHash = Sha3Utils.keccak(setupData.hexToByteArray())
        val salt = Sha3Utils.keccak(setupDataHash + Solidity.UInt256(saltNonce.toBigInteger()).encode().hexToByteArray())


        val deploymentCode = PROXY_CODE + MASTER_COPY_ADDRESS.encode()
        val codeHash = Sha3Utils.keccak(deploymentCode.hexToByteArray())
        val create2Hash = Sha3Utils.keccak(byteArrayOf(0xff.toByte()) + PROXY_FACTORY_ADDRESS.value.toBytes(20) + salt + codeHash)
        return Solidity.Address(BigInteger(1, create2Hash.copyOfRange(12, 32)))
    }

    private fun generateSafeCreationData(
        owners: List<Solidity.Address>,
        payment: BigInteger = BigInteger.ZERO,
        paymentToken: ERC20Token = ETHER_TOKEN,
        funder: Solidity.Address = TX_ORIGIN_ADDRESS
    ): String = GnosisSafe.Setup.encode(
        _owners = SolidityBase.Vector(owners),
        _threshold = Solidity.UInt256(if (owners.size == 3) BigInteger.ONE else 2.toBigInteger()),
        to = Solidity.Address(BigInteger.ZERO),
        data = Solidity.Bytes(byteArrayOf()),
        paymentToken = paymentToken.address,
        payment = Solidity.UInt256(payment),
        paymentReceiver = funder
    ) + "0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun createSafe() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val mnemonicSeed = byteArrayOf(0)
        var saltNonce: Long? = null
        var safeAddress: Solidity.Address? = null
        var response: RelaySafeCreation2? = null

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(ETHER_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.just(mnemonicAddress1 to mnemonicSeed))
        given(relayServiceApiMock.safeCreation2(MockUtils.any())).willAnswer {
            val request = it.arguments.first() as RelaySafeCreation2Params
            saltNonce = request.saltNonce
            val setupData = generateSafeCreationData(listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1))
            safeAddress = calculateSafeAddress(setupData, saltNonce!!)
            response = RelaySafeCreation2(
                setupData = setupData,
                safe = safeAddress!!,
                masterCopy = MASTER_COPY_ADDRESS,
                proxyFactory = PROXY_FACTORY_ADDRESS,
                payment = BigInteger.ZERO,
                paymentToken = ETHER_TOKEN.address,
                paymentReceiver = TX_ORIGIN_ADDRESS
            )
            Single.just(response!!)
        }
        given(
            gnosisSafeRepositoryMock.addPendingSafe(
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any()
            )
        ).willReturn(Completable.complete())

        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        testObserver.assertResult(safeAddress)

        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(relayServiceApiMock).should()
            .safeCreation2(
                RelaySafeCreation2Params(
                    listOf(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                    2, saltNonce!!, ETHER_TOKEN.address
                )
            )
        then(gnosisSafeRepositoryMock).should().addPendingSafe(response!!.safe, BigInteger.ZERO, null, response!!.payment, ETHER_TOKEN.address)
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun createSafeSaveDbError() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val deployer = Solidity.Address(1234.toBigInteger())
        val mnemonicSeed = byteArrayOf(0)
        var saltNonce: Long? = null
        var safeAddress: Solidity.Address?
        var response: RelaySafeCreation2? = null
        val exception = Exception()

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(PAYMENT_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.just(mnemonicAddress1 to mnemonicSeed))
        given(relayServiceApiMock.safeCreation2(MockUtils.any())).willAnswer {
            val request = it.arguments.first() as RelaySafeCreation2Params
            saltNonce = request.saltNonce
            val setupData = generateSafeCreationData(
                listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                BigInteger.TEN, PAYMENT_TOKEN
            )
            safeAddress = calculateSafeAddress(setupData, saltNonce!!)
            response = RelaySafeCreation2(
                setupData = setupData,
                safe = safeAddress!!,
                masterCopy = MASTER_COPY_ADDRESS,
                proxyFactory = PROXY_FACTORY_ADDRESS,
                payment = BigInteger.TEN,
                paymentToken = PAYMENT_TOKEN.address,
                paymentReceiver = TX_ORIGIN_ADDRESS
            )
            Single.just(response!!)
        }
        given(
            gnosisSafeRepositoryMock.addPendingSafe(
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any()
            )
        ).willReturn(Completable.error(exception))

        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        testObserver.assertError(exception)

        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(relayServiceApiMock).should()
            .safeCreation2(
                RelaySafeCreation2Params(
                    listOf(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                    2, saltNonce!!, PAYMENT_TOKEN.address
                )
            )
        then(gnosisSafeRepositoryMock).should().addPendingSafe(response!!.safe, BigInteger.ZERO, null, response!!.payment, PAYMENT_TOKEN.address)
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun createSafeAddressesNotMatching() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val safeAddress = "ffff".asEthereumAddress()!!
        val deployer = Solidity.Address(1234.toBigInteger())
        val mnemonicSeed = byteArrayOf(0)
        var saltNonce: Long? = null
        var response: RelaySafeCreation2? = null

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(ETHER_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.just(mnemonicAddress1 to mnemonicSeed))
        given(relayServiceApiMock.safeCreation2(MockUtils.any())).willAnswer {
            val request = it.arguments.first() as RelaySafeCreation2Params
            saltNonce = request.saltNonce
            val setupData = generateSafeCreationData(
                listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                BigInteger.ZERO, ETHER_TOKEN, FUNDER_ADDRESS
            )
            response = RelaySafeCreation2(
                setupData = setupData,
                safe = safeAddress,
                masterCopy = MASTER_COPY_ADDRESS,
                proxyFactory = PROXY_FACTORY_ADDRESS,
                payment = BigInteger.ZERO,
                paymentToken = ETHER_TOKEN.address,
                paymentReceiver = FUNDER_ADDRESS
            )
            Single.just(response!!)
        }

        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(relayServiceApiMock).should()
            .safeCreation2(
                RelaySafeCreation2Params(
                    listOf(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                    2, saltNonce!!, ETHER_TOKEN.address
                )
            )
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveNoMoreInteractions()
        testObserver.assertFailure(Predicate { error ->
            error is IllegalStateException && error.message == "Unexpected safe address returned"
        })
    }

    private fun testSafeCreationChecks(
        account: Account,
        mnemonicAddress0: Solidity.Address,
        mnemonicAddress1: Solidity.Address,
        chromeExtensionAddress: Solidity.Address?,
        responseAnswer: (RelaySafeCreation2Params) -> Single<RelaySafeCreation2>,
        failurePredicate: Predicate<Throwable>,
        paymentToken: ERC20Token = ETHER_TOKEN
    ) {
        val testObserver = TestObserver.create<Solidity.Address>()
        val mnemonicSeed = byteArrayOf(0)
        var saltNonce: Long? = null

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(paymentToken))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.just(mnemonicAddress1 to mnemonicSeed))
        given(relayServiceApiMock.safeCreation2(MockUtils.any())).willAnswer {
            val request = it.arguments.first() as RelaySafeCreation2Params
            saltNonce = request.saltNonce
            responseAnswer(request)
        }

        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(relayServiceApiMock).should()
            .safeCreation2(RelaySafeCreation2Params(
                listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                chromeExtensionAddress?.run { 2 } ?: 1, saltNonce!!, paymentToken.address)
            )
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveNoMoreInteractions()
        testObserver.assertFailure(failurePredicate)
    }

    @Test
    fun createSafeDifferentPaymentToken() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, PAYMENT_TOKEN
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = PAYMENT_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected payment token returned"
            }
        )
    }

    @Test
    fun createSafeDifferentMasterCopy() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, ETHER_TOKEN
                        ),
                        safe = safeAddress,
                        masterCopy = PAYMENT_TOKEN.address,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected master copy returned"
            }
        )
    }

    @Test
    fun createSafeDifferentProxyFactory() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, ETHER_TOKEN
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PAYMENT_TOKEN.address,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected proxy factory returned"
            }
        )
    }

    @Test
    fun createSafeUnknownReceiver() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, ETHER_TOKEN, PAYMENT_TOKEN.address
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = PAYMENT_TOKEN.address
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected payment receiver returned"
            }
        )
    }

    @Test
    fun createSafeInvalidSetupData() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = "0000000000000000000000000000000000000000000000000000000000000000",
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected setup data returned"
            }
        )
    }

    @Test
    fun createSafeDifferentOwnersInData() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(PAYMENT_TOKEN.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, ETHER_TOKEN
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected setup data returned"
            }
        )
    }

    @Test
    fun createSafeDifferentPaymentReceiverInData() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, ETHER_TOKEN, PAYMENT_TOKEN.address
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected setup data returned"
            }
        )
    }

    @Test
    fun createSafeDifferentPaymentTokenInData() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.ZERO, PAYMENT_TOKEN, TX_ORIGIN_ADDRESS
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected setup data returned"
            }
        )
    }

    @Test
    fun createSafeDifferentPaymentInData() {
        val safeAddress = "ffff".asEthereumAddress()!!
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        testSafeCreationChecks(
            account, mnemonicAddress0, mnemonicAddress1, chromeExtensionAddress,
            { request ->
                Single.just(
                    RelaySafeCreation2(
                        setupData = generateSafeCreationData(
                            listOfNotNull(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                            BigInteger.TEN, ETHER_TOKEN, TX_ORIGIN_ADDRESS
                        ),
                        safe = safeAddress,
                        masterCopy = MASTER_COPY_ADDRESS,
                        proxyFactory = PROXY_FACTORY_ADDRESS,
                        payment = BigInteger.ZERO,
                        paymentToken = ETHER_TOKEN.address,
                        paymentReceiver = TX_ORIGIN_ADDRESS
                    )
                )
            },
            Predicate { error ->
                error is IllegalStateException && error.message == "Unexpected setup data returned"
            }
        )
    }

    @Test
    fun createSafeWithoutBrowserExtension() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val deployer = Solidity.Address(1234.toBigInteger())
        val mnemonicSeed = byteArrayOf(0)
        var safeAddress: Solidity.Address? = null
        var saltNonce: Long? = null
        var response: RelaySafeCreation2? = null

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(ETHER_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.just(mnemonicAddress1 to mnemonicSeed))
        given(relayServiceApiMock.safeCreation2(MockUtils.any())).willAnswer {
            val request = it.arguments.first() as RelaySafeCreation2Params
            saltNonce = request.saltNonce
            val setupData = generateSafeCreationData(
                listOfNotNull(account.address, mnemonicAddress0, mnemonicAddress1),
                BigInteger.ZERO, ETHER_TOKEN, FUNDER_ADDRESS
            )
            safeAddress = calculateSafeAddress(setupData, saltNonce!!)
            response = RelaySafeCreation2(
                setupData = setupData,
                safe = safeAddress!!,
                masterCopy = MASTER_COPY_ADDRESS,
                proxyFactory = PROXY_FACTORY_ADDRESS,
                payment = BigInteger.ZERO,
                paymentToken = ETHER_TOKEN.address,
                paymentReceiver = FUNDER_ADDRESS
            )
            Single.just(response!!)
        }
        given(
            gnosisSafeRepositoryMock.addPendingSafe(
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any(),
                MockUtils.any()
            )
        ).willReturn(Completable.complete())

        viewModel.setup(null)
        viewModel.createSafe().subscribe(testObserver)

        testObserver.assertResult(safeAddress)

        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(relayServiceApiMock).should()
            .safeCreation2(
                RelaySafeCreation2Params(
                    listOf(account.address, mnemonicAddress0, mnemonicAddress1),
                    1, saltNonce!!, ETHER_TOKEN.address
                )
            )
        then(gnosisSafeRepositoryMock).should().addPendingSafe(response!!.safe, BigInteger.ZERO, null, response!!.payment, ETHER_TOKEN.address)
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun createSafeApiError() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val mnemonicAddress1 = Solidity.Address(12.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val mnemonicSeed = byteArrayOf(0)
        var saltNonce: Long? = null
        val exception = Exception()

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(ETHER_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.just(mnemonicAddress1 to mnemonicSeed))
        given(relayServiceApiMock.safeCreation2(MockUtils.any())).willAnswer {
            val request = it.arguments.first() as RelaySafeCreation2Params
            saltNonce = request.saltNonce
            Single.error<RelaySafeCreation2>(exception)
        }

        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(relayServiceApiMock).should()
            .safeCreation2(
                RelaySafeCreation2Params(
                    listOf(account.address, chromeExtensionAddress, mnemonicAddress0, mnemonicAddress1),
                    2, saltNonce!!, ETHER_TOKEN.address
                )
            )
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveNoMoreInteractions()
        testObserver.assertError(exception)
    }

    @Test
    fun createSafeFromMnemonicSeedError() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val mnemonicAddress0 = Solidity.Address(11.toBigInteger())
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val mnemonicSeed = byteArrayOf(0)
        val exception = Exception()

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(PAYMENT_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willReturn(mnemonicSeed)
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(0L))).willReturn(Single.just(mnemonicAddress0 to mnemonicSeed))
        given(accountsRepositoryMock.accountFromMnemonicSeed(MockUtils.any(), eq(1L))).willReturn(Single.error(exception))

        // Setup parent class
        viewModel.setup(RECOVERY_PHRASE)
        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 0L)
        then(accountsRepositoryMock).should().accountFromMnemonicSeed(mnemonicSeed, 1L)
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveZeroInteractions()
        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        testObserver.assertError(exception)
    }

    @Test
    fun createSafeMnemonicToSeedError() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val account = Account(Solidity.Address(10.toBigInteger()))
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val exception = IllegalArgumentException()

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(PAYMENT_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.just(account))
        given(bip39Mock.mnemonicToSeed(anyString(), MockUtils.any())).willThrow(exception)

        // Setup parent class
        viewModel.setup(RECOVERY_PHRASE)
        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        then(accountsRepositoryMock).should().loadActiveAccount()
        then(bip39Mock).should().mnemonicToSeed(RECOVERY_PHRASE)
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveNoMoreInteractions()
        then(relayServiceApiMock).shouldHaveZeroInteractions()
        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        testObserver.assertError(exception)
    }

    @Test
    fun createSafeLoadActiveAccountError() {
        val testObserver = TestObserver.create<Solidity.Address>()
        val chromeExtensionAddress = Solidity.Address(13.toBigInteger())
        val exception = IllegalArgumentException()

        given(tokenRepositoryMock.loadPaymentToken()).willReturn(Single.just(PAYMENT_TOKEN))
        given(accountsRepositoryMock.loadActiveAccount()).willReturn(Single.error(exception))

        // Setup parent class
        viewModel.setup(RECOVERY_PHRASE)
        viewModel.setup(chromeExtensionAddress)
        viewModel.createSafe().subscribe(testObserver)

        then(accountsRepositoryMock).should().loadActiveAccount()
        then(accountsRepositoryMock).shouldHaveNoMoreInteractions()
        then(bip39Mock).shouldHaveZeroInteractions()
        then(relayServiceApiMock).shouldHaveZeroInteractions()
        then(tokenRepositoryMock).should().loadPaymentToken()
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        testObserver.assertError(exception)
    }

    companion object {
        private val MASTER_COPY_ADDRESS = BuildConfig.CURRENT_SAFE_MASTER_COPY_ADDRESS.asEthereumAddress()!!
        private val PROXY_FACTORY_ADDRESS = BuildConfig.PROXY_FACTORY_ADDRESS.asEthereumAddress()!!
        private val ETHER_TOKEN = ERC20Token.ETHER_TOKEN
        private val PAYMENT_TOKEN = ERC20Token("0xdeadbeef".asEthereumAddress()!!, "Payment Token", "PT", 18)
        private val FUNDER_ADDRESS = BuildConfig.SAFE_CREATION_FUNDER.asEthereumAddress()!!
        private val TX_ORIGIN_ADDRESS = "0x0".asEthereumAddress()!!
        private const val RECOVERY_PHRASE = "degree media athlete harvest rocket plate minute obey head toward coach senior"
        private const val PROXY_CODE =
            "0x608060405234801561001057600080fd5b506040516020806101aa8339810180604052602081101561003057600080fd5b8101908080519060200190929190505050600073ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff16141515156100c9576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825260248152602001806101866024913960400191505060405180910390fd5b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050606e806101186000396000f3fe608060405273ffffffffffffffffffffffffffffffffffffffff600054163660008037600080366000845af43d6000803e6000811415603d573d6000fd5b3d6000f3fea165627a7a72305820fb5a6f727010799f33fdcf5590764db867b968b3e64528d5250c6935457674d00029496e76616c6964206d617374657220636f707920616464726573732070726f7669646564"
    }
}
