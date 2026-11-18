@file:Suppress("DEPRECATION") // 1.1.0 is the newest stable of security-crypto; every API in it is
// deprecated upstream (see docs/m5/security.md), and the milestone requires EncryptedSharedPreferences.

package vn.edu.vlu.trippocket.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * M5 stage 5: [SessionStore] in [EncryptedSharedPreferences], so the only file the app writes
 * about a session is ciphertext.
 *
 * - File `shared_prefs/trippocket_session.xml`; keys are AES256_SIV, values AES256_GCM.
 * - The key itself lives in the AndroidKeyStore (`MasterKey`, AES256_GCM), never in the APK and
 *   never in the file.
 * - Three values only: `last_email`, `last_uid`, `last_login_at`. No password, no token.
 *
 * The constructor opens the keystore, which is why [vn.edu.vlu.trippocket.di.AppContainer] creates
 * this lazily: Robolectric has no AndroidKeyStore, so an early instance would break every unit
 * test. For the same reason no test touches this class; tests use a fake [SessionStore].
 */
@Suppress("DEPRECATION") // see the file-level note above
class EncryptedSessionStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : SessionStore {

    /** Null when the keystore stays unusable after one retry: the app runs, it just forgets. */
    private val prefs: SharedPreferences? = open(context.applicationContext)

    override fun lastEmail(): String? = read { getString(KEY_EMAIL, null) }

    override fun remember(email: String, uid: String) = save {
        putString(KEY_EMAIL, email)
        putString(KEY_UID, uid)
        putLong(KEY_LOGIN_AT, now())
    }

    override fun clear() = save { this.clear() }

    private fun <T> read(block: SharedPreferences.() -> T?): T? {
        val prefs = prefs ?: return null
        return try {
            prefs.block()
        } catch (e: Exception) {
            // A keystore reset leaves unreadable ciphertext behind; "nothing stored" is the
            // honest answer, and the next remember() overwrites it.
            Log.w(TAG, "Cannot read the encrypted session", e)
            null
        }
    }

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        val prefs = prefs ?: return
        try {
            // KTX edit(): commits with apply() by default, so the caller on Dispatchers.IO never
            // blocks the UI thread waiting for the file.
            prefs.edit { block() }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write the encrypted session", e)
        }
    }

    /**
     * A keystore reset (seen on Android 12) leaves a file that cannot be decrypted. Deleting it
     * and creating it again recovers; if even that fails the store is disabled for this launch
     * instead of crashing the app on start-up.
     */
    private fun open(context: Context): SharedPreferences? = try {
        build(context)
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted session store unusable, recreating", e)
        context.deleteSharedPreferences(FILE)
        try {
            build(context)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted session store disabled for this launch", e)
            null
        }
    }

    private fun build(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        /** Name inside `shared_prefs/`; docs/m5/security.md shows its ciphertext. */
        const val FILE = "trippocket_session"

        private const val KEY_EMAIL = "last_email"
        private const val KEY_UID = "last_uid"
        private const val KEY_LOGIN_AT = "last_login_at"
        private const val TAG = "SessionStore"
    }
}
