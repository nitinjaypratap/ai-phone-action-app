package com.example.aiphoneaction

/** Free Fire-oriented recommendations; decisions are informational and never dispatch controls. */
class FreeFireDecisionEngine {
    fun recommend(estimate: PhaseEstimate, detections: List<VisualDetection>): FreeFireRecommendation {
        val notableObjects = detections
            .sortedByDescending { it.confidence }
            .take(3)
            .map { "${it.label} (${(it.confidence * 100).toInt()}%)" }

        val (headline, detail) = when (estimate.state) {
            GameState.LOBBY -> "Check mode and squad" to "Confirm the selected Free Fire mode and ready state manually before queueing."
            GameState.LOADING -> "Wait for the match" to "Avoid issuing controls until the spawn or drop view is visible."
            GameState.PARACHUTING -> "Plan a safe landing" to "Prefer a landing route with cover and an exit path; this app does not steer automatically."
            GameState.AIRDROP -> "Assess risk before approaching" to "Airdrops can attract other players. Check surroundings and retreat if the route is contested."
            GameState.COMBAT -> "Prioritize cover and awareness" to "Use the game's controls manually; OCR and generic object proposals cannot reliably identify opponents or aim points."
            GameState.LOOTING -> "Loot selectively" to "Prioritize survival supplies and useful upgrades, then rotate rather than staying exposed."
            GameState.DEAD -> "Review and reset manually" to "Check the match result and choose whether to spectate, return, or queue again."
            GameState.VICTORY -> "Match won" to "Review the result and continue only when you choose."
            GameState.MENU -> "Navigate the menu manually" to "Verify the screen before selecting a mode or changing settings."
            GameState.UNKNOWN -> "Need a clearer screen read" to "Wait for a stable screen and capture another frame; do not act on a low-confidence guess."
        }
        val objectSummary = if (notableObjects.isEmpty()) "No generic objects detected." else "Visual proposals: ${notableObjects.joinToString()} (not enemy classifications)."
        return FreeFireRecommendation(estimate.state, estimate.confidence, headline, "$detail $objectSummary")
    }
}

data class FreeFireRecommendation(
    val phase: GameState,
    val confidence: Float,
    val headline: String,
    val detail: String
)
