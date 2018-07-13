package pm.gnosis.heimdall.ui.safe.recover.extension

import android.content.Context
import android.content.Intent
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.ui.safe.pairing.PairingActivity
import pm.gnosis.heimdall.ui.safe.recover.phrase.RecoverInputRecoveryPhraseActivity
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString

class RecoverPairingActivity: PairingActivity() {
    override fun titleRes(): Int = R.string.recover_safe

    override fun onSuccess(extension: Solidity.Address) {
        val safeAddress = intent.getStringExtra(EXTRA_SAFE_ADDRESS).asEthereumAddress()!!
        startActivity(RecoverInputRecoveryPhraseActivity.createIntent(this, safeAddress, extension))
    }

    companion object {

        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"

        fun createIntent(context: Context, safeAddress: Solidity.Address) =
            Intent(context, RecoverPairingActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, safeAddress.asEthereumAddressString())
            }
    }
}
