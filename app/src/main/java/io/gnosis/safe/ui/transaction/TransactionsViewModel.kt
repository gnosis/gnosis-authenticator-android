package io.gnosis.safe.ui.transaction

import androidx.annotation.StringRes
import io.gnosis.data.models.Page
import io.gnosis.data.models.Transaction
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.data.repositories.TransactionRepository
import io.gnosis.safe.R
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import pm.gnosis.model.Solidity
import javax.inject.Inject

class TransactionsViewModel
@Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val safeRepository: SafeRepository,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<TransactionsViewState>(appDispatchers) {

    override fun initialState(): TransactionsViewState = TransactionsViewState(null, true)
    fun load() {
        safeLaunch {
            val safeAddress = safeRepository.getActiveSafe()!!.address
            val safeInfo = safeRepository.getSafeInfo(safeAddress)
            val transactions = transactionRepository.getTransactions(safeAddress, safeInfo)
            updateState {
                val newTransactions = newTransactions(transactions, safeAddress)
                val transactionsWithSectionHeaders = addSectionHeaders(newTransactions)
                TransactionsViewState(
                    isLoading = false,
                    viewAction = LoadTransactions(transactionsWithSectionHeaders)
                )
            }
        }
    }

    private fun newTransactions(
        transactions: Page<Transaction>,
        safeAddress: Solidity.Address
    ): List<TransactionView> {
        return transactions.results.mapNotNull { transaction ->
            when (transaction) {
                is Transaction.Transfer -> TransactionView.Transfer(transaction, transaction.recipient == safeAddress)
                is Transaction.SettingsChange -> TransactionView.SettingsChange(transaction)
                else -> null
            }
        }
    }

    private fun addSectionHeaders(newTransactions: List<TransactionView>): List<TransactionView> {
        val mutableList = newTransactions.toMutableList()
        //TODO: Find first QUEUED tx and insert SectionHeader before it
        mutableList.add(0, TransactionView.SectionHeader(title = R.string.tx_list_queue))
        //TODO: Find first HISTORY tx and insert SectionHeader before it
        mutableList.add(3, TransactionView.SectionHeader(title = R.string.tx_list_history))
        return mutableList
    }
}

sealed class TransactionView(open val transaction: Transaction?) {
    data class SectionHeader(override val transaction: Transaction? = null, @StringRes val title: Int) : TransactionView(transaction)
    data class ChangeMastercopy(override val transaction: Transaction) : TransactionView(transaction)
    data class ChangeMastercopyQueued(override val transaction: Transaction) : TransactionView(transaction)
    data class SettingsChange(override val transaction: Transaction.SettingsChange) : TransactionView(transaction)
    data class SettingsChangeQueued(override val transaction: Transaction) : TransactionView(transaction)
    data class Transfer(override val transaction: Transaction.Transfer, val isIncoming: Boolean) : TransactionView(transaction)
    data class TransferQueued(override val transaction: Transaction) : TransactionView(transaction)
}

data class TransactionsViewState(
    override var viewAction: BaseStateViewModel.ViewAction?,
    val isLoading: Boolean
) : BaseStateViewModel.State

data class LoadTransactions(
    val newTransactions: List<TransactionView>
) : BaseStateViewModel.ViewAction
