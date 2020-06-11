package io.gnosis.data.models

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.gnosis.data.adapters.OperationEnumAdapter
import io.gnosis.data.db.BigDecimalNumberAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.gnosis.common.adapters.moshi.MoshiBuilderFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

class MultisigTransactionDtoTest {

    private var moshi = MoshiBuilderFactory.makeMoshiBuilder()
        .add(BigDecimalNumberAdapter())
        .add(OperationEnumAdapter())
        .add(transactionDtoJsonAdapterFactory)
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(TransactionDto::class.java)

    @Test
    fun `fromJson (multisig transaction) should return MultisigTransactionDto`() {
        val jsonString: String = readResource("multisig_transaction.json")

        val transactionDto = adapter.fromJson(jsonString)

        assertTrue(transactionDto is MultisigTransactionDto)
    }

    @Test
    fun `fromJson (ethereum transaction) should return EthereumTransactionDto`() {
        val jsonString: String = readResource("ethereum_transaction.json")

        val transactionDto = adapter.fromJson(jsonString)

        assertTrue(transactionDto is EthereumTransactionDto)
    }

    @Test
    fun `fromJson (module transaction) should return ModuleTransactionDto`() {
        val jsonString: String = readResource("module_transaction.json")

        val transactionDto = adapter.fromJson(jsonString)

        assertTrue(transactionDto is ModuleTransactionDto)
    }

    @Test
    fun `fromJson (transaction with unknown txType) should return UnknownTransactionDto`() {
        val jsonString: String = readResource("unknown_transaction_type.json")

        val transactionDto = adapter.fromJson(jsonString)

        assertEquals(transactionDto, UnknownTransactionDto)
    }

    @Test(expected = JsonDataException::class)
    fun `fromJson (transaction with missing txType) should throw a JsonDataException`() {
        val jsonString: String = readResource("tx_type_missing_transaction.json")

        adapter.fromJson(jsonString)
    }

    private fun readResource(fileName: String): String {
        return BufferedReader(
            InputStreamReader(
                this::class.java.getClassLoader()?.getResourceAsStream(fileName)!!
            )
        ).lines().parallel().collect(Collectors.joining("\n"))
    }

}
