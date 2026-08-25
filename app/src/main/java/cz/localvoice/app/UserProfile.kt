package cz.localvoice.app

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.core.content.edit
import java.util.Locale

data class UserProfile(
    val languageTag: String,
    val style: String,
    val writingSample: String,
    val cleanup: CleanupLevel = CleanupLevel.POLISHED,
    val dictionary: List<DictionaryEntry> = emptyList(),
    val snippets: List<TextSnippet> = emptyList(),
) {
    val locale: Locale get() = Locale.forLanguageTag(languageTag)
}

data class StylePreview(val id: String, val name: String, val example: String)

object UserSettings {
    const val CASUAL = "casual"
    const val BALANCED = "balanced"
    const val PROFESSIONAL = "professional"
    const val CUSTOM = "custom"
    const val VERBATIM = "verbatim"
    const val MAX_WRITING_SAMPLE = 12_000
    const val DEFAULT_LANGUAGE_TAG = "en-US"
    private const val ACCESSIBILITY_DISCLOSURE_VERSION = 1
    private val styles = setOf(CASUAL, BALANCED, PROFESSIONAL, CUSTOM, VERBATIM)
    private val supportedLanguageTags = listOf("en-US", "cs-CZ", "de-DE", "fr-FR", "es-ES")
    private val supportedLanguages = supportedLanguageTags.map { Locale.forLanguageTag(it).language }.toSet()

    fun load(context: Context, requestedLanguageTag: String? = null): UserProfile {
        val preferences = preferences(context)
        val savedLanguageTag = preferences.getString("language_tag", null)
        if (requestedLanguageTag == null && savedLanguageTag != null) {
            migrateLegacyProfile(preferences, savedLanguageTag)
        }
        val languageTag = requestedLanguageTag
            ?.takeIf { Locale.forLanguageTag(it).language.isNotBlank() }
            ?: savedLanguageTag
            ?.takeIf { Locale.forLanguageTag(it).language.isNotBlank() }
            ?: DEFAULT_LANGUAGE_TAG
        val suffix = profileSuffix(languageTag)
        val useLegacyProfile = requestedLanguageTag == null ||
            profileSuffix(savedLanguageTag.orEmpty()) == suffix
        val sample = (preferences.getString("writing_sample_$suffix", null)
            ?: preferences.getString("writing_sample", "").takeIf { useLegacyProfile })
            .orEmpty().take(MAX_WRITING_SAMPLE)
        val savedStyle = (preferences.getString("style_$suffix", null)
            ?: preferences.getString("style", BALANCED).takeIf { useLegacyProfile }
            ?: BALANCED).orEmpty()
        val style = savedStyle.takeIf { it in styles && (it != CUSTOM || sample.isNotBlank()) } ?: BALANCED
        val cleanup = runCatching {
            CleanupLevel.valueOf(preferences.getString("cleanup_$suffix", CleanupLevel.POLISHED.name).orEmpty())
        }.getOrDefault(CleanupLevel.POLISHED)
        return UserProfile(
            languageTag = languageTag,
            style = style,
            writingSample = sample,
            cleanup = cleanup,
            dictionary = Personalization.decodeDictionary(
                preferences.getString("dictionary_$suffix", "[]").orEmpty(),
            ),
            snippets = Personalization.decodeSnippets(
                preferences.getString("snippets_$suffix", "[]").orEmpty(),
            ),
        )
    }

    fun save(context: Context, profile: UserProfile, onboardingDone: Boolean = true) {
        val languageTag = Locale.forLanguageTag(profile.languageTag).toLanguageTag()
        require(Locale.forLanguageTag(languageTag).language.isNotBlank()) { "Invalid language" }
        val sample = profile.writingSample.trim().take(MAX_WRITING_SAMPLE)
        val style = profile.style.takeIf { it in styles && (it != CUSTOM || sample.isNotBlank()) } ?: BALANCED
        val suffix = profileSuffix(languageTag)
        preferences(context).edit {
            putString("language_tag", languageTag)
            putString("style_$suffix", style)
            putString("writing_sample_$suffix", sample)
            putString("cleanup_$suffix", profile.cleanup.name)
            putString("dictionary_$suffix", Personalization.encodeDictionary(profile.dictionary))
            putString("snippets_$suffix", Personalization.encodeSnippets(profile.snippets))
            putBoolean("onboarding_done", onboardingDone)
        }
    }

    fun isOnboardingDone(context: Context): Boolean = preferences(context)
        .getBoolean("onboarding_done", false)

    fun accessibilityDisclosureAccepted(context: Context): Boolean = preferences(context)
        .getInt("accessibility_disclosure_version", 0) >= ACCESSIBILITY_DISCLOSURE_VERSION

    fun acceptAccessibilityDisclosure(context: Context) {
        preferences(context).edit {
            putInt("accessibility_disclosure_version", ACCESSIBILITY_DISCLOSURE_VERSION)
        }
    }

    fun bubble(context: Context): BubblePreferences = preferences(context).let {
        BubblePreferences.from(
            sizePercent = it.getInt("bubble_size_percent", 85),
            opacityPercent = it.getInt("bubble_opacity_percent", 80),
        )
    }

    fun saveBubble(context: Context, value: BubblePreferences) {
        val safe = BubblePreferences.from(value.sizePercent, value.opacityPercent)
        preferences(context).edit {
            putInt("bubble_size_percent", safe.sizePercent)
            putInt("bubble_opacity_percent", safe.opacityPercent)
        }
    }

    fun availableLanguages(context: Context): List<Locale> {
        val localeList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            context.resources.configuration.locales
        }
        val systemTags = (0 until localeList.size())
            .map(localeList::get)
            .map(Locale::toLanguageTag)
        val inputManager = context.getSystemService(InputMethodManager::class.java)
        val keyboardTags = inputManager.enabledInputMethodList.flatMap { inputMethod ->
            inputManager.getEnabledInputMethodSubtypeList(inputMethod, true).map { subtype ->
                subtype.localeTag()
            }
        }
        return availableLanguageTags(systemTags, keyboardTags).map(Locale::forLanguageTag)
    }

    internal fun availableLanguageTags(systemTags: List<String>, keyboardTags: List<String>): List<String> {
        val preferred = mergeLanguageTags(systemTags, keyboardTags)
            .filter { Locale.forLanguageTag(it).language in supportedLanguages }
            .distinctBy { Locale.forLanguageTag(it).language }
        val preferredLanguages = preferred.map { Locale.forLanguageTag(it).language }.toSet()
        return preferred + supportedLanguageTags.filter {
            Locale.forLanguageTag(it).language !in preferredLanguages
        }
    }

    internal fun mergeLanguageTags(systemTags: List<String>, keyboardTags: List<String>): List<String> =
        (systemTags + keyboardTags)
            .asSequence()
            .map { it.replace('_', '-') }
            .map(Locale::forLanguageTag)
            .filter { it.language.isNotBlank() }
            .map(Locale::toLanguageTag)
            .distinct()
            .toList()

    fun styleName(style: String): String = when (style) {
        CASUAL -> "Natural"
        PROFESSIONAL -> "Professional"
        CUSTOM -> "My style"
        VERBATIM -> "Verbatim"
        else -> "Balanced"
    }

    fun stylePreviews(language: String): List<StylePreview> {
        val examples = when (language) {
            "cs" -> listOf(
                "Ahoj, dneska to asi nestihnu, ozvu se večer.",
                "Dnes to pravděpodobně nestihnu. Ozvu se večer.",
                "Dnes bohužel termín nestihnu. Aktualizaci vám pošlu večer.",
            )
            "de" -> listOf(
                "Hi, ich schaffe es heute wohl nicht, ich melde mich heute Abend.",
                "Ich schaffe es heute wahrscheinlich nicht. Ich melde mich heute Abend.",
                "Leider kann ich den Termin heute nicht einhalten. Am Abend sende ich Ihnen ein Update.",
            )
            "fr" -> listOf(
                "Salut, je n’y arriverai sans doute pas aujourd’hui, je te réponds ce soir.",
                "Je n’y arriverai probablement pas aujourd’hui. Je répondrai ce soir.",
                "Je ne pourrai malheureusement pas respecter le délai aujourd’hui. Je vous enverrai un point ce soir.",
            )
            "es" -> listOf(
                "Hola, creo que hoy no llego, te escribo esta noche.",
                "Probablemente no llegue hoy. Escribiré esta noche.",
                "Lamentablemente, hoy no podré cumplir el plazo. Le enviaré una actualización esta noche.",
            )
            else -> listOf(
                "Hey, I probably won’t make it today, I’ll message you tonight.",
                "I probably won’t make it today. I’ll send an update tonight.",
                "Unfortunately, I will not meet today’s deadline. I will send you an update this evening.",
            )
        }
        return listOf(
            StylePreview(CASUAL, "Natural", examples[0]),
            StylePreview(BALANCED, "Balanced", examples[1]),
            StylePreview(PROFESSIONAL, "Professional", examples[2]),
        )
    }

    fun voicePrompt(language: String): String = when (language) {
        "cs" -> "Dnes je klidný den. Čtu tento text přirozeně, zřetelně a bez spěchu. Můj hlas zůstává stejný od první věty až do konce nahrávky."
        "de" -> "Heute ist ein ruhiger Tag. Ich lese diesen Text natürlich, deutlich und ohne Eile. Meine Stimme bleibt vom ersten Satz bis zum Ende der Aufnahme gleich."
        "fr" -> "Aujourd’hui est une journée calme. Je lis ce texte naturellement, clairement et sans me presser. Ma voix reste la même du début à la fin de l’enregistrement."
        "es" -> "Hoy es un día tranquilo. Leo este texto de forma natural, clara y sin prisa. Mi voz se mantiene igual desde la primera frase hasta el final de la grabación."
        else -> "Today is a quiet day. I am reading this text naturally, clearly, and without rushing. My voice stays consistent from the first sentence to the end of the recording."
    }

    fun calibrationPrompt(language: String): String = when (language) {
        "cs" -> """
            Dobrý den. Čtu tento text přirozeným hlasem, klidně a bez zbytečného spěchu. Ve čtvrtek v šest hodin se potkáme na náměstí Jiřího z Poděbrad. Přinesu žlutý zápisník, černá sluchátka a zprávu o projektu. Číslo objednávky je dvacet sedm a termín dokončení připadá na příští středu. Potřebuji správně vyslovit slova čtvrtek, hřiště, příležitost, zodpovědnost, rozhraní a zabezpečení. Když se přeřeknu, větu zopakuji od začátku. Teď mluvím o běžném pracovním dni, o zprávách, poznámkách, e-mailech a krátkých odpovědích. Prosím, připomeň mi schůzku s Michalem, Šárkou a Matějem. Nová aplikace se jmenuje Local Voice a funguje bez výměny klávesnice. Na závěr přečtu několik čísel: patnáct, čtyřicet dva, sto osm a dva tisíce dvacet šest. Tento vzorek pomůže najít opakované záměny v mém přepisu. Pokračuji delším odstavcem, aby aplikace poznala tempo, výslovnost a časté záměny v češtině. Ráno odpovídám zákazníkům, potom připravuji rozpočet, technickou dokumentaci a seznam úkolů pro kolegy. V pátek pojedu vlakem z Prahy do Brna a cestou doplním poznámky k prezentaci. Potřebuji přesně zapsat názvy Bluetooth, GitHub, Android, Samsung Galaxy a OpenAI. Adresa firmy je Křižíkova dvanáct, Praha osm. Kontaktní osoba se jmenuje Tereza Dvořáková a její telefonní číslo nadiktuji později. Mluvím souvisle, ale mezi větami dělám krátké přestávky. Pokud systém některé slovo zamění, po skončení mi nabídne opravu, kterou mohu před uložením zkontrolovat.
        """.trimIndent()
        "de" -> """
            Guten Tag. Ich lese diesen Text mit meiner normalen Stimme, ruhig und deutlich. Am Donnerstag um sechs Uhr treffen wir uns am Hauptbahnhof. Ich bringe das gelbe Notizbuch, schwarze Kopfhörer und den aktuellen Projektbericht mit. Die Bestellnummer ist siebenundzwanzig, und der Abgabetermin ist nächsten Mittwoch. Wörter wie Schnittstelle, Zuverlässigkeit, Sicherheit, selbstverständlich und außergewöhnlich sollen korrekt erkannt werden. Wenn ich mich verspreche, beginne ich den Satz noch einmal. Nun spreche ich über Nachrichten, Notizen, E-Mails, Besprechungen und kurze Antworten im Alltag. Bitte erinnere mich an den Termin mit Michael, Charlotte und Matthias. Die Anwendung heißt Local Voice und funktioniert mit meiner gewohnten Tastatur. Zum Schluss lese ich einige Zahlen: fünfzehn, zweiundvierzig, einhundertacht und zweitausendsechsundzwanzig. Ich lese weiter, damit die Anwendung mein Sprechtempo und wiederkehrende Verwechslungen im Deutschen erkennen kann. Am Morgen beantworte ich Kundenanfragen, danach erstelle ich ein Angebot, technische Unterlagen und eine Aufgabenliste für das Team. Am Freitag fahre ich mit dem Zug von Berlin nach Hamburg und ergänze unterwegs die Präsentation. Produktnamen wie Bluetooth, GitHub, Android, Samsung Galaxy und OpenAI sollen exakt geschrieben werden. Die Firmenadresse lautet Friedrichstraße zwölf in Berlin. Meine Ansprechpartnerin heißt Theresa König. Ich spreche flüssig und mache zwischen den Sätzen kurze Pausen. Nach der Aufnahme prüfe ich jeden vorgeschlagenen Eintrag, bevor er dauerhaft in meinem persönlichen Wörterbuch gespeichert wird.
        """.trimIndent()
        "fr" -> """
            Bonjour. Je lis ce texte avec ma voix habituelle, calmement et clairement. Jeudi à dix-huit heures, nous nous retrouverons près de la gare centrale. J’apporterai le carnet jaune, les écouteurs noirs et le dernier rapport du projet. Le numéro de commande est vingt-sept et la date limite tombe mercredi prochain. Des mots comme interface, confidentialité, responsabilité, développement et particulièrement doivent être reconnus correctement. Si je me trompe, je recommence la phrase depuis le début. Je parle maintenant de messages, de notes, de courriels, de réunions et de réponses courtes. Merci de me rappeler le rendez-vous avec Michel, Chloé et Mathieu. L’application s’appelle Local Voice et fonctionne avec mon clavier habituel. Enfin, je lis quelques nombres : quinze, quarante-deux, cent huit et deux mille vingt-six. Je continue avec un passage plus long afin que l’application mesure mon rythme et repère les confusions fréquentes en français. Le matin, je réponds aux clients, puis je prépare un devis, une documentation technique et la liste des tâches de l’équipe. Vendredi, je prendrai le train de Paris à Lyon et je compléterai la présentation pendant le trajet. Les noms Bluetooth, GitHub, Android, Samsung Galaxy et OpenAI doivent être écrits exactement. L’adresse du bureau est douze rue de la République à Paris. Ma correspondante s’appelle Thérèse Dubois. Je parle sans accélérer et je marque une courte pause entre les phrases. Après l’enregistrement, je vérifierai chaque correction proposée avant de l’ajouter à mon dictionnaire personnel.
        """.trimIndent()
        "es" -> """
            Buenos días. Leo este texto con mi voz habitual, despacio y con claridad. El jueves a las seis nos reuniremos junto a la estación central. Llevaré el cuaderno amarillo, los auriculares negros y el informe actualizado del proyecto. El número del pedido es veintisiete y la fecha límite será el próximo miércoles. Palabras como interfaz, privacidad, responsabilidad, desarrollo y extraordinario deben reconocerse correctamente. Si me equivoco, repetiré la frase desde el principio. Ahora hablo de mensajes, notas, correos electrónicos, reuniones y respuestas breves. Recuérdame la cita con Miguel, Sofía y Mateo. La aplicación se llama Local Voice y funciona con mi teclado de siempre. Para terminar leo algunos números: quince, cuarenta y dos, ciento ocho y dos mil veintiséis. Continúo con un párrafo más largo para que la aplicación mida mi ritmo y detecte confusiones habituales en español. Por la mañana respondo a los clientes, después preparo un presupuesto, la documentación técnica y una lista de tareas para el equipo. El viernes viajaré en tren de Madrid a Valencia y completaré la presentación durante el trayecto. Los nombres Bluetooth, GitHub, Android, Samsung Galaxy y OpenAI deben escribirse exactamente. La dirección de la oficina es calle Alcalá número doce, Madrid. Mi contacto se llama Teresa Domínguez. Hablo de forma continua y hago pausas breves entre las frases. Después de grabar, revisaré cada corrección propuesta antes de guardarla en mi diccionario personal.
        """.trimIndent()
        else -> """
            Hello. I am reading this text in my normal voice, clearly and without rushing. On Thursday at six o’clock, we will meet near the central station. I will bring the yellow notebook, black headphones, and the latest project report. The order number is twenty-seven, and the deadline is next Wednesday. Words such as interface, privacy, responsibility, development, reliability, and extraordinary should be recognized correctly. If I make a mistake, I will repeat the sentence from the beginning. I am now talking about messages, notes, email, meetings, and short everyday replies. Please remind me about the appointment with Michael, Charlotte, and Matthew. The application is called Local Voice and works with my usual keyboard. Finally, I will read a few numbers: fifteen, forty-two, one hundred eight, and two thousand twenty-six. This sample helps find repeated substitutions in my transcript. I will continue with a longer paragraph so the application can measure my pace and identify common recognition errors in English. In the morning I answer customers, then prepare a quotation, technical documentation, and a task list for the team. On Friday I will take the train from London to Manchester and finish the presentation during the journey. Product names such as Bluetooth, GitHub, Android, Samsung Galaxy, and OpenAI should be written exactly. The office address is twelve King Street, London. My contact is Theresa Johnson. I speak continuously but leave a short pause between sentences. After recording, I will review every proposed correction before saving it to my personal dictionary.
        """.trimIndent()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        VoiceAccessibilityService.PREFERENCES,
        Context.MODE_PRIVATE,
    )

    private fun migrateLegacyProfile(preferences: SharedPreferences, languageTag: String) {
        val suffix = profileSuffix(languageTag)
        preferences.edit {
            if (!preferences.contains("style_$suffix")) {
                preferences.getString("style", null)?.let { putString("style_$suffix", it) }
            }
            if (!preferences.contains("writing_sample_$suffix")) {
                preferences.getString("writing_sample", null)?.let { putString("writing_sample_$suffix", it) }
            }
        }
    }

    private fun profileSuffix(languageTag: String): String = Locale.forLanguageTag(languageTag)
        .toLanguageTag()
        .lowercase(Locale.ROOT)

    @Suppress("DEPRECATION")
    private fun InputMethodSubtype.localeTag(): String = languageTag.ifBlank { locale }
}
