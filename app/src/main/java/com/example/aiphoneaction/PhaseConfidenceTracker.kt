package com.example.aiphoneaction

/** A visual category proposal. ML Kit labels are generic and do not identify enemies. */
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

class PhaseConfidenceTracker(private val windowSize: Int = 5, private val minStableVotes: Int = 3) {
    private val history = ArrayDeque<Pair<GameState, Float>>()

    fun update(observation: ScreenObservation): PhaseEstimate {
        history.addLast(observation.inferredState to observation.confidence)
        while (history.size > windowSize) history.removeFirst()

        val votes = history.filter { it.first != GameState.UNKNOWN }
        val winner = votes.groupBy { it.first }
            .maxByOrNull { (_, samples) -> samples.sumOf { it.second.toDouble() } }
        val state = winner?.key ?: GameState.UNKNOWN
        val samples = winner?.value.orEmpty()
        val averageConfidence = if (samples.isEmpty()) 0f else samples.map { it.second }.average().toFloat()
        val stable = if (samples.size >= minStableVotes && averageConfidence >= 0.45f) state else GameState.UNKNOWN
        return PhaseEstimate(stable, averageConfidence, samples.size)
    }

    fun reset() = history.clear()
}
