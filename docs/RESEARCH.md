# Android research record

Verified 2026-08-20 from current primary sources.

Reverified 2026-08-22:

- Wispr Flow Android 2.2.4 uses a field-aware floating bubble, accessibility insertion, dictionary, app-category writing styles, transcript retry/recovery, copy-last, snooze, bubble sizing/opacity, and cloud-only transcription. Official Android requirements are Android 13+, internet, microphone, overlay, accessibility, and optional battery exemption.
- Phravia 1.6.7 keeps the user's keyboard and exposes a floating mic, optional full IME, AccessibilityService, Quick Settings tile, external start/stop/language/style actions, local or BYOK processing, offline model management, output styles, and language/script profiles. Its Play-delivered bundle is protected by Play licensing; live sideload onboarding correctly redirected to Play and was not bypassed.
- Sherpa-ONNX contextual hotwords are supported by transducer models, not the current Whisper backend. Local Voice therefore applies an explicit dictionary after Whisper and before semantic cleanup. Guided calibration proposes corrections but does not claim acoustic-model fine-tuning.
- Current competitor evidence was collected from official Wispr documentation/site media, Google Play metadata/screenshots, and signed package resources. Proprietary screenshots and APKs remain only in `.reference` and are excluded from publication.

- Android's platform `SpeechRecognizer` can request an on-device recognizer, but support and installed languages depend on the device.
- ML Kit GenAI Speech Recognition Basic does not list Czech; Advanced is limited to Pixel 10. It is not the Czech foundation for this app.
- ML Kit Prompt API is not available on Galaxy S25 Ultra. Feature-specific rewriting is available there but has fixed styles and no Czech support suitable for semantic dictation.
- Sherpa-ONNX 1.13.6 provides the current prebuilt Android AAR, offline multilingual Whisper, and Java/Kotlin TTS APIs.
- LiteRT-LM 0.16.1 provides the current Gradle/Kotlin Android API used here. The selected Apache-2.0 Qwen3 0.6B mixed INT4 model is 474.61 MiB and has documented CPU and OpenCL GPU measurements on a Samsung S25-class device. The smaller 328 MB artifact was rejected because it is GPU-optimized and failed real CPU inference without OpenCL.
- `TYPE_ACCESSIBILITY_OVERLAY` keeps the normal keyboard and avoids the separate draw-over-other-apps permission. Text is changed only with validated accessibility actions.
- Android `LocaleManager.systemLocales` exposes the user's configured system locale order; Android does not expose a public inventory of every downloaded language pack.
- Sherpa-ONNX PocketTTS supports offline zero-shot voice cloning from a short reference without a transcript. Current packaged examples are English. ZipVoice's current Android path is Chinese/English. Neither is an evidence-backed Czech mobile default.

Primary sources:

- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developers.google.com/ml-kit/genai/speech-recognition/android
- https://developers.google.com/ml-kit/genai
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.6
- https://k2-fsa.github.io/sherpa/onnx/hotwords/index.html
- https://wisprflow.ai/android
- https://docs.wisprflow.ai/articles/2809924024-android-download-installation-guide
- https://docs.wisprflow.ai/articles/6344532666-android-system-requirements
- https://phravia.com/
- https://play.google.com/store/apps/details?id=com.omkar.voiceflow
- https://k2-fsa.github.io/sherpa/onnx/android/
- https://developers.google.com/edge/litert-lm/android
- https://huggingface.co/litert-community/Qwen3-0.6B
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/reference/android/app/LocaleManager
- https://developer.android.com/reference/android/speech/tts/Voice
- https://k2-fsa.github.io/sherpa/onnx/tts/pocket.html
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.6
