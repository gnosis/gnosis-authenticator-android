package io.gnosis.safe.ui.settings.app.passcode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentPasscodeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.app.SettingsHandler
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.svalinn.common.utils.visible
import javax.inject.Inject

class ChangeCreatePasscodeFragment : BaseViewBindingFragment<FragmentPasscodeBinding>() {

    override fun screenId() = ScreenId.CHANGE_CREATE_PASSCODE
    private val navArgs by navArgs<ChangeCreatePasscodeFragmentArgs>()
    private val oldPasscode by lazy { navArgs.oldPasscode }

    @Inject
    lateinit var settingsHandler: SettingsHandler

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPasscodeBinding =
        FragmentPasscodeBinding.inflate(inflater, container, false)

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onResume() {
        super.onResume()
        binding.input.showKeyboardForView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {

            status.visibility = View.INVISIBLE

            backButton.setOnClickListener {
                skipPasscodeSetup()
            }

            val digits = listOf(digit1, digit2, digit3, digit4, digit5, digit6)
            input.showKeyboardForView()

            //Disable done button
            input.setOnEditorActionListener { _, actionId, _ ->
                actionId == EditorInfo.IME_ACTION_DONE
            }

            input.doOnTextChanged { text, _, _, _ ->
                text?.let {
                    if (input.text.length < 6) {
                        digits.forEach {
                            it.background = ContextCompat.getDrawable(requireContext(), R.color.surface_01)
                        }
                        (1..text.length).forEach { i ->
                            digits[i - 1].background = ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle_passcode_filled_20dp)
                        }
                    } else {
                        digits[digits.size - 1].background = ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle_passcode_filled_20dp)

                        input.setText("") // So it is empty, when the user navigates back
                        findNavController().navigate(
                            ChangeCreatePasscodeFragmentDirections.actionPasscodeSettingsFragmentToChangeRepeatPasscodeFragment(
                                passcode = text.toString(),
                                oldPasscode = oldPasscode
                            )
                        )
                    }
                }
            }

            // Skip Button
            actionButton.visible(false)
        }
    }

    private fun FragmentPasscodeBinding.skipPasscodeSetup() {

        findNavController().popBackStack(R.id.changePasscodeFragment, true)
        findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.OWNER_IMPORT_RESULT, false)
        findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.PASSCODE_SET_RESULT, false)
    }
}