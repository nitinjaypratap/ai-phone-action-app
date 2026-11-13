# AI Phone Action — Android prototype

An Android prototype for user-consented continuous screen capture, on-device OCR, rolling phase confidence, generic object proposals, and supervised Free Fire-oriented recommendations.

## Capture and processing

- The launcher opens the live dashboard (`PhaseDActivity`). Start capture from the dashboard and approve Android's screen-sharing prompt.
- `CaptureController` owns foreground-service start/stop requests. `ScreenCaptureService` produces throttled frames (~2 FPS, max 1280 px wide), reports stalls/errors, and stops when the user or Android ends projection.
- `FrameProcessor` is single-flight: it drops stale frames while work is in flight, decodes/OCRs and runs generic object detection, retries decode/OCR at most once, and always removes temporary frame files after processing.
- If capture is revoked or stalls, the user must stop and grant a new screen-sharing session; the app does not silently bypass Android consent.
- `PhaseConfidenceTracker` uses recent OCR evidence; confidence is heuristic, not a calibrated probability. Object labels are generic and do not identify enemies.

## Run

1. Open in Android Studio and sync Gradle.
2. Install on an Android 8.0+ device.
3. Start continuous capture in the dashboard and approve the Android prompt.
4. Review phase estimates, generic detections, and informational recommendations.
5. Turn off capture to stop the foreground service.

## Limitations

This version intentionally does not automate aiming, shooting, target selection, match queueing, or requeueing. The Free Fire-oriented engine offers informational guidance only. OCR/object detection are heuristic and require device testing; this update has not been built on a device in this environment.
