package cz.localvoice.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val capture = AudioCapture()
    private val speechGate = SpeechGate()
    private lateinit var windowManager: WindowManager
    private lateinit var bubble: TextView
    private lateinit var cancelBubble: TextView
    private lateinit var undoBubble: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var cancelParams: WindowManager.LayoutParams
    private lateinit var undoParams: WindowManager.LayoutParams
    private var overlayAttached = false
    private var cancelAttached = false
    private var undoAttached = false
    private var undoHideJob: Job? = null
    private var processingJob: Job? = null
    private var whisper: WhisperEngine? = null
    private var whisperLanguage: String? = null
    private var semantic: SemanticEngine? = null
    private var state = State.HIDDEN
    private var snapshot: FieldSnapshot? = null
    private var undo: UndoSnapshot? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        createOverlay()
        refreshTarget()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) refreshTarget()
    }

    override fun onInterrupt() {
        processingJob?.cancel()
        processingJob = null
        capture.cancel()
        snapshot = null
        hideCancel()
        hideUndo(clearSnapshot = true)
        state = State.HIDDEN
        detachOverlay()
    }

    override fun onDestroy() {
        detachOverlay()
        hideUndo(clearSnapshot = true)
        processingJob?.cancel()
        capture.cancel()
        whisper?.close()
        semantic?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val bubbleSize = bubbleSize(UserSettings.bubble(this))
        params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt("overlay_x", resources.displayMetrics.widthPixels - dp(80))
                .coerceIn(0, (resources.displayMetrics.widthPixels - bubbleSize).coerceAtLeast(0))
            y = preferences.getInt("overlay_y", resources.displayMetrics.heightPixels / 2)
                .coerceIn(0, (resources.displayMetrics.heightPixels - bubbleSize).coerceAtLeast(0))
        }
        bubble = BubbleView(this).apply {
            gravity = Gravity.CENTER
            text = getString(R.string.microphone_label)
            textSize = 12f
            setTextColor(Color.WHITE)
            elevation = dp(8).toFloat()
            contentDescription = "Local Voice microphone"
            background = circle(Color.rgb(17, 17, 17))
            setOnClickListener { handleBubbleClick() }
            setOnTouchListener(DragListener())
        }
        bubble.alpha = UserSettings.bubble(this).opacityPercent / 100f
        cancelParams = WindowManager.LayoutParams(
            dp(48),
            dp(48),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        cancelBubble = BubbleView(this).apply {
            gravity = Gravity.CENTER
            text = "×"
            textSize = 24f
            setTextColor(Color.WHITE)
            contentDescription = getString(R.string.cancel_recording)
            background = circle(Color.rgb(65, 65, 65))
            setOnClickListener { cancelRecording() }
        }
        undoParams = WindowManager.LayoutParams(
            dp(48),
            dp(48),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        undoBubble = BubbleView(this).apply {
            gravity = Gravity.CENTER
            text = "↶"
            textSize = 20f
            setTextColor(Color.WHITE)
            elevation = dp(8).toFloat()
            contentDescription = getString(R.string.undo_last_insertion)
            background = circle(Color.rgb(17, 17, 17))
            setOnClickListener { undoLastEdit() }
        }
    }

    private fun handleBubbleClick() {
        when (state) {
            State.HIDDEN -> Unit
            State.READY -> startRecording()
            State.LISTENING -> stopAndProcess()
            State.PROCESSING -> toast("The local model is still processing")
        }
    }

    private fun startRecording() {
        val facts = focusedEditable()?.toFacts()
        if (facts == null || !FieldTargetPolicy.isSupported(facts, excludedPackages())) {
            toast(getString(R.string.unsupported_field))
            resetTargetState()
            return
        }
        if (!ModelPack.isReady(this)) {
            toast("Download the Multilingual Offline Pack first")
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            toast("Allow microphone access in Local Voice")
            return
        }
        snapshot = FieldSnapshot.from(facts)
        hideUndo(clearSnapshot = true)
        try {
            capture.start(this, scope) { stopAndProcess() }
            setState(State.LISTENING)
        } catch (error: RuntimeException) {
            toast(error.message ?: "The microphone cannot start")
            resetTargetState()
        }
    }

    private fun stopAndProcess() {
        if (state != State.LISTENING) return
        setState(State.PROCESSING)
        processingJob = scope.launch {
            try {
                val samples = capture.stop()
                val speech = try {
                    speechGate.trim(samples, AudioCapture.SAMPLE_RATE)
                } catch (_: IllegalArgumentException) {
                    toast(getString(R.string.no_speech_detected))
                    resetTargetState()
                    return@launch
                }
                val rawTranscript = withContext(Dispatchers.Default) {
                    getWhisper().transcribe(speech)
                }
                val captured = snapshot
                val selection = captured?.selectedText.orEmpty()
                val profile = UserSettings.load(this@VoiceAccessibilityService)
                val transcript = Personalization.applyDictionary(rawTranscript, profile.dictionary)
                var usedFallback = false
                val snippet = Personalization.expandSnippet(transcript, profile.snippets)
                val plan = if (snippet != null) {
                    EditPlan.rawFallback(snippet, selection.isNotEmpty(), reason = "saved snippet")
                } else if (profile.style == UserSettings.VERBATIM || profile.cleanup == CleanupLevel.RAW) {
                    EditPlan.rawFallback(transcript, selection.isNotEmpty(), reason = "verbatim mode")
                } else {
                    runCatching { getSemantic().edit(transcript, selection, profile) }
                        .getOrElse {
                            usedFallback = true
                            EditPlan.rawFallback(
                                transcript,
                                selection.isNotEmpty(),
                                reason = "semantic model unavailable",
                            )
                        }
                }
                if (usedFallback) toast(getString(R.string.raw_fallback_used))
                if (applyToFocusedField(captured, plan)) {
                    setState(State.READY)
                    showUndo()
                } else {
                    copy(plan.text)
                    toast("The result is in the clipboard")
                    resetTargetState()
                }
            } catch (error: RuntimeException) {
                toast(error.message ?: "Dictation failed")
                resetTargetState()
            } finally {
                snapshot = null
            }
        }
    }

    private fun cancelRecording() {
        if (state != State.LISTENING) return
        capture.cancel()
        snapshot = null
        toast(getString(R.string.recording_cancelled))
        resetTargetState()
    }

    private fun applyToFocusedField(captured: FieldSnapshot?, plan: EditPlan): Boolean {
        if (captured == null || plan.action == EditAction.COPY_ONLY) return false
        val node = focusedEditable() ?: return false
        val current = node.toFacts() ?: return false
        if (!FieldTargetPolicy.isSupported(current, excludedPackages())) return false
        if (!FieldTargetPolicy.isFresh(captured, current)) return false
        val facts = captured.facts
        val result = TextEditor.apply(
            facts.text,
            facts.selectionStart,
            facts.selectionEnd,
            plan,
        ) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                result.text,
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return false
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, result.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, result.cursor)
            },
        )
        val after = FieldSnapshot.from(
            facts.copy(
                text = result.text,
                selectionStart = result.cursor,
                selectionEnd = result.cursor,
            ),
        )
        undo = UndoSnapshot(after = after, before = captured)
        return true
    }

    private fun undoLastEdit() {
        val saved = undo ?: return hideUndo(clearSnapshot = true)
        val node = focusedEditable()
        val current = node?.toFacts()
        if (current == null || !FieldTargetPolicy.isFresh(saved.after, current)) {
            toast("The field changed, so undo was blocked for safety")
            hideUndo(clearSnapshot = true)
            hideCancel()
            return resetTargetState()
        }
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                saved.before.facts.text,
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            toast("Undo failed")
        } else {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                        saved.before.facts.selectionStart,
                    )
                    putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                        saved.before.facts.selectionEnd,
                    )
                },
            )
        }
        hideUndo(clearSnapshot = true)
        resetTargetState()
    }

    private fun focusedEditable(): AccessibilityNodeInfo? = windows
        .asSequence()
        .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
        .mapNotNull { it.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
        .firstOrNull { it.isEditable }

    private fun AccessibilityNodeInfo.toFacts(): FieldFacts? {
        val value = text?.toString().orEmpty()
        if (value.length > MAX_FIELD_CHARS) return null
        val start = textSelectionStart.takeIf { it >= 0 }?.coerceAtMost(value.length) ?: value.length
        val end = textSelectionEnd.takeIf { it >= start }?.coerceAtMost(value.length) ?: start
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

    private fun refreshTarget() {
        if (!UserSettings.accessibilityDisclosureAccepted(this)) {
            if (state == State.LISTENING) capture.cancel()
            snapshot = null
            hideUndo(clearSnapshot = true)
            hideCancel()
            state = State.HIDDEN
            detachOverlay()
            return
        }
        applyBubblePreferences()
        val facts = focusedEditable()?.toFacts()
        val supported = facts != null && FieldTargetPolicy.isSupported(facts, excludedPackages())
        if (!supported) {
            if (state == State.LISTENING) {
                capture.cancel()
                snapshot = null
                toast(getString(R.string.target_lost))
            }
            hideUndo(clearSnapshot = true)
            if (state != State.PROCESSING) state = State.HIDDEN
            detachOverlay()
            return
        }
        attachOverlay()
        if (state == State.HIDDEN) setState(State.READY)
    }

    private fun resetTargetState() {
        state = State.HIDDEN
        refreshTarget()
    }

    private fun attachOverlay() {
        if (overlayAttached || !::bubble.isInitialized) return
        windowManager.addView(bubble, params)
        overlayAttached = true
    }

    private fun detachOverlay() {
        hideCancel()
        if (!overlayAttached || !::bubble.isInitialized) return
        runCatching { windowManager.removeView(bubble) }
        overlayAttached = false
    }

    private fun showCancel() {
        if (cancelAttached || !overlayAttached || !::cancelBubble.isInitialized) return
        positionCancel()
        windowManager.addView(cancelBubble, cancelParams)
        cancelAttached = true
    }

    private fun hideCancel() {
        if (!cancelAttached || !::cancelBubble.isInitialized) return
        runCatching { windowManager.removeView(cancelBubble) }
        cancelAttached = false
    }

    private fun positionCancel() {
        val left = params.x - cancelParams.width - dp(8)
        cancelParams.x = if (left >= 0) left else {
            (params.x + params.width + dp(8)).coerceAtMost(
                resources.displayMetrics.widthPixels - cancelParams.width,
            )
        }
        cancelParams.y = params.y + (params.height - cancelParams.height) / 2
    }

    private fun showUndo() {
        if (undo == null || !overlayAttached || !::undoBubble.isInitialized) return
        hideUndo(clearSnapshot = false)
        positionUndo()
        windowManager.addView(undoBubble, undoParams)
        undoAttached = true
        undoHideJob = scope.launch {
            delay(UNDO_TIMEOUT_MS)
            undoHideJob = null
            removeUndoView()
            undo = null
        }
    }

    private fun hideUndo(clearSnapshot: Boolean) {
        undoHideJob?.cancel()
        undoHideJob = null
        removeUndoView()
        if (clearSnapshot) undo = null
    }

    private fun removeUndoView() {
        if (!undoAttached || !::undoBubble.isInitialized) return
        runCatching { windowManager.removeView(undoBubble) }
        undoAttached = false
    }

    private fun positionUndo() {
        undoParams.x = (params.x - undoParams.width - dp(8)).coerceAtLeast(0)
        undoParams.y = params.y
    }

    private fun applyBubblePreferences() {
        if (!::bubble.isInitialized) return
        val preferences = UserSettings.bubble(this)
        val size = bubbleSize(preferences)
        val changed = params.width != size || params.height != size
        params.width = size
        params.height = size
        bubble.alpha = preferences.opacityPercent / 100f
        if (overlayAttached && changed) windowManager.updateViewLayout(bubble, params)
    }

    private fun bubbleSize(preferences: BubblePreferences): Int = maxOf(
        dp(48),
        dp(58) * preferences.sizePercent / 100,
    )

    private fun excludedPackages(): Set<String> = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        .getStringSet(EXCLUDED_PACKAGES, emptySet())
        .orEmpty()

    private fun getWhisper(): WhisperEngine {
        val language = UserSettings.load(this).locale.language
        if (whisperLanguage != language) {
            whisper?.close()
            whisper = WhisperEngine(this, language)
            whisperLanguage = language
        }
        return requireNotNull(whisper)
    }

    private fun getSemantic(): SemanticEngine = semantic ?: SemanticEngine(this).also { semantic = it }

    private fun setState(value: State) {
        state = value
        if (!::bubble.isInitialized) return
        when (value) {
            State.HIDDEN -> {
                hideCancel()
                detachOverlay()
            }
            State.READY -> {
                hideCancel()
                configureBubble("MIC", Color.rgb(17, 17, 17), "Start local dictation")
            }
            State.LISTENING -> {
                configureBubble("✓", Color.rgb(190, 25, 25), "Finish recording")
                showCancel()
            }
            State.PROCESSING -> {
                hideCancel()
                configureBubble("…", Color.rgb(80, 80, 80), "Processing locally")
            }
        }
    }

    private fun configureBubble(label: String, color: Int, description: String) {
        bubble.text = label
        bubble.background = circle(color)
        bubble.contentDescription = description
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(1), Color.WHITE)
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Local Voice", text))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class DragListener : View.OnTouchListener {
        private val gesture = BubbleGesture(dp(6))
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var activeView: View? = null
        private val holdAction = Runnable {
            if (gesture.hold() == BubbleGesture.Action.HOLD_START && state == State.READY) startRecording()
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    gesture.down()
                    activeView = view
                    view.postDelayed(holdAction, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - downX).toInt()
                    val deltaY = (event.rawY - downY).toInt()
                    if (gesture.move(deltaX, deltaY) == BubbleGesture.Action.DRAG) {
                        view.removeCallbacks(holdAction)
                        params.x = (startX + deltaX).coerceIn(
                            0,
                            (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0),
                        )
                        params.y = (startY + deltaY).coerceIn(
                            0,
                            (resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0),
                        )
                        windowManager.updateViewLayout(bubble, params)
                        if (undoAttached) {
                            positionUndo()
                            windowManager.updateViewLayout(undoBubble, undoParams)
                        }
                        if (cancelAttached) {
                            positionCancel()
                            windowManager.updateViewLayout(cancelBubble, cancelParams)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(holdAction)
                    when (gesture.up()) {
                        BubbleGesture.Action.TAP -> view.performClick()
                        BubbleGesture.Action.HOLD_END -> if (state == State.LISTENING) stopAndProcess()
                        BubbleGesture.Action.DRAG_END -> getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit {
                            putInt("overlay_x", params.x)
                            putInt("overlay_y", params.y)
                        }
                        else -> Unit
                    }
                    activeView = null
                }
                MotionEvent.ACTION_CANCEL -> {
                    activeView?.removeCallbacks(holdAction)
                    if (gesture.up() == BubbleGesture.Action.HOLD_END && state == State.LISTENING) {
                        cancelRecording()
                    }
                    activeView = null
                }
            }
            return true
        }
    }

    private enum class State { HIDDEN, READY, LISTENING, PROCESSING }

    private class BubbleView(context: Context) : TextView(context) {
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private data class UndoSnapshot(val after: FieldSnapshot, val before: FieldSnapshot)

    companion object {
        const val PREFERENCES = "local_voice"
        private const val EXCLUDED_PACKAGES = "excluded_packages"
        private const val UNDO_TIMEOUT_MS = 8_000L
    }
}
