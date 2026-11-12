package com.example.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.HistoryScene

@Composable
fun SvgStagePlayer(
    scene: HistoryScene,
    aspectRatio: String,
    stageBackgroundTheme: String,
    isPlaying: Boolean,
    showSubtitles: Boolean,
    modifier: Modifier = Modifier
) {
    val ratioFloat = when (aspectRatio) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        else -> 16f / 9f
    }

    val stageBgColor = if (stageBackgroundTheme == "light") {
        Color(0xFFFBF7F0)
    } else {
        Color(0xFF0E1015)
    }
    val stageBgHex = if (stageBackgroundTheme == "light") "#FBF7F0" else "#0E1015"

    val motionProgress = remember(scene.sceneNumber, scene.svgCode) { Animatable(0f) }

    LaunchedEffect(scene.sceneNumber, scene.svgCode, isPlaying) {
        if (isPlaying) {
            motionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (scene.durationSeconds.coerceAtLeast(3L) * 1000L).toInt(),
                    easing = LinearEasing
                )
            )
        }
    }

    val progress = motionProgress.value
    val (scale, tx, ty) = when (scene.cameraMotion) {
        "zoom_in" -> Triple(1f + 0.09f * progress, 0f, 0f)
        "zoom_out" -> Triple(1.09f - 0.09f * progress, 0f, 0f)
        "pan_left" -> Triple(1.08f, 22f - 44f * progress, 0f)
        "pan_right" -> Triple(1.08f, -22f + 44f * progress, 0f)
        else -> Triple(1f, 0f, 0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratioFloat)
            .clip(RoundedCornerShape(20.dp))
            .background(stageBgColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        AnimatedContent(
            targetState = scene.svgCode,
            transitionSpec = {
                fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350))
            },
            label = "scene_svg_transition"
        ) { currentSvg ->
            SvgWebView(
                svgMarkup = currentSvg,
                backgroundHex = stageBgHex,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = tx
                        translationY = ty
                    }
            )
        }

        // Camera & scene chapter badge
        Surface(
            color = Color.Black.copy(alpha = 0.52f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Text(
                text = "Scene ${scene.sceneNumber} • ${scene.cameraMotion.replace('_', ' ').uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF2ECE1),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // Subtitle overlay
        if (showSubtitles && scene.narration.isNotBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = scene.narration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFBF7F0),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SvgWebView(
    svgMarkup: String,
    backgroundHex: String,
    modifier: Modifier = Modifier
) {
    val htmlDocument = remember(svgMarkup, backgroundHex) {
        """
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <style>
              * { box-sizing: border-box; margin: 0; padding: 0; }
              html, body {
                width: 100%;
                height: 100%;
                background: $backgroundHex;
                display: flex;
                align-items: center;
                justify-content: center;
                overflow: hidden;
              }
              svg {
                width: 100%;
                height: 100%;
                max-width: 100%;
                max-height: 100%;
                display: block;
              }
            </style>
          </head>
          <body>
            $svgMarkup
          </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    loadsImagesAutomatically = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, htmlDocument, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
