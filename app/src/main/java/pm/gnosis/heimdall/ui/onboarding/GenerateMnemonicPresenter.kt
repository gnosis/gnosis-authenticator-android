package pm.gnosis.heimdall.ui.onboarding

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import pm.gnosis.heimdall.accounts.repositories.AccountsRepository
import pm.gnosis.heimdall.common.di.ForView
import pm.gnosis.heimdall.common.util.Result
import pm.gnosis.mnemonic.Bip39
import javax.inject.Inject

@ForView
class GenerateMnemonicPresenter @Inject constructor(private val accountsRepository: AccountsRepository) {
    fun generateMnemonic(): Observable<Result<String>> =
            Observable.fromCallable { Bip39.generateMnemonic() }
                    .map { Result(data = it) }
                    .onErrorReturn { Result(error = it) }
                    .subscribeOn(Schedulers.io())

    fun saveAccountWithMnemonic(mnemonic: String): Completable =
            accountsRepository.saveAccountFromMnemonic(mnemonic).andThen(accountsRepository.saveMnemonic(mnemonic))
                    .subscribeOn(Schedulers.io())
}
