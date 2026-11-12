package com.example.ui.screens

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.HistoryProduction
import com.example.ui.player.ShareExporter
import com.example.ui.player.SvgStagePlayer
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    production: HistoryProduction?,
    rethemeInProgress: Boolean,
    onBack: () -> Unit,
    onRethemeWithGemini: (HistoryProduction, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    if (production == null || production.scenes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Production not found", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onBack) { Text("Back to Archive") }
            }
        }
        return
    }

    val context = LocalContext.current
    var currentSceneIndex by remember(production.id) { mutableIntStateOf(0) }
    var isPlaying by remember(production.id) { mutableStateOf(true) }
    var showSubtitles by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(false) }
    var showSvgCode by remember { mutableStateOf(false) }
    var stagePreviewTheme by remember(production.id, production.backgroundTheme) {
        mutableStateOf(production.backgroundTheme)
    }
    var sceneElapsedMs by remember(production.id) { mutableLongStateOf(0L) }

    val safeIndex = currentSceneIndex.coerceIn(0, production.scenes.lastIndex)
    val currentScene = production.scenes[safeIndex]
    val sceneDurationMs = (currentScene.durationSeconds.coerceAtLeast(3L) * 1000L)

    // Android Text-to-Speech narrator
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
                ttsReady = true
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(safeIndex, isPlaying, voiceEnabled, ttsReady, production.id) {
        if (voiceEnabled && ttsReady && isPlaying && currentScene.narration.isNotBlank()) {
            tts.speak(currentScene.narration, TextToSpeech.QUEUE_FLUSH, null, "scene_$safeIndex")
        } else if (!voiceEnabled || !isPlaying) {
            tts.stop()
        }
    }

    // Playback ticker for animated storyboard scenes
    LaunchedEffect(isPlaying, safeIndex, production.scenes.size) {
        while (isPlaying) {
            delay(100L)
            sceneElapsedMs += 100L
            if (sceneElapsedMs >= sceneDurationMs) {
                sceneElapsedMs = 0L
                currentSceneIndex = if (safeIndex < production.scenes.lastIndex) {
                    safeIndex + 1
                } else {
                    0
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = production.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${production.era} • ${production.visualStyle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Live SVG Stage Player
            SvgStagePlayer(
                scene = currentScene,
                aspectRatio = production.aspectRatio,
                stageBackgroundTheme = stagePreviewTheme,
                isPlaying = isPlaying,
                showSubtitles = showSubtitles
            )

            // Progress & Controls Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${currentScene.title} (${safeIndex + 1}/${production.scenes.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${(sceneElapsedMs / 1000)}s / ${currentScene.durationSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (sceneElapsedMs.toFloat() / sceneDurationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    sceneElapsedMs = 0L
                                    currentSceneIndex = if (safeIndex > 0) safeIndex - 1 else production.scenes.lastIndex
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous scene")
                            }

                            FilledIconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                )
                            }

                            IconButton(
                                onClick = {
                                    sceneElapsedMs = 0L
                                    currentSceneIndex = if (safeIndex < production.scenes.lastIndex) safeIndex + 1 else 0
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next scene")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { voiceEnabled = !voiceEnabled },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Toggle narration voice",
                                    tint = if (voiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showSubtitles = !showSubtitles },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Subtitles,
                                    contentDescription = "Toggle subtitles",
                                    tint = if (showSubtitles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showSvgCode = !showSvgCode },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_svg_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Code,
                                    contentDescription = "Inspect SVG source",
                                    tint = if (showSvgCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Light / Dark Stage Preview + Gemini Re-theming Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Light & Dark Background Studio",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { stagePreviewTheme = "light" },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = if (stagePreviewTheme == "light") 2.dp else 1.dp,
                                color = if (stagePreviewTheme == "light") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Light")
                        }
                        OutlinedButton(
                            onClick = { stagePreviewTheme = "dark" },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = if (stagePreviewTheme == "dark") 2.dp else 1.dp,
                                color = if (stagePreviewTheme == "dark") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Dark")
                        }
                    }

                    val targetTheme = if (production.backgroundTheme == "light") "dark" else "light"
                    Button(
                        onClick = { onRethemeWithGemini(production, targetTheme) },
                        enabled = !rethemeInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("retheme_gemini_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (rethemeInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Adapting SVG vectors with Gemini…")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-palette SVG for ${targetTheme.replaceFirstChar { it.uppercase() }} Mode")
                        }
                    }
                }
            }

            // Scene Chapters Timeline
            if (production.scenes.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Scene Chapters",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        production.scenes.forEachIndexed { idx, scene ->
                            val selected = idx == safeIndex
                            Surface(
                                modifier = Modifier
                                    .width(164.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        currentSceneIndex = idx
                                        sceneElapsedMs = 0L
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Scene ${scene.sceneNumber} • ${scene.durationSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = scene.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { ShareExporter.shareSingleSvgFile(context, production.title, currentScene) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export .SVG")
                }
                OutlinedButton(
                    onClick = { ShareExporter.shareAnimatedHtmlFilm(context, production) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Film")
                }
            }

            // Raw SVG Source Code Inspector
            AnimatedVisibility(visible = showSvgCode) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SVG Markup (Scene ${currentScene.sceneNumber})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = {
                                    ShareExporter.copySvgToClipboard(context, currentScene.svgCode)
                                    Toast.makeText(context, "SVG copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy SVG")
                            }
                        }
                        Text(
                            text = currentScene.svgCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
