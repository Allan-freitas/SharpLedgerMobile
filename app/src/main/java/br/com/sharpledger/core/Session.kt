package br.com.sharpledger.core

import android.content.Context
import android.content.SharedPreferences
import br.com.sharpledger.data.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(
    val token: String?,
    val user: UserProfile?,
    val baseUrl: String,
    val locale: String,
) {
    val isLoggedIn: Boolean get() = !token.isNullOrBlank() && user != null

    val permissions: List<String> get() = user?.permissions.orEmpty()

    fun can(permission: String): Boolean = permissions.contains(permission)
}

object AppPrefs {
    private const val FILE = "sharpledger_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_LOCALE = "locale"

    const val DEFAULT_BASE_URL = "https://agfapp.com/"
    const val DEFAULT_LOCALE = "pt-BR"

    fun get(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun locale(context: Context): String =
        get(context).getString(KEY_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE

    fun normalizeBaseUrl(value: String): String {
        var url = value.trim()
        if (url.isEmpty()) return DEFAULT_BASE_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return url
    }
}

class SessionManager(context: Context) {
    private val prefs = AppPrefs.get(context)
    private val gson = Gson()

    private val mutableState = MutableStateFlow(readState().also { migrateLegacyBaseUrl(it) })

    private fun migrateLegacyBaseUrl(state: SessionState): SessionState {
        if (state.baseUrl != LEGACY_EMULATOR_BASE_URL) {
            return state
        }

        prefs.edit().putString("base_url", AppPrefs.DEFAULT_BASE_URL).apply()
        return state.copy(baseUrl = AppPrefs.DEFAULT_BASE_URL)
    }

    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private fun readState(): SessionState {
        val userJson = prefs.getString("user", null)
        val user = userJson?.let {
            runCatching { gson.fromJson(it, UserProfile::class.java) }.getOrNull()
        }

        return SessionState(
            token = prefs.getString("token", null),
            user = user,
            baseUrl = prefs.getString("base_url", AppPrefs.DEFAULT_BASE_URL)
                ?: AppPrefs.DEFAULT_BASE_URL,
            locale = prefs.getString("locale", AppPrefs.DEFAULT_LOCALE)
                ?: AppPrefs.DEFAULT_LOCALE,
        )
    }

    fun token(): String? = mutableState.value.token

    private companion object {
        const val LEGACY_EMULATOR_BASE_URL = "http://10.0.2.2:5080/"
    }

    fun saveAuth(token: String, user: UserProfile) {
        prefs.edit()
            .putString("token", token)
            .putString("user", gson.toJson(user))
            .apply()

        mutableState.value = mutableState.value.copy(token = token, user = user)
    }

    fun updateUser(user: UserProfile) {
        prefs.edit().putString("user", gson.toJson(user)).apply()
        mutableState.value = mutableState.value.copy(user = user)
    }

    fun saveBaseUrl(url: String) {
        val normalized = AppPrefs.normalizeBaseUrl(url)
        prefs.edit().putString("base_url", normalized).apply()
        mutableState.value = mutableState.value.copy(baseUrl = normalized)
    }

    fun saveLocale(locale: String) {
        prefs.edit().putString("locale", locale).apply()
        mutableState.value = mutableState.value.copy(locale = locale)
    }

    fun expire() {
        prefs.edit().remove("token").remove("user").apply()
        mutableState.value = mutableState.value.copy(token = null, user = null)
    }

    fun logout() = expire()
}
