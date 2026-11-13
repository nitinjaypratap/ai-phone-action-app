package com.example.aiphoneaction

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityService

class AutomationEngine(private val context: Context) {
    fun execute(script: AutomationScript, onProgress: (String) -> Unit) {
        if (script.isEmpty()) {
            onProgress("No actionable commands were parsed.")
            return
        }

        val service = TouchAccessibilityService.instance
        if (service == null) {
            onProgress("Enable AI Phone Action in Accessibility settings first.")
            return
        }

        fun runStep(index: Int) {
            if (index >= script.steps.size) {
                onProgress("Completed ${script.steps.size} automation step(s).")
                return
            }

            val step = script.steps[index]
            when (step.kind) {
                AutomationKind.TAP -> {
                    service.tap(step.x ?: 0, step.y ?: 0) { ok ->
                        onProgress(if (ok) "Tap at (${step.x}, ${step.y})." else "Tap failed.")
                        runStep(index + 1)
                    }
                }

                AutomationKind.SWIPE -> {
                    service.swipe(step.x ?: 0, step.y ?: 0, step.x2 ?: 0, step.y2 ?: 0) { ok ->
                        onProgress(if (ok) "Swipe complete." else "Swipe failed.")
                        runStep(index + 1)
                    }
                }

                AutomationKind.LONG_PRESS -> {
                    service.longPress(step.x ?: 0, step.y ?: 0, step.durationMs) { ok ->
                        onProgress(if (ok) "Long press complete." else "Long press failed.")
                        runStep(index + 1)
                    }
                }

                AutomationKind.WAIT -> {
                    onProgress("Waiting ${step.durationMs} ms...")
                    Handler(Looper.getMainLooper()).postDelayed({ runStep(index + 1) }, step.durationMs)
                }

                AutomationKind.OPEN_SETTINGS -> {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    onProgress("Opened Android settings.")
                    runStep(index + 1)
                }

                AutomationKind.GO_HOME -> {
                    val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    onProgress(if (ok) "Pressed home." else "Home action unavailable.")
                    runStep(index + 1)
                }

                AutomationKind.OPEN_APP -> {
                    val packageName = resolvePackage(step.target)
                    if (packageName != null) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            onProgress("Opened app: ${step.target ?: packageName}")
                            runStep(index + 1)
                            return
                        }
                    }
                    onProgress("App target '${step.target ?: "unknown"}' is not available on this device.")
                    runStep(index + 1)
                }

                AutomationKind.UNKNOWN -> {
                    onProgress("Skipped unsupported command.")
                    runStep(index + 1)
                }
            }
        }

        runStep(0)
    }

    private fun resolvePackage(target: String?): String? {
        val normalized = (target ?: "").trim().lowercase()
        if (normalized.isEmpty()) return null

        val map = mapOf(
            "free fire" to "com.dts.freefireth",
            "freefire" to "com.dts.freefireth",
            "free fire max" to "com.dts.freefiremax",
            "freefire max" to "com.dts.freefiremax",
            "whatsapp" to "com.whatsapp",
            "settings" to "com.android.settings",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera2",
            "messages" to "com.google.android.apps.messaging"
        )

        val exact = map[normalized]
        if (exact != null) return exact

        val packageName = if (normalized.contains(".")) normalized else null
        if (packageName != null) {
            return packageName
        }

        val installed = context.packageManager.packages
            .filter { pkg -> pkg.packageName.lowercase().contains(normalized) || pkg.applicationInfo?.loadLabel(context.packageManager)?.toString()?.lowercase()?.contains(normalized) == true }
            .map { it.packageName }
            .firstOrNull()
        return installed
    }

    private fun openUnknownAppInStore(target: String) {
        val url = "https://play.google.com/store/search?q=${Uri.encode(target)}&c=apps"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
