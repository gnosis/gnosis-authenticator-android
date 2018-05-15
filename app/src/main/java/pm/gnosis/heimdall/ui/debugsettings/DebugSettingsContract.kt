package pm.gnosis.heimdall.ui.debugsettings

import android.arch.lifecycle.ViewModel
import io.reactivex.Completable
import io.reactivex.Single
import pm.gnosis.svalinn.common.utils.Result

abstract class DebugSettingsContract : ViewModel() {
    abstract fun forceSyncAuthentication()
    abstract fun pair(payload: String): Completable
    abstract fun sendTestSafeCreationPush(chromeExtensionAddress: String): Single<Result<Unit>>
}
