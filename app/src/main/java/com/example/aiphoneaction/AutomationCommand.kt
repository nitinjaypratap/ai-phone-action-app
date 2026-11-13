package com.example.aiphoneaction

enum class AutomationKind {
    TAP,
    SWIPE,
    LONG_PRESS,
    WAIT,
    OPEN_SETTINGS,
    OPEN_APP,
    GO_HOME,
    UNKNOWN
}

data class AutomationStep(
    val kind: AutomationKind,
    val x: Int? = null,
    val y: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val durationMs: Long = 500L,
    val target: String? = null,
)

data class AutomationScript(
    val steps: List<AutomationStep>
) {
    fun isEmpty(): Boolean = steps.isEmpty()
}

object AutomationParser {
    fun parse(raw: String): AutomationScript {
        if (raw.isBlank()) return AutomationScript(emptyList())

        val commands = raw.lineSequence()
            .flatMap { line -> line.split(Regex("(?i)\\s*;\\s*")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val steps = commands.mapNotNull { parseSingleCommand(it) }
        return AutomationScript(steps)
    }

    private fun parseSingleCommand(command: String): AutomationStep? {
        val text = command.trim()
        if (text.isEmpty()) return null

        val lower = text.lowercase()

        if (lower.startsWith("tap")) {
            val coords = extractCoordinates(text)
            if (coords.size >= 2) {
                return AutomationStep(
                    kind = AutomationKind.TAP,
                    x = coords[0],
                    y = coords[1]
                )
            }
        }

        if (lower.startsWith("swipe")) {
            val coords = extractCoordinates(text)
            if (coords.size >= 4) {
                return AutomationStep(
                    kind = AutomationKind.SWIPE,
                    x = coords[0],
                    y = coords[1],
                    x2 = coords[2],
                    y2 = coords[3]
                )
            }
        }

        if (lower.startsWith("long press") || lower.startsWith("longpress")) {
            val coords = extractCoordinates(text)
            if (coords.size >= 2) {
                return AutomationStep(
                    kind = AutomationKind.LONG_PRESS,
                    x = coords[0],
                    y = coords[1]
                )
            }
        }

        if (lower.startsWith("wait")) {
            val number = text.substringAfter("wait", "").trim().removePrefix("(").removeSuffix(")")
            val millis = number.toLongOrNull() ?: 500L
            return AutomationStep(kind = AutomationKind.WAIT, durationMs = millis)
        }

        if (lower.startsWith("open settings") || lower == "settings") {
            return AutomationStep(kind = AutomationKind.OPEN_SETTINGS)
        }

        if (lower.startsWith("go home") || lower == "home") {
            return AutomationStep(kind = AutomationKind.GO_HOME)
        }

        if (lower.startsWith("open app") || lower.startsWith("launch app") || lower.startsWith("launch")) {
            val target = text.substringAfter("app", "").trim()
                .ifBlank { text.removePrefix("launch", ignoreCase = true).trim() }
            return AutomationStep(kind = AutomationKind.OPEN_APP, target = target)
        }

        return null
    }

    private fun extractCoordinates(command: String): List<Int> {
        return Regex("-?\\d+").findAll(command)
            .map { it.value.toInt() }
            .toList()
    }
}
