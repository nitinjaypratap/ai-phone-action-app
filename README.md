# AI Phone Action — Android prototype

An Android prototype for user-initiated screen capture, on-device OCR, game-state suggestions, and explicitly enabled limited assisted gestures.

## Current integration layer

Phase D connects one captured frame to OCR, state classification, strategy planning, and an optional bounded action. Screen capture remains user initiated and requires Android's screen-sharing consent. The **limited assisted actions** toggle is off by default. When enabled, the app waits until it sees the same phase in two captured frames before considering one action, and it won't repeat an action for that phase until another phase is detected.

The limited action allowlist is movement for parachuting/combat and one estimated interact tap while looting. It does **not** automate aim, shooting, combat targeting, match queueing, or requeueing. OCR and fixed relative screen regions can be wrong; test only on your own device and keep assistance off unless you are actively supervising it. You can stop by unchecking the toggle or leaving the app.

## Run

1. Open in Android Studio and sync Gradle.
2. Install on an Android 8.0+ device.
3. Enable **AI Phone Action** in Accessibility settings if using assisted gestures.
4. Tap **Capture and analyze one frame** and approve Android's screen-sharing prompt.
5. Review the detected phase and strategy. Leave assisted actions off for manual operation, or opt in to limited assistance while supervising.

## Limitations

- This is not a continuous 30–60 FPS capture service. Android requires capture consent and device support; this build analyzes one frame at a time.
- OCR-only state detection is heuristic and can misclassify screens.
- Gesture locations are approximate and must be calibrated for the game's control layout.
- There is no autonomous gameplay, object detection, enemy detection, or guaranteed action success.
