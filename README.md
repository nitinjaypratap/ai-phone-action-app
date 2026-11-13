# AI Phone Action — Android prototype

An Android prototype for user-consented continuous screen capture, on-device OCR, rolling phase confidence, generic object proposals, and supervised Free Fire-oriented recommendations.

## Confidence and recovery

- Phase confidence uses a bounded sliding window. UNKNOWN/low-confidence observations occupy window entries so old states expire; conflicting phases must satisfy vote-share and confidence thresholds to be shown as stable.
- Frame analysis is single-flight, drops stale frames, retries each decode/OCR failure at most once with bounded backoff, deletes temporary files, and returns the UI to UNKNOWN on exhausted retries.
- After repeated consecutive frame failures, the confidence history resets; after a longer failure streak the UI recommends stopping and restarting capture. It never silently reuses or renews Android screen-sharing consent.
- Successful analysis clears the failure streak. Capture errors and user stop also clear confidence history.
- JVM unit tests cover stable/ambiguous/unknown confidence transitions and retry/recovery policy.

## Run

1. Open in Android Studio and sync Gradle.
2. Install on an Android 8.0+ device.
3. Start continuous capture in the dashboard and approve the Android prompt.
4. Review phase estimates, generic detections, and informational recommendations.
5. Turn off capture to stop the foreground service.

## Limitations

Capture runs at a throttled rate (~2 FPS, max 1280 px wide). OCR confidence is heuristic; generic object labels do not identify enemies. The Free Fire-oriented engine provides informational guidance only and does not automate gameplay actions. Device build/runtime verification remains necessary.
