package pm.gnosis.android.app.wallet.ui.tokens

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.dialog_token_add_input.view.*
import kotlinx.android.synthetic.main.dialog_token_info.view.*
import kotlinx.android.synthetic.main.fragment_tokens.*
import pm.gnosis.android.app.wallet.R
import pm.gnosis.android.app.wallet.data.db.ERC20Token
import pm.gnosis.android.app.wallet.di.component.ApplicationComponent
import pm.gnosis.android.app.wallet.di.component.DaggerViewComponent
import pm.gnosis.android.app.wallet.di.module.ViewModule
import pm.gnosis.android.app.wallet.ui.base.BaseFragment
import pm.gnosis.android.app.wallet.util.*
import pm.gnosis.android.app.wallet.util.zxing.ZxingIntentIntegrator
import timber.log.Timber
import javax.inject.Inject

class TokensFragment : BaseFragment() {
    @Inject lateinit var presenter: TokensPresenter
    @Inject lateinit var adapter: TokensAdapter

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater?.inflate(R.layout.fragment_tokens, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_tokens_input_address.setOnClickListener {
            fragment_tokens_fab.close(true)
            showTokenAddressInputDialog()
        }

        fragment_tokens_scan_qr_code.setOnClickListener {
            fragment_tokens_fab.close(true)
            val integrator = ZxingIntentIntegrator(this)
            integrator.initiateScan(ZxingIntentIntegrator.QR_CODE_TYPES)
        }

        fragment_tokens_list.layoutManager = LinearLayoutManager(context)
        fragment_tokens_list.adapter = adapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ZxingIntentIntegrator.REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.hasExtra(ZxingIntentIntegrator.SCAN_RESULT_EXTRA)) {
                val scanResult = data.getStringExtra(ZxingIntentIntegrator.SCAN_RESULT_EXTRA)
                if (scanResult.isValidEthereumAddress()) {
                    showTokenAddressInputDialog(scanResult)
                } else {
                    snackbar(fragment_tokens_coordinator_layout, "Invalid address")
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                snackbar(fragment_tokens_coordinator_layout, "Cancelled by the user")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        disposables += presenter.observeTokens()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = this::onTokensList, onError = this::onTokensListError)

        disposables += adapter.tokensSelection
                .flatMap {
                    presenter.observeTokenInfo(it)
                            ?.observeOn(AndroidSchedulers.mainThread())
                            ?.doOnSubscribe { onTokenInfoLoading(true) }
                            ?.doOnTerminate { onTokenInfoLoading(false) }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = this::onTokenInfo, onError = this::onTokenInfoError)
    }

    private fun onTokensList(tokens: List<ERC20Token>) {
        adapter.setItems(tokens)
        fragment_tokens_empty_view.visibility = if (tokens.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onTokensListError(throwable: Throwable) {
        Timber.e(throwable)
    }

    private fun onTokenInfo(token: ERC20.Token) {
        if (token.decimals == null && token.name == null && token.symbol == null) {
            snackbar(fragment_tokens_coordinator_layout, "Could not get token information")
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_token_info, null)

        if (token.decimals != null) {
            dialogView.dialog_token_info_decimals.text = token.decimals.asDecimalString()
        } else {
            dialogView.dialog_token_info_decimals_container.visibility = View.GONE
        }

        dialogView.dialog_token_info_address.text = token.address
        dialogView.dialog_token_info_symbol.text = token.symbol

        AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(token.name)
                .show()
    }

    private fun showTokenAddressInputDialog(withAddress: String = "") {
        val dialogView = layoutInflater.inflate(R.layout.dialog_token_add_input, null)

        if (!withAddress.isNullOrEmpty()) {
            dialogView.dialog_token_add_address.setText(withAddress)
            dialogView.dialog_token_add_address.isEnabled = false
            dialogView.dialog_token_add_address.inputType = InputType.TYPE_NULL
        }

        val dialog = AlertDialog.Builder(context)
                .setTitle("Add a new ERC20 Token")
                .setView(dialogView)
                .setNegativeButton("Cancel", { _, _ -> })
                .setPositiveButton("Add", { _, _ -> })
                .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val address = dialogView.dialog_token_add_address.text.toString()
            val name = dialogView.dialog_token_add_name.text.toString()
            if (address.isValidEthereumAddress()) {
                disposables += addTokenDisposable(address, name)
                dialog.dismiss()
            } else {
                context.toast("Invalid ethereum address")
            }
        }
    }

    private fun onTokenInfoLoading(isLoading: Boolean) {
        adapter.itemsClickable = !isLoading
        fragment_tokens_progress_bar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun onTokenInfoError(throwable: Throwable) {
        Timber.e(throwable)
    }

    private fun addTokenDisposable(address: String, name: String) =
            presenter.addToken(address, name).observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onComplete = this::onTokenAdded, onError = this::onTokenAddError)

    private fun onTokenAdded() {
        snackbar(fragment_tokens_coordinator_layout, "Token added")
    }

    private fun onTokenAddError(throwable: Throwable) {
        Timber.e(throwable)
        if (throwable is SQLiteConstraintException) {
            snackbar(fragment_tokens_coordinator_layout, "Token already stored")
        }
    }

    override fun inject(component: ApplicationComponent) {
        DaggerViewComponent.builder()
                .applicationComponent(component)
                .viewModule(ViewModule(this.context))
                .build().inject(this)
    }
}
