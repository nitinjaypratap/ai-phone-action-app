package com.example.aiphoneaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseConfidenceTrackerTest {
    private val tracker = PhaseConfidenceTracker(windowSize = 5, minStableVotes = 3)

    @Test
    fun stableOnlyAfterThreeMatchingObservations() {
        assertEquals(GameState.UNKNOWN, tracker.update(observation(GameState.COMBAT, 0.9f)).state)
        assertEquals(GameState.UNKNOWN, tracker.update(observation(GameState.COMBAT, 0.9f)).state)
        val stable = tracker.update(observation(GameState.COMBAT, 0.9f))
        assertEquals(GameState.COMBAT, stable.state)
        assertEquals(3, stable.stableFrames)
        assertTrue(stable.confidence >= 0.89f)
    }

    @Test
    fun conflictingEvidenceDoesNotClaimStablePhase() {
        repeat(2) { tracker.update(observation(GameState.COMBAT, 0.9f)) }
        tracker.update(observation(GameState.LOBBY, 0.9f))
        tracker.update(observation(GameState.LOBBY, 0.9f))
        val estimate = tracker.update(observation(GameState.COMBAT, 0.9f))
        assertEquals(GameState.UNKNOWN, estimate.state)
    }

    @Test
    fun unknownFramesExpireOldPhaseEvidence() {
        repeat(3) { tracker.update(observation(GameState.LOOTING, 0.8f)) }
        assertEquals(GameState.LOOTING, tracker.update(observation(GameState.LOOTING, 0.8f)).state)
        repeat(5) { estimate = tracker.update(observation(GameState.UNKNOWN, 0f)) }
        assertEquals(GameState.UNKNOWN, estimate.state)
        assertEquals(0f, estimate.confidence)
    }

    @Test
    fun lowConfidenceEvidenceIsStoredAsUnknown() {
        val estimate = tracker.update(observation(GameState.VICTORY, 0.1f))
        assertEquals(GameState.UNKNOWN, estimate.state)
    }

    private fun observation(state: GameState, confidence: Float) =
        ScreenObservation("test", state, confidence, emptyMap())
}
