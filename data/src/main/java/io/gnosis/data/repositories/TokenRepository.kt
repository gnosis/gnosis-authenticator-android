package io.gnosis.data.repositories

import io.gnosis.data.backend.TransactionServiceApi
import io.gnosis.data.backend.dto.tokenAsErc20Token
import io.gnosis.data.db.daos.Erc20TokenDao
import io.gnosis.data.models.Balance
import io.gnosis.data.models.Erc20Token
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import java.math.BigInteger

class TokenRepository(
    private val erc20TokenDao: Erc20TokenDao,
    private val transactionServiceApi: TransactionServiceApi
//    private val relayServiceApi: RelayServiceApi
) {

    suspend fun loadBalanceOfNew(safe: Solidity.Address): List<Balance> =
        transactionServiceApi.loadBalances(safe.asEthereumAddressChecksumString())
            .map { Balance(it.tokenAsErc20Token(), it.balance, it.balanceUsd) }


    @Deprecated("Uses relay service")
    suspend fun loadBalancesOf(safe: Solidity.Address, forceRefetch: Boolean = false): List<Balance> =
        transactionServiceApi.loadBalances(safe.asEthereumAddressChecksumString())
            .associateWith {
                it.tokenAddress?.let { tokenAddress -> erc20TokenDao.loadToken(tokenAddress) }
            }
            .map { (balance, tokenFromDao) ->
                val token = when {
                    tokenFromDao != null && !forceRefetch -> tokenFromDao
//                    balance.tokenAddress != null -> loadToken(balance.tokenAddress)
                    else -> ETH_TOKEN_INFO
                }
                Balance(token, balance.balance, balance.balanceUsd)
            }

//    suspend fun loadToken(address: Solidity.Address): Erc20Token =
//        relayServiceApi.tokenInfo(address.asEthereumAddressString()).let {
//            it.toErc20Token().apply { erc20TokenDao.insertToken(this) }
//        }


    companion object {
        val ETH_ADDRESS = Solidity.Address(BigInteger.ZERO)
        val ETH_TOKEN_INFO = Erc20Token(ETH_ADDRESS, "Ether", "ETH", 18, "local::ethereum")
    }
}
