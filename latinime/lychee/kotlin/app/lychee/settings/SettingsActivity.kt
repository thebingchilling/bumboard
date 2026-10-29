@file:OptIn(ExperimentalMaterial3Api::class)

package app.lychee.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings as SystemSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lychee.CrashLog
import app.lychee.chinese.ChineseInput
import app.lychee.chinese.Readings
import com.android.inputmethod.keyboard.KeyboardTheme
import app.lychee.layout.LycheeLayout
import app.lychee.toolbar.Shortcut
import app.lychee.emoji.EmojiPanelView
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.settings.Settings
import com.android.inputmethod.latin.utils.IntentUtils
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import kotlin.math.roundToInt

/** The pages of the settings app. */
enum class Page(@StringRes val title: Int) {
    Home(R.string.english_ime_name),
    Languages(R.string.language_selection_title),
    Chinese(R.string.lychee_chinese),
    Personal(R.string.lychee_personal_title),
    Preferences(R.string.settings_screen_preferences),
    Theme(R.string.settings_screen_theme),
    Correction(R.string.settings_screen_correction),
    Gesture(R.string.settings_screen_gesture),
    Advanced(R.string.settings_screen_advanced),
    Fonts(R.string.lychee_fonts_title),
    Packs(R.string.lychee_packs_title),
    About(R.string.lychee_about),
    Licences(R.string.lychee_licences),
}

/** Whether Lychee is enabled in the system and whether it is the current keyboard. */
data class ImeStatus(val enabled: Boolean, val current: Boolean)

/** Lychee's settings, in Material 3. Replaces LatinIME's PreferenceActivity screens. */
class SettingsActivity : ComponentActivity() {

    private val status = mutableStateOf(ImeStatus(enabled = false, current = false))

    // A page asked for by a later intent (the activity is singleTask).
    private val requested = mutableStateOf<Page?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AudioAndHapticFeedbackManager.init(this)
        SubtypeLocaleUtils.init(this)
        val start = pageOf(intent) ?: Page.Home
        setContent {
            LycheeTheme { SettingsApp(start, status, requested) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requested.value = pageOf(intent)
    }

    private fun pageOf(intent: Intent?): Page? =
        intent?.getStringExtra(EXTRA_PAGE)?.let { name -> Page.entries.firstOrNull { it.name == name } }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // back from the browser (a download) or the install dialog: re-read the packs
        PackRefresh.tick++
    }

    // The input method picker is a dialog over this activity, so a choice there only changes focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun refreshStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        status.value = ImeStatus(
            enabled = UncachedInputMethodManagerUtils.isThisImeEnabled(this, imm),
            current = UncachedInputMethodManagerUtils.isThisImeCurrent(this, imm),
        )
    }

    companion object {
        const val EXTRA_PAGE = "app.lychee.settings.PAGE"
    }
}

@Composable
private fun SettingsApp(start: Page, status: MutableState<ImeStatus>, requested: MutableState<Page?>) {
    var page by rememberSaveable { mutableStateOf(start) }
    val asked = requested.value
    LaunchedEffect(asked) {
        if (asked != null) {
            page = asked
            requested.value = null
        }
    }
    val back = { page = if (page == Page.Licences) Page.About else Page.Home }
    BackHandler(enabled = page != Page.Home) { back() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(page.title)) },
                navigationIcon = {
                    if (page != Page.Home) {
                        IconButton(onClick = back) {
                            Icon(painterResource(R.drawable.ic_sym_arrow_back), stringResource(R.string.lychee_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(targetState = page, label = "page", modifier = Modifier.padding(padding)) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                when (shown) {
                    Page.Home -> HomePage(status.value) { page = it }
                    Page.Languages -> LanguagesPage()
                    Page.Chinese -> ChinesePage()
                    Page.Personal -> PersonalDictionaryPage()
                    Page.Preferences -> PreferencesPage()
                    Page.Theme -> ThemePage()
                    Page.Correction -> CorrectionPage()
                    Page.Gesture -> GesturePage()
                    Page.Advanced -> AdvancedPage()
                    Page.Fonts -> FontsPage()
                    Page.Packs -> PacksPage()
                    Page.About -> AboutPage { page = Page.Licences }
                    Page.Licences -> LicencesPage()
                }
            }
        }
    }
}

@Composable
internal fun prefs(): SharedPreferences {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    return remember { PreferenceManager.getDefaultSharedPreferences(context) }
}

// ---------------------------------------------------------------- home

@Composable
private fun HomePage(status: ImeStatus, open: (Page) -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.english_ime_name)

    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    crash?.let { log ->
        CrashCard(log) {
            CrashLog.clear(context)
            crash = null
        }
    }

    if (status.enabled && status.current) {
        Card {
            Text(stringResource(R.string.lychee_setup_done_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.lychee_setup_done_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Card(highlight = true) {
            Text(stringResource(R.string.lychee_setup_card_title), style = MaterialTheme.typography.titleLarge)
            SetupStep(
                number = 1,
                title = stringResource(R.string.setup_step1_title, appName),
                body = stringResource(R.string.setup_step1_instruction, appName),
                action = stringResource(R.string.setup_step1_action),
                done = status.enabled,
                enabled = true,
            ) {
                context.startActivity(
                    Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            SetupStep(
                number = 2,
                title = stringResource(R.string.setup_step2_title, appName),
                body = stringResource(R.string.setup_step2_instruction, appName),
                action = stringResource(R.string.setup_step2_action),
                done = status.current,
                enabled = status.enabled,
            ) {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }
    }

    var sample by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = sample,
        onValueChange = { sample = it },
        label = { Text(stringResource(R.string.lychee_try_it)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val showGesture = Settings.readFromBuildConfigIfGestureInputEnabled(context.resources)
    // grouped by what people look for: languages, how typing works, how it looks, downloads
    Group(stringResource(R.string.lychee_section_languages)) {
        row {
            NavRow(
                it, stringResource(R.string.language_selection_title),
                app.lychee.chinese.ChineseScript.ui(stringResource(R.string.lychee_languages_summary), context), R.drawable.ic_sym_language,
            ) { open(Page.Languages) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_chinese),
                stringResource(R.string.lychee_chinese_summary), R.drawable.ic_sym_translate,
            ) { open(Page.Chinese) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_personal_title),
                stringResource(R.string.lychee_personal_summary), R.drawable.ic_sym_edit,
            ) { open(Page.Personal) }
        }
    }
    Group(stringResource(R.string.lychee_section_typing)) {
        row {
            NavRow(
                it, stringResource(R.string.settings_screen_preferences),
                stringResource(R.string.lychee_preferences_summary), R.drawable.ic_sym_keyboard,
            ) { open(Page.Preferences) }
        }
        row {
            NavRow(
                it, stringResource(R.string.settings_screen_correction),
                stringResource(R.string.lychee_correction_summary), R.drawable.ic_sym_spellcheck,
            ) { open(Page.Correction) }
        }
        if (showGesture) {
            row {
                NavRow(
                    it, stringResource(R.string.settings_screen_gesture),
                    stringResource(R.string.lychee_gesture_summary), R.drawable.ic_sym_gesture,
                ) { open(Page.Gesture) }
            }
        }
    }
    Group(stringResource(R.string.lychee_section_look)) {
        row {
            NavRow(
                it, stringResource(R.string.settings_screen_theme),
                stringResource(R.string.lychee_theme_summary), R.drawable.ic_sym_palette,
            ) { open(Page.Theme) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_fonts_title),
                stringResource(R.string.lychee_fonts_summary), R.drawable.ic_sym_text_fields,
            ) { open(Page.Fonts) }
        }
    }
    Group(stringResource(R.string.lychee_section_more)) {
        row {
            NavRow(
                it, stringResource(R.string.lychee_packs_title),
                stringResource(R.string.lychee_packs_summary), R.drawable.ic_sym_download,
            ) { open(Page.Packs) }
        }
        row {
            NavRow(
                it, stringResource(R.string.settings_screen_advanced),
                stringResource(R.string.lychee_advanced_summary), R.drawable.ic_sym_build,
            ) { open(Page.Advanced) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_about),
                stringResource(R.string.lychee_about_summary), R.drawable.ic_sym_info,
            ) { open(Page.About) }
        }
    }
}

/** The last crash's report, so it can be copied or shared without adb. */
@Composable
private fun CrashCard(log: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.lychee_crash_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.lychee_crash_body), style = MaterialTheme.typography.bodyMedium)
            Text(
                log.lineSequence().take(12).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log),
                            null,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.lychee_crash_share)) }
                FilledTonalButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Lychee crash", log))
                }) { Text(stringResource(R.string.lychee_crash_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.lychee_crash_dismiss)) }
            }
        }
    }
}

@Composable
private fun Card(highlight: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SetupStep(
    number: Int, title: String, body: String, action: String,
    done: Boolean, enabled: Boolean, onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(
                    painterResource(R.drawable.ic_sym_check_circle), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp),
                )
            } else {
                Text("$number", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (!done) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (enabled) {
                Button(onClick = onClick) { Text(action) }
            } else {
                FilledTonalButton(onClick = onClick, enabled = false) { Text(action) }
            }
        }
    }
}

// ---------------------------------------------------------------- languages

/**
 * Turns Lychee's keyboards on and off here instead of in system settings. Android 14 added
 * InputMethodManager.setExplicitlyEnabledInputMethodSubtypes for an IME's own subtypes; older
 * versions fall back to the system screen.
 */
@Composable
private fun LanguagesPage() {
    val context = LocalContext.current
    val imm = remember { context.getSystemService(InputMethodManager::class.java) }
    val imi = remember { UncachedInputMethodManagerUtils.getInputMethodInfoOf(context.packageName, imm) }
    if (imi == null) {
        Text(
            stringResource(R.string.lychee_languages_enable_first),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        return
    }
    val subtypes = remember { (0 until imi.subtypeCount).map { imi.getSubtypeAt(it) } }
    var enabled by remember {
        mutableStateOf(imm.getEnabledInputMethodSubtypeList(imi, true).map { it.hashCode() }.toSet())
    }
    val canSet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    Text(
        stringResource(R.string.lychee_languages_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    Group {
        subtypes.forEach { subtype ->
            row { shape ->
                val hash = subtype.hashCode()
                val on = hash in enabled
                SwitchRow(shape, SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype), null, on) { checked ->
                    val next = if (checked) enabled + hash else enabled - hash
                    // Keep at least one keyboard on.
                    if (next.isEmpty()) return@SwitchRow
                    if (canSet && setEnabledSubtypes(imm, imi.id, next)) {
                        enabled = next
                    } else {
                        openSystemLanguages(context)
                    }
                }
            }
        }
    }
}

private fun setEnabledSubtypes(imm: InputMethodManager, imeId: String, hashes: Set<Int>): Boolean =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            imm.setExplicitlyEnabledInputMethodSubtypes(imeId, hashes.toIntArray())
            true
        } else {
            false
        }
    } catch (e: RuntimeException) {
        false
    }

private fun openSystemLanguages(context: Context) {
    val imm = context.getSystemService(InputMethodManager::class.java)
    val imi = UncachedInputMethodManagerUtils.getInputMethodInfoOf(context.packageName, imm)
    context.startActivity(
        IntentUtils.getInputLanguageSelectionIntent(imi?.id, Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

// ---------------------------------------------------------------- Chinese

@Composable
private fun ChinesePage() {
    val prefs = prefs()
    val uiScript = prefs.stringPref(app.lychee.chinese.ChineseScript.PREF_UI) {
        prefs.getString(app.lychee.chinese.ChineseScript.PREF_UI, null) ?: "traditional"
    }
    // the candidates follow Lychee's script until chosen here (or with the 繁/简 key)
    val script = prefs.stringPref(ChineseInput.PREF_SCRIPT) {
        prefs.getString(ChineseInput.PREF_SCRIPT, null) ?: uiScript.value
    }
    val cantonese = prefs.stringPref(ChineseInput.PREF_CANTONESE_INPUT) {
        prefs.getString(ChineseInput.PREF_CANTONESE_INPUT, null) ?: "jyutping"
    }
    val showReadings = prefs.boolPref(Readings.PREF_SHOW_READINGS) { Readings.showReadings(prefs) }
    val otherLanguage = prefs.boolPref(Readings.PREF_OTHER_LANGUAGE) {
        prefs.getBoolean(Readings.PREF_OTHER_LANGUAGE, false)
    }
    val meaning = prefs.boolPref(Readings.PREF_MEANING) { prefs.getBoolean(Readings.PREF_MEANING, true) }
    val toneColours = prefs.boolPref(Readings.PREF_TONE_COLOURS) { prefs.getBoolean(Readings.PREF_TONE_COLOURS, true) }
    Text(
        stringResource(R.string.lychee_chinese_how).let { if (uiScript.value == "simplified") app.lychee.chinese.ChineseScript.simplify(it) else it },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    Group(stringResource(R.string.lychee_group_script)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_script_ui), stringResource(R.string.lychee_script_ui_summary),
                options = listOf(
                    "traditional" to stringResource(R.string.lychee_script_traditional),
                    "simplified" to stringResource(R.string.lychee_script_simplified),
                ),
                selected = uiScript.value,
                onSelect = uiScript::set,
            )
        }
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_script),
                options = listOf(
                    "traditional" to stringResource(R.string.lychee_script_traditional),
                    "simplified" to stringResource(R.string.lychee_script_simplified),
                ),
                selected = script.value,
                onSelect = script::set,
            )
        }
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_cantonese_input),
                options = listOf(
                    "jyutping" to stringResource(R.string.lychee_jyutping),
                    "yale" to stringResource(R.string.lychee_yale),
                ),
                selected = cantonese.value,
                onSelect = cantonese::set,
            )
        }
    }
    val fuzzyCmn = prefs.boolPref(ChineseInput.PREF_FUZZY + "cmn") { prefs.getBoolean(ChineseInput.PREF_FUZZY + "cmn", false) }
    val fuzzyYue = prefs.boolPref(ChineseInput.PREF_FUZZY + "yue") { prefs.getBoolean(ChineseInput.PREF_FUZZY + "yue", false) }
    Group(stringResource(R.string.lychee_group_typing)) {
        row {
            SwitchRow(
                it, stringResource(R.string.lychee_fuzzy_pinyin), stringResource(R.string.lychee_fuzzy_pinyin_summary),
                fuzzyCmn.value, fuzzyCmn::set,
            )
        }
        row {
            SwitchRow(
                it, stringResource(R.string.lychee_fuzzy_jyutping), stringResource(R.string.lychee_fuzzy_jyutping_summary),
                fuzzyYue.value, fuzzyYue::set,
            )
        }
    }
    val jyutpingTones = prefs.stringPref(Readings.PREF_JYUTPING_TONES) { prefs.getString(Readings.PREF_JYUTPING_TONES, null) ?: "Numbers" }
    val yaleTones = prefs.stringPref(Readings.PREF_YALE_TONES) { prefs.getString(Readings.PREF_YALE_TONES, null) ?: "Marks" }
    val pinyinTones = prefs.stringPref(Readings.PREF_PINYIN_TONES) { prefs.getString(Readings.PREF_PINYIN_TONES, null) ?: "Marks" }
    Group(stringResource(R.string.lychee_group_tones)) {
        if (cantonese.value == "yale") {
            row {
                ChoiceRow(it, stringResource(R.string.lychee_tones_yale), null, listOf(
                    "Marks" to "néih", "Numbers" to "nei5", "Superscript" to "nei⁵",
                ), yaleTones.value, onSelect = yaleTones::set)
            }
        } else {
            row {
                ChoiceRow(it, stringResource(R.string.lychee_tones_jyutping), null, listOf(
                    "Numbers" to "nei5", "Superscript" to "nei⁵",
                ), jyutpingTones.value, onSelect = jyutpingTones::set)
            }
        }
        row {
            ChoiceRow(it, stringResource(R.string.lychee_tones_pinyin), null, listOf(
                "Marks" to "nǐ", "Numbers" to "ni3", "Superscript" to "ni³",
            ), pinyinTones.value, onSelect = pinyinTones::set)
        }
    }
    Group(stringResource(R.string.lychee_under_candidates)) {
        row {
            SwitchRow(
                it, stringResource(R.string.lychee_show_readings), stringResource(R.string.lychee_show_readings_summary),
                showReadings.value, showReadings::set,
            )
        }
        if (showReadings.value) {
            row {
                SwitchRow(
                    it, stringResource(R.string.lychee_other_language), stringResource(R.string.lychee_other_language_summary,
                        stringResource(if (cantonese.value == "yale") R.string.lychee_yale else R.string.lychee_jyutping)),
                    otherLanguage.value, otherLanguage::set,
                )
            }
            row {
                SwitchRow(
                    it, stringResource(R.string.lychee_show_meaning), stringResource(R.string.lychee_show_meaning_summary),
                    meaning.value, meaning::set,
                )
            }
            row {
                SwitchRow(
                    it, stringResource(R.string.lychee_tone_colours), stringResource(R.string.lychee_tone_colours_summary),
                    toneColours.value, toneColours::set,
                )
            }
        }
    }
    val baseSize = prefs.getFloat(ChineseInput.PREF_CANDIDATE_SIZE, 1f)
    // one size per language: characters and kana read best at different sizes
    val sizes = listOf(
        "yue" to R.string.lychee_language_cantonese, "cmn" to R.string.lychee_language_mandarin,
        "ja" to R.string.lychee_language_japanese, "ko" to R.string.lychee_language_korean,
    ).map { (engine, name) ->
        val key = ChineseInput.PREF_CANDIDATE_SIZE + "_" + engine
        Triple(engine, name, prefs.floatPref(key) { prefs.getFloat(key, baseSize) })
    }
    val dictionaryHold = prefs.stringPref(ChineseInput.PREF_DICTIONARY_HOLD) {
        prefs.getString(ChineseInput.PREF_DICTIONARY_HOLD, null) ?: "pin"
    }
    Group(stringResource(R.string.lychee_group_candidates)) {
        for ((_, name, size) in sizes) {
            row {
                SliderRow(
                    it, stringResource(R.string.lychee_candidate_size_for, stringResource(name)),
                    (size.value * 100).roundToInt(), 70..160, 10, format = { v -> "$v%" },
                ) { v -> size.set(v / 100f) }
            }
        }
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_dictionary_hold), stringResource(R.string.lychee_pref_dictionary_hold_summary),
                listOf(
                    "peek" to stringResource(R.string.lychee_pref_dictionary_peek),
                    "pin" to stringResource(R.string.lychee_pref_dictionary_pin),
                ),
                dictionaryHold.value, onSelect = dictionaryHold::set,
            )
        }
    }
}

// ---------------------------------------------------------------- preferences

@Composable
private fun PreferencesPage() {
    val prefs = prefs()
    val res = LocalContext.current.resources
    val autoCap = prefs.boolPref(Settings.PREF_AUTO_CAP) { prefs.getBoolean(Settings.PREF_AUTO_CAP, true) }
    val doubleSpace = prefs.boolPref(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD) {
        prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true)
    }
    val vibrate = prefs.boolPref(Settings.PREF_VIBRATE_ON) { Settings.readVibrationEnabled(prefs, res) }
    val sound = prefs.boolPref(Settings.PREF_SOUND_ON) { Settings.readKeypressSoundEnabled(prefs, res) }
    val keySound = prefs.stringPref(app.lychee.ui.KeySounds.PREF) {
        prefs.getString(app.lychee.ui.KeySounds.PREF, null) ?: app.lychee.ui.KeySounds.SYSTEM
    }
    val popup = prefs.boolPref(Settings.PREF_POPUP_ON) { Settings.readKeyPreviewPopupEnabled(prefs, res) }
    val voice = prefs.boolPref(Settings.PREF_VOICE_INPUT_KEY) { prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, true) }
    val numberRow = prefs.boolPref(LycheeLayout.PREF_NUMBER_ROW) { prefs.getBoolean(LycheeLayout.PREF_NUMBER_ROW, true) }
    val commaKey = prefs.stringPref(LycheeLayout.PREF_COMMA_KEY) { prefs.getString(LycheeLayout.PREF_COMMA_KEY, null) ?: "combined" }
    val hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator()

    Group(stringResource(R.string.lychee_group_typing)) {
        row {
            SwitchRow(it, stringResource(R.string.auto_cap), stringResource(R.string.auto_cap_summary), autoCap.value, autoCap::set)
        }
        row {
            SwitchRow(
                it, stringResource(R.string.use_double_space_period),
                stringResource(R.string.use_double_space_period_summary), doubleSpace.value, doubleSpace::set,
            )
        }
    }
    Group(stringResource(R.string.lychee_group_keys)) {
        row {
            SwitchRow(
                it, stringResource(R.string.lychee_pref_number_row),
                stringResource(R.string.lychee_pref_number_row_summary), numberRow.value, numberRow::set,
            )
        }
    }
    // the key next to the comma: which one, then whether it is folded into the comma or beside it
    val extra = when (commaKey.value) { "combined", "emoji" -> "emoji"; "language", "language_comma" -> "language"; else -> "none" }
    val besides = commaKey.value == "emoji" || commaKey.value == "language"
    Group(stringResource(R.string.lychee_pref_comma_key)) {
        listOf(
            Triple("emoji", R.string.lychee_pref_comma_key_emoji, R.drawable.ic_sym_mood),
            Triple("language", R.string.lychee_pref_comma_key_language, R.drawable.ic_sym_language),
            Triple("none", R.string.lychee_pref_comma_key_none, R.drawable.ic_sym_close),
        ).forEach { (id, label, icon) ->
            row { shape ->
                IconRadioRow(shape, icon, stringResource(label), extra == id) {
                    commaKey.set(commaKeyValue(id, besides))
                }
            }
        }
        if (extra != "none") {
            row {
                ChoiceRow(
                    it, stringResource(R.string.lychee_pref_comma_place), null,
                    listOf(
                        "in" to stringResource(R.string.lychee_pref_comma_place_in),
                        "beside" to stringResource(R.string.lychee_pref_comma_place_beside),
                    ),
                    if (besides) "beside" else "in",
                    onSelect = { place -> commaKey.set(commaKeyValue(extra, place == "beside")) },
                )
            }
        }
    }
    val spacebar = prefs.stringPref(com.android.inputmethod.keyboard.MainKeyboardView.PREF_LYCHEE_SPACEBAR) {
        prefs.getString(com.android.inputmethod.keyboard.MainKeyboardView.PREF_LYCHEE_SPACEBAR, null) ?: "language"
    }
    Group(stringResource(R.string.lychee_pref_spacebar)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_spacebar), stringResource(R.string.lychee_pref_spacebar_summary),
                listOf(
                    "language" to stringResource(R.string.lychee_pref_spacebar_language),
                    "method" to stringResource(R.string.lychee_pref_spacebar_method),
                ),
                spacebar.value, onSelect = spacebar::set,
            )
        }
    }
    CommaMenuGroup(commaKey.value)
    Group(stringResource(R.string.lychee_group_feedback)) {
        if (hasVibrator) {
            row { SwitchRow(it, stringResource(R.string.vibrate_on_keypress), null, vibrate.value, vibrate::set) }
        }
        row { SwitchRow(it, stringResource(R.string.sound_on_keypress), null, sound.value, sound::set) }
        if (sound.value) {
            row {
                RadioChoice(it, stringResource(R.string.lychee_pref_key_sound), keySound.value,
                    app.lychee.ui.KeySounds.STYLES.map { s -> s to stringResource(keySoundName(s)) }) { s ->
                    keySound.set(s)
                }
            }
        }
        if (Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            row { SwitchRow(it, stringResource(R.string.popup_on_keypress), null, popup.value, popup::set) }
        }
        row { SwitchRow(it, stringResource(R.string.voice_input), null, voice.value, voice::set) }
    }
}

/** What the comma's long-press shows: Settings always, then the shortcuts switched on here. */
@Composable
private fun CommaMenuGroup(commaKey: String) {
    val context = LocalContext.current
    val layout = remember(commaKey) { LycheeLayout.get(context) }
    var chosen by remember(commaKey) { mutableStateOf(layout.chosenCommaMenu()) }
    Group(stringResource(R.string.lychee_pref_comma_menu)) {
        row { NavRow(it, stringResource(R.string.lychee_sc_settings), stringResource(R.string.lychee_pref_comma_menu_settings), R.drawable.ic_sym_settings) {} }
        // the chosen shortcuts in their popup order (the arrows move them), then the rest
        val rest = Shortcut.entries.filter { it != Shortcut.SETTINGS && it !in chosen }
        fun save(list: List<Shortcut>) {
            chosen = list
            layout.setCommaMenu(list)
        }
        chosen.forEachIndexed { i, sc ->
            row {
                ReorderSwitchRow(
                    it, stringResource(sc.label), true, sc.icon,
                    onUp = if (i > 0) ({ save(chosen.toMutableList().apply { add(i - 1, removeAt(i)) }) }) else null,
                    onDown = if (i < chosen.size - 1) ({ save(chosen.toMutableList().apply { add(i + 1, removeAt(i)) }) }) else null,
                ) { on -> if (!on) save(chosen - sc) }
            }
        }
        rest.forEach { sc ->
            row {
                IconSwitchRow(it, stringResource(sc.label), null, false, sc.icon) { on ->
                    if (on) save(chosen + sc)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- theme

@Composable
private fun ThemePage() {
    val prefs = prefs()
    val context = LocalContext.current
    val names = context.resources.getStringArray(R.array.keyboard_theme_names)
    val ids = context.resources.getIntArray(R.array.keyboard_theme_ids)
    var selected by remember { mutableStateOf(KeyboardTheme.getKeyboardTheme(context).mThemeId) }

    Text(
        stringResource(R.string.lychee_theme_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    val colors = prefs.stringPref(app.lychee.ui.LycheeColorStyle.PREF) {
        prefs.getString(app.lychee.ui.LycheeColorStyle.PREF, null) ?: app.lychee.ui.LycheeColorStyle.NEUTRAL
    }
    val themeMode = prefs.stringPref(app.lychee.ui.KeyStyle.PREF_THEME_MODE) {
        prefs.getString(app.lychee.ui.KeyStyle.PREF_THEME_MODE, null) ?: app.lychee.ui.KeyStyle.MODE_SYSTEM
    }
    val borders = prefs.boolPref(app.lychee.ui.KeyStyle.PREF_BORDERS) { prefs.getBoolean(app.lychee.ui.KeyStyle.PREF_BORDERS, true) }
    val roundness = prefs.intPref(app.lychee.ui.KeyStyle.PREF_ROUNDNESS) {
        prefs.getInt(app.lychee.ui.KeyStyle.PREF_ROUNDNESS, app.lychee.ui.KeyStyle.DEFAULT_ROUNDNESS)
    }
    Group(stringResource(R.string.lychee_group_appearance)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_theme_mode), stringResource(R.string.lychee_pref_theme_mode_summary),
                listOf(
                    app.lychee.ui.KeyStyle.MODE_SYSTEM to stringResource(R.string.lychee_theme_system),
                    app.lychee.ui.KeyStyle.MODE_LIGHT to stringResource(R.string.lychee_theme_light),
                    app.lychee.ui.KeyStyle.MODE_DARK to stringResource(R.string.lychee_theme_dark),
                    app.lychee.ui.KeyStyle.MODE_TIME to stringResource(R.string.lychee_theme_time),
                ),
                themeMode.value, onSelect = themeMode::set,
            )
        }
    }
    Group(stringResource(R.string.lychee_pref_colors)) {
        listOf(
            app.lychee.ui.LycheeColorStyle.NEUTRAL to R.string.lychee_pref_colors_neutral,
            app.lychee.ui.LycheeColorStyle.WALLPAPER to R.string.lychee_pref_colors_wallpaper,
            app.lychee.ui.LycheeColorStyle.ROSE to R.string.lychee_pref_colors_rose,
            app.lychee.ui.LycheeColorStyle.OCEAN to R.string.lychee_pref_colors_ocean,
            app.lychee.ui.LycheeColorStyle.FOREST to R.string.lychee_pref_colors_forest,
        ).forEach { (id, name) ->
            row { shape -> RadioRow(shape, stringResource(name), colors.value == id) { colors.set(id) } }
        }
    }
    Group(stringResource(R.string.lychee_group_key_style)) {
        row {
            SwitchRow(it, stringResource(R.string.lychee_pref_key_borders), stringResource(R.string.lychee_pref_key_borders_summary),
                borders.value, borders::set)
        }
        row {
            SliderRow(it, stringResource(R.string.lychee_pref_key_roundness), roundness.value, 0..100, 5,
                format = { v -> "$v%" }) { v -> roundness.set(v) }
        }
        names.indices.forEach { i ->
            row { shape ->
                RadioRow(shape, names[i], selected == ids[i]) {
                    selected = ids[i]
                    KeyboardTheme.saveKeyboardThemeId(ids[i], prefs)
                }
            }
        }
    }
    val mode = prefs.stringPref(LycheeLayout.PREF_MODE) { prefs.getString(LycheeLayout.PREF_MODE, null) ?: "normal" }
    val height = prefs.floatPref(LycheeLayout.PREF_HEIGHT_SCALE) { prefs.getFloat(LycheeLayout.PREF_HEIGHT_SCALE, 1f) }
    val columns = prefs.intPref(EmojiPanelView.PREF_COLUMNS) { prefs.getInt(EmojiPanelView.PREF_COLUMNS, 0) }
    Group(stringResource(R.string.lychee_group_size)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_layout), null,
                listOf(
                    "normal" to stringResource(R.string.lychee_pref_layout_normal),
                    "one_handed" to stringResource(R.string.lychee_pref_layout_one_handed),
                ),
                mode.value, onSelect = mode::set,
            )
        }
        row {
            SliderRow(
                it, stringResource(R.string.lychee_pref_height), (height.value * 100).roundToInt(),
                (LycheeLayout.MIN_HEIGHT * 100).roundToInt()..(LycheeLayout.MAX_HEIGHT * 100).roundToInt(), 5,
                format = { v -> "$v%" },
            ) { v -> height.set(v / 100f) }
        }
        row {
            SliderRow(
                it, stringResource(R.string.lychee_pref_emoji_size),
                if (columns.value > 0) columns.value else 9,
                EmojiPanelView.MIN_COLUMNS..EmojiPanelView.MAX_COLUMNS,
            ) { v -> columns.set(v) }
        }
    }
    Text(
        stringResource(R.string.lychee_pref_emoji_size_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
    )
}

@Composable
private fun RadioRow(shape: androidx.compose.ui.graphics.Shape, title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            leadingContent = { RadioButton(selected = selected, onClick = onClick) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

// ---------------------------------------------------------------- text correction

@Composable
private fun CorrectionPage() {
    val prefs = prefs()
    val context = LocalContext.current
    val res = context.resources
    val offensive = prefs.boolPref(Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE) {
        Settings.readBlockPotentiallyOffensive(prefs, res)
    }
    val autoCorrect = prefs.boolPref(Settings.PREF_AUTO_CORRECTION) { Settings.readAutoCorrectEnabled(prefs, res) }
    val showSuggestions = prefs.boolPref(Settings.PREF_SHOW_SUGGESTIONS) {
        prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
    }
    val personalized = prefs.boolPref(Settings.PREF_KEY_USE_PERSONALIZED_DICTS) {
        prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
    }
    val nextWord = prefs.boolPref(Settings.PREF_BIGRAM_PREDICTIONS) {
        prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, true)
    }
    val dictionaryIntent = remember {
        Intent(SystemSettings.ACTION_USER_DICTIONARY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null }
    }

    if (dictionaryIntent != null) {
        Group(stringResource(R.string.lychee_group_dictionary)) {
            row {
                NavRow(it, stringResource(R.string.edit_personal_dictionary)) {
                    context.startActivity(dictionaryIntent)
                }
            }
        }
    }
    Group(stringResource(R.string.lychee_group_suggestions)) {
        row {
            SwitchRow(
                it, stringResource(R.string.prefs_show_suggestions),
                stringResource(R.string.prefs_show_suggestions_summary), showSuggestions.value, showSuggestions::set,
            )
        }
        row {
            SwitchRow(
                it, stringResource(R.string.auto_correction),
                stringResource(R.string.auto_correction_summary), autoCorrect.value, autoCorrect::set,
            )
        }
        row {
            SwitchRow(
                it, stringResource(R.string.bigram_prediction),
                stringResource(R.string.bigram_prediction_summary), nextWord.value, nextWord::set,
            )
        }
        row {
            SwitchRow(
                it, stringResource(R.string.use_personalized_dicts),
                stringResource(R.string.use_personalized_dicts_summary), personalized.value, personalized::set,
            )
        }
        row {
            SwitchRow(
                it, stringResource(R.string.prefs_block_potentially_offensive_title),
                stringResource(R.string.prefs_block_potentially_offensive_summary), offensive.value, offensive::set,
            )
        }
    }
}

// ---------------------------------------------------------------- gesture typing

@Composable
private fun GesturePage() {
    val prefs = prefs()
    val res = LocalContext.current.resources
    val gesture = prefs.boolPref(Settings.PREF_GESTURE_INPUT) { Settings.readGestureInputEnabled(prefs, res) }
    val floating = prefs.boolPref(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT) {
        prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true)
    }
    val trail = prefs.boolPref(Settings.PREF_GESTURE_PREVIEW_TRAIL) {
        prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true)
    }
    Group {
        row {
            SwitchRow(
                it, stringResource(R.string.gesture_input), stringResource(R.string.gesture_input_summary),
                gesture.value, gesture::set,
            )
        }
        row {
            SwitchRow(
                it, stringResource(R.string.gesture_floating_preview_text),
                stringResource(R.string.gesture_floating_preview_text_summary), floating.value, floating::set,
            )
        }
        row { SwitchRow(it, stringResource(R.string.gesture_preview_trail), null, trail.value, trail::set) }
    }
}

// ---------------------------------------------------------------- advanced

@Composable
private fun AdvancedPage() {
    val prefs = prefs()
    val context = LocalContext.current
    val res = context.resources
    val feedback = AudioAndHapticFeedbackManager.getInstance()
    val ms = { value: Int -> res.getString(R.string.abbreviation_unit_milliseconds, value.toString()) }

    val lingerDefault = res.getInteger(R.integer.config_key_preview_linger_timeout).toString()
    val dismissDelay = prefs.stringPref(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY) {
        prefs.getString(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY, null) ?: lingerDefault
    }
    val vibration = prefs.intPref(Settings.PREF_VIBRATION_DURATION_SETTINGS) {
        Settings.readKeypressVibrationDuration(prefs, res)
    }
    val volume = prefs.floatPref(Settings.PREF_KEYPRESS_SOUND_VOLUME) { Settings.readKeypressSoundVolume(prefs, res) }
    val longPress = prefs.intPref(Settings.PREF_KEY_LONGPRESS_TIMEOUT) { Settings.readKeyLongpressTimeout(prefs, res) }
    val split = prefs.boolPref(Settings.PREF_ENABLE_SPLIT_KEYBOARD) {
        prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false)
    }
    val emojiAlt = prefs.boolPref(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY) {
        prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true)
    }

    Group(stringResource(R.string.lychee_group_popups)) {
        if (Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            row {
                ChoiceRow(
                    it, stringResource(R.string.key_preview_popup_dismiss_delay),
                    options = listOf(
                        "0" to stringResource(R.string.key_preview_popup_dismiss_no_delay),
                        lingerDefault to stringResource(R.string.key_preview_popup_dismiss_default_delay),
                    ),
                    selected = dismissDelay.value,
                    onSelect = dismissDelay::set,
                )
            }
        }
        row {
            SliderRow(
                it, stringResource(R.string.prefs_key_longpress_timeout_settings),
                value = longPress.value,
                range = res.getInteger(R.integer.config_min_longpress_timeout)..
                    res.getInteger(R.integer.config_max_longpress_timeout),
                step = res.getInteger(R.integer.config_longpress_timeout_step),
                format = ms,
                onChange = longPress::set,
            )
        }
    }
    Group(stringResource(R.string.lychee_group_feedback)) {
        if (feedback.hasVibrator()) {
            row {
                // -1 means "system default"; the slider starts at 0 and stores real durations.
                SliderRow(
                    it, stringResource(R.string.prefs_keypress_vibration_duration_settings),
                    value = vibration.value.coerceAtLeast(0),
                    range = 0..res.getInteger(R.integer.config_max_vibration_duration),
                    format = ms,
                ) { value ->
                    vibration.set(value)
                    feedback.vibrate(value.toLong())
                }
            }
        }
        row {
            SliderRow(
                it, stringResource(R.string.prefs_keypress_sound_volume_settings),
                value = if (volume.value < 0f) 50 else (volume.value * 100).roundToInt(),
                range = 0..100,
                format = { value -> res.getString(R.string.lychee_percent, value) },
            ) { value ->
                volume.set(value / 100f)
                context.getSystemService(android.media.AudioManager::class.java)
                    .playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, value / 100f)
            }
        }
    }
    Group(stringResource(R.string.lychee_group_other)) {
        row {
            SwitchRow(it, stringResource(R.string.enable_split_keyboard), null, split.value, split::set)
        }
        row {
            SwitchRow(
                it, stringResource(R.string.prefs_enable_emoji_alt_physical_key),
                stringResource(R.string.prefs_enable_emoji_alt_physical_key_summary), emojiAlt.value, emojiAlt::set,
            )
        }
    }
}

// ---------------------------------------------------------------- about

@Composable
private fun AboutPage(openLicences: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }
    Group {
        row { TextRow(it, stringResource(R.string.english_ime_name), stringResource(R.string.lychee_version, version)) }
        row { TextRow(it, stringResource(R.string.lychee_about_base_title), stringResource(R.string.lychee_about_base_body)) }
        row {
            NavRow(it, stringResource(R.string.lychee_licences), stringResource(R.string.lychee_licences_summary),
                R.drawable.ic_sym_license, openLicences)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LicencesPage() {
    val context = LocalContext.current
    val software = remember {
        context.resources.openRawResource(R.raw.lychee_software_licenses).bufferedReader().use { it.readText() }
    }
    val data = remember {
        runCatching {
            context.assets.open("lychee/data-licenses.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    val packs = remember {
        app.lychee.packs.Packs.catalog(context).filter { it.credit.isNotEmpty() }
            .joinToString("\n\n") { "${it.name}\n${it.credit}\n${it.license}" }
    }
    Group {
        row { TextRow(it, stringResource(R.string.lychee_licences_software), software) }
        if (data.isNotEmpty()) {
            row { TextRow(it, stringResource(R.string.lychee_licences_data), data) }
        }
        row { TextRow(it, stringResource(R.string.lychee_licences_packs), packs) }
    }
}

private fun keySoundName(style: String) = when (style) {
    "soft" -> R.string.lychee_key_sound_soft
    "click" -> R.string.lychee_key_sound_click
    "typewriter" -> R.string.lychee_key_sound_typewriter
    "bubble" -> R.string.lychee_key_sound_bubble
    else -> R.string.lychee_key_sound_system
}

/** A titled list of radio choices inside one row. */
@Composable
private fun <T> RadioChoice(shape: androidx.compose.ui.graphics.Shape, title: String, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        options.forEach { (value, label) -> RadioRow(shape, label, value == selected) { onSelect(value) } }
    }
}

/**
 * The comma key setting from its two choices: emoji in the comma ("combined") or beside it,
 * next language beside it or in the comma's popup ("language_comma"), or nothing.
 */
private fun commaKeyValue(extra: String, beside: Boolean) = when (extra) {
    "emoji" -> if (beside) "emoji" else "combined"
    "language" -> if (beside) "language" else "language_comma"
    else -> "none"
}

/** A radio choice with an icon before its name. */
@Composable
private fun IconRadioRow(shape: androidx.compose.ui.graphics.Shape, icon: Int, title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
