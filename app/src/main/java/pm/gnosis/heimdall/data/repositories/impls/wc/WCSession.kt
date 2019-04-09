package pm.gnosis.heimdall.data.repositories.impls.wc

import pm.gnosis.heimdall.data.repositories.impls.Session
import pm.gnosis.model.Solidity
import pm.gnosis.utils.nullOnThrow
import pm.gnosis.utils.toHexString
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class WCSession(
    private val config: Session.Config,
    private val payloadAdapter: Session.PayloadAdapter,
    private val sessionStore: WCSessionStore,
    transportBuilder: Session.Transport.Builder,
    clientMeta: Session.PeerMeta,
    clientId: String? = null
) : Session {

    private val keyLock = Any()

    // Persisted state
    private var nextKey: String?
    private var currentKey: String

    private var approvedAccounts: List<String>? = null
    private var handshakeId: Long? = null
    private var peerId: String? = null
    private var peerMeta: Session.PeerMeta? = null

    private val clientData: Session.PeerData

    // Getters
    private val encryptionKey: String
        get() = currentKey

    private val decryptionKey: String
        get() = currentKey

    // Non-persisted state
    private val transport = transportBuilder.build(config.bridge, ::handleStatus, ::handleMessage)
    private val requests: MutableMap<Long, (Session.MethodCall.Response) -> Unit> = ConcurrentHashMap()
    private val sessionCallbacks: MutableSet<Session.Callback> = Collections.newSetFromMap(ConcurrentHashMap<Session.Callback, Boolean>())
    private val queue: Queue<QueuedMethod> = ConcurrentLinkedQueue()

    init {
        nextKey = null
        currentKey = config.key
        clientData = sessionStore.load(config.handshakeTopic)?.let {
            nextKey = it.nextKey
            currentKey = it.currentKey
            approvedAccounts = it.approvedAccounts
            handshakeId = it.handshakeId
            peerId = it.peerData?.id
            peerMeta = it.peerData?.meta
            if (clientId != null && clientId != it.clientData.id)
                throw IllegalArgumentException("Provided clientId is different from stored clientId")
            it.clientData
        } ?: run {
            Session.PeerData(clientId ?: UUID.randomUUID().toString(), clientMeta)
        }
        storeSession()
    }

    override fun addCallback(cb: Session.Callback) {
        sessionCallbacks.add(cb)
    }

    override fun removeCallback(cb: Session.Callback) {
        sessionCallbacks.remove(cb)
    }

    override fun peerMeta(): Session.PeerMeta? = peerMeta

    override fun approvedAccounts(): List<String>? = approvedAccounts

    override fun init() {
        if (transport.connect()) {
            // Register for all messages for this client
            transport.send(
                Session.Transport.Message(
                    config.handshakeTopic, "sub", ""
                )
            )
        }
    }

    override fun approve(accounts: List<String>, chainId: Long) {
        val handshakeId = handshakeId ?: return
        approvedAccounts = accounts
        // We should not use classes in the Response, since this will not work with proguard
        val params = Session.SessionParams(true, chainId, accounts, null).intoMap()
        send(Session.MethodCall.Response(handshakeId, params))
        storeSession()
        sessionCallbacks.forEach { nullOnThrow { it.sessionApproved() } }
    }

    override fun update(accounts: List<String>, chainId: Long) {
        val params = Session.SessionParams(true, chainId, accounts, null)
        send(Session.MethodCall.SessionUpdate(createCallId(), params))
    }

    override fun reject() {
        handshakeId?.let {
            // We should not use classes in the Response, since this will not work with proguard
            val params = Session.SessionParams(false, null, null, null).intoMap()
            send(Session.MethodCall.Response(it, params))
        }
        endSession()
    }

    override fun approveRequest(id: Long, response: Any) {
        send(Session.MethodCall.Response(id, response))
    }

    override fun rejectRequest(id: Long, errorCode: Long, errorMsg: String) {
        send(Session.MethodCall.Response(id, result = null, error = Session.Error(errorCode, errorMsg)))
    }

    private fun handleStatus(status: Session.Transport.Status) {
        when (status) {
            Session.Transport.Status.CONNECTED ->
                // Register for all messages for this client
                transport.send(
                    Session.Transport.Message(
                        clientData.id, "sub", ""
                    )
                )
            Session.Transport.Status.DISCONNECTED -> {
            } // noop
        }
    }

    private fun handleMessage(message: Session.Transport.Message) {
        if (message.type != "pub") return
        val data: Session.MethodCall
        synchronized(keyLock) {
            try {
                data = payloadAdapter.parse(message.payload, decryptionKey)
            } catch (e: Exception) {
                handlePayloadError(e)
                return
            }
        }
        var accountToCheck: String? = null
        when (data) {
            is Session.MethodCall.SessionRequest -> {
                handshakeId = data.id
                peerId = data.peer.id
                peerMeta = data.peer.meta
                // exchangeKey stores the session no need to do that again
                exchangeKey()
            }
            is Session.MethodCall.SessionUpdate -> {
                if (!data.params.approved) {
                    endSession(data.params.message)
                }
                // TODO handle session update -> not important for our usecase
            }
            is Session.MethodCall.ExchangeKey -> {
                peerId = data.peer.id
                peerMeta = data.peer.meta
                send(Session.MethodCall.Response(data.id, true))
                // swapKeys stores the session no need to do that again
                swapKeys(data.nextKey)
                // TODO: expose peer meta update
            }
            is Session.MethodCall.SendTransaction -> {
                accountToCheck = data.from
            }
            is Session.MethodCall.SignMessage -> {
                accountToCheck = data.address
            }
            is Session.MethodCall.Response -> {
                val callback = requests[data.id] ?: return
                callback(data)
            }
        }

        if (accountToCheck?.let { accountCheck(data.id(), it) } != false) {
            sessionCallbacks.forEach {
                nullOnThrow {
                    it.handleMethodCall(data)
                }
            }
        }
    }

    private fun accountCheck(id: Long, address: String): Boolean {
        approvedAccounts?.find { it.toLowerCase() == address.toLowerCase() } ?: run {
            handlePayloadError(Session.MethodCallException.InvalidAccount(id, address))
            return false
        }
        return true
    }

    private fun handlePayloadError(e: Exception) {
        e.printStackTrace()
        (e as? Session.MethodCallException)?.let {
            rejectRequest(it.id, it.code, it.message ?: "Unknown error")
        }
    }

    private fun endSession(message: String? = null) {
        sessionStore.remove(config.handshakeTopic)
        approvedAccounts = null
        internalClose()
        sessionCallbacks.forEach { nullOnThrow { it.sessionClosed(message) } }
    }

    private fun storeSession() {
        sessionStore.store(
            config.handshakeTopic,
            WCSessionStore.State(
                config,
                clientData,
                peerId?.let { Session.PeerData(it, peerMeta) },
                handshakeId,
                currentKey,
                nextKey,
                approvedAccounts
            )
        )
    }

    private fun generateKey(length: Int = 256) = ByteArray(length / 8).also { SecureRandom().nextBytes(it) }.toHexString()

    private fun exchangeKey() {
        val nextKey = generateKey()
        synchronized(keyLock) {
            this.nextKey = nextKey
            send(
                Session.MethodCall.ExchangeKey(
                    createCallId(),
                    nextKey,
                    clientData
                ),
                forceSend = true // This is an exchange key ... we should force it
            ) {
                if (it.result as? Boolean == true) {
                    swapKeys()
                } else {
                    this.nextKey = null
                    drainQueue()
                }
            }
        }
        storeSession()
    }

    private fun swapKeys(newKey: String? = nextKey) {
        synchronized(keyLock) {
            newKey?.let {
                this.currentKey = it
                // We always reset the nextKey
                nextKey = null
            }
        }
        storeSession()
        drainQueue()
    }

    private fun drainQueue() {
        var method = queue.poll()
        while (method != null) {
            // We could not send it ... bail
            if (!send(method.call, method.topic, false, method.callback)) return
            method = queue.poll()
        }
    }

    // Returns true if method call was handed over to transport
    private fun send(
        msg: Session.MethodCall,
        topic: String? = peerId,
        forceSend: Boolean = false,
        callback: ((Session.MethodCall.Response) -> Unit)? = null
    ): Boolean {
        topic ?: return false
        // Check if key exchange is in progress
        if (!forceSend && nextKey != null) {
            queue.offer(QueuedMethod(topic, msg, callback))
            return false
        }

        val payload: String
        synchronized(keyLock) {
            payload = payloadAdapter.prepare(msg, encryptionKey)
        }
        callback?.let {
            requests[msg.id()] = callback
        }
        transport.send(Session.Transport.Message(topic, "pub", payload))
        return true
    }

    private fun createCallId() = System.currentTimeMillis() * 1000 + Random().nextInt(999)

    private fun internalClose() {
        transport.close()
    }

    override fun kill() {
        reject()
    }

    private data class QueuedMethod(
        val topic: String,
        val call: Session.MethodCall,
        val callback: ((Session.MethodCall.Response) -> Unit)?
    )
}

interface WCSessionStore {
    fun load(id: String): State?

    fun store(id: String, state: State)

    fun remove(id: String)

    fun list(): List<State>

    data class State(
        val config: Session.Config,
        val clientData: Session.PeerData,
        val peerData: Session.PeerData?,
        val handshakeId: Long?,
        val currentKey: String,
        val nextKey: String?,
        val approvedAccounts: List<String>?
    )
}
