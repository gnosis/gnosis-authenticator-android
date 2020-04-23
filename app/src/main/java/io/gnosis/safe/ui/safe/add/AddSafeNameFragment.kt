package io.gnosis.safe.ui.safe.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.R
import io.gnosis.safe.databinding.FragmentAddSafeNameBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseFragment
import io.gnosis.safe.ui.base.BaseStateViewModel
import kotlinx.android.synthetic.main.fragment_add_safe.*
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import javax.inject.Inject

class AddSafeNameFragment : BaseFragment<FragmentAddSafeNameBinding>() {

    @Inject
    lateinit var viewModel: AddSafeNameViewModel

    private val navArgs by navArgs<AddSafeNameFragmentArgs>()
    private val newAddress by lazy { navArgs.newAddress.asEthereumAddress()!! }

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAddSafeNameBinding =
        FragmentAddSafeNameBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            newAddressBlockies.setAddress(newAddress)
            newAddressHex.text = newAddress.asEthereumAddressString()
            backButton.setOnClickListener { findNavController().navigateUp() }
            nextButton.setOnClickListener { viewModel.submitAddressAndName(newAddress, addSafeNameEntry.text.toString()) }
        }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is CaptureSafeName -> {
                    state.viewAction?.let { action ->
                        when (action) {
                            is BaseStateViewModel.ViewAction.CloseScreen -> findNavController().navigateUp()
                            is BaseStateViewModel.ViewAction.Loading -> binding.progress.visible(action.isLoading)
                            is BaseStateViewModel.ViewAction.ShowError -> {
                                progress.visible(false)
                                binding.addSafeNameEntry.error = "Error adding safe"
                                Timber.e(action.error)
                            }
                            else -> Timber.i("Unsupported action by view: $action")
                        }
                    }
                }
            }
        })
    }
}
