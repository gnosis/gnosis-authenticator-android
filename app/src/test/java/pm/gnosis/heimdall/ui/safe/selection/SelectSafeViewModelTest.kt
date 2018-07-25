package pm.gnosis.heimdall.ui.safe.selection

import android.content.Context
import android.content.Intent
import io.reactivex.Single
import io.reactivex.functions.Predicate
import io.reactivex.observers.TestObserver
import io.reactivex.processors.PublishProcessor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.TransactionInfoRepository
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.ErrorResult
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import pm.gnosis.tests.utils.mockGetString
import java.math.BigInteger
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class SelectSafeViewModelTest {

    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    private lateinit var contextMock: Context

    @Mock
    private lateinit var safeRepositoryMock: GnosisSafeRepository

    @Mock
    private lateinit var infoRepositoryMock: TransactionInfoRepository

    private lateinit var viewModel: SelectSafeViewModel

    @Before
    fun setUp() {
        viewModel = SelectSafeViewModel(contextMock, safeRepositoryMock, infoRepositoryMock)
    }

    @Test
    fun loadSafes() {
        val testProcessor = PublishProcessor.create<List<Safe>>()
        given(safeRepositoryMock.observeSafes()).willReturn(testProcessor)

        val testObserver = TestObserver<List<Safe>>()
        viewModel.loadSafes().subscribe(testObserver)
        testObserver.assertEmpty()

        val safes = listOf(Safe(Solidity.Address(BigInteger.TEN)))
        testProcessor.offer(safes)
        testObserver.assertResult(safes)

        testProcessor.offer(emptyList())
        // No new results
        testObserver.assertResult(safes)

        then(safeRepositoryMock).should().observeSafes()
        then(safeRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadSafesEmpty() {
        val testProcessor = PublishProcessor.create<List<Safe>>()
        given(safeRepositoryMock.observeSafes()).willReturn(testProcessor)

        val testObserver = TestObserver<List<Safe>>()
        viewModel.loadSafes().subscribe(testObserver)
        testObserver.assertEmpty()

        testProcessor.onComplete()
        testObserver.assertFailure(NoSuchElementException::class.java)

        then(safeRepositoryMock).should().observeSafes()
        then(safeRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadSafesError() {
        val testProcessor = PublishProcessor.create<List<Safe>>()
        given(safeRepositoryMock.observeSafes()).willReturn(testProcessor)

        val testObserver = TestObserver<List<Safe>>()
        viewModel.loadSafes().subscribe(testObserver)
        testObserver.assertEmpty()

        val error = IllegalStateException()
        testProcessor.onError(error)
        testObserver.assertFailure(Predicate { it == error })

        then(safeRepositoryMock).should().observeSafes()
        then(safeRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun reviewTransactionNoSafe() {
        contextMock.mockGetString()
        given(infoRepositoryMock.parseTransactionData(MockUtils.any())).willReturn(
            Single.just(
                TransactionData.AssetTransfer(Solidity.Address(BigInteger.ZERO), BigInteger.TEN, Solidity.Address(BigInteger.ONE))
            )
        )

        val testObserver = TestObserver<Result<Intent>>()
        viewModel.reviewTransaction(null, TEST_TRANSACTION).subscribe(testObserver)

        testObserver.assertResult(ErrorResult(SimpleLocalizedException(R.string.no_safe_selected_error.toString())))

        then(infoRepositoryMock).should().parseTransactionData(TEST_TRANSACTION)
        then(infoRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun reviewTransactionError() {
        val error = IllegalStateException()
        given(infoRepositoryMock.parseTransactionData(MockUtils.any())).willReturn(Single.error(error))

        val testObserver = TestObserver<Result<Intent>>()
        viewModel.reviewTransaction(TEST_SAFE, TEST_TRANSACTION).subscribe(testObserver)

        testObserver.assertResult(ErrorResult(error))

        then(infoRepositoryMock).should().parseTransactionData(TEST_TRANSACTION)
        then(infoRepositoryMock).shouldHaveNoMoreInteractions()
    }

    private fun testReviewTransaction(type: TransactionData) {
        given(infoRepositoryMock.parseTransactionData(MockUtils.any())).willReturn(Single.just(type))

        val testObserver = TestObserver<Result<Intent>>()
        viewModel.reviewTransaction(TEST_SAFE, TEST_TRANSACTION).subscribe(testObserver)

        testObserver.assertValue({ it is DataResult }).assertComplete()

        then(infoRepositoryMock).should().parseTransactionData(TEST_TRANSACTION)
        then(infoRepositoryMock).shouldHaveNoMoreInteractions()
        Mockito.reset(infoRepositoryMock)
    }

    @Test
    fun reviewTransaction() {
        TransactionData::class.nestedClasses.filter { it != TransactionData.Companion::class }.forEach {
            testReviewTransaction(TEST_DATA[it] ?: throw IllegalStateException("Missing test for ${it.simpleName}"))
        }
    }

    companion object {
        private val TEST_SAFE = Solidity.Address(BigInteger.ONE)
        private val TEST_TRANSACTION = SafeTransaction(Transaction(Solidity.Address(BigInteger.TEN)), TransactionExecutionRepository.Operation.CALL)

        private const val REPLACE_RECOVERY_PHRASE_DATA =
            "0x8d80ff0a" + // Multi send method
                    "0000000000000000000000000000000000000000000000000000000000000020" +
                    "0000000000000000000000000000000000000000000000000000000000000240" +
                    "0000000000000000000000000000000000000000000000000000000000000000" + // Operation
                    "0000000000000000000000001f81fff89bd57811983a35650296681f99c65c7e" + // Safe address
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000080" +
                    "0000000000000000000000000000000000000000000000000000000000000064" +
                    "e318b52b" + // Swap owner method
                    "000000000000000000000000000000000000000000000000000000000000000c" + // Previous Owner
                    "000000000000000000000000000000000000000000000000000000000000000d" + // Old Owner
                    "000000000000000000000000000000000000000000000000000000000000000f" + // New Owner
                    "00000000000000000000000000000000000000000000000000000000" + // Padding
                    "0000000000000000000000000000000000000000000000000000000000000000" + // Operation
                    "0000000000000000000000001f81fff89bd57811983a35650296681f99c65c7e" + // Safe address
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000080" +
                    "0000000000000000000000000000000000000000000000000000000000000064" +
                    "e318b52b" + // Swap owner method
                    "0000000000000000000000000000000000000000000000000000000000000001" + // Previous Owner
                    "000000000000000000000000000000000000000000000000000000000000000a" + // Old Owner
                    "000000000000000000000000000000000000000000000000000000000000000e" + // New Owner
                    "00000000000000000000000000000000000000000000000000000000" // Padding

        private val REPLACE_RECOVERY_PHRASE_TX =
            SafeTransaction(
                Transaction(
                    address = TEST_SAFE,
                    value = Wei.ZERO,
                    data = REPLACE_RECOVERY_PHRASE_DATA,
                    nonce = BigInteger.ZERO
                ), TransactionExecutionRepository.Operation.DELEGATE_CALL
            )

        private val TEST_DATA = mapOf(
            TransactionData.Generic::class to TransactionData.Generic(TEST_SAFE, BigInteger.ONE, null),
            TransactionData.AssetTransfer::class to TransactionData.AssetTransfer(TEST_SAFE, BigInteger.ONE, Solidity.Address(BigInteger.TEN)),
            TransactionData.ReplaceRecoveryPhrase::class to TransactionData.ReplaceRecoveryPhrase(REPLACE_RECOVERY_PHRASE_TX)
        )
    }
}
