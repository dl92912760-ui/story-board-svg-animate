package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppThemeMode
import com.example.core.ThemePreferences
import com.example.core.configureAppCheck
import com.example.ui.navigation.HistoriaRoot
import com.example.ui.theme.HistoriaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val themePrefs = remember { ThemePreferences(applicationContext) }
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()

            HistoriaTheme(themeMode = themeMode) {
                HistoriaRoot(
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        scope.launch { themePrefs.setThemeMode(newMode) }
                    }
                )
            }
        }
    }
}
