package io.gnosis.data.backend.dto

import io.gnosis.data.models.TransactionStatus
import pm.gnosis.model.Solidity
import java.math.BigInteger

data class GateTransactionDto(
    val id: String,
    val timestamp: Long,
    val txStatus: TransactionStatus,
    val txInfo: TransactionInfo,
    val executionInfo: ExecutionInfo?
)

data class ExecutionInfo(
    val nonce: BigInteger,
    val confirmationsRequired: Int,
    val confirmationsSubmitted: Int
)

enum class GateTransactionType {
    Transfer,
    SettingsChange,
    Custom,
    Creation,
    Unknown
}

sealed class TransactionInfo {
    abstract val type: GateTransactionType

    data class Custom(
        override val type: GateTransactionType = GateTransactionType.Custom,
        val to: Solidity.Address,
        val dataSize: String,
        val value: String
    ) : TransactionInfo()

    data class SettingsChange(
        override val type: GateTransactionType = GateTransactionType.SettingsChange,
        val dataDecoded: DataDecodedDto
    ) : TransactionInfo()

    data class Transfer(
        override val type: GateTransactionType = GateTransactionType.Transfer,
        val sender: Solidity.Address,
        val recipient: Solidity.Address,
        val transferInfo: TransferInfo,
        val direction: TransactionDirection
    ) : TransactionInfo()

    data class Creation(
        override val type: GateTransactionType = GateTransactionType.Creation
    ) : TransactionInfo()

    data class Unknown(
        override val type: GateTransactionType = GateTransactionType.Unknown
    ) : TransactionInfo()
}

enum class TransactionDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN
}

enum class GateTransferType {
    ERC20, ERC721, ETHER
}

interface TransferInfo {
    val type: GateTransferType
}

data class Erc20Transfer(
    override val type: GateTransferType = GateTransferType.ERC20,
    val tokenAddress: Solidity.Address,
    val tokenName: String?,
    val tokenSymbol: String?,
    val logoUri: String?,
    val decimals: Int?,
    val value: String
) : TransferInfo

data class Erc721Transfer(
    override val type: GateTransferType = GateTransferType.ERC721,
    val tokenAddress: Solidity.Address,
    val tokenId: String,
    val tokenName: String?,
    val tokenSymbol: String?,
    val logoUri: String?
) : TransferInfo

data class EtherTransfer(
    override val type: GateTransferType = GateTransferType.ETHER,
    val value: String
) : TransferInfo


