package io.gnosis.data.models.transaction

import com.squareup.moshi.Json
import io.gnosis.data.repositories.TokenRepository
import pm.gnosis.model.Solidity
import java.math.BigInteger

enum class TransferType {
    ERC20,
    ERC721,
    ETHER
}

sealed class TransferInfo(
    @Json(name = "type") val type: TransferType
) {

    data class Erc20Transfer(
        @Json(name = "tokenAddress")
        val tokenAddress: Solidity.Address,
        @Json(name = "tokenName")
        val tokenName: String?,
        @Json(name = "tokenSymbol")
        val tokenSymbol: String?,
        @Json(name = "logoUri")
        val logoUri: String?,
        @Json(name = "decimals")
        val decimals: Int?,
        @Json(name = "value")
        val value: BigInteger
    ) : TransferInfo(TransferType.ERC20)

    data class Erc721Transfer(
        @Json(name = "tokenAddress")
        val tokenAddress: Solidity.Address,
        @Json(name = "tokenId")
        val tokenId: String,
        @Json(name = "tokenName")
        val tokenName: String?,
        @Json(name = "tokenSymbol")
        val tokenSymbol: String?,
        @Json(name = "logoUri")
        val logoUri: String?
    ) : TransferInfo(TransferType.ERC721)

    data class EtherTransfer(
        @Json(name = "value")
        val value: BigInteger
    ) : TransferInfo(TransferType.ETHER)
}


fun TransferInfo.value(): BigInteger =
    when (this) {
        is TransferInfo.EtherTransfer -> value
        is TransferInfo.Erc20Transfer -> value
        is TransferInfo.Erc721Transfer -> BigInteger.ONE
    }

fun TransferInfo.symbol(): String? =
    when (this) {
        is TransferInfo.EtherTransfer -> TokenRepository.NATIVE_CURRENCY_INFO.symbol
        is TransferInfo.Erc20Transfer -> tokenSymbol
        is TransferInfo.Erc721Transfer -> tokenSymbol
    }

fun TransferInfo.decimals(): Int? =
    when (this) {
        is TransferInfo.Erc20Transfer -> decimals
        is TransferInfo.Erc721Transfer -> 0
        is TransferInfo.EtherTransfer -> TokenRepository.NATIVE_CURRENCY_INFO.decimals
    }
