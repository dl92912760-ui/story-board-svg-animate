package com.example.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.HistoryProduction
import com.example.data.model.HistoryScene
import java.io.File

object ShareExporter {

    fun copySvgToClipboard(context: Context, svgCode: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Historia SVG", svgCode))
    }

    fun shareSingleSvgFile(context: Context, title: String, scene: HistoryScene) {
        val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "historia" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "${safeName}_scene_${scene.sceneNumber}.svg")
        outFile.writeText(scene.svgCode)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/svg+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$title - Scene ${scene.sceneNumber}.svg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export SVG Vector"))
    }

    fun shareAnimatedHtmlFilm(context: Context, production: HistoryProduction) {
        val safeName = production.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "historia" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "${safeName}_historia_video.html")

        val bg = if (production.backgroundTheme == "light") "#FBF7F0" else "#0E1015"
        val fg = if (production.backgroundTheme == "light") "#1E1A16" else "#F2ECE1"
        val scenesHtml = production.scenes.joinToString("\n") { s ->
            """
            <section class="scene" data-duration="${s.durationSeconds}">
              <div class="stage">${s.svgCode}</div>
              <div class="caption"><strong>${escapeHtml(s.title)}</strong>: ${escapeHtml(s.narration)}</div>
            </section>
            """.trimIndent()
        }

        val fullHtml = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <title>${escapeHtml(production.title)} — Historia</title>
          <style>
            body { margin:0; font-family: system-ui, sans-serif; background:$bg; color:$fg; display:flex; flex-direction:column; align-items:center; padding:24px; }
            .player { max-width: 900px; width:100%; }
            .scene { display:none; }
            .scene.active { display:block; animation: fadeIn 0.5s ease; }
            .stage svg { width:100%; height:auto; border-radius:16px; }
            .caption { margin-top:12px; padding:12px 16px; border-radius:12px; background:rgba(128,128,128,0.14); }
            @keyframes fadeIn { from { opacity:0; } to { opacity:1; } }
          </style>
        </head>
        <body>
          <div class="player">
            <h1>${escapeHtml(production.title)}</h1>
            <p>${escapeHtml(production.era)} • ${escapeHtml(production.summary)}</p>
            $scenesHtml
          </div>
          <script>
            const scenes = Array.from(document.querySelectorAll('.scene'));
            let i = 0;
            function show(idx) {
              scenes.forEach((s, j) => s.classList.toggle('active', j === idx));
              const dur = parseInt(scenes[idx].dataset.duration || '5', 10) * 1000;
              setTimeout(() => { i = (idx + 1) % scenes.length; show(i); }, dur);
            }
            if (scenes.length > 0) show(0);
          </script>
        </body>
        </html>
        """.trimIndent()

        outFile.writeText(fullHtml)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${production.title} — Animated Historia Film")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Animated Video Storyboard"))
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
