package io.gnosis.safe.ui.safe.terms

import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.gnosis.safe.HeimdallApplication
import io.gnosis.safe.R
import io.gnosis.safe.databinding.BottomSheetTermsAndConditionsBinding
import io.gnosis.safe.di.components.DaggerViewComponent
import io.gnosis.safe.di.modules.ViewModule
import io.gnosis.safe.ui.safe.terms.TermsViewModel.ViewAction.ShowBottomSheet
import io.gnosis.safe.ui.safe.terms.TermsViewModel.ViewAction.TermsAgreed
import javax.inject.Inject

class TermsBottomSheetDialog(activity: AppCompatActivity) : BottomSheetDialog(activity) {

    private lateinit var advance: () -> Unit
    private var binding: BottomSheetTermsAndConditionsBinding

    @Inject
    lateinit var viewModel: TermsViewModel

    init {
        inject()

        binding = BottomSheetTermsAndConditionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomSheetTermsAndConditionsPrivacyPolicyLink.text = Html.fromHtml(activity.getString(R.string.terms_privacy_link))
        binding.bottomSheetTermsAndConditionsPrivacyPolicyLink.movementMethod = LinkMovementMethod.getInstance()

        binding.bottomSheetTermsAndConditionsPrivacyPolicyLink.text = Html.fromHtml(activity.getString(R.string.terms_terms_of_use_link))
        binding.bottomSheetTermsAndConditionsPrivacyPolicyLink.movementMethod = LinkMovementMethod.getInstance()

        binding.bottomSheetTermsAndConditionsAgree.setOnClickListener {
            onAgree()
        }

        binding.bottomSheetTermsAndConditionsReject.setOnClickListener {
            onReject()
        }

        viewModel.state.observe(activity, Observer { state ->
            when (state.viewAction) {
                ShowBottomSheet -> show()
                TermsAgreed -> dismissAndAdvance()
            }
        })
    }

    private fun dismissAndAdvance() {
        if (this.isShowing) {
            dismiss()
        }
        advance()
    }

    private fun onAgree() {
        viewModel.onAgreeClicked()
    }

    private fun onReject() {
        dismiss()
    }

    private fun inject() {
        DaggerViewComponent.builder()
            .applicationComponent(HeimdallApplication[context])
            .viewModule(ViewModule(context))
            .build().inject(this)
    }

    fun checkTerms(function: () -> Unit) {
        viewModel.checkTerms()
        advance = function
    }
}
