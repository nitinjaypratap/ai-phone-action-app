# AI Phone Action — Android prototype

An Android prototype for user-consented continuous screen capture, on-device OCR, phase confidence tracking, generic object proposals, and Free Fire-oriented informational guidance.

## Live perception

- Start **Continuous screen capture** and approve Android's screen-sharing prompt. A foreground service emits frames at a throttled rate of about 2 FPS (scaled to at most 1280 pixels wide). Stop capture from the checkbox/app; Android can also stop the projection.
- OCR scores phase-specific UI phrases and reports a heuristic confidence. A rolling five-frame tracker requires repeated evidence before displaying a stable phase.
- ML Kit object detection reports generic object labels and bounding boxes. It does not identify enemies, targets, weapons reliably, or tactical intent.
- The Free Fire-oriented decision engine provides phase-specific suggestions only. It does not dispatch game controls, aim, fire, select targets, queue, or requeue.

## Run

1. Open this repository in Android Studio and sync Gradle.
2. Install on an Android 8.0+ device.
3. Open the app and enable **Continuous screen capture**.
4. Approve Android's screen-sharing request and supervise the ongoing capture notification.
5. Review the phase estimate, confidence, generic visual detections, and recommendations. Turn capture off to stop.

## Privacy and limitations

- Frames and OCR/object analysis remain on-device in this prototype; temporary frame files are deleted after processing.
- Continuous capture uses Android MediaProjection and stops when the user ends sharing, the OS revokes the projection, or the service is stopped.
- OCR confidence is a heuristic, not a calibrated probability. Detection quality depends on device/model and game UI.
- Generic object detection is not enemy detection. This app deliberately avoids autonomous gameplay actions and combat target automation.
- Coordinates and strategy are not inferred from game telemetry; use the game only in ways allowed by its rules.
