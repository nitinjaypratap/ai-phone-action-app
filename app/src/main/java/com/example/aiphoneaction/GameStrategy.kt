package com.example.aiphoneaction

enum class TacticalActionType {
    OPEN_MATCH,
    SELECT_MODE,
    DROP_LANE,
    SCOUT,
    LOOT,
    HEAL,
    ENGAGE,
    RETREAT,
    RELOAD,
    REQUEUE,
    UNKNOWN
}

data class StrategyAction(
    val type: TacticalActionType,
    val label: String,
    val description: String,
    val priority: Int
)

data class StrategyPlan(
    val phase: GameState,
    val goal: String,
    val actions: List<StrategyAction>
)

class GameStrategyEngine {
    fun plan(state: GameState): StrategyPlan = when (state) {
        GameState.LOBBY -> StrategyPlan(
            phase = state,
            goal = "Enter match and select a safe mode",
            actions = listOf(
                StrategyAction(TacticalActionType.OPEN_MATCH, "Queue for match", "Open the match queue and confirm the selected mode.", 1),
                StrategyAction(TacticalActionType.SELECT_MODE, "Pick mode", "Choose a mode aligned with current comfort and squad setup.", 2),
                StrategyAction(TacticalActionType.UNKNOWN, "Wait for load", "Hold position until the match starts and the spawn screen is ready.", 3)
            )
        )

        GameState.LOADING -> StrategyPlan(
            phase = state,
            goal = "Wait for a clean spawn and map sync",
            actions = listOf(
                StrategyAction(TacticalActionType.UNKNOWN, "Monitor load screen", "Stay idle while the client loads the map and player data.", 1),
                StrategyAction(TacticalActionType.UNKNOWN, "Calibrate timing", "Wait for the parachute or landing view to appear before acting.", 2)
            )
        )

        GameState.PARACHUTING -> StrategyPlan(
            phase = state,
            goal = "Land safe and rotate early",
            actions = listOf(
                StrategyAction(TacticalActionType.DROP_LANE, "Choose landing lane", "Pick a lower-risk landing zone with early cover and low traffic.", 1),
                StrategyAction(TacticalActionType.SCOUT, "Scan area", "Check for nearby threats, supply routes, and high-value loot opportunities.", 2),
                StrategyAction(TacticalActionType.LOOT, "Secure gear", "Grab armor, ammo, and healing before moving toward the next zone.", 3)
            )
        )

        GameState.AIRDROP -> StrategyPlan(
            phase = state,
            goal = "Secure the airdrop route without overcommitting",
            actions = listOf(
                StrategyAction(TacticalActionType.SCOUT, "Track enemy pressure", "Observe nearby players and the drop perimeter before committing.", 1),
                StrategyAction(TacticalActionType.LOOT, "Prioritize loot", "Take only mandatory loot and rotate fast to avoid a fight on top of the drop.", 2),
                StrategyAction(TacticalActionType.RETREAT, "Rotate out", "Exit the zone once the drop is cleared or contested.", 3)
            )
        )

        GameState.COMBAT -> StrategyPlan(
            phase = state,
            goal = "Fight efficiently with cover and sustain",
            actions = listOf(
                StrategyAction(TacticalActionType.ENGAGE, "Engage with control", "Keep a short burst pattern while moving behind cover and tracking the target.", 1),
                StrategyAction(TacticalActionType.HEAL, "Use heal if needed", "Apply med-kit or armor when health drops below acceptable threshold.", 2),
                StrategyAction(TacticalActionType.RETREAT, "Break contact", "Retreat when pressure spikes or when the fight is no longer favorable.", 3)
            )
        )

        GameState.LOOTING -> StrategyPlan(
            phase = state,
            goal = "Collect high-value gear and rotate quickly",
            actions = listOf(
                StrategyAction(TacticalActionType.LOOT, "Loot priority", "Take med-kit, armor, ammo, and weapon upgrades in that order.", 1),
                StrategyAction(TacticalActionType.RELOAD, "Prepare weapon", "Ensure the weapon is loaded and the inventory is stable before moving.", 2),
                StrategyAction(TacticalActionType.SCOUT, "Move to next cover", "Rotate to a safer or more strategic location before the next fight.", 3)
            )
        )

        GameState.DEAD -> StrategyPlan(
            phase = state,
            goal = "Re-enter with better positioning and decision quality",
            actions = listOf(
                StrategyAction(TacticalActionType.REQUEUE, "Queue again", "Reset quickly and focus on the decision point that caused the loss.", 1),
                StrategyAction(TacticalActionType.SCOUT, "Review mistakes", "Check whether the death was caused by poor route choice, overexposure, or poor loot decisions.", 2)
            )
        )

        GameState.VICTORY -> StrategyPlan(
            phase = state,
            goal = "End cleanly and prepare for next round",
            actions = listOf(
                StrategyAction(TacticalActionType.OPEN_MATCH, "Continue to next match", "Hold a brief reset and queue for the next game when ready.", 1),
                StrategyAction(TacticalActionType.UNKNOWN, "Capture outcome", "Record final state and note the map, route, and loot pattern for later optimization.", 2)
            )
        )

        GameState.MENU -> StrategyPlan(
            phase = state,
            goal = "Return to the actual match flow",
            actions = listOf(
                StrategyAction(TacticalActionType.OPEN_MATCH, "Return to lobby flow", "Return to the main game path and choose the next objective.", 1),
                StrategyAction(TacticalActionType.SELECT_MODE, "Reset mode", "Verify the match or mode selection before proceeding.", 2)
            )
        )

        GameState.UNKNOWN -> StrategyPlan(
            phase = state,
            goal = "Refresh the screen and retry detection",
            actions = listOf(
                StrategyAction(TacticalActionType.UNKNOWN, "Capture fresh frame", "Immediately retry OCR for a more reliable state read.", 1),
                StrategyAction(TacticalActionType.UNKNOWN, "Validate screen", "Check for menu transitions, loading states, or mismatch between UI text and actual gameplay.", 2)
            )
        )
    }

    fun summaryFor(state: GameState): String {
        val plan = plan(state)
        return buildString {
            append(plan.goal)
            append("\n")
            append(plan.actions.joinToString(separator = " -> ") { it.label })
        }
    }
}
