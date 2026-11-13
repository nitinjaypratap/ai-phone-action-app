package com.example.aiphoneaction

/**
 * OCR-derived phase guess. Confidence and evidence are heuristic, not calibrated probabilities.
 * Defaults preserve source compatibility for callers constructing test/manual observations.
 */
data class ScreenObservation(
    val rawText: String,
    val inferredState: GameState,
    val confidence: Float = 0f,
    val evidence: Map<GameState, Int> = emptyMap()
)

class GameScreenAnalyzer {
    private val keywords = linkedMapOf(
        GameState.VICTORY to listOf("booyah" to 5, "victory" to 4, "winner" to 4, "match result" to 4),
        GameState.DEAD to listOf("eliminated" to 4, "spectating" to 4, "knocked" to 3, "revive" to 2, "defeated" to 3),
        GameState.LOADING to listOf("loading" to 4, "connecting" to 3, "matchmaking" to 4),
        GameState.PARACHUTING to listOf("parachute" to 5, "skydiving" to 4, "glide" to 2, "jumpmaster" to 3),
        GameState.AIRDROP to listOf("airdrop" to 5, "supply drop" to 4),
        GameState.LOOTING to listOf("loot" to 3, "backpack" to 3, "inventory" to 3, "pick up" to 2),
        GameState.COMBAT to listOf("headshot" to 4, "ammo" to 2, "reload" to 2, "safe zone" to 2, "kill" to 2, "enemy" to 2),
        GameState.LOBBY to listOf("battle royale" to 4, "ranked" to 3, "classic" to 2, "start" to 1, "play" to 1, "lobby" to 4),
        GameState.MENU to listOf("settings" to 3, "profile" to 2, "store" to 2, "guild" to 2)
    )

    fun analyze(rawText: String): ScreenObservation {
        val normalized = rawText.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val scores = keywords.mapValues { (_, terms) ->
            terms.sumOf { (term, weight) -> Regex("\\b${Regex.escape(term)}\\b").findAll(normalized).count() * weight }
        }.filterValues { it > 0 }
        val ranked = scores.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull()
        val topScore = top?.value ?: 0
        val secondScore = ranked.getOrNull(1)?.value ?: 0
        val separation = if (topScore == 0) 0f else topScore.toFloat() / (topScore + secondScore).toFloat()
        val strength = (topScore / 5f).coerceIn(0f, 1f)
        val confidence = (separation * strength).coerceIn(0f, 1f)
        val state = if (confidence >= MIN_CONFIDENCE) top?.key ?: GameState.UNKNOWN else GameState.UNKNOWN
        return ScreenObservation(rawText, state, confidence, scores)
    }

    companion object { private const val MIN_CONFIDENCE = 0.35f }
}
