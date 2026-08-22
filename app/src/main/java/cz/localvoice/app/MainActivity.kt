package cz.localvoice.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
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
    private var selectedLanguageTag by mutableStateOf("cs-CZ")
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
            0 -> AppPage("LOCAL VOICE", "Mluv. Telefon píše.") {
                Text("Lokální hlasová vrstva pro celý telefon. Bez účtu, bez cloudu, bez výměny klávesnice.")
                Feature("Hlas → text", "Lokální přepis a sémantické opravy")
                Feature("Text → hlas", "Pouze nainstalovaný offline hlas")
                Feature("Soukromí", "Audio v RAM, historie vypnutá")
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Začít") { onboardingStep = 1 }
            }

            1 -> AppPage("KROK 1 / 5", "Jakým jazykem hlavně mluvíš?") {
                Text("Nabízíme jazyky nastavené v telefonu. Pořadí respektuje systém.")
                UserSettings.phoneLanguages(this@MainActivity).forEach { locale ->
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

            2 -> AppPage("KROK 2 / 5", "Který text zní nejvíc jako ty?") {
                Text("Rozdíl je v délce vět, interpunkci, čárkách a tónu.")
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

            3 -> AppPage("KROK 3 / 5", "Nebo nauč aplikaci svůj styl") {
                Text("Vlož nebo načti vlastní text. Zůstane jen v telefonu a lokální model z něj převezme styl, ne obsah.")
                OutlinedTextField(
                    value = writingSample,
                    onValueChange = { writingSample = it.take(UserSettings.MAX_WRITING_SAMPLE) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 7,
                    label = { Text("Tvůj text – ideálně 300+ znaků") },
                )
                Text("${writingSample.length} / ${UserSettings.MAX_WRITING_SAMPLE} znaků", fontSize = 12.sp)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("text/plain")
                        openDocument.launch(Intent.createChooser(intent, "Vybrat text"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Načíst UTF-8 .txt") }
                documentError?.let { ErrorText(it) }
                PrimaryButton(
                    label = "Použít můj styl",
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
                ) { Text("Přeskočit") }
                OutlinedButton(onClick = { onboardingStep = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Zpět")
                }
            }

            4 -> AppPage("KROK 4 / 5", getString(R.string.accessibility_disclosure_title)) {
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
                    Text("Zpět")
                }
            }

            else -> AppPage("KROK 5 / 5", "Dokončit lokální nastavení") {
                Text("Model se stáhne jednou. Potom diktování funguje i v režimu letadlo.")
                StatusLine(
                    "Multilingual Offline Pack",
                    modelReady,
                    if (modelReady) "Připraven" else "${ModelPack.totalBytes / 1_000_000} MB",
                )
                StatusLine("Mikrofon", microphoneGranted, if (microphoneGranted) "Povolen" else "Čeká")
                StatusLine("Plovoucí mikrofon", accessibilityEnabled, if (accessibilityEnabled) "Aktivní" else "Čeká")
                PrimaryButton(
                    label = when {
                        modelReady -> "Offline Pack je připraven"
                        downloading -> "Stahuji ${(downloadProgress * 100).roundToInt()} %"
                        else -> "Stáhnout Offline Pack"
                    },
                    enabled = !modelReady && !downloading,
                    onClick = ::downloadModels,
                )
                downloadError?.let { ErrorText("Stažení selhalo: $it") }
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    enabled = !microphoneGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (microphoneGranted) "Mikrofon povolen" else "Povolit mikrofon") }
                OutlinedButton(
                    onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (accessibilityEnabled) "Plovoucí mikrofon je aktivní" else "Zapnout plovoucí mikrofon") }
                PrimaryButton(
                    label = "Hotovo",
                    enabled = modelReady && microphoneGranted && accessibilityEnabled &&
                        UserSettings.accessibilityDisclosureAccepted(this@MainActivity),
                    onClick = ::finishOnboarding,
                )
                OutlinedButton(onClick = { onboardingStep = 4 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Zpět")
                }
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        var speechText by remember { mutableStateOf("Ahoj, tohle je místní hlas.") }
        val locale = Locale.forLanguageTag(selectedLanguageTag)
        val semanticAvailable = SemanticEngine.isAvailable(this@MainActivity)
        AppPage("LOCAL VOICE", "Připraveno mluvit.", showNavigation = true) {
            StatusLine("Jazyk", true, languageName(locale))
            StatusLine("Styl", true, UserSettings.styleName(selectedStyle))
            StatusLine("Offline Pack", modelReady, if (modelReady) "Lokální" else "Chybí")
            StatusLine(
                "Sémantická AI",
                semanticAvailable,
                if (semanticAvailable) "Aktivní" else "Raw režim",
            )
            StatusLine("Plovoucí mikrofon", accessibilityEnabled, if (accessibilityEnabled) "Aktivní" else "Vypnutý")
            if (!modelReady) PrimaryButton("Stáhnout Offline Pack", onClick = ::downloadModels)
            if (!microphoneGranted) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Povolit mikrofon") }
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
                ) { Text("Zapnout plovoucí mikrofon") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Rychlý styl", fontWeight = FontWeight.Bold)
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
                    label = { Text("Můj styl") },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Místní text-to-speech", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = speechText,
                onValueChange = { speechText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            PrimaryButton("Přečíst") {
                if (!speaker().speak(speechText)) toast("Není nainstalovaný offline hlas pro zvolený jazyk")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = { screen = AppScreen.VOICE_LAB }, modifier = Modifier.fillMaxWidth()) {
                Text(if (voiceReferenceReady) "Můj hlas • reference uložena" else "Můj hlas • Voice Lab")
            }
            OutlinedButton(
                onClick = {
                    onboardingStep = 1
                    screen = AppScreen.ONBOARDING
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Změnit jazyk nebo styl") }

            Text(
                "LOCAL ONLY  •  AUDIO V RAM  •  HISTORIE VYPNUTÁ\n" +
                    "Internet se používá jen pro jednorázové stažení ověřených modelů.",
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
        AppPage("PERSONALIZACE", "Slovník", showNavigation = true) {
            Text("Přesné tvary jmen, produktů a odborných výrazů. Oprava běží lokálně i v Raw režimu.")
            OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Hledat") })
            val visible = dictionary.filter { query.isBlank() || it.spoken.contains(query, true) || it.written.contains(query, true) }
            if (visible.isEmpty()) Text("Zatím tu nejsou žádné odpovídající výrazy.")
            visible.forEach { entry ->
                Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                    Text(entry.written, fontWeight = FontWeight.Bold)
                    Text("Když slyším: ${entry.spoken}", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { spoken = entry.spoken; written = entry.written }) { Text("Upravit") }
                        OutlinedButton(onClick = {
                            dictionary = dictionary.filterNot { it == entry }
                            UserSettings.save(this@MainActivity, currentProfile())
                        }) { Text("Smazat") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Přidat nebo upravit", fontWeight = FontWeight.Bold)
            OutlinedTextField(spoken, { spoken = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Model slyší") })
            OutlinedTextField(written, { written = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Má napsat") })
            PrimaryButton("Uložit výraz", spoken.isNotBlank() && written.isNotBlank()) {
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
        AppPage("PERSONALIZACE", "Snippets", showNavigation = true) {
            Text("Řekni přesnou spoušť a Local Voice vloží uložený text. Běžná věta podobná spoušti se nerozbalí.")
            if (snippets.isEmpty()) Text("Zatím nemáš uložený žádný snippet.")
            snippets.forEach { snippet ->
                Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                    Text(snippet.trigger, fontWeight = FontWeight.Bold)
                    Text(snippet.text, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { trigger = snippet.trigger; text = snippet.text }) { Text("Upravit") }
                        OutlinedButton(onClick = {
                            snippets = snippets.filterNot { it == snippet }
                            UserSettings.save(this@MainActivity, currentProfile())
                        }) { Text("Smazat") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedTextField(trigger, { trigger = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Hlasová spoušť") })
            OutlinedTextField(text, { text = it.take(4_000) }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("Vložený text") })
            PrimaryButton("Uložit snippet", trigger.isNotBlank() && text.isNotBlank()) {
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
        AppPage("LOCAL VOICE", "Nastavení", showNavigation = true) {
            StatusLine("Jazykový profil", true, languageName(locale))
            Text("Úroveň čištění", fontWeight = FontWeight.Bold)
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
            Text("Raw pouze opraví výrazy ze slovníku. Light upraví interpunkci. Polished řeší i přeřeknutí a opravy.")

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Plovoucí mikrofon", fontWeight = FontWeight.Bold)
            Text("Velikost", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BubblePreferences.sizes.forEach { size ->
                    FilterChip(
                        selected = bubblePreferences.sizePercent == size,
                        onClick = { saveBubble(bubblePreferences.copy(sizePercent = size)) },
                        label = { Text("$size %") },
                    )
                }
            }
            Text("Průhlednost", fontSize = 12.sp)
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
            PrimaryButton("Kalibrovat rozpoznávání hlasu") { screen = AppScreen.CALIBRATION }
            OutlinedButton(onClick = { screen = AppScreen.VOICE_LAB }, modifier = Modifier.fillMaxWidth()) {
                Text("Voice Lab")
            }
            OutlinedButton(
                onClick = { onboardingStep = 1; screen = AppScreen.ONBOARDING },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Změnit jazyk nebo styl") }
            Text(
                "LOCAL ONLY • bez účtu • audio se po zpracování zahodí • historie je vypnutá",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    @Composable
    private fun CalibrationScreen() {
        val language = Locale.forLanguageTag(selectedLanguageTag).language
        val report = calibrationReport
        AppPage("KALIBRACE • ${language.uppercase()}", "Nauč Local Voice svůj hlas") {
            Text("Čti přibližně 1–2 minuty. Nahrávka zůstane v RAM, lokální Whisper ji přepíše a aplikace nabídne potvrdit konkrétní záměny.")
            Text(UserSettings.calibrationPrompt(language), modifier = Modifier.background(Color.White).padding(12.dp))
            PrimaryButton(
                label = when {
                    calibrationProcessing -> "Analyzuji lokálně…"
                    calibrationRecording -> "Zastavit a analyzovat"
                    else -> "Spustit kalibraci"
                },
                enabled = !calibrationProcessing && microphoneGranted && modelReady,
            ) {
                if (calibrationRecording) stopCalibration() else startCalibration()
            }
            if (!modelReady) ErrorText("Nejdřív stáhni Offline Pack.")
            if (!microphoneGranted) ErrorText("Pro kalibraci je potřeba povolit mikrofon.")
            calibrationError?.let { ErrorText(it) }
            if (report != null) {
                StatusLine("Přesnost vzorku", true, "${report.baselineAccuracyPercent} %")
                StatusLine("Po navržených opravách", true, "${report.correctedAccuracyPercent} %")
                Text("Druhé číslo měří stejný kalibrační vzorek; není to tvrzení o dotrénování vah modelu.", fontSize = 12.sp)
                if (report.suggestions.isEmpty()) {
                    Text("Nenašel jsem bezpečnou jednoslovnou záměnu k potvrzení.")
                } else {
                    report.suggestions.forEach { suggestion ->
                        Text("${suggestion.spoken} → ${suggestion.written}")
                    }
                    PrimaryButton("Přidat návrhy do slovníku") { acceptCalibration(report) }
                }
            }
            OutlinedButton(
                onClick = {
                    if (calibrationRecording) calibrationCapture.cancel()
                    calibrationRecording = false
                    screen = AppScreen.SETTINGS
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zpět") }
        }
    }

    @Composable
    private fun VoiceLabScreen() {
        var consent by remember { mutableStateOf(VoiceConsent.OWN_VOICE) }
        var confirmed by remember { mutableStateOf(false) }
        val language = Locale.forLanguageTag(selectedLanguageTag).language
        AppPage("VOICE LAB • EXPERIMENT", "Vytvoř hlasovou referenci") {
            Text("Nejde o trénování celého modelu. Ukládá se 10–30 sekund čistého hlasu pro budoucí zero-shot klonování.")
            Text(UserSettings.voicePrompt(language), fontWeight = FontWeight.Bold)
            Text("Nahraj v tiché místnosti, přirozeně a bez hudby.")

            FilterChip(
                selected = consent == VoiceConsent.OWN_VOICE,
                onClick = { consent = VoiceConsent.OWN_VOICE },
                label = { Text("Je to můj hlas") },
            )
            FilterChip(
                selected = consent == VoiceConsent.EXPLICIT_PERMISSION,
                onClick = { consent = VoiceConsent.EXPLICIT_PERMISSION },
                label = { Text("Mám výslovný souhlas") },
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text("Potvrzuji oprávnění tento hlas uložit a klonovat.", modifier = Modifier.padding(top = 12.dp))
            }

            if (!microphoneGranted) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Povolit mikrofon") }
            }
            PrimaryButton(
                label = if (voiceRecording) "Zastavit a uložit" else "Nahrát hlasovou referenci",
                enabled = confirmed && microphoneGranted,
            ) {
                if (voiceRecording) stopVoiceRecording(consent) else startVoiceRecording(consent)
            }
            voiceError?.let { ErrorText(it) }
            StatusLine("Lokální reference", voiceReferenceReady, if (voiceReferenceReady) "Uložena" else "Chybí")
            if (voiceReferenceReady) {
                OutlinedButton(onClick = ::playVoiceReference, modifier = Modifier.fillMaxWidth()) {
                    Text("Přehrát referenci")
                }
                OutlinedButton(
                    onClick = {
                        VoiceProfileStore.delete(this@MainActivity)
                        voiceReferenceReady = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Smazat hlasová data") }
            }

            Text(
                "Syntéza vlastním hlasem zůstává zamčená, dokud Android engine neprojde českým benchmarkem. " +
                    "Současný PocketTTS/ZipVoice nemá ověřenou češtinu.",
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
                Text("Zpět")
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
                    } ?: error("Soubor nelze otevřít")
                }
            }.onSuccess { writingSample = it }
                .onFailure { documentError = it.message ?: "Soubor nelze načíst" }
        }
    }

    private fun startCalibration() {
        calibrationError = null
        calibrationReport = null
        runCatching {
            calibrationCapture.start(this, lifecycleScope, ::stopCalibration)
            calibrationRecording = true
        }.onFailure { calibrationError = it.message ?: "Kalibraci nelze spustit" }
    }

    private fun stopCalibration() {
        if (!calibrationRecording) return
        calibrationRecording = false
        calibrationProcessing = true
        lifecycleScope.launch {
            runCatching {
                val samples = calibrationCapture.stop()
                require(samples.size >= AudioCapture.SAMPLE_RATE * 30) {
                    "Pro spolehlivou kalibraci čti alespoň 30 sekund"
                }
                val speech = SpeechGate().trim(samples, AudioCapture.SAMPLE_RATE)
                withContext(Dispatchers.Default) {
                    WhisperEngine(this@MainActivity, Locale.forLanguageTag(selectedLanguageTag).language).use { engine ->
                        RecognitionCalibration.analyze(
                            reference = UserSettings.calibrationPrompt(
                                Locale.forLanguageTag(selectedLanguageTag).language,
                            ),
                            transcript = engine.transcribe(speech),
                        )
                    }
                }
            }.onSuccess { calibrationReport = it }
                .onFailure { calibrationError = it.message ?: "Kalibrace selhala" }
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
            toast("Potvrzené opravy byly přidány do slovníku")
        }.onFailure { calibrationError = it.message }
    }

    private fun saveBubble(value: BubblePreferences) {
        bubblePreferences = BubblePreferences.from(value.sizePercent, value.opacityPercent)
        UserSettings.saveBubble(this, bubblePreferences)
    }

    private fun startVoiceRecording(consent: VoiceConsent) {
        voiceError = null
        runCatching {
            voiceCapture.start(this, lifecycleScope) { stopVoiceRecording(consent) }
            voiceRecording = true
        }.onFailure { voiceError = it.message ?: "Nahrávání nelze spustit" }
    }

    private fun stopVoiceRecording(consent: VoiceConsent) {
        if (!voiceRecording) return
        voiceRecording = false
        lifecycleScope.launch {
            runCatching {
                val samples = voiceCapture.stop()
                withContext(Dispatchers.IO) {
                    VoiceProfileStore.save(this@MainActivity, samples, consent)
                }
            }.onSuccess {
                voiceReferenceReady = true
                toast("Hlasová reference byla uložena lokálně")
            }.onFailure {
                voiceError = it.message ?: "Hlasovou referenci nelze uložit"
            }
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
        }.onFailure { toast("Referenci nelze přehrát") }
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
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        NavigationBar(containerColor = Color.White) {
            listOf(
                Triple(AppScreen.HOME, "⌂", "Domů"),
                Triple(AppScreen.DICTIONARY, "Aa", "Slovník"),
                Triple(AppScreen.SNIPPETS, "§", "Snippets"),
                Triple(AppScreen.SETTINGS, "⚙", "Nastavení"),
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
        PrimaryButton("Pokračovat", onClick = onNext)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Zpět") }
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

    private fun languageName(locale: Locale): String = locale.getDisplayName(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun speaker(): TtsSpeaker = tts ?: TtsSpeaker(this).also { tts = it }

    private fun cleanupName(level: CleanupLevel): String = when (level) {
        CleanupLevel.RAW -> "Raw • jen slovník"
        CleanupLevel.LIGHT -> "Light • lehká úprava"
        CleanupLevel.POLISHED -> "Polished • sémantické opravy"
    }

    private enum class AppScreen { ONBOARDING, HOME, DICTIONARY, SNIPPETS, SETTINGS, CALIBRATION, VOICE_LAB }

    companion object {
        private val BLACK = Color(0xFF111111)
        private val PAPER = Color(0xFFF4F4F0)
    }
}
