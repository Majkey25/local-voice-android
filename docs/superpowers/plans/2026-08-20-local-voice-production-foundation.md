# Local Voice Production Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop unsafe global recording, reject silent audio before model loading, expose fallbacks, remove eager TTS startup, and add the required accessibility disclosure while preserving the working Android APK.

**Architecture:** Keep the single app module and existing direct call flow. Extract only pure target and speech rules that need JVM tests. Keep Android node lookup, overlay windows, audio ownership, and insertion inside `VoiceAccessibilityService`; do not introduce provider interfaces until a second proven provider exists.

**Tech Stack:** Kotlin 2.2.21, Java 17, Android API 26-36, Jetpack Compose Material 3, Android AccessibilityService, coroutines, JUnit 4, existing Sherpa-ONNX and LiteRT-LM dependencies.

**Spec:** `docs/superpowers/specs/2026-08-20-local-voice-production-redesign-design.md`

## Global Constraints

- Primary acceptance target: Samsung SM-S938B, Android 16, API 36.
- Keep `minSdk = 26`, `targetSdk = 36`, and package `cz.localvoice.app`.
- Keep one application module and add no dependency in this slice.
- The normal keyboard remains active.
- Never record without a fresh supported editable target.
- Never capture password or PIN fields; numeric and phone fields remain disabled by default.
- Audio remains bounded in RAM and is cleared on every terminal path.
- Local semantic fallback is explicit; never hide a raw downgrade.
- Do not commit, push, or open a PR without explicit user approval. Each task ends with a local diff review instead of a commit.
- The repo has no `pyproject.toml`; Gradle tasks are the authoritative quality commands.

## File Structure

### Create

- `app/src/main/java/cz/localvoice/app/FieldTarget.kt`: pure target facts, snapshot fingerprint, eligibility, and freshness rules.
- `app/src/main/java/cz/localvoice/app/SpeechGate.kt`: bounded energy-based speech detection and trimming.
- `app/src/test/java/cz/localvoice/app/FieldTargetTest.kt`: supported, sensitive, excluded, and stale target cases.
- `app/src/test/java/cz/localvoice/app/SpeechGateTest.kt`: silence, short noise, valid speech, and trimming cases.

### Modify

- `app/src/main/java/cz/localvoice/app/VoiceAccessibilityService.kt`: event-driven overlay, mandatory target gate, focus-loss cancellation, separate undo action, speech gate, explicit fallback, and lazy TTS.
- `app/src/main/java/cz/localvoice/app/MainActivity.kt`: lazy TTS and separate accessibility disclosure/consent step.
- `app/src/main/java/cz/localvoice/app/UserProfile.kt`: persisted disclosure version and language discovery from system plus enabled keyboard locales.
- `app/src/main/java/cz/localvoice/app/Models.kt`: caller-supplied fallback reason with bounded text validation.
- `app/src/main/res/values/strings.xml`: user-visible disclosure, overlay, fallback, and error strings in Czech UTF-8.
- `app/src/test/java/cz/localvoice/app/EditPlanTest.kt`: explicit raw fallback reason.
- `app/src/test/java/cz/localvoice/app/StylePreviewTest.kt`: deduplicated language behavior where it remains a pure test.
- `app/src/androidTest/java/cz/localvoice/app/LocalPipelineInstrumentedTest.kt`: release-device prerequisites fail instead of skip; emulator memory guard remains explicit.
- `docs/QA.md`: replace obsolete claims with measured results from this slice.

---

### Task 1: Pure field eligibility and freshness

Status: completed 2026-08-20

**Files:**
- Create: `app/src/main/java/cz/localvoice/app/FieldTarget.kt`
- Create: `app/src/test/java/cz/localvoice/app/FieldTargetTest.kt`

**Interfaces:**
- Produces: `FieldFacts`, `FieldSnapshot`, `FieldTargetPolicy.isSupported(FieldFacts, Set<String>): Boolean`, and `FieldTargetPolicy.isFresh(FieldSnapshot, FieldFacts): Boolean`.
- Consumed by: Tasks 2 and 4.

- [ ] **Step 1: Write the failing eligibility tests**

Cover one valid plain text field and reject disabled, invisible, password, numeric, phone, excluded-package, and non-editable facts.

```kotlin
class FieldTargetTest {
    private val text = FieldFacts(
        packageName = "com.example.notes",
        windowId = 7,
        viewId = "editor",
        left = 10,
        top = 20,
        right = 400,
        bottom = 220,
        text = "Ahoj",
        selectionStart = 4,
        selectionEnd = 4,
        editable = true,
        enabled = true,
        visible = true,
        password = false,
        inputType = InputType.TYPE_CLASS_TEXT,
    )

    @Test
    fun supportsOnlySafeTextTargets() {
        assertTrue(FieldTargetPolicy.isSupported(text, emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(password = true), emptySet()))
        assertFalse(
            FieldTargetPolicy.isSupported(
                text.copy(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
                emptySet(),
            ),
        )
        assertFalse(FieldTargetPolicy.isSupported(text.copy(inputType = InputType.TYPE_CLASS_NUMBER), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(inputType = InputType.TYPE_CLASS_PHONE), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(editable = false), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(enabled = false), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(visible = false), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text, setOf("com.example.notes")))
    }
}
```

- [ ] **Step 2: Write the failing freshness tests**

Create a snapshot from `text`, then prove that package, window, view ID, text, and selection changes invalidate it.

```kotlin
@Test
fun invalidatesChangedTargets() {
    val snapshot = FieldSnapshot.from(text)
    assertTrue(FieldTargetPolicy.isFresh(snapshot, text))
    assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(packageName = "com.other")))
    assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(windowId = 8)))
    assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(viewId = "subject")))
    assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(text = "Ahoj!")))
    assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(selectionStart = 0, selectionEnd = 0)))
}
```

- [ ] **Step 3: Run the focused tests and confirm red state**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cz.localvoice.app.FieldTargetTest
```

Expected: compilation fails because the field types do not exist.

- [ ] **Step 4: Implement the minimum pure model and policy**

Use primitive bounds instead of Android `Rect` so local JVM tests stay deterministic. Clamp selection only when converting the Android node in Task 2; pure facts remain strict.

```kotlin
data class FieldFacts(
    val packageName: String,
    val windowId: Int,
    val viewId: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val editable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val password: Boolean,
    val inputType: Int,
)

data class FieldSnapshot(
    val facts: FieldFacts,
    val selectedText: String,
) {
    companion object {
        fun from(facts: FieldFacts): FieldSnapshot {
            require(facts.text.length <= 100_000) { "Field is too long" }
            require(facts.selectionStart in 0..facts.selectionEnd && facts.selectionEnd <= facts.text.length) {
                "Invalid field selection"
            }
            return FieldSnapshot(
                facts = facts,
                selectedText = facts.text.substring(facts.selectionStart, facts.selectionEnd),
            )
        }
    }
}

object FieldTargetPolicy {
    fun isSupported(facts: FieldFacts, excludedPackages: Set<String>): Boolean {
        val inputClass = facts.inputType and InputType.TYPE_MASK_CLASS
        val variation = facts.inputType and InputType.TYPE_MASK_VARIATION
        val passwordVariation = inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        return facts.editable && facts.enabled && facts.visible && !facts.password &&
            !passwordVariation && facts.packageName !in excludedPackages &&
            inputClass == InputType.TYPE_CLASS_TEXT
    }

    fun isFresh(snapshot: FieldSnapshot, current: FieldFacts): Boolean = snapshot.facts == current
}
```

Before `FieldSnapshot.from`, require `0 <= selectionStart <= selectionEnd <= text.length` and `text.length <= 100_000`.

- [ ] **Step 5: Run tests and review only these two files**

Run the focused test command again. Expected: PASS. Then run:

```powershell
rg -n "\bAny\b" app/src/main/java/cz/localvoice/app/FieldTarget.kt app/src/test/java/cz/localvoice/app/FieldTargetTest.kt
```

Expected: no match.

---

### Task 2: Event-driven overlay and mandatory target

Status: completed 2026-08-20; live device verification remains in Task 6

**Files:**
- Modify: `app/src/main/java/cz/localvoice/app/VoiceAccessibilityService.kt:36-370`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `FieldFacts`, `FieldSnapshot`, and `FieldTargetPolicy` from Task 1.
- Produces: an overlay that exists only for a supported focused field and a recording entrypoint that requires a fresh snapshot.

- [ ] **Step 1: Replace the empty accessibility-event handler**

`onServiceConnected` creates overlay views but does not add them. `onAccessibilityEvent` refreshes eligibility only for configured focus, selection, and window events.

```kotlin
override fun onServiceConnected() {
    super.onServiceConnected()
    createOverlay()
    refreshTarget()
}

override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    refreshTarget()
}
```

Keep `notificationTimeout="100"`; do not add polling or a full-tree timer.

- [ ] **Step 2: Convert the focused node to strict facts**

Use `packageName`, `windowId`, `viewIdResourceName`, `boundsInScreen`, `text`, selection, `isEditable`, `isEnabled`, `isVisibleToUser`, `isPassword`, and `inputType`. Reject invalid selection and fields above 100,000 characters before creating a snapshot.

```kotlin
private fun AccessibilityNodeInfo.toFacts(): FieldFacts? {
    val value = text?.toString().orEmpty()
    if (value.length > MAX_FIELD_CHARS) return null
    val start = textSelectionStart.takeIf { it in 0..value.length } ?: value.length
    val end = textSelectionEnd.takeIf { it in start..value.length } ?: start
    val bounds = Rect().also(::getBoundsInScreen)
    return FieldFacts(
        packageName = packageName?.toString().orEmpty(),
        windowId = windowId,
        viewId = viewIdResourceName,
        left = bounds.left,
        top = bounds.top,
        right = bounds.right,
        bottom = bounds.bottom,
        text = value,
        selectionStart = start,
        selectionEnd = end,
        editable = isEditable,
        enabled = isEnabled,
        visible = isVisibleToUser,
        password = isPassword,
        inputType = inputType,
    )
}
```

- [ ] **Step 3: Attach and detach the existing bubble idempotently**

Track one `overlayAttached` Boolean. Add `HIDDEN` and `READY` to the existing state enum and initialize it as `HIDDEN`; keep the temporary `UNDO` state until Task 4 replaces it with a separate view. `refreshTarget` attaches only for `FieldTargetPolicy.isSupported`. Target loss while listening calls `capture.cancel()`, clears the snapshot, enters `HIDDEN`, and removes the overlay. Target loss while processing keeps `PROCESSING` without an overlay; the result remains eligible only for preview/copy, never insertion.

```kotlin
private fun refreshTarget() {
    val facts = focusedInput()?.toFacts()
    val supported = facts != null && FieldTargetPolicy.isSupported(facts, excludedPackages())
    if (!supported) {
        if (state == State.LISTENING) cancelRecording(getString(R.string.target_lost))
        detachOverlay()
        return
    }
    attachOverlay()
}
```

`cancelRecording(message)` calls `capture.cancel()`, clears `snapshot`, shows the supplied string, and enters `HIDDEN`. `excludedPackages()` reads `getStringSet("excluded_packages", emptySet())` from existing preferences and returns an immutable copy.

Do not invent a financial package list without a maintained source; numeric, phone, password, PIN, and user-excluded package blocking cover the immediate sensitive-field boundary.

- [ ] **Step 4: Require a fresh target before microphone start**

`startRecording` fails closed when `focusedInput()?.toFacts()` is missing or unsupported. Only then create `snapshot = FieldSnapshot.from(facts)` and start `AudioCapture`.

- [ ] **Step 5: Use the shared freshness rule before insertion and undo**

Rebuild current facts, call `FieldTargetPolicy.isFresh`, then apply `TextEditor`. Remove the service-private `FieldSnapshot` data class. Undo additionally requires the exact post-edit text and same fingerprint.

- [ ] **Step 6: Add Czech string resources and remove new inline user text**

Add exact strings for unsupported field, target lost, secure field, recording start/stop descriptions, and processing. Existing unrelated copy remains untouched in this slice.

- [ ] **Step 7: Build and run focused unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: BUILD SUCCESSFUL and zero lint errors.

---

### Task 3: Energy VAD before ASR

Status: completed 2026-08-20; physical microphone calibration remains in Task 6

**Files:**
- Create: `app/src/main/java/cz/localvoice/app/SpeechGate.kt`
- Create: `app/src/test/java/cz/localvoice/app/SpeechGateTest.kt`
- Modify: `app/src/main/java/cz/localvoice/app/VoiceAccessibilityService.kt:149-184`

**Interfaces:**
- Produces: `SpeechGate.trim(samples: FloatArray, sampleRate: Int): FloatArray`.
- Consumed by: `VoiceAccessibilityService.stopAndProcess` before `getWhisper()`.

- [ ] **Step 1: Write red tests for silence and valid speech**

Use 20 ms frames, a configurable RMS floor of `0.005`, at least 160 ms of active frames, and 120 ms leading/trailing padding.

```kotlin
class SpeechGateTest {
    private val gate = SpeechGate()

    @Test
    fun rejectsSilenceAndShortNoise() {
        assertFailsWith<IllegalArgumentException> { gate.trim(FloatArray(16_000), 16_000) }
        val click = FloatArray(16_000).also { samples ->
            repeat(800) { samples[4_000 + it] = 0.1f }
        }
        assertFailsWith<IllegalArgumentException> { gate.trim(click, 16_000) }
    }

    @Test
    fun trimsSilenceAroundSpeech() {
        val samples = FloatArray(32_000)
        for (index in 8_000 until 24_000) samples[index] = if (index % 2 == 0) 0.1f else -0.1f
        val trimmed = gate.trim(samples, 16_000)
        assertTrue(trimmed.size in 19_000..20_000)
    }
}
```

- [ ] **Step 2: Run the focused test and confirm red state**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cz.localvoice.app.SpeechGateTest
```

Expected: compilation fails because `SpeechGate` does not exist.

- [ ] **Step 3: Implement one-pass bounded detection**

Scan complete frames once, compute frame RMS, retain the first and last frame of the longest contiguous active run, require eight contiguous active frames, then copy only the padded range. Reject invalid sample rates, empty input, non-finite samples, and insufficient activity.

```kotlin
class SpeechGate(
    private val minimumRms: Double = 0.005,
    private val minimumSpeechMs: Int = 160,
    private val paddingMs: Int = 120,
) {
    fun trim(samples: FloatArray, sampleRate: Int): FloatArray {
        require(sampleRate > 0 && samples.isNotEmpty()) { "Invalid audio" }
        require(samples.all(Float::isFinite)) { "Invalid audio samples" }
        val frameSize = (sampleRate * 20 / 1_000).coerceAtLeast(1)
        val requiredFrames = (minimumSpeechMs + 19) / 20
        var runStart = -1
        var runFrames = 0
        var bestStart = -1
        var bestEnd = -1
        var bestFrames = 0
        for (start in samples.indices step frameSize) {
            val end = minOf(start + frameSize, samples.size)
            var energy = 0.0
            for (index in start until end) energy += samples[index] * samples[index]
            if (sqrt(energy / (end - start)) >= minimumRms) {
                if (runStart < 0) runStart = start
                runFrames++
                if (runFrames > bestFrames) {
                    bestStart = runStart
                    bestEnd = end
                    bestFrames = runFrames
                }
            } else {
                runStart = -1
                runFrames = 0
            }
        }
        require(bestFrames >= requiredFrames) { "No speech detected" }
        val padding = sampleRate * paddingMs / 1_000
        return samples.copyOfRange(
            (bestStart - padding).coerceAtLeast(0),
            (bestEnd + padding).coerceAtMost(samples.size),
        )
    }
}
```

The thresholds are constructor values because physical microphones require calibration. Do not add a settings screen until SM-S938B evidence shows users need control.

- [ ] **Step 4: Gate the model pipeline**

Immediately after `capture.stop()`, run `SpeechGate.trim`. Do not call `getWhisper` or `getSemantic` when it throws `No speech detected`. Show `R.string.no_speech_detected`, clear the snapshot, restore idle state, and keep the target bubble available if focus is still valid.

- [ ] **Step 5: Run focused and full JVM tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cz.localvoice.app.SpeechGateTest
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

---

### Task 4: Explicit semantic fallback and independent undo

Status: completed 2026-08-20; live overlay verification remains in Task 6

**Files:**
- Modify: `app/src/main/java/cz/localvoice/app/Models.kt:11-49`
- Modify: `app/src/main/java/cz/localvoice/app/VoiceAccessibilityService.kt:109-230`
- Modify: `app/src/test/java/cz/localvoice/app/EditPlanTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: target freshness from Task 1 and speech-trimmed samples from Task 3.
- Produces: visible raw fallback and an undo view that does not replace microphone behavior.

- [ ] **Step 1: Add a failing fallback-reason test**

```kotlin
@Test
fun rawFallbackKeepsExplicitBoundedReason() {
    val plan = EditPlan.rawFallback(" Ahoj ", hasSelection = false, reason = "Model unavailable")
    assertEquals(EditAction.INSERT, plan.action)
    assertEquals("Ahoj", plan.text)
    assertEquals("Model unavailable", plan.reason)
}
```

Also test that blank raw text is rejected.

- [ ] **Step 2: Change only the fallback signature**

```kotlin
fun rawFallback(text: String, hasSelection: Boolean, reason: String): EditPlan {
    val normalized = text.trim()
    require(normalized.isNotEmpty() && normalized.length <= 50_000) { "Invalid fallback text" }
    return EditPlan(
        action = if (hasSelection) EditAction.COPY_ONLY else EditAction.INSERT,
        text = normalized,
        reason = reason.take(160),
    )
}
```

Update every call site. Keep `EditPlan.parse` unchanged unless a failing test proves another defect.

- [ ] **Step 3: Surface semantic failure before insertion**

Capture the exception message as a bounded local reason, create the raw plan, and show `R.string.raw_fallback_used`. Do not write the transcript or exception stack to logcat.

- [ ] **Step 4: Remove `State.UNDO`**

After successful insertion, return the microphone to `READY`. Create one small secondary undo `TextView` beside the bubble, attach it for eight seconds, and remove it on timeout, focus mismatch, service destruction, or a new recording. Its click calls the existing guarded undo logic.

- [ ] **Step 5: Run model, editor, and full unit checks**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cz.localvoice.app.EditPlanTest --tests cz.localvoice.app.TextEditorTest
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Expected: PASS and zero lint errors.

---

### Task 5: Lazy TTS and accessibility disclosure

Status: completed 2026-08-20; live onboarding verification remains in Task 6

**Files:**
- Modify: `app/src/main/java/cz/localvoice/app/MainActivity.kt:63-128`
- Modify: `app/src/main/java/cz/localvoice/app/MainActivity.kt:173-408`
- Modify: `app/src/main/java/cz/localvoice/app/VoiceAccessibilityService.kt:36-69`
- Modify: `app/src/main/java/cz/localvoice/app/UserProfile.kt:19-68`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `UserSettings.accessibilityDisclosureAccepted(Context): Boolean` and `UserSettings.acceptAccessibilityDisclosure(Context)`.
- Consumed by: onboarding and every app-owned button that opens accessibility settings.

- [ ] **Step 1: Remove eager TTS initialization**

Change both activity and service fields to nullable `TtsSpeaker`. Construct only inside the existing playback or long-press action. Close with `tts?.close()`.

```kotlin
private var tts: TtsSpeaker? = null

private fun speaker(): TtsSpeaker = tts ?: TtsSpeaker(this).also { tts = it }
```

No TTS constructor may run from `onCreate` or `onServiceConnected`.

- [ ] **Step 2: Persist an exact disclosure version**

Use one integer preference `accessibility_disclosure_version`. Current required value is `1`. Acceptance writes the value only after the user checks an initially unchecked checkbox and presses Continue.

```kotlin
fun accessibilityDisclosureAccepted(context: Context): Boolean = preferences(context)
    .getInt("accessibility_disclosure_version", 0) >= ACCESSIBILITY_DISCLOSURE_VERSION

fun acceptAccessibilityDisclosure(context: Context) {
    preferences(context).edit { putInt("accessibility_disclosure_version", ACCESSIBILITY_DISCLOSURE_VERSION) }
}
```

Extract the repeated SharedPreferences lookup into one private `preferences(context)` function; this is existing duplication, not a new repository layer.

- [ ] **Step 3: Insert a separate disclosure page before Android settings**

Use exact Czech copy that states:

- Local Voice reads the currently focused editable field to return text safely to the same place; the current semantic model receives only selected text.
- It starts listening only after the user taps or holds the microphone.
- It inserts text into that field.
- Password/PIN fields are blocked.
- Local Only processing remains on the phone; model download uses the internet.

The checkbox starts false on every page entry. The settings button remains disabled until checked. On Continue, save disclosure version `1`, then open `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

- [ ] **Step 4: Route Home through the same disclosure**

When accessibility is disabled and disclosure version is missing, open the disclosure onboarding page. When the disclosure is accepted, opening system settings directly is allowed. Never mark accessibility enabled from consent alone; `refreshState` continues reading Android's enabled-service list.

- [ ] **Step 5: Add enabled keyboard subtype locales**

Extend `phoneLanguages` with the enabled input method subtypes from `InputMethodManager`. Normalize legacy subtype strings by replacing `_` with `-`, parse with `Locale.forLanguageTag`, remove blank languages, deduplicate by language tag, and keep system locale order first. Do not claim this is a list of downloaded ASR packs.

Extract a pure helper and add this exact regression to `StylePreviewTest`:

```kotlin
@Test
fun mergesSystemAndKeyboardLanguagesInStableOrder() {
    assertEquals(
        listOf("cs-CZ", "en-US", "de-DE"),
        UserSettings.mergeLanguageTags(
            systemTags = listOf("cs-CZ", "en-US"),
            keyboardTags = listOf("en_US", "de_DE", ""),
        ),
    )
}
```

- [ ] **Step 6: Run static and startup checks**

Run:

```powershell
rg -n "TtsSpeaker\(" app/src/main/java/cz/localvoice/app
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: constructor calls exist only in `speaker()` helpers; build succeeds with zero lint errors.

---

### Task 6: Release-device test gating and live QA

Status: in progress; connected/live steps blocked because no ADB target is attached and the local API 35 system-image installation failed before activation

**Files:**
- Modify: `app/src/androidTest/java/cz/localvoice/app/LocalPipelineInstrumentedTest.kt:15-38`
- Modify: `docs/QA.md`
- Create runtime artifacts only under: `.reference/audit-foundation/`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: build, emulator, latency, memory, offline, and UI evidence. No production API.

- [ ] **Step 1: Make release-device prerequisites fail closed**

Read `Build.MODEL` and `Build.VERSION.SDK_INT`. On `SM-S938B` API 36, use assertions for `ModelPack.isReady` and `SemanticEngine.isAvailable`; never call `assumeTrue`. On other devices, retain the explicit memory guard and report the skip reason.

```kotlin
val releaseDevice = Build.MODEL == "SM-S938B" && Build.VERSION.SDK_INT == 36
if (releaseDevice) {
    assertTrue("Release model pack missing", ModelPack.isReady(context))
    assertTrue("Release semantic engine unavailable", SemanticEngine.isAvailable(context))
} else {
    assumeTrue("Non-release device lacks safe model capacity", prerequisitesReady)
}
```

- [ ] **Step 2: Run the complete Gradle gate**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace
```

Expected: all available tasks pass; any non-release semantic skip names the memory/device reason.

- [ ] **Step 3: Install the exact debug APK on one clean emulator**

Use the already attached API 35 emulator with the highest available RAM. Clear only package `cz.localvoice.app`, install `app/build/outputs/apk/debug/app-debug.apk`, grant microphone permission after onboarding requests it, and enable the accessibility service through Android settings.

- [ ] **Step 4: Verify four live field scenarios**

Capture screenshot, UI XML, and logcat evidence for:

1. Launcher/no editable field -> no bubble and no recording.
2. Plain editable text field -> bubble appears; tap starts recording.
3. Password or PIN field -> bubble hides.
4. Focus changes while listening -> capture stops, no insertion occurs in the new field, and no semantic model loads from silence.

- [ ] **Step 5: Verify fallback and next dictation**

Force semantic unavailability without deleting user data by using the low-memory emulator route. Verify the UI states that raw fallback was used. After a successful plain-field insertion, verify the primary mic content description is `Spustit lokální diktování`, the independent undo action says `Vrátit poslední vložení`, and a second mic tap starts another recording.

- [ ] **Step 6: Measure startup and idle memory**

Measure five force-stopped launches with `am start -W`, discard the first warm-up sample, and report median plus p95 of the remaining measurements. Capture `dumpsys meminfo cz.localvoice.app` on the idle Home screen. Confirm no TTS service connection before playback.

- [ ] **Step 7: Verify offline and local-only behavior**

After model installation, disable network and complete raw local transcription. Re-enable network after the test. Do not claim zero egress from airplane mode alone; packet-capture zero-egress remains a later release gate.

- [ ] **Step 8: Hostile review and documentation**

Review the complete diff for duplicate target logic, stale overlay attachment, microphone leaks, hidden fallback, unsafe whole-field replacement, inline Czech strings, dead states, and unrelated reformatting. Update `docs/QA.md` with exact commands, device IDs, pass/fail results, skipped checks, measurements, and the still-open SM-S938B gate.

## Completion Boundary

This plan completes the production foundation only. It does not claim the full product release. The next evidence-driven plans are:

1. ASR and semantic benchmark plus model selection.
2. Safe semantic operations, dictionary, snippets, context, and style profiles.
3. Optional quality/BYOK route.
4. Production UI, full corpus acceptance, and Voice Lab evaluation.

Those plans use the interfaces and evidence produced here. They must not select a model or provider before its Czech benchmark exists.
