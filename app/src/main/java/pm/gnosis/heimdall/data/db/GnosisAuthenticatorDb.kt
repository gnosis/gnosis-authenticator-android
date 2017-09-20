package pm.gnosis.heimdall.data.db

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase

@Database(entities = arrayOf(MultisigWallet::class, ERC20Token::class), version = 1)
abstract class GnosisAuthenticatorDb : RoomDatabase() {
    companion object {
        const val DB_NAME = "gnosis-authenticator-db"
    }

    abstract fun multisigWalletDao(): MultisigWalletDao
    abstract fun erc20TokenDao(): ERC20TokenDao
}
