package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.HistoryProduction
import com.example.data.model.HistoryScene
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class HistoriaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleProduction(uid: String): HistoryProduction = HistoryProduction(
        id = UUID.randomUUID().toString(),
        userId = uid,
        title = "Library of Alexandria",
        prompt = "Animated history of the Great Library of Alexandria",
        era = "3rd Century BCE • Ptolemaic Egypt",
        summary = "Scholars gather scrolls beneath sunlit colonnades.",
        generationType = "video",
        backgroundTheme = "light",
        visualStyle = "Archival Engraving",
        aspectRatio = "16:9",
        totalDurationSeconds = 12L,
        scenes = listOf(
            HistoryScene(
                sceneNumber = 1L,
                title = "The Great Hall",
                narration = "Scrolls arrive from every port across the Mediterranean.",
                durationSeconds = 6L,
                cameraMotion = "zoom_in",
                svgCode = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 450\"><rect width=\"800\" height=\"450\" fill=\"#FBF7F0\"/></svg>"
            ),
            HistoryScene(
                sceneNumber = 2L,
                title = "Lanterns at Dusk",
                narration = "Astronomers chart the constellations from the terrace.",
                durationSeconds = 6L,
                cameraMotion = "pan_right",
                svgCode = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 450\"><rect width=\"800\" height=\"450\" fill=\"#F4EDE1\"/></svg>"
            )
        ),
        favorite = false
    )

    @Test
    fun createHistory_authenticatedOwner_succeedsAndEmitsInObserve() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = HistoriaRepository(firestore)
        val item = sampleProduction(uid)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createHistory(uid, item)
        }
        assertTrue("Expected createHistory success, got: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val createdId = createResult.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHistories(uid).first { list -> list.any { it.id == createdId } }
        }
        val matched = observed.first { it.id == createdId }
        assertEquals("Library of Alexandria", matched.title)
        assertEquals(2, matched.scenes.size)
    }

    @Test
    fun toggleFavorite_authenticatedOwner_updatesSuccessfully() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = HistoriaRepository(firestore)
        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createHistory(uid, sampleProduction(uid)).getOrThrow()
        }

        val toggleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleFavorite(uid, createdId, true)
        }
        assertTrue("toggleFavorite failed: ${toggleResult.exceptionOrNull()}", toggleResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getHistoryById(uid, createdId).getOrThrow()
        }
        assertNotNull(fetched)
        assertTrue(fetched!!.favorite)
    }

    @Test
    fun getHistory_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = HistoriaRepository(firestore)
        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createHistory(aliceUid, sampleProduction(aliceUid)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = HistoriaRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getHistoryById(aliceUid, createdId)
        }
        assertTrue("Expected cross-user get to fail", result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeHistories_unauthenticated_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = HistoriaRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeHistories(aliceUid).first()
            }
            fail("Expected unauthenticated observeHistories to throw PERMISSION_DENIED")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected Firestore exception, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.historia@test.com"
        const val BOB_EMAIL = "bob.historia@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
