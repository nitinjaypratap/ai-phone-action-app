package com.example.aiphoneaction

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

class GameDecisionEngine {
    fun decide(state: GameState): TacticalDecision {
        return when (state) {
            GameState.LOBBY -> TacticalDecision(
                phase = state,
                primaryAction = "Open match queue / select mode",
                summary = "Stay in lobby, confirm mode, and ready the squad before queueing."
            )
            GameState.LOADING -> TacticalDecision(
                phase = state,
                primaryAction = "Wait and monitor load screen",
                summary = "Do not fire yet; wait for player spawn and map sync."
            )
            GameState.PARACHUTING -> TacticalDecision(
                phase = state,
                primaryAction = "Move to safe landing lane",
                summary = "Choose a clean drop path and avoid high-risk hot zones early."
            )
            GameState.AIRDROP -> TacticalDecision(
                phase = state,
                primaryAction = "Secure airdrop route and loot fast",
                summary = "Prioritize cover and speed; airdrops are high-traffic contest zones."
            )
            GameState.COMBAT -> TacticalDecision(
                phase = state,
                primaryAction = "Engage with cover and recoil control",
                summary = "Keep movement constant, short bursts only, and circle around cover."
            )
            GameState.LOOTING -> TacticalDecision(
                phase = state,
                primaryAction = "Loot high-value gear and move on",
                summary = "Prioritize med-kit, armor, and weapon upgrades, then rotate to safer ground."
            )
            GameState.DEAD -> TacticalDecision(
                phase = state,
                primaryAction = "Reset and re-enter match",
                summary = "Hold position, analyze loss cause, and queue for the next round."
            )
            GameState.VICTORY -> TacticalDecision(
                phase = state,
                primaryAction = "Celebrate and wait for next match",
                summary = "Win state reached; move to next match or exit safely."
            )
            GameState.MENU -> TacticalDecision(
                phase = state,
                primaryAction = "Return to lobby or select match mode",
                summary = "Animation and menu flow indicate the app is not in combat."
            )
            GameState.UNKNOWN -> TacticalDecision(
                phase = state,
                primaryAction = "Request fresh frame and re-evaluate",
                summary = "The detected text is ambiguous; refresh the screen and retry detection."
            )
        }
    }
}

data class TacticalDecision(
    val phase: GameState,
    val primaryAction: String,
    val summary: String
)
