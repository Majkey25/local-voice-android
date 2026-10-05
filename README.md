# Install Local Voice

Local Voice is an Android-only, local-first dictation app. It keeps the active keyboard and adds a movable microphone through an `AccessibilityService` overlay.

[Privacy policy, terms and data deletion](https://majkey25.github.io/local-voice-android/)
are linked from every app screen. Version 0.4.1 clarifies optional saved voice
references and prevents overlapping voice saves/deletes or false deletion success.

## Install the APK

1. Copy `release/LocalVoice-0.4.0.apk` to the phone.
2. Allow installation from the app that opens the APK.
3. Install and open **Local Voice**.
4. Pick English, Czech, German, French, or Spanish. This choice is independent of the phone's system language.
5. Pick a writing style or import a UTF-8 `.txt` writing sample.
6. Download the 601 MB Multilingual Offline Pack.
7. Allow microphone access.
8. Enable **Local Voice - floating microphone** in Android accessibility settings.

Tap the floating **MIC** button to start and stop dictation. Drag the button to move it. Tap **↶** within eight seconds to undo the last insertion.

## Personalize recognition

- **Dictionary** stores exact spoken → written forms per language and applies them even in Raw mode.
- **Snippets** expand only an exact spoken trigger, so ordinary sentences are not replaced accidentally.
- **Voice calibration** records a roughly two-minute guided sample in RAM, compares local Whisper output with the displayed text, and proposes explicit dictionary corrections. Consecutive mistakes in a name or product stay one phrase. Later sessions apply confirmed terms first and propose only new corrections. It does not claim to fine-tune Whisper model weights.
- **Cleanup** offers Raw, Light, and Polished profiles. Bubble size and opacity are adjustable.

Tap starts normal toggle dictation. Hold the bubble for push-to-talk; release to process. A separate cancel action is visible while recording.

## Build the APK

Run the repository-defined Gradle wrapper with JDK 17:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The build writes `app/build/outputs/apk/debug/app-debug.apk`.

## Read the project records

- `docs/SPEC.md` defines the shipped scope.
- `docs/RESEARCH.md` records current platform and model decisions.
- `docs/QA.md` records the checks and known limits.
