package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.core.AppThemeMode

private val HistoriaDarkColorScheme = darkColorScheme(
    primary = TerracottaCoral,
    onPrimary = Color(0xFF3E0F06),
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = OnDarkTerracottaContainer,
    secondary = AegeanCyan,
    onSecondary = Color(0xFF042630),
    secondaryContainer = DarkAegeanContainer,
    onSecondaryContainer = OnDarkAegeanContainer,
    tertiary = GildedAmber,
    onTertiary = Color(0xFF2D1E00),
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = OnDarkGoldContainer,
    background = ObsidianBackground,
    onBackground = AlabasterInk,
    surface = MidnightSurface,
    onSurface = AlabasterInk,
    surfaceVariant = MidnightSurfaceLow,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLow = MidnightSurfaceLow,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightSurfaceHigh,
    outline = ObsidianOutline,
    error = ErrorDark
)

private val HistoriaLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = AegeanSecondary,
    onSecondary = Color.White,
    secondaryContainer = AegeanContainer,
    onSecondaryContainer = OnAegeanContainer,
    tertiary = AntiqueGoldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = ParchmentBackground,
    onBackground = IronInk,
    surface = VellumSurface,
    onSurface = IronInk,
    surfaceVariant = VellumSurfaceLow,
    onSurfaceVariant = SepiaMuted,
    surfaceContainerLow = VellumSurfaceLow,
    surfaceContainer = VellumSurface,
    surfaceContainerHigh = VellumSurfaceHigh,
    outline = ParchmentOutline,
    error = ErrorLight
)

val HistoriaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun HistoriaTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) HistoriaDarkColorScheme else HistoriaLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HistoriaTypography,
        shapes = HistoriaShapes,
        content = content
    )
}
