package com.diagnostico.senales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.diagnostico.senales.data.prefs.AppPreferences
import com.diagnostico.senales.ui.components.DismissedNotesProvider
import com.diagnostico.senales.ui.nav.MainScaffold
import com.diagnostico.senales.ui.privacy.PrivacyModeProvider
import com.diagnostico.senales.ui.theme.SenalesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as DiagnosticsApplication).container

        setContent {
            val preferences = remember { container.preferences }
            var themeMode by remember {
                mutableStateOf(preferences.themeMode)
            }
            var privacyMode by remember {
                mutableStateOf(preferences.privacyMode)
            }
            val darkTheme = when (themeMode) {
                AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppPreferences.ThemeMode.LIGHT -> false
                AppPreferences.ThemeMode.DARK -> true
            }

            SenalesTheme(darkTheme = darkTheme) {
                DismissedNotesProvider(store = container.dismissedNotes) {
                    PrivacyModeProvider(enabled = privacyMode) {
                        MainScaffold(
                            preferences = preferences,
                            themeMode = themeMode,
                            onThemeChange = { mode ->
                                preferences.themeMode = mode
                                themeMode = mode
                            },
                            privacyMode = privacyMode,
                            onPrivacyModeChange = { enabled ->
                                preferences.privacyMode = enabled
                                privacyMode = enabled
                            },
                        )
                    }
                }
            }
        }
    }
}