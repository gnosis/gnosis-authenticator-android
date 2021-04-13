package io.gnosis.safe.ui.settings.owner

import androidx.annotation.VisibleForTesting
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.PublishViewModel
import pm.gnosis.crypto.KeyPair
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asBigInteger
import pm.gnosis.utils.hexAsBigInteger
import javax.inject.Inject

class OwnerSeedPhraseViewModel
@Inject constructor(
    private val bip39Generator: Bip39,
    private val credentialsRepository: CredentialsRepository,
    appDispatchers: AppDispatchers
) : PublishViewModel<ImportOwnerKeyState>(appDispatchers) {

    fun validate(seedPhraseOrKey: String) {
        if (isPrivateKey(seedPhraseOrKey)) {
            validatePrivateKey(seedPhraseOrKey)
        } else {
            validateSeedPhrase(seedPhraseOrKey)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun isPrivateKey(seedPhraseOrKey: String): Boolean {
        val input = removeHexPrefix(seedPhraseOrKey)

        val pattern = "[0-9a-fA-F]{64}".toRegex()
        pattern.matchEntire(input)?.let {
            return true
        }

        return false
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun validatePrivateKey(key: String) {
        val input = removeHexPrefix(key)

        if (input == "0000000000000000000000000000000000000000000000000000000000000000") {
            safeLaunch {
                updateState { ImportOwnerKeyState.Error(InvalidPrivateKey) }
            }
        } else {

            safeLaunch {
                if (credentialsRepository.owner(Solidity.Address(KeyPair.fromPrivate(input.hexAsBigInteger()).address.asBigInteger())) == null) {
                    updateState { ImportOwnerKeyState.ValidKeySubmitted(input) }
                } else {
                    updateState { ImportOwnerKeyState.Error(KeyAlreadyImported) }
                }
            }
        }
    }

    private fun removeHexPrefix(key: String): String = key.replace("0x", "")

    fun validateSeedPhrase(seedPhrase: String) {
        val cleanedUpSeedPhrase = cleanupSeedPhrase(seedPhrase)
        runCatching { bip39Generator.validateMnemonic(cleanedUpSeedPhrase) }
            .onFailure { safeLaunch { updateState { ImportOwnerKeyState.Error(InvalidSeedPhrase) } } }
            .onSuccess { mnemonic ->
                safeLaunch {
                    updateState {
                        if (cleanedUpSeedPhrase == mnemonic) {
                            ImportOwnerKeyState.ValidSeedPhraseSubmitted(mnemonic)
                        } else {
                            ImportOwnerKeyState.Error(InvalidSeedPhrase)
                        }
                    }
                }
            }
    }

    private fun cleanupSeedPhrase(seedPhrase: String): String {
        return seedPhrase.split("\\s+?|\\p{Punct}+?".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(separator = " ", transform = String::toLowerCase)
    }

}

object InvalidSeedPhrase : Throwable()
object InvalidPrivateKey : Throwable()
object KeyAlreadyImported : Throwable()

sealed class ImportOwnerKeyState(
    override var viewAction: BaseStateViewModel.ViewAction? = null
) : BaseStateViewModel.State {

    data class ValidSeedPhraseSubmitted(val validSeedPhrase: String) : ImportOwnerKeyState()
    data class ValidKeySubmitted(val key: String) : ImportOwnerKeyState()
    data class Error(val throwable: Throwable) : ImportOwnerKeyState(BaseStateViewModel.ViewAction.ShowError(throwable))
}
