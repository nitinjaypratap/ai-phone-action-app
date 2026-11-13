package com.example.aiphoneaction

import android.util.Log

class FramePipeline {
    private val analyzer = GameScreenAnalyzer()
    private val decisionEngine = GameDecisionEngine()

    data class FrameResult(
        val rawText: String,
        val inferredState: GameState,
        val recommendation: TacticalDecision
    )

    fun analyzeFrame(rawText: String): FrameResult {
        val observation = analyzer.analyze(rawText)
        val decision = decisionEngine.decide(observation.inferredState)
        Log.d("FramePipeline", "Detected state=${observation.inferredState.name}, action=${decision.primaryAction}")
        return FrameResult(
            rawText = rawText,
            inferredState = observation.inferredState,
            recommendation = decision
        )
    }
}
