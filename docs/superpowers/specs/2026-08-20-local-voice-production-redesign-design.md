# Local Voice Production Redesign

Date: 2026-08-20
Status: Design direction approved; written specification awaiting final review
Target: Android phone first, package `cz.localvoice.app`

## Decision

Local Voice will become a hybrid, local-first dictation layer for Android.

- The default route keeps audio, transcript, context, and edits on the phone.
- An optional quality route may use a user-enabled cloud or BYOK provider.
- A capability router selects only routes that satisfy the chosen language, privacy mode, available hardware, memory budget, and model readiness.
- The normal keyboard remains active. A focused-field accessibility overlay starts dictation and applies validated edits.
- Voice Lab remains a separate experimental feature. It does not block the dictation release and cannot claim voice training or Czech cloning until a real engine passes its own gates.

Pure local-only was rejected as the sole architecture because the current 0.6B semantic model fails a basic self-correction and is too slow and memory-heavy. Cloud-only was rejected because privacy, offline use, and user control are core differentiators.

## Current Evidence

The 0.2.0 APK builds and runs, but it is not production-ready.

- Eleven JVM tests, Android Lint, APK assembly, and connected instrumentation completed successfully at the Gradle task level.
- The connected semantic test was skipped on normal 2 GB emulators by a memory assumption.
- On an 8 GB API 35 AVD with the full 601 MB model pack, the real semantic assertion failed: “The meeting is at five, actually at six” retained both `five` and `six`.
- The first semantic run took 44.5 seconds. Warm runs took about 12.3 seconds.
- Warm semantic processing reached about 2.0 GB total PSS. A launcher recording that hallucinated from silence reached about 2.6 GB total PSS.
- The floating microphone records without a valid editable target and remains visible on the launcher.
- There is no voice activity detection, partial transcript, dictionary, snippets, app context, correction learning, or visible fallback reason.
- The current custom style forwards part of the raw sample instead of learning inspectable rules.
- The current AccessibilityService onboarding lacks the separate prominent disclosure and affirmative consent expected by Google Play policy.
- Voice Lab correctly gates recording with consent and rejects silence, but it stores only a reference sample and performs no voice cloning.

Detailed artifacts are in `.reference/audit-current`.

The competitor feature-readiness baseline was estimated from current official product material, not from a controlled audio benchmark:

| Product | Estimated readiness | Main advantage | Main limitation |
| --- | ---: | --- | --- |
| Local Voice 0.2.0 | 27/100 | Offline privacy and local model pack | Incorrect, slow semantic edit and weak field behavior |
| Phravia | 68/100 | Local/cloud/BYOK choice and normal keyboard | Lighter public evidence for advanced editing |
| Wispr Flow Android | 82/100 | Mature field bubble, context, dictionary, styles, recovery | Cloud dependency |
| Gboard Rambler | 85/100 | Real-time polish and multilingual correction | Limited device rollout and platform control |

These estimates guide scope only. Release acceptance uses the measured gates in this document.

## Product Goals

1. Produce correct final Czech text from natural speech, including false starts and self-corrections.
2. Start quickly and insert text fast enough to feel like normal typing.
3. Show the microphone only for a supported focused field and never record without a valid target.
4. Preserve the user's keyboard, cursor, selection, surrounding text, and clipboard whenever the target supports safe insertion.
5. Make local processing the default and make every network route explicit before data leaves the phone.
6. Personalize punctuation, tone, vocabulary, snippets, and per-app style through inspectable settings.
7. Recover clearly from model, microphone, accessibility, focus, memory, and network failures.
8. Pass the same adversarial corpus on the Samsung SM-S938B Android 16 release device without skipped assertions.

## Non-goals for the Core Release

- Replacing the Android keyboard.
- Executing destructive spoken commands against unselected existing text.
- Automatic permanent learning from private user text or corrections.
- Accounts, social features, analytics, or transcript history.
- Background recording.
- Claiming voice cloning before Czech quality, consent, licensing, latency, memory, and deletion gates pass.
- Supporting every Android editor through unsafe whole-field replacement.

## Supported Platform

- Primary acceptance device: Samsung SM-S938B, Android 16, API 36.
- Minimum supported Android version remains API 26 unless benchmark evidence forces a documented increase.
- Low-memory validation uses at least one 4 GB emulator or physical device.
- The app remains one Android application module until a measured build or ownership problem justifies another module.

## User Experience

### Onboarding

Onboarding has five short stages and resumes from the last completed stage.

1. **Language**
   - Build a deduplicated list from Android system locales and enabled keyboard subtype locales.
   - Put the current system locale first.
   - Explain that Android does not expose a reliable inventory of every downloaded speech pack.
   - Persist one primary language and allow later language profiles.
2. **Writing style**
   - Show the same localized source text as Casual, Balanced, and Professional examples.
   - Make punctuation, commas, paragraphing, casing, emoji use, and sentence length visibly different.
   - Let the user compare examples before selecting one.
3. **My style**
   - Allow skip, paste, or UTF-8 text import.
   - Accept 200 to 10,000 normalized characters.
   - Extract a bounded, inspectable style profile instead of attaching the raw sample to every request.
   - Show a before/after preview and require confirmation before activation.
4. **Privacy and AccessibilityService disclosure**
   - Explain, on a separate app screen, that Local Voice reads the active editable field and bounded surrounding text, listens only after a user gesture, and writes the result back.
   - Show local and optional network data categories separately.
   - Require an unchecked affirmative consent before opening Android accessibility settings.
5. **Microphone and models**
   - Request microphone permission only after explaining the recording gesture.
   - Show required storage, free-space validation, download progress, pause, resume, verification, and deletion.
   - Finish only when the selected route is usable. A raw local dictation fallback is valid when the semantic pack is unavailable.

Returning users do not repeat onboarding after an upgrade. They see a one-time disclosure update only when the data behavior materially changes.

### Focused-field microphone

The accessibility overlay is event-driven.

- Hidden when there is no focused, editable, supported target.
- Hidden for password and PIN fields.
- Hidden by default for numeric and phone fields.
- Hidden for packages in a shipped sensitive-app denylist and for user-selected excluded apps.
- Small, movable, and reachable with a 48 dp touch target.
- Shows clear idle, listening, transcribing, editing, fallback, and error states.
- Shows an input level or waveform while listening and a partial transcript when the selected engine supports streaming.
- Keeps the main microphone action available after insertion. Undo appears as a separate, temporary action and never replaces the next-dictation gesture.

A tap toggles short dictation. A hold records only while held. Moving focus during capture invalidates the original target. The app may offer the result as a preview or clipboard copy, but it must not insert into a different field.

### Dictation and editing

Core release modes are deliberately narrow:

- **Dictate:** insert polished speech at the current cursor or replace the current selection.
- **Rewrite selection:** transform only the selection after the user explicitly opens the rewrite action.
- **Raw fallback:** insert or copy the unedited transcript when semantic editing is unavailable or rejected.

Dictation mode never interprets speech as a destructive command against pre-existing text. Mechanical spoken punctuation is allowed only through locale-specific rules covered by tests. Selected rewrite never changes text outside the captured selection.

Unsafe rich editors receive an explicit preview and clipboard action instead of blind whole-field replacement.

### Personalization

Each language profile contains only user-visible data:

- Preferred built-in style.
- Custom style rules.
- Dictionary entries with spoken and written forms.
- Text snippets with unique triggers.
- Per-app style override keyed by package name.
- Cleanup level: Raw, Light, or Polished.

The custom-style analyzer records bounded traits such as average sentence length, comma rate, terminal punctuation, paragraph density, capitalization, emoji use, and a small validated set of tone rules. The user can edit, disable, export, or delete every rule. The original sample can be deleted immediately after analysis.

No correction becomes a permanent rule automatically. The user must confirm a suggested dictionary or style change.

### Voice Lab

Voice Lab is a separate top-level screen marked Experimental.

- Require speaker ownership or explicit-permission consent.
- Guide the user through approximately two minutes of localized prompts.
- Display live clipping, silence, noise, and completion feedback.
- Store references in credential-encrypted app storage with backup disabled.
- Provide playback, rename, and permanent delete.
- Do not expose synthesis until a Czech-capable mobile engine passes the Voice Lab gates.
- Describe zero-shot reference synthesis accurately; do not call it model training unless the device actually trains a model.

The core dictation release can ship while synthesis remains locked. Voice Lab recording must not download large synthesis models automatically.

## Runtime Design

### Components

The current single app module remains. Large classes are split by responsibility, not by speculative layers.

| Component | Responsibility |
| --- | --- |
| `DictationController` | Own one dictation state machine and coordinate capture, transcription, edit, validation, insertion, and fallback |
| `AudioCapture` | Capture bounded mono PCM after a user gesture and release microphone resources on every terminal path |
| `VadGate` | Detect speech start/end, reject silence, and limit maximum utterance length |
| Local speech engine | Provide final transcript and optional partials through the benchmark winner |
| Local edit engine | Convert a transcript plus bounded context into one validated edit operation |
| Route selector | Select from language, privacy mode, model readiness, memory budget, connectivity, and quality preference after a second proven route exists |
| `AccessibilityFieldBridge` | Discover supported targets, capture field snapshots, validate target freshness, insert safely, and create bounded undo data |
| `PersonalizationStore` | Persist typed language profiles, dictionary, snippets, and app overrides with existing Android storage |
| `ModelPackManager` | Download, resume, hash-check, version, activate, and delete model packs |
| `PrivacyPolicy` | Reject disallowed data egress before a provider call and describe the data categories for the current route |

No factory, service locator, repository layer, or manager is added unless two real callers or implementations require it. `SpeechEngine` and `EditEngine` interfaces plus the route selector are introduced only when the first proven network implementation creates a real second route. Until then, the controller calls the selected local implementations directly.

### Core data contracts

`FieldSnapshot` contains:

- Package name, window ID, stable view identifier when available, and bounds.
- Input kind and secure-field status.
- Bounded field text and selection indices.
- A text hash and monotonic capture time for stale-target validation.

`DictationRequest` contains:

- Language tag and cleanup level.
- Final transcript.
- At most 500 characters before and after the cursor, or at most 2,000 selected characters.
- Matching dictionary entries, resolved snippets, and active style rules.
- Route privacy mode.

`EditOperation` is one of:

- `InsertText(text)` at the captured cursor.
- `ReplaceSelection(expectedHash, text)` for the captured selection.
- `NoChange(reason)`.

An edit engine cannot return arbitrary field indices or delete surrounding text in the core release. Output length, control characters, target hash, selection, and package/window identity are validated before insertion.

### State machine

| State | Entry | Valid exit |
| --- | --- | --- |
| `HIDDEN` | No valid field or sensitive target | `READY` after a supported editable field is focused |
| `READY` | Valid target and no capture | `LISTENING`, `HIDDEN`, or `ERROR` |
| `LISTENING` | User tap or hold with a fresh target | `TRANSCRIBING`, `READY` for no speech, or `FALLBACK` after target loss |
| `TRANSCRIBING` | VAD produced bounded speech | `EDITING`, `FALLBACK`, or `ERROR` |
| `EDITING` | Transcript exists and cleanup is enabled | `INSERTING`, `FALLBACK`, or `ERROR` |
| `INSERTING` | Target snapshot still matches | `READY`, `HIDDEN`, or `FALLBACK` |
| `FALLBACK` | Safe insertion is unavailable | `READY` after preview/copy/dismiss |
| `ERROR` | Recoverable explicit failure | `READY` after recovery or `HIDDEN` without a target |

Every terminal path clears audio buffers and releases the microphone. Service destruction clears overlay windows and in-memory undo data.

### Target discovery and insertion

The service reacts to focus, window, text-selection, and content-change events. It does not keep a bubble permanently visible and does not scan the full accessibility tree on every event.

Before capture and again before insertion, the bridge verifies:

1. The target remains editable, enabled, visible, and non-secure.
2. Package, window, view identity, bounds, text hash, and selection still match within the supported tolerance.
3. The operation changes only the captured cursor or selection.
4. The resulting text is within the field and app limits.

Plain Android and verified Compose text fields may use `ACTION_SET_TEXT` with a reconstructed value and restored selection. Each supported editor class must have a regression test. Web, rich-text, or unknown editors use preview plus clipboard when span preservation and cursor behavior cannot be proven.

Undo stores one bounded pre-edit snapshot for eight seconds. It is available only while the same field and text hash still match.

### Audio and VAD

- Capture mono PCM in a bounded ring buffer.
- Maximum utterance length is configurable in one place and initially 120 seconds.
- VAD rejects silence before ASR and ends capture after a tested trailing-silence window.
- Audio is discarded after final transcription or failure.
- No raw audio or transcript log is written by default.
- Bluetooth and built-in microphones are included in the device corpus.

### Model and route selection

Model choice is benchmark-driven. The current Whisper tiny plus Qwen 0.6B pair is the baseline, not the default by declaration.

Local ASR candidates include the current Sherpa-ONNX Whisper pack, Sherpa-ONNX Omnilingual ASR 300M INT8, Qwen3-ASR 0.6B INT8, and Android on-device recognition only when Czech is installed and `isOnDeviceRecognitionAvailable` is true. Oversized candidates are eliminated before integration if they violate memory or latency gates.

The initial benchmark ranks candidates by:

- Czech clean and noisy WER: 35%.
- Numbers, dates, punctuation, and custom-name accuracy: 20%.
- End-of-speech latency: 20%.
- Peak PSS and thermal stability: 15%.
- Download size and startup cost: 10%.

A candidate must satisfy all hard gates; weighted rank cannot compensate for destructive errors, OOM risk, or unacceptable latency.

The optional network route is disabled by default. Provider selection occurs through the same Czech corpus, privacy review, current API verification, cost disclosure, and failure tests. A provider that does not pass is not exposed in the UI. BYOK secrets are stored with Android Keystore-backed encryption and never enter logs or diagnostics.

The router uses this order:

1. Enforce Local Only when selected.
2. Reject routes without the selected language or required model.
3. Reject routes above the current memory budget.
4. Prefer the selected Fast or Quality mode.
5. Fall back explicitly to raw local transcription, preview, or copy. Never hide the downgrade.

### Semantic editing

The editor receives a typed request and returns one typed operation. Its prompt or local instruction must:

- Preserve factual content, names, numbers, URLs, and code.
- Resolve self-corrections to the final stated value.
- Remove filler only at the selected cleanup level.
- Respect the selected language and code-switching.
- Apply dictionary and style rules before generic cleanup.
- Use surrounding context only for casing, punctuation, continuity, and duplication avoidance.
- Never invent missing facts or mutate text outside the allowed operation.

The output parser rejects invalid structure, unexplained content loss, unexpected language change, excessive length change, or target mismatch. Rejection routes to a visible raw transcript or preview. Semantic failures are never silently presented as successful polished text.

### Startup and lifecycle

- The launcher renders before initializing ASR, edit models, TTS, or Voice Lab.
- TTS initializes only when the user requests playback.
- Model loading occurs only after capture begins or through an explicit warm-up setting.
- Download work uses Android background work appropriate to user-visible, resumable downloads.
- Accessibility service diagnostics show enabled, disconnected, stopped-by-system, model-unavailable, and permission states separately.
- The app gives direct recovery actions and does not claim it can re-enable an accessibility service without Android user consent.

## Privacy and Security

Local mode is the default.

- Audio, transcript, context, dictionary, style, and edit remain local in Local Only mode.
- Optional network use has a per-route disclosure before first use and a persistent route indicator during dictation.
- Only the minimum required data categories are sent. ASR receives audio; semantic editing receives transcript plus bounded relevant context. A provider receives neither category when it does not need it.
- Password, PIN, and denied sensitive-app fields are never captured.
- Backups and cleartext traffic remain disabled.
- Model downloads require HTTPS, pinned artifact metadata, expected size, SHA-256 verification, atomic activation, and safe deletion.
- API keys use Android Keystore-backed storage.
- Diagnostics are local, bounded, redacted, and user-exported only. They include timings, route names, error codes, and model versions, not field text, transcript, audio, or secrets.
- With network available, a packet capture across ten Local Only dictations must show no inference connection or payload egress. A second airplane-mode run must complete without a network dependency after model installation.

Google Play publication requires policy and legal review in addition to engineering checks. The app must not represent engineering acceptance as Play policy approval.

## Evaluation System

### Corpora

The release corpus is versioned under `.reference`, contains consented or synthetic material, and contains no private user recordings.

1. **Czech audio corpus: 100 clips**
   - Clean and noisy rooms.
   - Built-in and Bluetooth microphones.
   - Different speaking rates and voices.
   - Names, numbers, dates, punctuation, URLs, and mixed Czech/English.
   - False starts, pauses, and self-corrections.
2. **Adversarial edit corpus: 40 cases**
   - Self-correction and abandoned thought.
   - Literal words that resemble commands.
   - Selected rewrite and no-selection behavior.
   - Lists and paragraph formatting.
   - Dictionary names and snippet collisions.
   - Mixed-language input.
   - Silence, noise, focus loss, stale targets, secure fields, and no target.
3. **Editor compatibility matrix**
   - Classic Android `EditText`.
   - Jetpack Compose text field.
   - Browser textarea/content editor.
   - At least three common messaging or productivity apps available on the release phone.
   - One unsupported rich editor to verify preview fallback.
   - Password, PIN, numeric, and denied financial-app fields.

### Metrics

- Word error rate and character error rate.
- Exact accuracy for names, numbers, dates, URLs, and punctuation.
- Final-state semantic correctness.
- Destructive error rate.
- Correct target and cursor insertion rate.
- Manual edit distance after insertion.
- Undo, retry, and fallback frequency.
- Speech-end-to-insert latency at p50 and p95.
- Cold launch and warm field-to-bubble latency.
- Peak PSS, model size, battery use, and thermal state.
- Service survival and recovery rate.

Subjective style comparisons use pairwise A/B samples with order reversal. Reviewers write a reason before assigning a score. At least one native Czech reviewer validates every release corpus result. Automated judges may assist but cannot replace the destructive-error and Czech final-state review.

### Competitive comparison

When Wispr Flow, Gboard Rambler, or Phravia is available on the same phone, Local Voice uses the same audio clips and final-text scoring rules. When a product cannot be installed or its staged feature is unavailable, the report separates official feature evidence from measured evidence. Marketing claims never count as passed Local Voice tests.

## Release Gates

All core gates apply to the actual release configuration. Route-specific gates apply to every route exposed in that build; a route that is not shipped is not counted as passing.

### Correctness

- Czech final-state semantic correctness: at least 95% across the 100-clip release corpus.
- Destructive mistakes: 0 across the 40 adversarial cases.
- Clean Czech WER: at most 12% for the local route and 8% for the quality route.
- Noisy Czech WER: at most 20% for the local route and 15% for the quality route.
- Names with an active dictionary entry: at least 95% exact.
- Numbers and dates: at least 95% exact.
- Correct target, cursor, or selection: 100% across the compatibility matrix.

### Performance

- Physical cold launch to first usable frame: at most 1.2 seconds at p50 and 2.0 seconds at p95.
- Valid field focus to bubble visibility: at most 250 ms at p95.
- Warm speech end to insertion for a 10-word utterance: local p50 at most 2.5 seconds and p95 at most 4.0 seconds.
- Quality network route under 50 ms network RTT: p50 at most 1.2 seconds and p95 at most 2.5 seconds.
- Default local route peak PSS: at most 1.2 GB on SM-S938B.
- A 20-dictation continuous run has no crash, service kill, severe thermal state, or latency degradation above 25% from its first-five median.

### Reliability and UX

- No recording starts without a valid editable target.
- Bubble hides within 250 ms after target loss, password focus, or denied-app focus.
- Silence does not launch the semantic model and produces no transcript.
- Model download resume, hash mismatch, insufficient storage, offline provider, microphone denial, service disconnect, and target loss each have a tested recovery path.
- Every fallback states what happened and preserves the raw transcript when safe.
- Release-device semantic instrumentation fails when prerequisites are absent; it never skips.
- Onboarding can be completed with TalkBack and touch targets meet Android accessibility guidance.

### Privacy

- Prominent disclosure and explicit AccessibilityService consent are completed before system settings open.
- Local Only mode passes the network-available zero-egress capture and the separate airplane-mode run after models are installed.
- No audio, transcript, context, field text, API key, or personal sample appears in normal logs or exported diagnostics.
- Delete actions remove language profiles, model packs, dictionary, snippets, and Voice Lab references as described in the UI.

## Delivery Slices

Each slice leaves the APK buildable and its changed flow directly testable.

1. **Benchmark foundation**
   - Freeze the Czech corpus, adversarial cases, measurement scripts, and current 0.2.0 baseline.
   - Run candidate ASR and editor benchmarks before replacing models.
2. **Field and capture reliability**
   - Add the state machine, event-driven bubble, valid-target gate, VAD, separate undo, explicit fallback, lazy TTS, and service diagnostics.
3. **Speech route**
   - Integrate the best local ASR candidate that passes hard gates.
   - Add streaming partials only when the selected engine provides them without violating memory and latency gates.
4. **Safe semantic editing**
   - Add typed operations, output validation, corpus-driven prompts, selected rewrite, and visible raw fallback.
5. **Personalization and context**
   - Add language profiles, inspectable style analysis, dictionary, snippets, bounded surrounding context, and per-app styles.
6. **Optional quality route**
   - Benchmark and integrate one provider only if it passes Czech quality, privacy, cost, latency, and failure gates.
   - Add BYOK and per-route disclosure after the provider is proven.
7. **Production UI and policy pass**
   - Replace the current dashboard with focused onboarding, home, language/style, privacy, models, and diagnostics screens.
   - Complete TalkBack, localization, lifecycle, policy, and device regression checks.
8. **Voice Lab evaluation**
   - Add the two-minute recording game and quality checks.
   - Unlock synthesis only after a Czech engine passes its independent benchmark.

## Upgrade and Data Migration

- Preserve completed onboarding, primary language, selected style, permissions, and consent where their meaning is unchanged.
- Convert a valid existing writing sample into the new preview-and-confirm flow. Do not activate inferred rules without confirmation.
- Keep the old verified model pack until the replacement pack is fully downloaded and activated, then offer deletion.
- Preserve Voice Lab references and consent metadata. Keep synthesis locked.
- Roll back to the previous active model pack after activation failure without deleting the working pack.

## Acceptance Output

A release candidate is complete only with:

- Signed APK and reproducible build command.
- Exact model manifest, licenses, sizes, and SHA-256 values.
- Full corpus result table with failed cases visible.
- SM-S938B Android 16 screenshots, UI hierarchy, logcat, latency, PSS, and thermal evidence.
- Low-memory fallback evidence.
- Zero-egress Local Only capture.
- Accessibility disclosure and consent evidence.
- Hostile diff review with no unresolved release blocker.

Passing Gradle tasks without the physical-device and corpus evidence is not release acceptance.

## Primary Research Sources

- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android on-device SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Google Play Accessibility API policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Sherpa-ONNX Android: https://k2-fsa.github.io/sherpa/onnx/android/
- Sherpa-ONNX Qwen3-ASR: https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/pretrained.html
- Sherpa-ONNX Omnilingual ASR: https://k2-fsa.github.io/sherpa/onnx/omnilingual-asr/models.html
- Wispr Flow Android: https://wisprflow.ai/android
- Wispr Flow product documentation: https://docs.wisprflow.ai/articles/2772472373-what-is-flow
- Wispr Flow changelog: https://wisprflow.ai/whats-new
- Google Rambler: https://blog.google/products-and-platforms/platforms/android/gemini-intelligence/
- Phravia: https://phravia.com/
- Fluence Android: https://github.com/raviumeshkulkarni-web/Fluence-Android
- Sasayaki: https://github.com/pluja/sasayaki
