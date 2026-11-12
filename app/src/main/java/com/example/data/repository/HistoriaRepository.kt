package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.HistoryProduction
import com.example.data.model.HistoryScene
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class HistoriaRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userHistoriesCollection(userId: String) =
        db.collection("users").document(userId).collection("histories")

    fun observeHistories(userId: String): Flow<List<HistoryProduction>> {
        val path = "users/$userId/histories"
        return userHistoriesCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    HistoryProduction::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                val currentUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                if (exceptionToLog != null && (currentUid == null || currentUid == userId)) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getHistoryById(userId: String, historyId: String): Result<HistoryProduction?> {
        val docRef = userHistoriesCollection(userId).document(historyId)
        return try {
            val snap = docRef.get().await()
            val item = snap.toObject(
                HistoryProduction::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createHistory(userId: String, production: HistoryProduction): Result<String> {
        val docId = production.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userHistoriesCollection(userId).document(docId)
        val payload = mapOf(
            "userId" to userId,
            "title" to production.title.take(120),
            "prompt" to production.prompt.take(600),
            "era" to production.era.take(100),
            "summary" to production.summary.take(1200),
            "generationType" to production.generationType,
            "backgroundTheme" to production.backgroundTheme,
            "visualStyle" to production.visualStyle.take(60),
            "aspectRatio" to production.aspectRatio,
            "totalDurationSeconds" to production.totalDurationSeconds.coerceIn(1L, 300L),
            "scenes" to production.scenes.take(8).map { it.toFirestoreMap() },
            "favorite" to production.favorite,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(userId: String, historyId: String, favorite: Boolean): Result<Unit> {
        val docRef = userHistoriesCollection(userId).document(historyId)
        return try {
            docRef.update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateScenesTheme(
        userId: String,
        historyId: String,
        backgroundTheme: String,
        scenes: List<HistoryScene>,
        totalDurationSeconds: Long
    ): Result<Unit> {
        val docRef = userHistoriesCollection(userId).document(historyId)
        return try {
            docRef.update(
                mapOf(
                    "backgroundTheme" to backgroundTheme,
                    "scenes" to scenes.take(8).map { it.toFirestoreMap() },
                    "totalDurationSeconds" to totalDurationSeconds.coerceIn(1L, 300L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHistory(userId: String, historyId: String): Result<Unit> {
        val docRef = userHistoriesCollection(userId).document(historyId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
