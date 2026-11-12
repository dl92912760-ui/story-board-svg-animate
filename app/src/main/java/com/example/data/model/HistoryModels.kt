package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class HistoryScene(
    val sceneNumber: Long = 1L,
    val title: String = "",
    val narration: String = "",
    val durationSeconds: Long = 5L,
    val cameraMotion: String = "zoom_in",
    val svgCode: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "sceneNumber" to sceneNumber,
        "title" to title,
        "narration" to narration,
        "durationSeconds" to durationSeconds,
        "cameraMotion" to cameraMotion,
        "svgCode" to svgCode
    )
}

@IgnoreExtraProperties
data class HistoryProduction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val era: String = "",
    val summary: String = "",
    val generationType: String = "video",
    val backgroundTheme: String = "dark",
    val visualStyle: String = "Engraved Vector",
    val aspectRatio: String = "16:9",
    val totalDurationSeconds: Long = 0L,
    val scenes: List<HistoryScene> = emptyList(),
    val favorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
