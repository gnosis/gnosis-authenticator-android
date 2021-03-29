package io.gnosis.safe.ui.dialogs

import androidx.lifecycle.ViewModel
import io.gnosis.data.repositories.UnstoppableDomainsRepository
import pm.gnosis.model.Solidity
import javax.inject.Inject

class UnstoppableInputViewModel
@Inject constructor(
        private val unstoppableRepository: UnstoppableDomainsRepository
) : ViewModel() {

    suspend fun processInput(input: CharSequence): Solidity.Address {
        return kotlin.runCatching {
            unstoppableRepository.resolve(input.toString())
        }
                .onSuccess {
                    it
                }
                .onFailure {
                   throw it
                }
                .getOrNull()!!
    }
}
