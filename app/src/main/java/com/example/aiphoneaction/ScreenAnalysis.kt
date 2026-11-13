package com.example.aiphoneaction

enum class GameState {
    UNKNOWN,
    LOBBY,
    LOADING,
    PARACHUTING,
    AIRDROP,
    COMBAT,
    LOOTING,
    DEAD,
    VICTORY,
    MENU
}

data class ScreenObservation(
    val rawText: String,
    val inferredState: GameState
)

class GameScreenAnalyzer {
    fun analyze(rawText: String): ScreenObservation {
        val normalized = rawText.lowercase()
        val state = when {
            normalized.contains("victory") || normalized.contains("winner") || normalized.contains("mission complete") -> GameState.VICTORY
            normalized.contains("dead") || normalized.contains("knocked") || normalized.contains("spectator") -> GameState.DEAD
            normalized.contains("loading") || normalized.contains("connecting") || normalized.contains("matchmaking") -> GameState.LOADING
            normalized.contains("lobby") || normalized.contains("play") || normalized.contains("match") -> GameState.LOBBY
            normalized.contains("parachute") || normalized.contains("jump") || normalized.contains("drop") -> GameState.PARACHUTING
            normalized.contains("airdrop") -> GameState.AIRDROP
            normalized.contains("battle") || normalized.contains("enemy") || normalized.contains("fire") || normalized.contains("aim") -> GameState.COMBAT
            normalized.contains("loot") || normalized.contains("inventory") || normalized.contains("bag") -> GameState.LOOTING
            normalized.contains("menu") || normalized.contains("settings") -> GameState.MENU
            else -> GameState.UNKNOWN
        }
        return ScreenObservation(rawText = rawText, inferredState = state)
    }
}
