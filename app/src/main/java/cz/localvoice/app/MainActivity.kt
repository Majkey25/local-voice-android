package cz.localvoice.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(AppScreen.ONBOARDING)
    private var onboardingStep by mutableIntStateOf(0)
    private var selectedLanguageTag by mutableStateOf(UserSettings.DEFAULT_LANGUAGE_TAG)
    private var selectedStyle by mutableStateOf(UserSettings.BALANCED)
    private var writingSample by mutableStateOf("")
    private var cleanupLevel by mutableStateOf(CleanupLevel.POLISHED)
    private var dictionary by mutableStateOf<List<DictionaryEntry>>(emptyList())
    private var snippets by mutableStateOf<List<TextSnippet>>(emptyList())
    private var bubblePreferences by mutableStateOf(BubblePreferences())
    private var modelReady by mutableStateOf(false)
    private var downloading by mutableStateOf(false)
    private var downloadProgress by mutableFloatStateOf(0f)
    private var microphoneGranted by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var downloadError by mutableStateOf<String?>(null)
    private var documentError by mutableStateOf<String?>(null)
    private var voiceRecording by mutableStateOf(false)
    private var voiceDataBusy by mutableStateOf(false)
    private var voiceReferenceReady by mutableStateOf(false)
    private var voiceError by mutableStateOf<String?>(null)
    private val voiceCapture = AudioCapture(maxDurationSeconds = 30)
    private val calibrationCapture = AudioCapture(maxDurationSeconds = 120)
    private var calibrationRecording by mutableStateOf(false)
    private var calibrationProcessing by mutableStateOf(false)
    private var calibrationReport by mutableStateOf<CalibrationReport?>(null)
    private var calibrationError by mutableStateOf<String?>(null)
    private var tts: TtsSpeaker? = null
    private var mediaPlayer: MediaPlayer? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> microphoneGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedProfile = UserSettings.load(this)
        val draftLanguage = savedInstanceState?.getString("draft_language") ?: savedProfile.languageTag
        val profile = if (draftLanguage == savedProfile.languageTag) savedProfile else UserSettings.load(this, draftLanguage)
        selectedLanguageTag = profile.languageTag
        selectedStyle = savedInstanceState?.getString("draft_style") ?: profile.style
        writingSample = savedInstanceState?.getString("draft_sample") ?: profile.writingSample
        cleanupLevel = profile.cleanup
        dictionary = profile.dictionary
        snippets = profile.snippets
        bubblePreferences = UserSettings.bubble(this)
        screen = if (UserSettings.isOnboardingDone(this)) AppScreen.HOME else AppScreen.ONBOARDING
        onboardingStep = savedInstanceState?.getInt("onboarding_step") ?: 0
        voiceReferenceReady = VoiceProfileStore.referenceFile(this).isFile
        refreshState()
        setContent {
            MaterialTheme {
                when (screen) {
                    AppScreen.ONBOARDING -> OnboardingScreen()
                    AppScreen.HOME -> HomeScreen()
                    AppScreen.DICTIONARY -> DictionaryScreen()
                    AppScreen.SNIPPETS -> SnippetsScreen()
                    AppScreen.SETTINGS -> SettingsScreen()
                    AppScreen.CALIBRATION -> CalibrationScreen()
                    AppScreen.VOICE_LAB -> VoiceLabScreen()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("onboarding_step", onboardingStep)
        outState.putString("draft_language", selectedLanguageTag)
        outState.putString("draft_style", selectedStyle)
        outState.putString("draft_sample", writingSample)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onDestroy() {
        voiceCapture.cancel()
        calibrationCapture.cancel()
        mediaPlayer?.release()
        tts?.close()
        super.onDestroy()
    }

    private fun refreshState() {
        modelReady = ModelPack.isReady(this)
        microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = ComponentName(this, VoiceAccessibilityService::class.java)
        accessibilityEnabled = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component }
        voiceReferenceReady = VoiceProfileStore.referenceFile(this).isFile
    }

    private fun currentProfile(): UserProfile = UserProfile(
        languageTag = selectedLanguageTag,
        style = selectedStyle,
        writingSample = writingSample,
        cleanup = cleanupLevel,
        dictionary = dictionary,
        snippets = snippets,
    )

    private fun selectLanguage(languageTag: String) {
        val profile = UserSettings.load(this, languageTag)
        selectedLanguageTag = profile.languageTag
        selectedStyle = profile.style
        writingSample = profile.writingSample
        cleanupLevel = profile.cleanup
        dictionary = profile.dictionary
        snippets = profile.snippets
        calibrationReport = null
        calibrationError = null
    }

    private fun saveDraft() = UserSettings.save(this, currentProfile(), onboardingDone = false)

    private fun finishOnboarding() {
        UserSettings.save(this, currentProfile())
        screen = AppScreen.HOME
    }

    private fun downloadModels() {
        if (downloading) return
        downloading = true
        downloadError = null
        lifecycleScope.launch {
            runCatching {
                ModelPack.download(this@MainActivity) { complete, total ->
                    runOnUiThread { downloadProgress = complete.toFloat() / total }
                }
            }.onSuccess {
                modelReady = true
            }.onFailure {
                downloadError = it.message ?: it.javaClass.simpleName
            }
            downloading = false
        }
    }

    @Composable
    private fun OnboardingScreen() {
        var disclosureConfirmed by remember(onboardingStep) { mutableStateOf(false) }
        val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) result.data?.data?.let(::loadWritingSample)
        }
        when (onboardingStep) {
            0 -> AppPage("LOCAL VOICE", "Speak. Your phone types.") {
                Text("A local voice layer for your whole phone. No account, no cloud, no keyboard replacement.")
                Feature("Voice → text", "Local transcription and semantic cleanup")
                Feature("Text → voice", "Installed offline voices only")
                Feature("Privacy", "Dictation audio stays in RAM. Optional Voice Lab references are saved only on this phone.")
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Get started") { onboardingStep = 1 }
            }

            1 -> AppPage("STEP 1 / 5", "Which language do you want to speak?") {
                Text("All supported languages are available. System and keyboard languages appear first.")
                UserSettings.availableLanguages(this@MainActivity).forEach { locale ->
                    OutlinedButton(
                        onClick = {
                            selectLanguage(locale.toLanguageTag())
                            saveDraft()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedLanguageTag == locale.toLanguageTag()) {
                            ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFE8DFFF))
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) { Text(languageName(locale)) }
                }
                NavigationButtons(onBack = { onboardingStep = 0 }) {
                    saveDraft()
                    onboardingStep = 2
                }
            }

            2 -> AppPage("STEP 2 / 5", "Which result sounds most like you?") {
                Text("Compare sentence length, punctuation, rhythm, and tone.")
                UserSettings.stylePreviews(Locale.forLanguageTag(selectedLanguageTag).language).forEach { preview ->
                    OutlinedButton(
                        onClick = {
                            selectedStyle = preview.id
                            saveDraft()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedStyle == preview.id) {
                            ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFE8DFFF))
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(preview.name, fontWeight = FontWeight.Bold)
                            Text(preview.example)
                        }
                    }
                }
                NavigationButtons(onBack = { onboardingStep = 1 }) {
                    saveDraft()
                    onboardingStep = 3
                }
            }

            3 -> AppPage("STEP 3 / 5", "Or teach the app your style") {
                Text("Paste or load your own text. It stays on this phone. The local model learns style, not content.")
                OutlinedTextField(
                    value = writingSample,
                    onValueChange = { writingSample = it.take(UserSettings.MAX_WRITING_SAMPLE) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 7,
                    label = { Text("Your text, ideally 300+ characters") },
                )
                Text("${writingSample.length} / ${UserSettings.MAX_WRITING_SAMPLE} characters", fontSize = 12.sp)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("text/plain")
                        openDocument.launch(Intent.createChooser(intent, "Choose text"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Load UTF-8 .txt") }
                documentError?.let { ErrorText(it) }
                PrimaryButton(
                    label = "Use my style",
                    enabled = writingSample.trim().length >= 200,
                ) {
                    selectedStyle = UserSettings.CUSTOM
                    saveDraft()
                    onboardingStep = 4
                }
                OutlinedButton(
                    onClick = {
                        if (selectedStyle == UserSettings.CUSTOM) selectedStyle = UserSettings.BALANCED
                        saveDraft()
                        onboardingStep = 4
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip") }
                OutlinedButton(onClick = { onboardingStep = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }

            4 -> AppPage("STEP 4 / 5", getString(R.string.accessibility_disclosure_title)) {
                Text(getString(R.string.accessibility_disclosure_field_access))
                Text(getString(R.string.accessibility_disclosure_recording))
                Text(getString(R.string.accessibility_disclosure_insertion))
                Text(getString(R.string.accessibility_disclosure_secure_fields))
                Text(getString(R.string.accessibility_disclosure_network))
                Row {
                    Checkbox(
                        checked = disclosureConfirmed,
                        onCheckedChange = { disclosureConfirmed = it },
                    )
                    Text(
                        getString(R.string.accessibility_disclosure_consent),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                PrimaryButton(
                    label = getString(R.string.accessibility_disclosure_continue),
                    enabled = disclosureConfirmed,
                ) {
                    UserSettings.acceptAccessibilityDisclosure(this@MainActivity)
                    onboardingStep = 5
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                OutlinedButton(onClick = { onboardingStep = 3 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }

            else -> AppPage("STEP 5 / 5", "Finish local setup") {
                Text("Download the model once. Dictation then works in airplane mode.")
                StatusLine(
                    "Multilingual Offline Pack",
                    modelReady,
                    if (modelReady) "Ready" else "${ModelPack.totalBytes / 1_000_000} MB",
                )
                StatusLine("Microphone", microphoneGranted, if (microphoneGranted) "Allowed" else "Pending")
                StatusLine("Floating microphone", accessibilityEnabled, if (accessibilityEnabled) "Active" else "Pending")
                PrimaryButton(
                    label = when {
                        modelReady -> "Offline Pack is ready"
                        downloading -> "Downloading ${(downloadProgress * 100).roundToInt()} %"
                        else -> "Download Offline Pack"
                    },
                    enabled = !modelReady && !downloading,
                    onClick = ::downloadModels,
                )
                downloadError?.let { ErrorText("Download failed: $it") }
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    enabled = !microphoneGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (microphoneGranted) "Microphone allowed" else "Allow microphone") }
                OutlinedButton(
                    onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (accessibilityEnabled) "Floating microphone is active" else "Enable floating microphone") }
                PrimaryButton(
                    label = "Done",
                    enabled = modelReady && microphoneGranted && accessibilityEnabled &&
                        UserSettings.accessibilityDisclosureAccepted(this@MainActivity),
                    onClick = ::finishOnboarding,
                )
                OutlinedButton(onClick = { onboardingStep = 4 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        var speechText by remember { mutableStateOf("Hello, this is a local voice.") }
        val locale = Locale.forLanguageTag(selectedLanguageTag)
        val semanticAvailable = SemanticEngine.isAvailable(this@MainActivity)
        AppPage("LOCAL VOICE", "Ready to speak.", showNavigation = true) {
            StatusLine("Language", true, languageName(locale))
            StatusLine("Style", true, UserSettings.styleName(selectedStyle))
            StatusLine("Offline Pack", modelReady, if (modelReady) "Local" else "Missing")
            StatusLine(
                "Semantic AI",
                semanticAvailable,
                if (semanticAvailable) "Active" else "Raw mode",
            )
            StatusLine("Floating microphone", accessibilityEnabled, if (accessibilityEnabled) "Active" else "Off")
            if (!modelReady) PrimaryButton("Download Offline Pack", onClick = ::downloadModels)
            if (!microphoneGranted) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow microphone") }
            }
            if (!UserSettings.accessibilityDisclosureAccepted(this@MainActivity)) {
                OutlinedButton(
                    onClick = {
                        onboardingStep = 4
                        screen = AppScreen.ONBOARDING
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(getString(R.string.accessibility_disclosure_open)) }
            } else if (!accessibilityEnabled) {
                OutlinedButton(
                    onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enable floating microphone") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Quick style", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UserSettings.CASUAL, UserSettings.BALANCED, UserSettings.PROFESSIONAL).forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = {
                            selectedStyle = style
                            UserSettings.save(this@MainActivity, currentProfile())
                        },
                        label = { Text(UserSettings.styleName(style)) },
                    )
                }
            }
            if (writingSample.isNotBlank()) {
                FilterChip(
                    selected = selectedStyle == UserSettings.CUSTOM,
                    onClick = {
                        selectedStyle = UserSettings.CUSTOM
                        UserSettings.save(this@MainActivity, currentProfile())
                    },
                    label = { Text("My style") },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Local text-to-speech", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = speechText,
                onValueChange = { speechText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            PrimaryButton("Read aloud") {
                if (!speaker().speak(speechText)) toast("No offline voice is installed for the selected language")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = { screen = AppScreen.VOICE_LAB }, modifier = Modifier.fillMaxWidth()) {
                Text(if (voiceReferenceReady) "My voice • reference saved" else "My voice • Voice Lab")
            }
            OutlinedButton(
                onClick = {
                    onboardingStep = 1
                    screen = AppScreen.ONBOARDING
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Change language or style") }

            Text(
                "LOCAL ONLY  •  DICTATION AUDIO IN RAM\nOptional voice references are saved locally.\n" +
                    "Internet is used only for the one-time download of verified models.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
            )
        }
    }

    @Composable
    private fun DictionaryScreen() {
        var spoken by remember { mutableStateOf("") }
        var written by remember { mutableStateOf("") }
        var query by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AppPage("PERSONALIZATION", "Dictionary", showNavigation = true) {
            Text("Exact forms for names, products, and technical terms. Corrections run locally, including in Raw mode.")
            OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Search") })
            val visible = dictionary.filter { query.isBlank() || it.spoken.contains(query, true) || it.written.contains(query, true) }
            if (visible.isEmpty()) Text("No matching terms yet.")
            visible.forEach { entry ->
                Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                    Text(entry.written, fontWeight = FontWeight.Bold)
                    Text("When heard: ${entry.spoken}", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { spoken = entry.spoken; written = entry.written }) { Text("Edit") }
                        OutlinedButton(onClick = {
                            dictionary = dictionary.filterNot { it == entry }
                            UserSettings.save(this@MainActivity, currentProfile())
                        }) { Text("Delete") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Add or update", fontWeight = FontWeight.Bold)
            OutlinedTextField(spoken, { spoken = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Model hears") })
            OutlinedTextField(written, { written = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Write as") })
            PrimaryButton("Save term", spoken.isNotBlank() && written.isNotBlank()) {
                runCatching { Personalization.upsertDictionary(dictionary, DictionaryEntry(spoken, written)) }
                    .onSuccess {
                        dictionary = it
                        UserSettings.save(this@MainActivity, currentProfile())
                        spoken = ""
                        written = ""
                        error = null
                    }.onFailure { error = it.message }
            }
            error?.let { ErrorText(it) }
        }
    }

    @Composable
    private fun SnippetsScreen() {
        var trigger by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AppPage("PERSONALIZATION", "Snippets", showNavigation = true) {
            Text("Say an exact trigger and Local Voice inserts the saved text. Similar phrases will not expand.")
            if (snippets.isEmpty()) Text("No snippets saved yet.")
            snippets.forEach { snippet ->
                Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                    Text(snippet.trigger, fontWeight = FontWeight.Bold)
                    Text(snippet.text, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { trigger = snippet.trigger; text = snippet.text }) { Text("Edit") }
                        OutlinedButton(onClick = {
                            snippets = snippets.filterNot { it == snippet }
                            UserSettings.save(this@MainActivity, currentProfile())
                        }) { Text("Delete") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedTextField(trigger, { trigger = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Voice trigger") })
            OutlinedTextField(text, { text = it.take(4_000) }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("Inserted text") })
            PrimaryButton("Save snippet", trigger.isNotBlank() && text.isNotBlank()) {
                runCatching { Personalization.upsertSnippets(snippets, TextSnippet(trigger, text)) }
                    .onSuccess {
                        snippets = it
                        UserSettings.save(this@MainActivity, currentProfile())
                        trigger = ""
                        text = ""
                        error = null
                    }.onFailure { error = it.message }
            }
            error?.let { ErrorText(it) }
        }
    }

    @Composable
    private fun SettingsScreen() {
        val locale = Locale.forLanguageTag(selectedLanguageTag)
        AppPage("LOCAL VOICE", "Settings", showNavigation = true) {
            StatusLine("Speech language", true, languageName(locale))
            Text("Cleanup level", fontWeight = FontWeight.Bold)
            CleanupLevel.entries.forEach { level ->
                FilterChip(
                    selected = cleanupLevel == level,
                    onClick = {
                        cleanupLevel = level
                        UserSettings.save(this@MainActivity, currentProfile())
                    },
                    label = { Text(cleanupName(level)) },
                )
            }
            Text("Raw applies dictionary terms only. Light fixes punctuation. Polished also handles restarts and corrections.")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Floating microphone", fontWeight = FontWeight.Bold)
            Text("Size", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BubblePreferences.sizes.forEach { size ->
                    FilterChip(
                        selected = bubblePreferences.sizePercent == size,
                        onClick = { saveBubble(bubblePreferences.copy(sizePercent = size)) },
                        label = { Text("$size %") },
                    )
                }
            }
            Text("Opacity", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BubblePreferences.opacities.forEach { opacity ->
                    FilterChip(
                        selected = bubblePreferences.opacityPercent == opacity,
                        onClick = { saveBubble(bubblePreferences.copy(opacityPercent = opacity)) },
                        label = { Text("$opacity") },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            PrimaryButton("Calibrate voice recognition") { screen = AppScreen.CALIBRATION }
            OutlinedButton(onClick = { screen = AppScreen.VOICE_LAB }, modifier = Modifier.fillMaxWidth()) {
                Text("Voice Lab")
            }
            OutlinedButton(
                onClick = { onboardingStep = 1; screen = AppScreen.ONBOARDING },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Change language or style") }
            Text(
                "LOCAL ONLY • NO ACCOUNT • AUDIO DISCARDED AFTER PROCESSING • HISTORY OFF",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    @Composable
    private fun CalibrationScreen() {
        val language = Locale.forLanguageTag(selectedLanguageTag).language
        val report = calibrationReport
        AppPage("CALIBRATION • ${language.uppercase()}", "Teach Local Voice your speech") {
            Text("Read for about 1–2 minutes. Audio stays in RAM. Local Whisper transcribes it and asks you to confirm specific substitutions.")
            Text(UserSettings.calibrationPrompt(language), modifier = Modifier.background(Color.White).padding(12.dp))
            PrimaryButton(
                label = when {
                    calibrationProcessing -> "Analyzing locally…"
                    calibrationRecording -> "Stop and analyze"
                    else -> "Start calibration"
                },
                enabled = !calibrationProcessing && microphoneGranted && modelReady,
            ) {
                if (calibrationRecording) stopCalibration() else startCalibration()
            }
            if (!modelReady) ErrorText("Download the Offline Pack first.")
            if (!microphoneGranted) ErrorText("Microphone permission is required for calibration.")
            calibrationError?.let { ErrorText(it) }
            if (report != null) {
                StatusLine("Sample accuracy", true, "${report.baselineAccuracyPercent} %")
                StatusLine("After personal corrections", true, "${report.correctedAccuracyPercent} %")
                Text(
                    "The second number applies confirmed dictionary terms and new suggestions to the same sample. " +
                        "It does not mean the model weights were fine-tuned.",
                    fontSize = 12.sp,
                )
                if (report.suggestions.isEmpty()) {
                    Text("No new safe substitution was found.")
                } else {
                    report.suggestions.forEach { suggestion ->
                        Text("${suggestion.spoken} → ${suggestion.written}")
                    }
                    PrimaryButton("Add suggestions to dictionary") { acceptCalibration(report) }
                }
            }
            OutlinedButton(
                onClick = {
                    if (calibrationRecording) calibrationCapture.cancel()
                    calibrationRecording = false
                    screen = AppScreen.SETTINGS
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Back") }
        }
    }

    @Composable
    private fun VoiceLabScreen() {
        var consent by remember { mutableStateOf(VoiceConsent.OWN_VOICE) }
        var confirmed by remember { mutableStateOf(false) }
        val language = Locale.forLanguageTag(selectedLanguageTag).language
        AppPage("VOICE LAB • EXPERIMENT", "Create a voice reference") {
            Text("This does not train the full model. It stores 10–30 seconds of clean speech for possible future zero-shot cloning.")
            Text(UserSettings.voicePrompt(language), fontWeight = FontWeight.Bold)
            Text("Record naturally in a quiet room without music.")

            FilterChip(
                selected = consent == VoiceConsent.OWN_VOICE,
                onClick = { consent = VoiceConsent.OWN_VOICE },
                label = { Text("It is my voice") },
            )
            FilterChip(
                selected = consent == VoiceConsent.EXPLICIT_PERMISSION,
                onClick = { consent = VoiceConsent.EXPLICIT_PERMISSION },
                label = { Text("I have explicit permission") },
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text("I confirm that I may store and clone this voice.", modifier = Modifier.padding(top = 12.dp))
            }

            if (!microphoneGranted) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow microphone") }
            }
            PrimaryButton(
                label = if (voiceRecording) "Stop and save" else "Record voice reference",
                enabled = confirmed && microphoneGranted && !voiceDataBusy,
            ) {
                if (voiceRecording) stopVoiceRecording(consent) else startVoiceRecording(consent)
            }
            voiceError?.let { ErrorText(it) }
            StatusLine("Local reference", voiceReferenceReady, if (voiceReferenceReady) "Saved" else "Missing")
            if (voiceReferenceReady) {
                OutlinedButton(onClick = ::playVoiceReference, enabled = !voiceDataBusy,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Play reference")
                }
                OutlinedButton(
                    onClick = {
                        voiceDataBusy = true
                        lifecycleScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { VoiceProfileStore.delete(this@MainActivity) }
                            }.onSuccess {
                                mediaPlayer?.release()
                                mediaPlayer = null
                                voiceReferenceReady = false
                                voiceError = null
                            }.onFailure { voiceError = "Voice data could not be deleted. Try again." }
                            voiceDataBusy = false
                        }
                    },
                    enabled = !voiceRecording && !voiceDataBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete voice data") }
            }

            Text(
                "Personal voice synthesis stays locked until an Android engine passes the Czech benchmark. " +
                    "PocketTTS and ZipVoice do not have verified Czech support.",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
            )
            OutlinedButton(
                onClick = {
                    if (voiceRecording) {
                        voiceCapture.cancel()
                        voiceRecording = false
                    }
                    screen = AppScreen.HOME
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back")
            }
        }
    }

    private fun loadWritingSample(uri: Uri) {
        documentError = null
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                            val text = StringBuilder()
                            val buffer = CharArray(2_048)
                            while (text.length < UserSettings.MAX_WRITING_SAMPLE) {
                                val read = reader.read(
                                    buffer,
                                    0,
                                    minOf(buffer.size, UserSettings.MAX_WRITING_SAMPLE - text.length),
                                )
                                if (read < 0) break
                                text.append(buffer, 0, read)
                            }
                            text.toString()
                        }
                    } ?: error("The file cannot be opened")
                }
            }.onSuccess { writingSample = it }
                .onFailure { documentError = it.message ?: "The file cannot be loaded" }
        }
    }

    private fun startCalibration() {
        calibrationError = null
        calibrationReport = null
        runCatching {
            calibrationCapture.start(this, lifecycleScope, ::stopCalibration)
            calibrationRecording = true
        }.onFailure { calibrationError = it.message ?: "Calibration cannot start" }
    }

    private fun stopCalibration() {
        if (!calibrationRecording) return
        calibrationRecording = false
        calibrationProcessing = true
        lifecycleScope.launch {
            runCatching {
                val samples = calibrationCapture.stop()
                require(samples.size >= AudioCapture.SAMPLE_RATE * 30) {
                    "Read for at least 30 seconds for reliable calibration"
                }
                val speech = SpeechGate().trim(samples, AudioCapture.SAMPLE_RATE)
                withContext(Dispatchers.Default) {
                    WhisperEngine(this@MainActivity, Locale.forLanguageTag(selectedLanguageTag).language).use { engine ->
                        RecognitionCalibration.analyze(
                            reference = UserSettings.calibrationPrompt(
                                Locale.forLanguageTag(selectedLanguageTag).language,
                            ),
                            transcript = engine.transcribe(speech),
                            confirmedDictionary = dictionary,
                        )
                    }
                }
            }.onSuccess { calibrationReport = it }
                .onFailure { calibrationError = it.message ?: "Calibration failed" }
            calibrationProcessing = false
        }
    }

    private fun acceptCalibration(report: CalibrationReport) {
        runCatching {
            report.suggestions.fold(dictionary, Personalization::upsertDictionary)
        }.onSuccess {
            dictionary = it
            UserSettings.save(this, currentProfile())
            calibrationReport = report.copy(suggestions = emptyList())
            toast("Confirmed corrections were added to the dictionary")
        }.onFailure { calibrationError = it.message }
    }

    private fun saveBubble(value: BubblePreferences) {
        bubblePreferences = BubblePreferences.from(value.sizePercent, value.opacityPercent)
        UserSettings.saveBubble(this, bubblePreferences)
    }

    private fun startVoiceRecording(consent: VoiceConsent) {
        if (voiceDataBusy) return
        voiceError = null
        runCatching {
            voiceCapture.start(this, lifecycleScope) { stopVoiceRecording(consent) }
            voiceRecording = true
        }.onFailure { voiceError = it.message ?: "Recording cannot start" }
    }

    private fun stopVoiceRecording(consent: VoiceConsent) {
        if (!voiceRecording) return
        voiceRecording = false
        voiceDataBusy = true
        lifecycleScope.launch {
            runCatching {
                val samples = voiceCapture.stop()
                withContext(Dispatchers.IO) {
                    VoiceProfileStore.save(this@MainActivity, samples, consent)
                }
            }.onSuccess {
                voiceReferenceReady = true
                toast("Voice reference saved locally")
            }.onFailure {
                voiceError = it.message ?: "Voice reference cannot be saved"
            }
            voiceDataBusy = false
        }
    }

    private fun playVoiceReference() {
        runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(VoiceProfileStore.referenceFile(this@MainActivity).path)
                prepare()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                start()
            }
        }.onFailure { toast("Voice reference cannot be played") }
    }

    @Composable
    private fun AppPage(
        eyebrow: String,
        title: String,
        showNavigation: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Scaffold(
            containerColor = PAPER,
            bottomBar = { if (showNavigation) AppNavigation() },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(eyebrow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                content()
                TextButton(onClick = {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://majkey25.github.io/local-voice-android/")))
                    } catch (_: ActivityNotFoundException) {
                        toast("No browser available. Contact majkeylab@gmail.com for the policy.")
                    }
                }) { Text("Privacy, terms and data deletion") }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        NavigationBar(containerColor = Color.White) {
            listOf(
                Triple(AppScreen.HOME, "⌂", "Home"),
                Triple(AppScreen.DICTIONARY, "Aa", "Dictionary"),
                Triple(AppScreen.SNIPPETS, "§", "Snippets"),
                Triple(AppScreen.SETTINGS, "⚙", "Settings"),
            ).forEach { (target, icon, label) ->
                NavigationBarItem(
                    selected = screen == target,
                    onClick = { screen = target },
                    icon = { Text(icon, fontWeight = FontWeight.Bold) },
                    label = { Text(label) },
                )
            }
        }
    }

    @Composable
    private fun Feature(title: String, description: String) {
        Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description)
        }
    }

    @Composable
    private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = BLACK),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(label) }
    }

    @Composable
    private fun NavigationButtons(onBack: () -> Unit, onNext: () -> Unit) {
        PrimaryButton("Continue", onClick = onNext)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }

    @Composable
    private fun StatusLine(name: String, ready: Boolean, value: String) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name)
            Text(if (ready) "● $value" else "○ $value", fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun ErrorText(value: String) = Text(value, color = Color(0xFFB00020))

    private fun languageName(locale: Locale): String = locale.getDisplayName(Locale.ENGLISH)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun speaker(): TtsSpeaker = tts ?: TtsSpeaker(this).also { tts = it }

    private fun cleanupName(level: CleanupLevel): String = when (level) {
        CleanupLevel.RAW -> "Raw • dictionary only"
        CleanupLevel.LIGHT -> "Light • punctuation cleanup"
        CleanupLevel.POLISHED -> "Polished • semantic cleanup"
    }

    private enum class AppScreen { ONBOARDING, HOME, DICTIONARY, SNIPPETS, SETTINGS, CALIBRATION, VOICE_LAB }

    companion object {
        private val BLACK = Color(0xFF111111)
        private val PAPER = Color(0xFFF4F4F0)
    }
}
