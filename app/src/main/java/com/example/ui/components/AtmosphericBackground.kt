package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance

@Composable
fun AtmosphericBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    val topGlow = if (isDark) {
        colorScheme.tertiary.copy(alpha = 0.14f)
    } else {
        colorScheme.tertiary.copy(alpha = 0.16f)
    }
    val cornerGlow = if (isDark) {
        colorScheme.secondary.copy(alpha = 0.12f)
    } else {
        colorScheme.primary.copy(alpha = 0.08f)
    }
    val lineColor = colorScheme.outline.copy(alpha = if (isDark) 0.22f else 0.30f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(topGlow, colorScheme.background.copy(alpha = 0f)),
                    center = Offset(size.width * 0.82f, size.height * 0.10f),
                    radius = size.maxDimension * 0.65f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(cornerGlow, colorScheme.background.copy(alpha = 0f)),
                    center = Offset(size.width * 0.12f, size.height * 0.85f),
                    radius = size.maxDimension * 0.55f
                )
            )

            // Celestial astrolabe rings for subtle archival/cinema geometry
            val ringCenter = Offset(size.width * 0.88f, size.height * 0.14f)
            val baseRadius = size.minDimension * 0.28f
            drawCircle(
                color = lineColor,
                radius = baseRadius,
                center = ringCenter,
                style = Stroke(width = 1.2f)
            )
            drawCircle(
                color = lineColor.copy(alpha = lineColor.alpha * 0.6f),
                radius = baseRadius * 1.45f,
                center = ringCenter,
                style = Stroke(width = 1f)
            )
            drawLine(
                color = lineColor.copy(alpha = lineColor.alpha * 0.45f),
                start = Offset(0f, size.height * 0.14f),
                end = Offset(size.width, size.height * 0.14f),
                strokeWidth = 0.8f
            )
        }
        content()
    }
}
