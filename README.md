# AI Phone Action — Android prototype

An Android prototype for user-initiated screen capture, OCR-based analysis, and explicit touch automation. The app is designed as a foundation for the wider AI Phone Action roadmap, including screen understanding, command parsing, and basic automation workflows.

## Included

- One-shot screen capture using MediaProjection with a visible foreground-service notification.
- OCR text recognition using ML Kit.
- Explicit tap and swipe controls through Android Accessibility gestures.
- A lightweight command parser for commands like `tap 200 400`, `swipe 100 200 800 1200`, and `wait 500`.
- Basic game-state inference that maps OCR text to states such as lobby, loading, combat, loot, and victory.

## Open and run

1. Open this repository in Android Studio.
2. Let Gradle sync, then run the `app` configuration on an Android 8.0+ device/emulator.
3. Tap **Enable touch actions** and enable **AI Phone Action** in Android Accessibility settings.
4. Return to the app and use **Capture screen + read text**. Approve Android's screen-capture prompt each time.
5. Enter manual coordinates or a command string and press **Tap**, **Swipe**, or **Run command**.

## Supported command examples

- `tap 200 400`
- `swipe 100 200 800 1200`
- `long press 500 900`
- `wait 500`
- `open settings`
- `go home`
- `open app free fire`

## Notes and limitations

- This is a controlled prototype, not an autonomous bot. Every gesture still requires the user to approve accessibility access and interact with the app.
- OCR is a best-effort screen analysis and may miss text or misread content.
- Full autonomous gameplay and real-time tactical decisions require a much larger pipeline involving frame capture, object detection, decision logic, and device-specific optimization.
