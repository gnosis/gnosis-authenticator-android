package io.gnosis.safe.ui.safe.settings.safe

import io.gnosis.data.models.SafeInfo
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import javax.inject.Inject

class AdvancedSafeSettingsViewModel
@Inject constructor(
    private val safeRepository: SafeRepository,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<AdvancedSafeSettingsState>(appDispatchers) {

    override fun initialState(): AdvancedSafeSettingsState = AdvancedSafeSettingsState()

    fun load() {
        safeLaunch {
            safeRepository.getActiveSafe()?.let { safe ->
                val safeInfo = safeRepository.getSafeInfo(safe.address)
                updateState {
                    AdvancedSafeSettingsState(
                        isLoading = false,
                        viewAction = LoadSafeInfo(safeInfo)
                    )
                }
            }
        }
    }
}

data class LoadSafeInfo(
    val safeInfo: SafeInfo
) : BaseStateViewModel.ViewAction

data class AdvancedSafeSettingsState(
    val isLoading: Boolean = true,
    override var viewAction: BaseStateViewModel.ViewAction? = null
) : BaseStateViewModel.State
