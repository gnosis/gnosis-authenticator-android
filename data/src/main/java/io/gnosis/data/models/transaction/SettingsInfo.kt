package io.gnosis.data.models.transaction

import com.squareup.moshi.Json
import pm.gnosis.model.Solidity

enum class SettingsInfoType {
    SET_FALLBACK_HANDLER,
    ADD_OWNER,
    REMOVE_OWNER,
    SWAP_OWNER,
    CHANGE_THRESHOLD,
    CHANGE_IMPLEMENTATION,
    ENABLE_MODULE,
    DISABLE_MODULE
}

sealed class SettingsInfo(
    @Json(name = "type") val type: SettingsInfoType
) {

    data class SetFallbackHandler(
        @Json(name = "handler") val handler: Solidity.Address
    ) : SettingsInfo(SettingsInfoType.SET_FALLBACK_HANDLER)

    data class AddOwner(
        @Json(name = "owner") val owner: Solidity.Address,
        @Json(name = "threshold") val threshold: Long
    ) : SettingsInfo(SettingsInfoType.ADD_OWNER)

    data class RemoveOwner(
        @Json(name = "owner") val owner: Solidity.Address,
        @Json(name = "threshold") val threshold: Long
    ) : SettingsInfo(SettingsInfoType.REMOVE_OWNER)

    data class SwapOwner(
        @Json(name = "oldOwner") val oldOwner: Solidity.Address,
        @Json(name = "newOwner") val newOwner: Solidity.Address
    ) : SettingsInfo(SettingsInfoType.SWAP_OWNER)

    data class ChangeThreshold(
        @Json(name = "threshold") val threshold: Long
    ) : SettingsInfo(SettingsInfoType.CHANGE_THRESHOLD)

    data class ChangeImplementation(
        @Json(name = "implementation") val implementation: Solidity.Address
    ) : SettingsInfo(SettingsInfoType.CHANGE_IMPLEMENTATION)

    data class EnableModule(
        @Json(name = "module") val module: Solidity.Address
    ) : SettingsInfo(SettingsInfoType.ENABLE_MODULE)

    data class DisableModule(
        @Json(name = "module") val module: Solidity.Address
    ) : SettingsInfo(SettingsInfoType.DISABLE_MODULE)
}
