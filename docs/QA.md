# Android QA record

## 0.3.1 candidate - 2026-08-22

Release gate:

- `:app:testDebugUnitTest`: 42 tests passed; zero failures and zero skips.
- `:app:lintDebug`: passed with zero errors and six warnings.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Combined clean gate: `BUILD SUCCESSFUL` with all 84 tasks executed.
- APK: 61,514,584 bytes; SHA-256 `463f793da93ec02bf3a37e782e349a4db697a0998602e38562a7adc2d034708e`.
- Package `cz.localvoice.app`, version `0.3.1` (`4`), minimum API 26, target API 36.
- APK Signature Scheme v2 verified with the Android debug certificate.

Recognition personalization:

- Tier-one calibration prompts contain 180 to 260 words and render as a scrollable roughly two-minute script.
- Consecutive substitutions such as `lokal vojs` are proposed as one `Local Voice` phrase correction.
- Correction runs longer than four words are rejected.
- Dictionary add, save, process restart, reload, and delete passed live on `emulator-5600`.
- The updated Czech calibration and dictionary screens rendered without a fresh `AndroidRuntime` crash.
- The exact `release/LocalVoice-0.3.1.apk` installed as an upgrade and cold-launched with version `0.3.1` (`4`).

Competitor refresh:

- Current official Play screenshots for Wispr Flow 2.2.4 and Phravia 1.6.7 were archived under ignored `.reference` research evidence.
- Phravia onboarding rendered from the signed sideload, then Play licensing opened Google Play when setup continued. The license was not bypassed.
- Wispr Flow's developer-disabled APKMirror download was respected. Its package, version, certificate, requirements, screenshots, and current feature set were verified from Google Play, Wispr documentation, and APKMirror metadata.
- The isolated AVD has no Google account. No public Google Play test account exists, and no unrecoverable account or fake phone verification was created.

## 0.3.0 checkpoint — 2026-08-22

Fresh local gate:

- `:app:testDebugUnitTest`: 39 tests passed; zero failures and zero skips.
- `:app:lintDebug`: passed with zero errors and six warnings.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed.
- Combined gate: `BUILD SUCCESSFUL`.
- APK: 61,511,776 bytes; SHA-256 `874671185576e46c289f561c9d91d53a760caa8e9e2fada5fad1f3444ec86089`.
- APK Signature Scheme v2 verified with the Android debug certificate.

Isolated Google Play AVD evidence (`VoiceLayerResearch_API35_22Aug`, API 35):

- Clean onboarding rendered without a crash.
- Seeded debug QA rendered Home, Dictionary, Settings, and Czech Calibration screens.
- Dictionary empty/add controls, Raw/Light/Polished cleanup choices, bubble size/opacity controls, and the full calibration prompt were present in UI trees.
- The package launched repeatedly without a fresh `AndroidRuntime` crash.
- Exact-serial instrumentation completed with one expected assumption skip: the API 35 x86_64 AVD lacked the model pack/safe semantic capacity.
- The physical Samsung and other agents' emulators were not modified.

Competitor verification:

- Phravia XAPK SHA-256 matched `4b0f105d23d6645ed8ca5d09824a4535975ba54932da5cf8ed0a5fbe572683bb`.
- Phravia base and splits verified with APK Signature Scheme v3 and Google Play App Signing; package `com.omkar.voiceflow`, version `1.6.7` (`59`).
- Sideload onboarding redirected to Google Play because the bundle requires Play licensing. Installer identity was not spoofed and the license check was not bypassed.
- Current Wispr Flow 2.2.4 package/certificate and UI were verified from official documentation and APKMirror metadata; direct bundle download is disabled by the developer.

Open release gates:

- Fresh physical SM-S938B Czech microphone/calibration benchmark.
- Fresh live push-to-talk/cancel/undo overlay check after the phone is available.
- Local semantic quality remains gated by the release-device corpus; no production-quality claim is made from build success alone.

## Production foundation checkpoint

Verified locally on 2026-08-20 after the production-redesign audit.

Implemented behavior:

- The accessibility overlay is event-driven and hidden without a supported focused text field.
- Password, password-variation, numeric, phone, disabled, invisible, and user-excluded targets are rejected by one shared policy.
- Package, window, view ID, bounds, text, and selection must still match before insertion or undo.
- An energy VAD rejects silence and separated noise bursts before Whisper or Qwen loads.
- A semantic-engine failure is reported before raw fallback is used.
- Undo is a separate eight-second action; it no longer replaces the next dictation gesture.
- TTS is initialized only after a playback gesture.
- AccessibilityService use has a separate disclosure with an unchecked affirmative-consent control. The service stays hidden when that consent is missing, even if enabled directly in Android settings.
- System and enabled-keyboard locales are merged and deduplicated before supported languages are offered.

Automated evidence:

- `:app:testDebugUnitTest`: 24 tests, zero failures, zero skips.
- `:app:lintDebug`: zero errors and six dependency-version warnings.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed.
- Debug APK: 61,919,618 bytes.
- Debug APK SHA-256: `e3ebd41415bc01f2cc4ea69522e0575343cd7c35355d2cd4049ba3c58ca4f05a`.
- APK Signature Scheme v2 verification passed with the Android debug certificate.

Connected and live validation is not complete. `adb devices -l` returned no targets. The installed API 35 system-image directory contained only incomplete installer metadata; `avdmanager` rejected it as an invalid package. A subsequent `sdkmanager` download ended without a registered `package.xml`, and the replacement `android sdk` bootstrap failed while moving its temporary file. No SM-S938B was discoverable over ADB. Therefore overlay visibility, password-field behavior, focus-loss cancellation, lazy-TTS startup improvement, latency, PSS, offline dictation, and the new release-device semantic gate are not claimed as live-passed in this checkpoint.

Verified on 2026-08-20.

## Build result

- Package: `cz.localvoice.app`
- Version: `0.2.0` (`versionCode` 2)
- Minimum Android API: 26
- Target and compile API: 36
- APK size: 61,436,768 bytes
- APK SHA-256: `6bd9ded39860470fae34b2914983c40e96d77495f790fe735f13f4ecafecc96d`

The delivered APK uses the Android debug signing key. It is installable. It is not a Play Store release artifact.

## Automated checks

The final command ran `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, and `connectedDebugAndroidTest`.

- Eleven JVM tests passed.
- Android Lint reported zero errors and six dependency-version warnings.
- Both APKs compiled and assembled.
- Instrumented semantic tests skipped on the two attached x86_64 AVDs because the app's memory gate rejected local CPU inference.

The API 35 AVD has 2,019,420 KiB RAM. A forced CPU inference without the gate was killed during XNNPACK initialization. The selected model's official Samsung CPU benchmark reports about 2.9 GB peak memory. The app now requires at least 6 GiB total RAM and 3 GiB available RAM before it attempts the CPU fallback. Low-memory devices use raw local dictation instead of risking an out-of-memory process kill.

## Live emulator checks

The following flows ran on the API 35 x86_64 AVD:

- A clean install opened the onboarding flow without a crash.
- The language page read the system locale list.
- The style page showed English casual, balanced, and professional examples after English selection.
- The custom-style page enforced the 200-character minimum and opened the system document chooser for UTF-8 text files.
- Microphone denial left the app stable. A later grant updated the status.
- The AccessibilityService overlay appeared without the draw-over-apps permission.
- The overlay kept focus in a Compose text field and moved through `MIC`, `REC`, processing, and `MIC` states.
- Sherpa-ONNX 1.13.6 loaded and transcribed captured audio.
- LiteRT-LM 0.16.1 loaded the local model runtime.
- A target-window change during processing blocked text insertion and used the clipboard fallback.
- The Voice Lab kept recording disabled until consent confirmation.
- The final APK installed and launched with no `AndroidRuntime` crash.

## Model verification

The final pack contains 601,273,903 bytes. Every file has a pinned URL, byte count, and SHA-256 value. The downloader resumes a `.part` file with an HTTP Range request. It verifies the completed file before moving it into place.

The selected Qwen model is `qwen3_0_6b_mixed_int4.litertlm`. Its local and device-side SHA-256 matched `b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9`.

## Checks not completed

No arm64 physical phone was attached. The Samsung SM-S938B Android 16 checks remain open:

- Czech microphone audio through Whisper and Qwen.
- GPU latency, memory, battery, and thermal behavior.
- Czech offline TTS voice availability.
- English and Czech instrumented semantic assertions on arm64.
- OEM accessibility settings and overlay behavior.

Personal-voice synthesis remains locked. Voice Lab records and validates a consented reference, but the app does not claim Czech voice cloning until an Android engine passes a Czech benchmark.
