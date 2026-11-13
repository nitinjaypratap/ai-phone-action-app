package com.example.aiphoneaction

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Opt-in, conservative assistance for a few non-combat states.
 * This deliberately never aims, fires, throws items, queues matches, or requeues.
 * Coordinates are normalized to the current display and still need in-game testing.
 */
class GameOrchestrator(context: Context) {
    private val appContext = context.applicationContext
    private var candidateState = GameState.UNKNOWN
    private var consecutiveFrames = 0
    private var lastActionState: GameState? = null

    data class Result(
        val state: GameState,
        val message: String,
        val actionDispatched: Boolean
    )

    fun observe(state: GameState, enabled: Boolean, callback: (Result) -> Unit) {
        if (state == candidateState) consecutiveFrames++ else {
            candidateState = state
            consecutiveFrames = 1
        }

        if (!enabled) {
            callback(Result(state, "Assisted actions are off; review the recommendation and act manually.", false))
            return
        }
        if (consecutiveFrames < REQUIRED_STABLE_FRAMES) {
            callback(Result(state, "Confirming state (${consecutiveFrames}/$REQUIRED_STABLE_FRAMES frames); no action taken.", false))
            return
        }
        if (lastActionState == state) {
            callback(Result(state, "Already assisted for this state. A different detected state is required before another action.", false))
            return
        }

        val service = TouchAccessibilityService.instance
        if (service == null) {
            callback(Result(state, "Accessibility service is not enabled; no action taken.", false))
            return
        }

        val metrics = displayMetrics()
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val action: (() -> Unit)? = when (state) {
            // Short, bounded movement gestures only. No target selection or combat automation.
            GameState.PARACHUTING -> ({
                service.swipe((w * 0.17f).toInt(), (h * 0.72f).toInt(), (w * 0.17f).toInt(), (h * 0.80f).toInt()) { ok ->
                    if (ok) lastActionState = state
                    callback(Result(state, if (ok) "Sent one short forward movement gesture." else "Movement gesture was cancelled.", ok))
                }
            })
            GameState.LOOTING -> ({
                service.tap((w * 0.83f).toInt(), (h * 0.66f).toInt()) { ok ->
                    if (ok) lastActionState = state
                    callback(Result(state, if (ok) "Sent one tap at the estimated interact/loot area." else "Loot tap was cancelled.", ok))
                }
            })
            GameState.COMBAT -> ({
                service.swipe((w * 0.17f).toInt(), (h * 0.72f).toInt(), (w * 0.10f).toInt(), (h * 0.72f).toInt()) { ok ->
                    if (ok) lastActionState = state
                    callback(Result(state, if (ok) "Sent one short movement gesture; combat and aiming remain manual." else "Movement gesture was cancelled.", ok))
                }
            })
            else -> null
        }

        if (action == null) {
            callback(Result(state, "No automatic action is configured for this state; continue manually.", false))
            return
        }
        action.invoke()
    }

    private fun displayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    companion object {
        private const val REQUIRED_STABLE_FRAMES = 2
    }
}
