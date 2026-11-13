package com.example.aiphoneaction

/** Pure bounded-recovery state for frame processing; safe to exercise in JVM unit tests. */
class FrameRecoveryPolicy(
    private val maxRetriesPerFrame: Int = 1,
    private val resetConfidenceAfterFailures: Int = 3,
    private val baseRetryDelayMs: Long = 250L,
    private val maxRetryDelayMs: Long = 2_000L
) {
    data class FailureOutcome(
        val consecutiveFailures: Int,
        val resetConfidence: Boolean,
        val requiresUserRestart: Boolean
    )

    private var consecutiveFailures = 0

    init {
        require(maxRetriesPerFrame >= 0)
        require(resetConfidenceAfterFailures > 0)
        require(baseRetryDelayMs >= 0)
        require(maxRetryDelayMs >= baseRetryDelayMs)
    }

    /** Null means the retry budget for this frame has been exhausted. */
    fun retryDelayMs(retryAttempt: Int): Long? {
        if (retryAttempt < 0 || retryAttempt >= maxRetriesPerFrame) return null
        val multiplier = 1L shl retryAttempt.coerceAtMost(30)
        return (baseRetryDelayMs * multiplier).coerceAtMost(maxRetryDelayMs)
    }

    @Synchronized
    fun recordFailure(): FailureOutcome {
        consecutiveFailures++
        val thresholdReached = consecutiveFailures >= resetConfidenceAfterFailures
        return FailureOutcome(
            consecutiveFailures = consecutiveFailures,
            resetConfidence = thresholdReached,
            // Analysis failures do not silently restart MediaProjection. Ask the user to
            // intervene only after repeated failures; capture consent must be renewed manually.
            requiresUserRestart = consecutiveFailures >= resetConfidenceAfterFailures * 2
        )
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
    }

    @Synchronized
    fun reset() {
        consecutiveFailures = 0
    }
}
