package com.example.aiphoneaction

/** A visual category proposal. Generic detector labels are not game target classifications. */
data class VisualDetection(
    val label: String,
    val confidence: Float,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class PhaseEstimate(
    val state: GameState,
    val confidence: Float,
    val stableFrames: Int
)

/**
 * Sliding-window vote with conservative hysteresis. UNKNOWN and low-confidence observations
 * occupy a slot in the window so old evidence expires instead of keeping a stale phase alive.
 */
class PhaseConfidenceTracker(
    private val windowSize: Int = 5,
    private val minStableVotes: Int = 3,
    private val minAverageConfidence: Float = 0.45f,
    private val minVoteShare: Float = 0.60f,
    private val minObservationConfidence: Float = 0.25f
) {
    private val history = ArrayDeque<Pair<GameState, Float>>()

    init {
        require(windowSize > 0)
        require(minStableVotes in 1..windowSize)
        require(minVoteShare in 0f..1f)
        require(minAverageConfidence in 0f..1f)
        require(minObservationConfidence in 0f..1f)
    }

    @Synchronized
    fun update(observation: ScreenObservation): PhaseEstimate {
        val valid = observation.inferredState != GameState.UNKNOWN &&
            observation.confidence.isFinite() &&
            observation.confidence >= minObservationConfidence
        history.addLast(if (valid) observation.inferredState to observation.confidence.coerceIn(0f, 1f) else GameState.UNKNOWN to 0f)
        while (history.size > windowSize) history.removeFirst()

        val validVotes = history.filter { it.first != GameState.UNKNOWN }
        val winner = validVotes.groupBy { it.first }
            .maxByOrNull { (_, samples) -> samples.sumOf { it.second.toDouble() } }
        val samples = winner?.value.orEmpty()
        val averageConfidence = if (samples.isEmpty()) 0f else samples.map { it.second }.average().toFloat()
        val voteShare = if (validVotes.isEmpty()) 0f else samples.size.toFloat() / validVotes.size
        val isStable = samples.size >= minStableVotes &&
            averageConfidence >= minAverageConfidence &&
            voteShare >= minVoteShare
        return PhaseEstimate(
            state = if (isStable) winner!!.key else GameState.UNKNOWN,
            confidence = if (isStable) averageConfidence else 0f,
            stableFrames = samples.size
        )
    }

    @Synchronized
    fun reset() = history.clear()
}
