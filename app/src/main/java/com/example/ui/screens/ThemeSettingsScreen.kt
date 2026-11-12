package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppThemeMode
import com.example.ui.components.GeminiApiKeyCard
import com.example.ui.viewmodel.ApiKeyUiState
import com.google.firebase.auth.FirebaseUser

@Composable
fun ThemeSettingsScreen(
    themeMode: AppThemeMode,
    currentUser: FirebaseUser?,
    activeApiKey: String,
    activeApiModel: String,
    apiValidationState: ApiKeyUiState,
    onApplyApiKey: (String) -> Unit,
    onResetDefaultApi: () -> Unit,
    onClearApiValidation: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Appearance & Account",
                style = MaterialTheme.typography.headlineLarge
            )

            // Light & Dark Theme Showcase Cards
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Studio Background Atmosphere",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Switch between Archival Daylight (Light), Cinema Obsidian (Dark), or follow your device system theme.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ThemeOptionTile(
                        title = "Light — Archival Daylight",
                        subtitle = "Warm museum parchment #FBF7F0 with terracotta & bronze accents",
                        previewBg = Color(0xFFFBF7F0),
                        previewAccent = Color(0xFF8C3B2B),
                        icon = Icons.Filled.LightMode,
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                        modifier = Modifier.testTag("settings_theme_light")
                    )

                    ThemeOptionTile(
                        title = "Dark — Cinema Obsidian",
                        subtitle = "Deep midnight obsidian #0E1015 with gilded amber & cyan highlights",
                        previewBg = Color(0xFF0E1015),
                        previewAccent = Color(0xFFE5B85C),
                        icon = Icons.Filled.DarkMode,
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = { onThemeModeChange(AppThemeMode.DARK) },
                        modifier = Modifier.testTag("settings_theme_dark")
                    )

                    ThemeOptionTile(
                        title = "System Default",
                        subtitle = "Automatically adapts to your Android light/dark mode",
                        previewBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                        previewAccent = MaterialTheme.colorScheme.secondary,
                        icon = Icons.Filled.BrightnessAuto,
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(AppThemeMode.SYSTEM) },
                        modifier = Modifier.testTag("settings_theme_system")
                    )
                }
            }

            // Firebase Cloud & Gemini Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Signed-In Creator",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = currentUser?.email ?: currentUser?.uid.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (activeApiKey.isNotBlank()) {
                                "Firestore Sync Active • Custom Gemini (${activeApiModel.ifBlank { "gemini-2.5-flash" }})"
                            } else {
                                "Firebase Firestore Sync & Gemini 3.8 Flash Active"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }

            GeminiApiKeyCard(
                activeApiKey = activeApiKey,
                activeModelName = activeApiModel,
                validationState = apiValidationState,
                onApplyApiKey = onApplyApiKey,
                onResetDefaultApi = onResetDefaultApi,
                onClearValidation = onClearApiValidation
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeOptionTile(
    title: String,
    subtitle: String,
    previewBg: Color,
    previewAccent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(previewBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = previewAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
