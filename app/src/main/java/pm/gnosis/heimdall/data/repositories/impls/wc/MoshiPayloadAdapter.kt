package pm.gnosis.heimdall.data.repositories.impls.wc

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.paddings.PKCS7Padding
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.crypto.params.ParametersWithIV
import pm.gnosis.heimdall.data.repositories.impls.Session
import pm.gnosis.utils.hexToByteArray
import pm.gnosis.utils.nullOnThrow
import pm.gnosis.utils.toHexString
import java.security.SecureRandom

class MoshiPayloadAdapter(moshi: Moshi) : Session.PayloadAdapter {

    private val payloadAdapter = moshi.adapter(EncryptedPayload::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    private fun createRandomBytes(i: Int) = ByteArray(i).also { SecureRandom().nextBytes(it) }

    override fun parse(payload: String, key: String): Session.PayloadAdapter.MethodCall {
        val encryptedPayload = payloadAdapter.fromJson(payload) ?: throw IllegalArgumentException("Invalid json payload!")

        // TODO verify hmac

        val padding = PKCS7Padding()
        val aes = PaddedBufferedBlockCipher(
            CBCBlockCipher(AESEngine()),
            padding
        )
        val ivAndKey = ParametersWithIV(
            KeyParameter(key.hexToByteArray()),
            encryptedPayload.iv.hexToByteArray()
        )
        aes.init(false, ivAndKey)

        val encryptedData = encryptedPayload.data.hexToByteArray()
        val minSize = aes.getOutputSize(encryptedData.size)
        val outBuf = ByteArray(minSize)
        var len = aes.processBytes(encryptedData, 0, encryptedData.size, outBuf, 0)
        len += aes.doFinal(outBuf, len)

        return outBuf.copyOf(len).toMethodCall()
    }

    override fun prepare(data: Session.PayloadAdapter.MethodCall, key: String): String {
        val bytesData = data.toBytes()
        val hexKey = key.hexToByteArray()
        val iv = createRandomBytes(16)

        val padding = PKCS7Padding()
        val aes = PaddedBufferedBlockCipher(
            CBCBlockCipher(AESEngine()),
            padding
        )
        aes.init(true, ParametersWithIV(KeyParameter(hexKey), iv))

        val minSize = aes.getOutputSize(bytesData.size)
        val outBuf = ByteArray(minSize)
        val length1 = aes.processBytes(bytesData, 0, bytesData.size, outBuf, 0)
        aes.doFinal(outBuf, length1)


        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(hexKey))

        val hmacResult = ByteArray(hmac.macSize)
        hmac.update(outBuf, 0, outBuf.size)
        hmac.update(iv, 0, iv.size)
        hmac.doFinal(hmacResult, 0)

        return payloadAdapter.toJson(
            EncryptedPayload(
                outBuf.toHexString(),
                hmac = hmacResult.toHexString(),
                iv = iv.toHexString()
            )
        )
    }

    /**
     * Convert FROM request bytes
     */
    private fun ByteArray.toMethodCall(): Session.PayloadAdapter.MethodCall =
        mapAdapter.fromJson(String(this))?.let {
            System.out.println("Json map: $it")
            val method = it["method"]
            when (method) {
                "wc_sessionRequest" -> it.toSessionRequest()
                "wc_sessionUpdate" -> it.toSessionUpdate()
                "wc_exchangeKey" -> it.toExchangeKey()
                "eth_sendTransaction" -> it.toSendTransaction()
                null -> it.toResponse()
                else -> throw Session.PayloadAdapter.InvalidMethodException(it.getId(), method.toString())
            }
        } ?: throw IllegalArgumentException("Invalid json")

    private fun Map<String, *>.getId(): Long =
        (this["id"] as? Double)?.toLong() ?: throw IllegalArgumentException("id missing")

    private fun Map<String, *>.toSessionRequest(): Session.PayloadAdapter.MethodCall.SessionRequest {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val data = params.firstOrNull() as? Map<*, *> ?: throw IllegalArgumentException("Invalid params")

        return Session.PayloadAdapter.MethodCall.SessionRequest(
            getId(),
            data.extractPeerData()
        )
    }

    private fun Map<String, *>.toSessionUpdate(): Session.PayloadAdapter.MethodCall.SessionUpdate {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val data = params.firstOrNull() as? Map<*, *> ?: throw IllegalArgumentException("Invalid params")
        val approved = data["approved"] as? Boolean ?: throw IllegalArgumentException("approved missing")
        val chainId = data["chainId"] as? Long
        val message = data["message"] as? String
        val accounts = nullOnThrow { (data["accounts"] as? List<*>)?.toStringList() }
        return Session.PayloadAdapter.MethodCall.SessionUpdate(
            getId(),
            Session.PayloadAdapter.SessionParams(approved, chainId, accounts, message)
        )
    }

    private fun Map<String, *>.toExchangeKey(): Session.PayloadAdapter.MethodCall.ExchangeKey {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val data = params.firstOrNull() as? Map<*, *> ?: throw IllegalArgumentException("Invalid params")
        val nextKey = data["nextKey"] as? String ?: throw IllegalArgumentException("next key missing")
        return Session.PayloadAdapter.MethodCall.ExchangeKey(
            getId(),
            nextKey,
            data.extractPeerData()
        )
    }

    private fun Map<String, *>.toSendTransaction(): Session.PayloadAdapter.MethodCall.SendTransaction {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val data = params.firstOrNull() as? Map<*, *> ?: throw IllegalArgumentException("Invalid params")
        val from = data["from"] as? String ?: throw IllegalArgumentException("from key missing")
        val to = data["to"] as? String ?: throw IllegalArgumentException("to key missing")
        val nonce = data["nonce"] as? String ?: throw IllegalArgumentException("nonce key missing")
        val gasPrice = data["gasPrice"] as? String ?: throw IllegalArgumentException("gasPrice key missing")
        val gasLimit = data["gasLimit"] as? String ?: throw IllegalArgumentException("gasLimit key missing")
        val value = data["value"] as? String ?: throw IllegalArgumentException("value key missing")
        val txData = data["data"] as? String ?: throw IllegalArgumentException("data key missing")
        return Session.PayloadAdapter.MethodCall.SendTransaction(getId(), from, to, nonce, gasPrice, gasLimit, value, txData)
    }

    private fun Map<String, *>.toResponse(): Session.PayloadAdapter.MethodCall.Response {
        val result = this["result"]
        val error = this["error"] as? Map<*, *>
        if (result == null && error == null) throw IllegalArgumentException("no result or error")
        return Session.PayloadAdapter.MethodCall.Response(
            getId(),
            result,
            error?.extractError()
        )
    }

    private fun Map<*, *>.extractError(): Session.PayloadAdapter.Error {
        val code = (this["code"] as? Double)?.toLong()
        val message = this["message"] as? String
        return Session.PayloadAdapter.Error(code ?: 0, message ?: "Unknown error")
    }

    private fun Map<*, *>.extractPeerData(): Session.PayloadAdapter.PeerData {
        val peerId = this["peerId"] as? String ?: throw IllegalArgumentException("peerId missing")
        val peerMeta = this["peerMeta"] as? Map<*, *>
        return Session.PayloadAdapter.PeerData(peerId, peerMeta.extractPeerMeta())
    }

    private fun Map<*, *>?.extractPeerMeta(): Session.PayloadAdapter.PeerMeta {
        val description = this?.get("description") as? String
        val url = this?.get("url") as? String
        val name = this?.get("name") as? String
        return Session.PayloadAdapter.PeerMeta(url, name, description)
    }

    private fun List<*>.toStringList(): List<String> =
        this.map {
            (it as? String) ?: throw IllegalArgumentException("List contains non-String values")
        }

    /**
     * Convert INTO request bytes
     */
    private fun Session.PayloadAdapter.MethodCall.toBytes() =
        mapAdapter.toJson(
            when (this) {
                is Session.PayloadAdapter.MethodCall.SessionRequest -> this.toMap()
                is Session.PayloadAdapter.MethodCall.ExchangeKey -> this.toMap()
                is Session.PayloadAdapter.MethodCall.Response -> this.toMap()
                is Session.PayloadAdapter.MethodCall.SessionUpdate -> this.toMap()
                is Session.PayloadAdapter.MethodCall.SendTransaction -> this.toMap()
            }
        ).toByteArray()

    private fun Session.PayloadAdapter.MethodCall.SessionRequest.toMap() =
        jsonRpc(id, "wc_sessionRequest", peer.intoMap())

    private fun Session.PayloadAdapter.MethodCall.SessionUpdate.toMap() =
        jsonRpc(id, "wc_sessionUpdate", params.intoMap())

    private fun Session.PayloadAdapter.MethodCall.ExchangeKey.toMap() =
        jsonRpc(
            id, "wc_exchangeKey", peer.intoMap(
                mutableMapOf(
                    "nextKey" to nextKey
                )
            )
        )

    private fun Session.PayloadAdapter.MethodCall.SendTransaction.toMap() =
        jsonRpc(
            this.id, "eth_sendTransaction", mapOf(
                "from" to from,
                "to" to to,
                "nonce" to nonce,
                "gasPrice" to gasPrice,
                "gasLimit" to gasLimit,
                "value" to value,
                "data" to data
            )
        )

    private fun Session.PayloadAdapter.MethodCall.Response.toMap() =
        mutableMapOf(
            "id" to id,
            "jsonrpc" to "2.0"
        ).apply {
            result?.let { this["result"] = result }
            error?.let { this["error"] = error.intoMap() }
        }

    private fun jsonRpc(id: Long, method: String, vararg params: Any) =
        mapOf<String, Any>(
            "id" to id,
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params
        )

    @JsonClass(generateAdapter = true)
    data class EncryptedPayload(val data: String, val iv: String, val hmac: String)
}
