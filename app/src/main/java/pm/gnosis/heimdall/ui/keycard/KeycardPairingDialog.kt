package pm.gnosis.heimdall.ui.keycard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import im.status.keycard.applet.KeycardCommandSet
import kotlinx.android.synthetic.main.screen_keycard_pairing_input.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.rx2.await
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.AccountsRepository
import pm.gnosis.heimdall.data.repositories.CardRepository
import pm.gnosis.heimdall.data.repositories.impls.StatusKeyCardManager
import pm.gnosis.heimdall.di.ApplicationContext
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.di.modules.ApplicationModule
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.ui.base.BaseStateViewModel
import pm.gnosis.heimdall.utils.AuthenticatorInfo
import pm.gnosis.heimdall.utils.AuthenticatorSetupInfo
import pm.gnosis.heimdall.utils.getAuthenticatorInfo
import pm.gnosis.heimdall.utils.toKeyIndex
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.transaction
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject

@ExperimentalCoroutinesApi
abstract class KeycardPairingContract(
    context: Context,
    appDispatchers: ApplicationModule.AppCoroutineDispatchers
) : BaseStateViewModel<KeycardPairingContract.State>(context, appDispatchers) {

    abstract fun setup(safe: Solidity.Address?)

    abstract fun handleInitResult(authenticatorInfo: AuthenticatorSetupInfo)

    abstract fun callback(): NfcAdapter.ReaderCallback

    abstract fun startPairing(pin: String, pairingKey: String)

    abstract fun stopPairing()

    sealed class State : BaseStateViewModel.State {
        data class WaitingForInput(val pinError: String?, val pairingKeyError: String?, override var viewAction: ViewAction?) : State() {
            override fun withAction(viewAction: ViewAction?) = copy(viewAction = viewAction)
        }

        data class ReadingCard(val reading: Boolean, val error: String?, override var viewAction: ViewAction?) : State() {
            override fun withAction(viewAction: ViewAction?) = copy(viewAction = viewAction)
        }

        data class CardBlocked(override var viewAction: ViewAction?) : State() {
            override fun withAction(viewAction: ViewAction?) = copy(viewAction = viewAction)
        }

        data class NoSlotsAvailable(override var viewAction: ViewAction?) : State() {
            override fun withAction(viewAction: ViewAction?) = copy(viewAction = viewAction)
        }

        data class PairingDone(val authenticatorInfo: AuthenticatorSetupInfo, override var viewAction: ViewAction?) : State() {
            override fun withAction(viewAction: ViewAction?) = copy(viewAction = viewAction)
        }

        abstract fun withAction(viewAction: ViewAction?): State
    }
}

@ExperimentalCoroutinesApi
class KeycardPairingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    appDispatchers: ApplicationModule.AppCoroutineDispatchers,
    private val accountsRepository: AccountsRepository,
    private val cardRepository: CardRepository
) : KeycardPairingContract(context, appDispatchers) {

    override val state: LiveData<State> = liveData {
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private val manager = WrappedManager()

    private var safe: Solidity.Address? = null

    override fun setup(safe: Solidity.Address?) {
        this.safe = safe
    }

    override fun callback(): NfcAdapter.ReaderCallback = manager.callback()

    override fun handleInitResult(authenticatorInfo: AuthenticatorSetupInfo) {
        safeLaunch {
            updateState { State.PairingDone(authenticatorInfo, null) }
        }
    }

    override fun startPairing(pin: String, pairingKey: String) {
        safeLaunch {
            updateState { State.ReadingCard(false, null, null) }
            manager.performOnChannel {
                safeLaunch {
                    updateState { State.ReadingCard(true, null, null) }
                    try {
                        val safeOwner = (safe?.let { accountsRepository.signingOwner(it) } ?: accountsRepository.createOwner()).await()
                        val keyIndex = safeOwner.address.toKeyIndex()
                        // TODO: add proper exceptions to handle different cases
                        val cardAddress =
                            cardRepository.pairCard(
                                StatusKeyCardManager(KeycardCommandSet(it)),
                                StatusKeyCardManager.PairingParams(pin, pairingKey),
                                "",
                                keyIndex
                            )
                        val authenticatorInfo = AuthenticatorSetupInfo(
                            safeOwner,
                            AuthenticatorInfo(
                                AuthenticatorInfo.Type.KEYCARD,
                                cardAddress,
                                keyIndex
                            )
                        )
                        updateState { State.PairingDone(authenticatorInfo, null) }
                    } catch (e: Exception) {
                        updateState { State.ReadingCard(true, e.message, null) }
                    }
                }
            }
        }
    }

    override fun stopPairing() {
        safeLaunch {
            updateState { withAction(ViewAction.CloseScreen) }
        }
    }

    override fun initialState(): State = State.WaitingForInput(null, null, null)
}

@ExperimentalCoroutinesApi
abstract class KeycardPairingBaseFragment : KeycardBaseFragment<KeycardPairingContract.State, KeycardPairingContract>() {

    @Inject
    override lateinit var viewModel: KeycardPairingContract
}

@ExperimentalCoroutinesApi
class KeycardPairingReadingCardFragment : KeycardPairingBaseFragment(), ReadingCardScreen {
    override val layout = R.layout.screen_keycard_reading

    override val screen: View
        get() = view!!

    override val appContext: Context
        get() = context!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView(cancelListener = { viewModel.stopPairing() })
    }

    override fun updateState(state: KeycardPairingContract.State) {
        if (state !is KeycardPairingContract.State.ReadingCard) return
        updateView(state.reading, state.error)
    }

    override fun inject(component: ViewComponent) = component.inject(this)
}

@ExperimentalCoroutinesApi
class KeycardPairingInputFragment private constructor() : KeycardPairingBaseFragment() {

    override val layout = R.layout.screen_keycard_pairing_input

    override fun inject(component: ViewComponent) = component.inject(this)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_INIT) {
            if (resultCode == Activity.RESULT_OK && data != null)
                data.getAuthenticatorInfo()?.let { viewModel.handleInitResult(it) }
        } else
            super.onActivityResult(requestCode, resultCode, data)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.keycard_pairing_input_init_button.setOnClickListener {
            startActivityForResult(
                KeycardInitializeActivity.createIntent(context!!, arguments?.getString(ARGUMENTS_SAFE)?.asEthereumAddress()),
                REQUEST_CODE_INIT
            )
        }
        view.keycard_pairing_input_pair_button.setOnClickListener {
            val pin = view.keycard_pairing_input_pin.text.toString()
            val pairingKey = view.keycard_pairing_input_pairing_key.text.toString()
            viewModel.startPairing(pin, pairingKey)
        }
    }

    override fun updateState(state: KeycardPairingContract.State) {
        if (state !is KeycardPairingContract.State.WaitingForInput) return
        view!!.keycard_pairing_input_pin_error.apply {
            visible(state.pinError != null)
            text = state.pinError
        }
        view!!.keycard_pairing_input_pairing_key_error.apply {
            visible(state.pairingKeyError != null)
            text = state.pairingKeyError
        }
    }

    companion object {
        private const val REQUEST_CODE_INIT = 4521
        private const val ARGUMENTS_SAFE = "argument.string.safe"

        fun create(safe: Solidity.Address?) =
            KeycardPairingInputFragment().apply {
                arguments = Bundle().apply {
                    putString(ARGUMENTS_SAFE, safe?.asEthereumAddressString())
                }
            }
    }
}

@ExperimentalCoroutinesApi
class KeycardPairingDialog private constructor(): DialogFragment() {

    @Inject
    lateinit var viewModel: KeycardPairingContract

    private var currentState: KeycardPairingContract.State? = null

    private lateinit var adapter: NfcAdapter
    private var safe: Solidity.Address? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject()
        safe = arguments?.getString(ARGUMENTS_SAFE)?.asEthereumAddress()
        viewModel.setup(safe)
        adapter = context?.let { NfcAdapter.getDefaultAdapter(it) } ?: run {
            dismiss()
            return
        }
    }

    override fun onStart() {
        super.onStart()
        adapter.enableReaderMode(activity!!, viewModel.callback(), NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    override fun onStop() {
        activity?.let { adapter.disableReaderMode(it) }
        super.onStop()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.screen_single_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.state.observe(this, Observer { updateState(it) })
    }

    private fun updateState(state: KeycardPairingContract.State) {
        if (state.viewAction == BaseStateViewModel.ViewAction.CloseScreen) {
            dismiss()
            return
        }
        if (state == currentState) return
        currentState = state
        when (state) {
            is KeycardPairingContract.State.WaitingForInput -> KeycardPairingInputFragment.create(safe)
            is KeycardPairingContract.State.ReadingCard -> KeycardPairingReadingCardFragment()
            is KeycardPairingContract.State.CardBlocked -> TODO()
            is KeycardPairingContract.State.NoSlotsAvailable -> TODO()
            is KeycardPairingContract.State.PairingDone -> {
                ((activity ?: context) as? PairingCallback)?.onPaired(state.authenticatorInfo)
                dismiss()
                null
            }
        }?.let {
            childFragmentManager.transaction { replace(R.id.single_fragment_content, it) }
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun inject() {
        DaggerViewComponent.builder()
            .viewModule(ViewModule(context!!, this))
            .applicationComponent(HeimdallApplication[context!!])
            .build()
            .inject(this)
    }

    interface PairingCallback {
        fun onPaired(authenticatorInfo: AuthenticatorSetupInfo)
    }

    companion object {
        private const val ARGUMENTS_SAFE = "argument.string.safe"

        fun create(safe: Solidity.Address?) =
            KeycardPairingDialog().apply {
                arguments = Bundle().apply {
                    putString(ARGUMENTS_SAFE, safe?.asEthereumAddressString())
                }
            }
    }
}