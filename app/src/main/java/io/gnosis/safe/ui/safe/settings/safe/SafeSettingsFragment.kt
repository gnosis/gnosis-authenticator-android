package io.gnosis.safe.ui.safe.settings.safe

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import io.gnosis.data.models.Safe
import io.gnosis.data.models.SafeInfo
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentSettingsSafeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.BaseViewBindingFragment
import io.gnosis.safe.ui.safe.settings.view.SettingItem
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.snackbar
import pm.gnosis.svalinn.common.utils.visible
import timber.log.Timber
import javax.inject.Inject

class SafeSettingsFragment : BaseViewBindingFragment<FragmentSettingsSafeBinding>() {

    override fun screenId() = ScreenId.SETTINGS_SAFE

    @Inject
    lateinit var viewModel: SafeSettingsViewModel

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsSafeBinding =
        FragmentSettingsSafeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.remove.setOnClickListener {
            AlertDialog.Builder(requireContext()).apply {
                setMessage(R.string.safe_settings_dialog_description)
                setNegativeButton(R.string.safe_settings_dialog_cancel) { dialog, _ -> dialog.dismiss() }
                setPositiveButton(R.string.safe_settings_dialog_remove) { _, _ -> viewModel.removeSafe() }
            }.create().show()
        }
        viewModel.state.observe(viewLifecycleOwner, Observer {
            when (val viewAction = it.viewAction) {
                is BaseStateViewModel.ViewAction.Loading -> loadDetails(viewAction.isLoading, it.safe, it.safeInfo, it.ensName)
                is BaseStateViewModel.ViewAction.ShowError -> showError(viewAction.error)
            }
        })
    }

    private fun loadDetails(isLoading: Boolean, safe: Safe?, safeInfo: SafeInfo?, ensNameValue: String?) {
        with(binding) {
            if (isLoading) {
                progress.visible(true)
                mainContainer.visible(false)
            } else {
                progress.visible(false)
                mainContainer.visible(true)
                localName.name = safe?.localName
                threshold.name = getString(R.string.safe_settings_confirmations_required, safeInfo?.threshold, safeInfo?.owners?.size)
                ownersContainer.removeAllViews()
                safeInfo?.owners?.forEach { owner -> ownersContainer.addView(ownerView(owner)) }
                ensName.name = ensNameValue?.takeUnless { it.isBlank() } ?: getString(R.string.safe_settings_not_set)
            }
        }
    }

    private fun ownerView(owner: Solidity.Address): SettingItem {
        return SettingItem(requireContext()).apply {
            background = ContextCompat.getDrawable(requireContext(), R.drawable.background_selectable_white)
            name = owner.asEthereumAddressChecksumString()
        }
    }

    private fun showError(throwable: Throwable) {
        with(binding) {
            mainContainer.visible(false)
            progress.visible(false)
        }
        snackbar(requireView(), throwable.message ?: getString(R.string.error_invalid_safe))
        Timber.e(throwable)
    }

    companion object {

        fun newInstance(): SafeSettingsFragment {
            return SafeSettingsFragment()
        }
    }
}
