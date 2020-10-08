package io.gnosis.safe.ui.signing.owners

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.utils.MnemonicKeyAndAddressDerivator
import io.gnosis.safe.utils.OwnerKeyHandler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.toHexString
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

class OwnerSelectionViewModel
@Inject constructor(
    private val derivator: MnemonicKeyAndAddressDerivator,
    private val ownerKeyHandler: OwnerKeyHandler,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<OwnerSelectionState>(appDispatchers) {

    private var ownerIndex: Long = 0

    override fun initialState() = OwnerSelectionState(ViewAction.Loading(true))

    fun loadOwners(mnemonic: String) {
        safeLaunch {
            derivator.initialize(mnemonic)
            OwnerPagingProvider(derivator).getOwnersStream()
                .map {
                    it.insertSeparators { before, after ->
                        return@insertSeparators if (before == null) {
                            Solidity.Address(BigInteger.ZERO)
                        } else {
                            null
                        }
                    }
                }
                .collectLatest {
                    updateState {
                        OwnerSelectionState(
                            LoadedOwners(
                                it
                            )
                        )
                    }
                }
        }
    }

    fun setOwnerIndex(index: Long) {
        ownerIndex = index
    }

    fun importOwner() {
        safeLaunch {
            val key = derivator.keyForIndex(ownerIndex)
            Timber.i("---> Storing private key: ${key.toHexString()}")
            ownerKeyHandler.storeKey(key)
            val addresses = derivator.addressesForPage(ownerIndex, 1)
            Timber.i("---> Storing address: ${addresses[0].asEthereumAddressChecksumString()}")
            ownerKeyHandler.storeOwnerAddress(addresses[0])

            updateState {
                OwnerSelectionState(ViewAction.CloseScreen)
            }
        }
    }
}

data class OwnerSelectionState(
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State


data class LoadedOwners(
    val newOwners: PagingData<Solidity.Address>
) : BaseStateViewModel.ViewAction

