# Local Voice Android 0.3

## Outcome

An installable Android APK whose primary flow is:

`floating mic -> in-memory PCM -> local Whisper -> local Qwen semantic edit -> validated accessibility insertion`

## Required behavior

- Preserve the user's normal keyboard.
- Use an AccessibilityService-owned movable microphone overlay.
- Keep captured audio in bounded RAM and discard it after transcription.
- Run Czech Whisper STT and semantic editing on the phone after one verified model-pack download.
- Reject cloud inference and cleartext traffic.
- Refuse secure/password fields.
- Insert only when the focused field still matches the captured snapshot.
- Keep a clipboard fallback and one-step bounded undo.
- Read user text with Android's installed Czech TTS voice.
- Keep transcript/audio history off.
- Keep independent cleanup, dictionary, snippets, and calibration data per language profile.
- Apply exact dictionary replacements before semantic cleanup, including in Raw mode.
- Expand a snippet only when the complete spoken input matches its trigger.
- Offer a guided 1–2 minute local calibration that measures the displayed sample and requires confirmation before storing suggestions.
- Support tap-to-toggle and hold-to-talk with separate cancel and bounded undo actions.
- Let the user change the bubble size and opacity while retaining a minimum 48 dp touch target.
- Onboard from the phone's configured Tier 1 languages.
- Show localized writing-style examples and persist one language profile.
- Let the user paste or import a bounded UTF-8 writing sample for local style imitation.
- Keep Personal Voice in a separate consent-gated Voice Lab.

## Multilingual model pack

- Sherpa-ONNX Whisper tiny INT8: 103,609,903 bytes.
- LiteRT-LM Qwen3 0.6B mixed INT4: 497,664,000 bytes.
- Total download: 601,273,903 bytes.

Every file uses a pinned revision, expected byte count, SHA-256 verification, resumable `.part` download, and atomic rename.

The current production-claim scope is Czech, English, German, French, and Spanish. Whisper and Qwen are multilingual, but other languages are not offered as production-quality without a benchmark.

## Personal Voice boundary

Voice Lab records one 10–30 second mono PCM reference into Android's credential-encrypted app storage. It records whether the speaker confirmed ownership or explicit permission. It does not claim to train a new model.

Personal-voice synthesis is intentionally locked. Current Android-ready PocketTTS and ZipVoice integrations do not have verified Czech output. The stored reference can be used only after a Czech-capable engine passes quality, licensing, memory, and device benchmarks.

## Non-goals for 0.3

- Cloud providers, accounts, analytics, automatic permanent learning, voice cloning, paired-PC mode, and Play Store publication.
- On-device acoustic fine-tuning of Whisper weights. Calibration is an explicit local correction layer, not model training.
