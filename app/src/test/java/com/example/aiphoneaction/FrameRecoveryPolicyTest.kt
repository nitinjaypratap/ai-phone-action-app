package com.example.aiphoneaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRecoveryPolicyTest {
    @Test
    fun retryBudgetIsBoundedAndUsesBackoff() {
        val policy = FrameRecoveryPolicy(maxRetriesPerFrame = 2, baseRetryDelayMs = 100, maxRetryDelayMs = 500)
        assertEquals(100L, policy.retryDelayMs(0))
        assertEquals(200L, policy.retryDelayMs(1))
        assertNull(policy.retryDelayMs(2))
        assertNull(policy.retryDelayMs(-1))
    }

    @Test
    fun repeatedFailuresResetConfidenceAndEventuallyRequireUserRestart() {
        val policy = FrameRecoveryPolicy(resetConfidenceAfterFailures = 2)
        val first = policy.recordFailure()
        assertFalse(first.resetConfidence)
        assertFalse(first.requiresUserRestart)
        val second = policy.recordFailure()
        assertTrue(second.resetConfidence)
        assertFalse(second.requiresUserRestart)
        val fourth = policy.recordFailure().let { policy.recordFailure() }
        assertTrue(fourth.requiresUserRestart)
    }

    @Test
    fun successfulFrameClearsFailureStreak() {
        val policy = FrameRecoveryPolicy(resetConfidenceAfterFailures = 2)
        policy.recordFailure()
        policy.recordSuccess()
        assertEquals(1, policy.recordFailure().consecutiveFailures)
    }
}
