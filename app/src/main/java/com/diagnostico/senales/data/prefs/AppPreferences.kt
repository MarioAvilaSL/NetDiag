package com.diagnostico.senales.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.diagnostico.senales.data.net.SpeedTestEngine

/** Preferencias locales: tema, destinos de prueba y parámetros por defecto. */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var pingTarget: String
        get() = prefs.getString(KEY_PING_TARGET, null) ?: SpeedTestEngine.DEFAULT_PING_TARGET
        set(value) = prefs.edit().putString(KEY_PING_TARGET, value).apply()

    var pingCount: Int
        get() = prefs.getInt(KEY_PING_COUNT, 10)
        set(value) = prefs.edit().putInt(KEY_PING_COUNT, value.coerceIn(3, 50)).apply()

    var downloadUrl: String
        get() = prefs.getString(KEY_DOWNLOAD_URL, null) ?: SpeedTestEngine.DEFAULT_DOWNLOAD_URL
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_URL, value).apply()

    var uploadUrl: String
        get() = prefs.getString(KEY_UPLOAD_URL, null) ?: SpeedTestEngine.DEFAULT_UPLOAD_URL
        set(value) = prefs.edit().putString(KEY_UPLOAD_URL, value).apply()

    var assumedChannelWidthMHz: Int
        get() = prefs.getInt(KEY_CHANNEL_WIDTH, 20)
        set(value) = prefs.edit().putInt(KEY_CHANNEL_WIDTH, value).apply()

    /**
     * Sustituye los datos identificativos por puntos al mostrarlos, para poder
     * hacer capturas sin filtrar la red, el operador ni la posición. Se
     * recuerda entre sesiones: si se activó, sigue activo al reabrir la app.
     */
    var privacyMode: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_MODE, value).apply()

    var stabilitySeconds: Int
        get() = prefs.getInt(KEY_STABILITY_SECONDS, 30)
        set(value) = prefs.edit().putInt(KEY_STABILITY_SECONDS, value).apply()

    enum class ThemeMode(val label: String) {
        SYSTEM("Seguir al sistema"),
        LIGHT("Tema claro"),
        DARK("Tema oscuro"),
    }

    private companion object {
        const val NAME = "app_senales_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_PING_TARGET = "ping_target"
        const val KEY_PING_COUNT = "ping_count"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_UPLOAD_URL = "upload_url"
        const val KEY_CHANNEL_WIDTH = "assumed_channel_width"
        const val KEY_STABILITY_SECONDS = "stability_seconds"
        const val KEY_PRIVACY_MODE = "privacy_mode"
    }
}