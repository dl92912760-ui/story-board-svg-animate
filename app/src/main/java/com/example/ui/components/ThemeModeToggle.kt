package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.AppThemeMode

@Composable
fun ThemeModeCycleButton(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextMode = when (themeMode) {
        AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
        AppThemeMode.LIGHT -> AppThemeMode.DARK
        AppThemeMode.DARK -> AppThemeMode.SYSTEM
    }
    val icon = when (themeMode) {
        AppThemeMode.LIGHT -> Icons.Filled.LightMode
        AppThemeMode.DARK -> Icons.Filled.DarkMode
        AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    }

    IconButton(
        onClick = { onThemeModeChange(nextMode) },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("theme_cycle_button")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Switch background theme (current: ${themeMode.label})",
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun ThemeModeChipRow(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppThemeMode.entries.forEach { mode ->
            val icon = when (mode) {
                AppThemeMode.LIGHT -> Icons.Filled.LightMode
                AppThemeMode.DARK -> Icons.Filled.DarkMode
                AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
            }
            val selected = themeMode == mode
            FilterChip(
                selected = selected,
                onClick = { onThemeModeChange(mode) },
                modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}"),
                label = { Text(mode.label) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
