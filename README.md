# AI Phone Action — Android prototype

An Android Studio starter for **user-initiated** screen capture/OCR and touch gestures. Every capture requires Android's screen-sharing consent, and gestures require the user to explicitly enable the app's Accessibility Service. The prototype does not run an autonomous gameplay loop or attempt to bypass app/game protections.

## Included

- One-shot screen capture using MediaProjection with a visible foreground-service notification.
- On-device text recognition with ML Kit.
- Explicit tap and swipe controls through Android Accessibility gestures.
- A small Kotlin UI that guides users through the required permissions.

## Open and run

1. Open this repository in Android Studio (a current version with Android SDK 35 support).
2. Let Gradle sync, then run the `app` configuration on an Android 8.0+ device/emulator.
3. Tap **Enable touch actions** and enable **AI Phone Action** in Android Accessibility settings.
4. Return to the app and use **Capture screen + read text**. Approve Android's screen-capture prompt each time.
5. Enter screen pixel coordinates and tap **Tap** or **Swipe**. Actions only run when you press the corresponding button.

## Notes and limitations

- Coordinates are physical screen pixels; the app does not infer targets or adapt across display sizes.
- OCR works on a captured frame and may miss text or misread content.
- Screen capture is a one-shot prototype, not a 30–60 FPS stream. High-rate capture can affect performance, battery life, privacy, and app compatibility.
- Android/other apps may restrict accessibility gestures or screen capture. Use only on your own device and with apps where automation is permitted.
- This project intentionally leaves out autonomous game play, target detection, combat decisions, credential storage, CAPTCHA handling, and unattended actions.
