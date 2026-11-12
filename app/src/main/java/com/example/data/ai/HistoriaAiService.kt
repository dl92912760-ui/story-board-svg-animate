package com.example.data.ai

import com.example.data.model.HistoryProduction
import com.example.data.model.HistoryScene
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface ApiKeyCheckResult {
    data class Valid(val modelName: String, val message: String) : ApiKeyCheckResult
    data class Invalid(val message: String) : ApiKeyCheckResult
}

class HistoriaAiService {

    private val sceneSchema = Schema.obj(
        properties = mapOf(
            "sceneNumber" to Schema.integer(description = "1-based sequential scene number"),
            "title" to Schema.string(description = "Short evocative scene chapter title"),
            "narration" to Schema.string(description = "1 to 2 sentence documentary voiceover narration"),
            "durationSeconds" to Schema.integer(description = "Scene playback duration between 4 and 8 seconds"),
            "cameraMotion" to Schema.enumeration(
                values = listOf("zoom_in", "zoom_out", "pan_left", "pan_right", "static"),
                description = "Camera movement style during video playback"
            ),
            "svgCode" to Schema.string(
                description = "Complete valid self-contained <svg> markup with explicit viewBox, rich layered vector paths, <defs> gradients, and subtle SMIL <animate> or <animateTransform> motion"
            )
        )
    )

    private val productionSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Evocative documentary title under 80 characters"),
            "era" to Schema.string(description = "Historical era and dates, e.g., 44 BCE - Roman Republic"),
            "summary" to Schema.string(description = "Concise 2-sentence historical synopsis"),
            "scenes" to Schema.array(
                items = sceneSchema,
                description = "Sequential animated SVG scenes forming the historical video"
            )
        )
    )

    private val singleSvgSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Artwork title under 80 characters"),
            "era" to Schema.string(description = "Historical era and dates"),
            "summary" to Schema.string(description = "Short historical context note"),
            "narration" to Schema.string(description = "One-sentence caption describing the scene"),
            "svgCode" to Schema.string(
                description = "Complete valid self-contained <svg> markup with rich vector artwork, gradients, and subtle SVG animation"
            )
        )
    )

    private val videoModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = DEFAULT_FIREBASE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = productionSchema
            }
        )
    }

    private val svgModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = DEFAULT_FIREBASE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleSvgSchema
            }
        )
    }

    suspend fun validateGeminiApiKey(rawKey: String): ApiKeyCheckResult {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) {
            return ApiKeyCheckResult.Invalid("Please enter a Gemini API key.")
        }
        return try {
            withTimeout(15_000L) {
                withContext(Dispatchers.IO) {
                    val encodedKey = URLEncoder.encode(trimmed, "UTF-8")
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$encodedKey")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 12_000
                    }
                    try {
                        val code = conn.responseCode
                        val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                        if (code in 200..299) {
                            val selected = pickBestGeminiModel(body)
                            if (selected != null) {
                                ApiKeyCheckResult.Valid(
                                    modelName = selected,
                                    message = "Correct API key! Switched to Gemini ($selected)."
                                )
                            } else {
                                ApiKeyCheckResult.Invalid("API key is valid for Google Cloud, but no Gemini content-generation models are enabled.")
                            }
                        } else {
                            val errMsg = extractGoogleErrorMessage(body) ?: "HTTP $code"
                            ApiKeyCheckResult.Invalid("Incorrect API key: $errMsg")
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            ApiKeyCheckResult.Invalid("API check timed out. Check your internet connection and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiKeyCheckResult.Invalid("Incorrect API or network error: ${e.localizedMessage ?: "Validation failed"}")
        }
    }

    private fun pickBestGeminiModel(modelsJson: String): String? {
        val root = runCatching { JSONObject(modelsJson) }.getOrNull() ?: return null
        val models = root.optJSONArray("models") ?: return null
        val supported = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val item = models.optJSONObject(i) ?: continue
            val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
            var canGenerate = false
            for (j in 0 until methods.length()) {
                if (methods.optString(j) == "generateContent") {
                    canGenerate = true
                    break
                }
            }
            if (canGenerate) {
                val fullName = item.optString("name", "")
                val shortName = fullName.removePrefix("models/")
                if (shortName.contains("gemini", ignoreCase = true)) {
                    supported.add(shortName)
                }
            }
        }
        val preferredOrder = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
            "gemini-2.0-flash"
        )
        for (preferred in preferredOrder) {
            if (supported.contains(preferred)) return preferred
        }
        return supported.firstOrNull { it.contains("flash", ignoreCase = true) }
            ?: supported.firstOrNull()
    }

    private fun viewBoxForAspectRatio(aspectRatio: String): Pair<Int, Int> = when (aspectRatio) {
        "9:16" -> 450 to 800
        "1:1" -> 600 to 600
        else -> 800 to 450
    }

    suspend fun generateHistoriaVideo(
        prompt: String,
        visualStyle: String,
        backgroundTheme: String,
        aspectRatio: String,
        sceneCount: Int = 4,
        customApiKey: String = "",
        customModelName: String = ""
    ): HistoryProduction {
        val (width, height) = viewBoxForAspectRatio(aspectRatio)
        val clampedCount = sceneCount.coerceIn(3, 6)
        val paletteDirective = if (backgroundTheme == "light") {
            "Use a LIGHT archival museum parchment background (#FBF7F0 or #F4EDE1) with rich terracotta (#8C3B2B), Aegean teal (#2B5B6C), antique gold (#B58428), and deep iron ink (#1E1A16) vector layers."
        } else {
            "Use a DARK obsidian cinema background (#0E1015 or #161922) with luminous amber gold (#E5B85C), terracotta coral (#E8927C), celestial cyan (#7CC4D6), and alabaster (#F2ECE1) vector highlights."
        }

        val fullPrompt = """
            You are an expert historical documentary director and master SVG vector illustrator.
            Create a $clampedCount-scene animated historical video storyboard about: "$prompt".
            Visual art style: $visualStyle.
            Canvas & Background directive: $paletteDirective
            SVG technical requirements for EVERY scene's svgCode:
            1. Root element MUST be <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="100%" height="100%">.
            2. Include a full-bleed background <rect width="$width" height="$height" .../> using a <linearGradient> or <radialGradient> in <defs> matching the $backgroundTheme theme.
            3. Compose at least 4 distinct depth layers (sky/backdrop, architecture/landscape, historical figures/vessels/monuments, atmospheric foreground particles/light rays) using crisp <path>, <polygon>, <circle>, <ellipse>, and <g> elements.
            4. Add smooth continuous motion using SMIL <animate> or <animateTransform> (e.g., drifting clouds/smoke, shimmering water/stars, marching silhouettes, glowing sun/torchlight, rotating gears/astrolabe).
            5. Never reference external images, scripts, or fonts. Keep all SVG self-contained and valid XML.
        """.trimIndent()

        val jsonText = generateStructuredJson(
            prompt = fullPrompt,
            isSingleSvg = false,
            customApiKey = customApiKey,
            customModelName = customModelName
        )

        val root = JSONObject(jsonText)
        val title = root.optString("title", "Historical Chronicle").ifBlank { "Historical Chronicle" }
        val era = root.optString("era", "World History")
        val summary = root.optString("summary", "")
        val scenesArray = root.getJSONArray("scenes")

        val scenes = (0 until scenesArray.length()).map { idx ->
            val obj = scenesArray.getJSONObject(idx)
            HistoryScene(
                sceneNumber = obj.optLong("sceneNumber", (idx + 1).toLong()),
                title = obj.optString("title", "Scene ${idx + 1}"),
                narration = obj.optString("narration", ""),
                durationSeconds = obj.optLong("durationSeconds", 5L).coerceIn(3L, 12L),
                cameraMotion = obj.optString("cameraMotion", "zoom_in"),
                svgCode = sanitizeSvg(obj.optString("svgCode", ""), width, height, backgroundTheme)
            )
        }

        val totalDuration = scenes.sumOf { it.durationSeconds }.coerceAtLeast(1L)

        return HistoryProduction(
            title = title,
            prompt = prompt,
            era = era,
            summary = summary,
            generationType = "video",
            backgroundTheme = backgroundTheme,
            visualStyle = visualStyle,
            aspectRatio = aspectRatio,
            totalDurationSeconds = totalDuration,
            scenes = scenes,
            favorite = false
        )
    }

    suspend fun generateHistoriaSvg(
        prompt: String,
        visualStyle: String,
        backgroundTheme: String,
        aspectRatio: String,
        customApiKey: String = "",
        customModelName: String = ""
    ): HistoryProduction {
        val (width, height) = viewBoxForAspectRatio(aspectRatio)
        val paletteDirective = if (backgroundTheme == "light") {
            "Use a LIGHT archival parchment background (#FBF7F0 / #EFE6D8) with terracotta (#8C3B2B), Aegean teal (#2B5B6C), antique gold (#B58428), and iron ink (#1E1A16) details."
        } else {
            "Use a DARK obsidian night background (#0E1015 / #161922) with glowing amber gold (#E5B85C), coral (#E8927C), celestial cyan (#7CC4D6), and warm ivory (#F2ECE1) vector layers."
        }

        val fullPrompt = """
            You are a master historical SVG illustrator and vector animator.
            Create a single museum-grade animated SVG illustration for: "$prompt".
            Visual style: $visualStyle.
            Background directive: $paletteDirective
            Technical rules for svgCode:
            1. Root MUST be <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="100%" height="100%">.
            2. Include rich <defs> gradients, radial lighting, ornate historical geometry, and at least 15-25 detailed vector primitives (<path>, <polygon>, <circle>, <g>).
            3. Include subtle continuous SMIL <animate> or <animateTransform> effects (glowing aura, orbiting stars, swaying banners, rippling water).
            4. Self-contained XML only; no external URLs.
        """.trimIndent()

        val jsonText = generateStructuredJson(
            prompt = fullPrompt,
            isSingleSvg = true,
            customApiKey = customApiKey,
            customModelName = customModelName
        )

        val root = JSONObject(jsonText)
        val title = root.optString("title", "Historical Vector").ifBlank { "Historical Vector" }
        val era = root.optString("era", "Archival Illustration")
        val summary = root.optString("summary", "")
        val narration = root.optString("narration", summary)
        val svgCode = sanitizeSvg(root.optString("svgCode", ""), width, height, backgroundTheme)

        return HistoryProduction(
            title = title,
            prompt = prompt,
            era = era,
            summary = summary,
            generationType = "svg",
            backgroundTheme = backgroundTheme,
            visualStyle = visualStyle,
            aspectRatio = aspectRatio,
            totalDurationSeconds = 8L,
            scenes = listOf(
                HistoryScene(
                    sceneNumber = 1L,
                    title = title,
                    narration = narration,
                    durationSeconds = 8L,
                    cameraMotion = "zoom_in",
                    svgCode = svgCode
                )
            ),
            favorite = false
        )
    }

    suspend fun rethemeProduction(
        production: HistoryProduction,
        targetBackgroundTheme: String,
        customApiKey: String = "",
        customModelName: String = ""
    ): List<HistoryScene> {
        val (width, height) = viewBoxForAspectRatio(production.aspectRatio)
        val targetDirective = if (targetBackgroundTheme == "light") {
            "Convert all colors and gradients to a luminous LIGHT Museum Parchment palette (#FBF7F0 background, #F4EDE1 mid-tones, #8C3B2B terracotta, #2B5B6C teal, #B58428 gold, #1E1A16 dark ink strokes)."
        } else {
            "Convert all colors and gradients to a dramatic DARK Cinema Obsidian palette (#0E1015 background, #161922 mid-tones, #E5B85C luminous gold, #E8927C coral, #7CC4D6 cyan, #F2ECE1 highlights)."
        }

        val existingSummary = production.scenes.joinToString("\n---\n") { scene ->
            "Scene ${scene.sceneNumber} (${scene.title}):\n${scene.svgCode}"
        }

        val fullPrompt = """
            Re-palette the following historical SVG scenes for a $targetBackgroundTheme background while preserving all geometry, composition, narration, and animations.
            $targetDirective
            Keep title, era, and summary consistent with "${production.title}".
            Return the updated scenes array with the exact same scene count (${production.scenes.size}).
            Existing scenes:
            $existingSummary
        """.trimIndent()

        val jsonText = generateStructuredJson(
            prompt = fullPrompt,
            isSingleSvg = false,
            customApiKey = customApiKey,
            customModelName = customModelName
        )

        val root = JSONObject(jsonText)
        val scenesArray = root.getJSONArray("scenes")

        return (0 until scenesArray.length()).map { idx ->
            val obj = scenesArray.getJSONObject(idx)
            val original = production.scenes.getOrNull(idx)
            HistoryScene(
                sceneNumber = original?.sceneNumber ?: obj.optLong("sceneNumber", (idx + 1).toLong()),
                title = original?.title?.ifBlank { obj.optString("title", "Scene ${idx + 1}") }
                    ?: obj.optString("title", "Scene ${idx + 1}"),
                narration = original?.narration?.ifBlank { obj.optString("narration", "") }
                    ?: obj.optString("narration", ""),
                durationSeconds = original?.durationSeconds ?: obj.optLong("durationSeconds", 5L),
                cameraMotion = original?.cameraMotion ?: obj.optString("cameraMotion", "zoom_in"),
                svgCode = sanitizeSvg(obj.optString("svgCode", ""), width, height, targetBackgroundTheme)
            )
        }
    }

    private suspend fun generateStructuredJson(
        prompt: String,
        isSingleSvg: Boolean,
        customApiKey: String,
        customModelName: String
    ): String {
        val key = customApiKey.trim()
        if (key.isBlank()) {
            val response = try {
                withTimeout(60_000L) {
                    if (isSingleSvg) {
                        svgModel.generateContent(prompt)
                    } else {
                        videoModel.generateContent(prompt)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            }
            return response.text ?: throw IllegalStateException("Gemini returned an empty response.")
        }

        val model = customModelName.trim().ifBlank { "gemini-2.5-flash" }
        return withTimeout(60_000L) {
            withContext(Dispatchers.IO) {
                val encodedKey = URLEncoder.encode(key, "UTF-8")
                val encodedModel = URLEncoder.encode(model, "UTF-8")
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey")
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    ))
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("responseSchema", if (isSingleSvg) buildSingleSvgRestSchema() else buildProductionRestSchema())
                    })
                }

                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 55_000
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                try {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                    }
                    val code = conn.responseCode
                    val body = readStream(if (code in 200..299) conn.inputStream else conn.errorStream)
                    if (code !in 200..299) {
                        val errMsg = extractGoogleErrorMessage(body) ?: "HTTP $code"
                        throw IllegalStateException("Gemini API Error ($code): $errMsg")
                    }
                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    val text = candidates
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        .orEmpty()
                    if (text.isBlank()) {
                        throw IllegalStateException("Gemini returned an empty response.")
                    }
                    text
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun buildProductionRestSchema(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("title", JSONObject().put("type", "STRING"))
            put("era", JSONObject().put("type", "STRING"))
            put("summary", JSONObject().put("type", "STRING"))
            put("scenes", JSONObject().apply {
                put("type", "ARRAY")
                put("items", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("sceneNumber", JSONObject().put("type", "INTEGER"))
                        put("title", JSONObject().put("type", "STRING"))
                        put("narration", JSONObject().put("type", "STRING"))
                        put("durationSeconds", JSONObject().put("type", "INTEGER"))
                        put("cameraMotion", JSONObject().apply {
                            put("type", "STRING")
                            put("enum", JSONArray(listOf("zoom_in", "zoom_out", "pan_left", "pan_right", "static")))
                        })
                        put("svgCode", JSONObject().put("type", "STRING"))
                    })
                    put("required", JSONArray(listOf("sceneNumber", "title", "narration", "durationSeconds", "cameraMotion", "svgCode")))
                })
            })
        })
        put("required", JSONArray(listOf("title", "era", "summary", "scenes")))
    }

    private fun buildSingleSvgRestSchema(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("title", JSONObject().put("type", "STRING"))
            put("era", JSONObject().put("type", "STRING"))
            put("summary", JSONObject().put("type", "STRING"))
            put("narration", JSONObject().put("type", "STRING"))
            put("svgCode", JSONObject().put("type", "STRING"))
        })
        put("required", JSONArray(listOf("title", "era", "summary", "narration", "svgCode")))
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun extractGoogleErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun sanitizeSvg(raw: String, width: Int, height: Int, theme: String): String {
        val trimmed = raw.trim()
        if (trimmed.contains("<svg") && trimmed.contains("</svg>")) {
            return trimmed
        }
        val bg = if (theme == "light") "#FBF7F0" else "#0E1015"
        val fg = if (theme == "light") "#8C3B2B" else "#E5B85C"
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="100%" height="100%">
              <rect width="$width" height="$height" fill="$bg"/>
              <circle cx="${width / 2}" cy="${height / 2}" r="${minOf(width, height) / 4}" fill="$fg" opacity="0.85">
                <animate attributeName="r" values="${minOf(width, height) / 5};${minOf(width, height) / 4};${minOf(width, height) / 5}" dur="4s" repeatCount="indefinite"/>
              </circle>
            </svg>
        """.trimIndent()
    }

    private companion object {
        const val DEFAULT_FIREBASE_MODEL = "gemini-3.8-flash"
    }
}
