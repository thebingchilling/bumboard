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
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
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
    Chinese(R.string.lychee_readings_title),
    Pronunciation(R.string.lychee_pronunciation_title),
    Personal(R.string.lychee_personal_title),
    Preferences(R.string.lychee_typing_title),
    Keys(R.string.lychee_keys_title),
    Theme(R.string.settings_screen_theme),
    Fonts(R.string.lychee_fonts_title),
    Packs(R.string.lychee_addons_title),
    Storage(R.string.lychee_storage_title),
    About(R.string.lychee_about),
    Licences(R.string.lychee_licences),
    Diagnostics(R.string.lychee_diagnostics),
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
        app.lychee.DiagLog.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AudioAndHapticFeedbackManager.init(this)
        SubtypeLocaleUtils.init(this)
        val start = pageOf(intent) ?: Page.Home
        addonOf(intent)
        setContent {
            LycheeTheme { SettingsApp(start, status, requested) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requested.value = pageOf(intent)
        addonOf(intent)
    }

    /** An add-on whose sheet opens with the page (the keyboard asking for its dictionary). */
    private fun addonOf(intent: Intent?) {
        // a download's page, or a store listing's (the handwriting pad's chip opens Google Handwriting's)
        intent?.getStringExtra(EXTRA_ADDON)?.let { id ->
            app.lychee.packs.Packs.byId(this, id)?.let { AddonSheet.open = it }
                ?: app.lychee.store.Store.byId(id)?.let { ProductSheet.open = it }
        }
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
        const val EXTRA_ADDON = "app.lychee.settings.ADDON"
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
    // a setting's choices open on a page of their own over the current one
    var submenu by remember { mutableStateOf<Submenu?>(null) }
    LaunchedEffect(page) { submenu = null }
    val back = {
        val inner = InnerPage.onBack
        if (inner != null) inner()
        else if (submenu != null) submenu = null
        else page = when (page) {
            Page.Licences, Page.Diagnostics -> Page.About
            Page.Storage -> Page.Packs
            else -> Page.Home
        }
    }
    BackHandler(enabled = page != Page.Home || submenu != null || InnerPage.onBack != null) { back() }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    Scaffold(
        containerColor = pageColor(),
        topBar = {
            // Material 3's app bar: one line, the page's title, on the page's own colour
            run {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Text(InnerPage.title ?: submenu?.title ?: stringResource(page.title), style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    navigationIcon = {
                        if (page != Page.Home || submenu != null) {
                            IconButton(onClick = back) {
                                Icon(painterResource(R.drawable.ic_sym_arrow_back), stringResource(R.string.lychee_back))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        // pages (and submenus) slide in from the side they lead to (deeper to the right),
        // fading as they go
        val shownMenu = submenu
        androidx.compose.runtime.CompositionLocalProvider(LocalSubmenu provides { submenu = it }) {
            AnimatedContent(
                targetState = page to shownMenu, label = "page", modifier = Modifier.padding(padding),
                transitionSpec = {
                    val (fromPage, fromMenu) = initialState
                    val (toPage, toMenu) = targetState
                    val deeper = when {
                        toMenu != null && fromMenu == null -> true
                        toMenu == null && fromMenu != null -> false
                        else -> toPage.ordinal > fromPage.ordinal && fromPage != Page.Licences && fromPage != Page.Diagnostics &&
                            fromPage != Page.Storage
                    }
                    // Material's shared X axis: a short slide and a cross-fade
                    val sign = if (deeper) 1 else -1
                    val ease = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(350, easing = ease)) { sign * it / 10 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(210, delayMillis = 90)) +
                        // settling in with a soft spring, from a hair smaller
                        androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow), initialScale = 0.97f))
                        .togetherWith(
                            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(350, easing = ease)) { -sign * it / 10 } +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)),
                        )
                },
            ) { (shown, menu) ->
                // each page counts its own groups (the first has no divider above it)
                val counter = remember(shown, menu) { GroupCounter() }
                androidx.compose.runtime.CompositionLocalProvider(LocalGroupCounter provides counter) {
                Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // a page opened inside this one (an add-on's listing) starts at its top
                        .verticalScroll(remember(shown, menu, InnerPage.title) { androidx.compose.foundation.ScrollState(0) })
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    if (menu != null) {
                        SubmenuPage(menu) { if (submenu === menu) submenu = null }
                    } else when (shown) {
                        Page.Home -> HomePage(status.value) { page = it }
                        Page.Languages -> LanguagesPage()
                        Page.Chinese -> ChinesePage()
                        Page.Pronunciation -> PronunciationPage()
                        Page.Personal -> PersonalDictionaryPage()
                        Page.Preferences -> PreferencesPage()
                        Page.Keys -> KeysPage()
                        Page.Theme -> ThemePage()

                        Page.Fonts -> FontsPage()
                        Page.Packs -> AddonsPage()
                        Page.Storage -> StoragePage()
                        Page.About -> AboutPage({ page = Page.Licences }) { page = Page.Diagnostics }
                        Page.Licences -> LicencesPage()
                        Page.Diagnostics -> DiagnosticsPage()
                    }
                }
                }
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

    // a header: the icon, the name, and a field to try the keyboard in
    // (a Material 2 card: white, 4 dp corners, a soft shadow)
    RaisedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.lychee_icon_background), null,
                        Modifier.requiredSize(84.dp))
                    androidx.compose.foundation.Image(painterResource(R.drawable.lychee_icon_foreground), null,
                        Modifier.requiredSize(84.dp))
                }
                Spacer(Modifier.size(16.dp))
                // the bar above already says Lychee: here, whether it is ready
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (status.enabled && status.current) {
                        Text(stringResource(R.string.lychee_setup_done_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.lychee_setup_done_body), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.lychee_setup_card_title), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (!(status.enabled && status.current)) {
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
            var sample by rememberSaveable { mutableStateOf("") }
            androidx.compose.material3.TextField(
                value = sample,
                onValueChange = { sample = it },
                placeholder = { Text(stringResource(R.string.lychee_try_it)) },
                singleLine = false,
                // Material 3's rounded filled field, like its search bar: no underline, a
                // keyboard at its start
                leadingIcon = { Icon(painterResource(R.drawable.ic_sym_keyboard), null) },
                shape = RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // the add-ons store first, central and inviting: a tinted banner opening it
    StoreBanner { open(Page.Packs) }

    // a few short groups, by what people look for
    Group(stringResource(R.string.lychee_section_languages)) {
        row {
            NavRow(
                it, stringResource(R.string.language_selection_title),
                app.lychee.chinese.ChineseScript.ui(stringResource(R.string.lychee_languages_summary), context), R.drawable.ic_sym_language,
            ) { open(Page.Languages) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_readings_title),
                stringResource(R.string.lychee_readings_summary), R.drawable.ic_sym_translate,
            ) { open(Page.Chinese) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_pronunciation_title),
                stringResource(R.string.lychee_pronunciation_summary), R.drawable.lychee_ic_volume,
            ) { open(Page.Pronunciation) }
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
                it, stringResource(R.string.lychee_typing_title),
                stringResource(R.string.lychee_typing_summary), R.drawable.ic_sym_spellcheck,
            ) { open(Page.Preferences) }
        }
        row {
            NavRow(
                it, stringResource(R.string.lychee_keys_title),
                stringResource(R.string.lychee_keys_summary), R.drawable.ic_sym_keyboard,
            ) { open(Page.Keys) }
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
    RaisedCard(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    // raised, never a flat grey block
    RaisedCard(
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
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
        PageNote(stringResource(R.string.lychee_languages_enable_first))
        return
    }
    val subtypes = remember { (0 until imi.subtypeCount).map { imi.getSubtypeAt(it) } }
    var enabled by remember {
        mutableStateOf(imm.getEnabledInputMethodSubtypeList(imi, true).map { it.hashCode() }.toSet())
    }
    val canSet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    PageNote(stringResource(R.string.lychee_languages_hint))
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
    // one choice, the same as the keyboard's 繁/简 key: the candidates and Lychee's labels
    val script = prefs.stringPref(ChineseInput.PREF_SCRIPT) {
        if (app.lychee.chinese.ChineseScript.candidatesSimplified(prefs)) "simplified" else "traditional"
    }
    val cantonese = prefs.stringPref(ChineseInput.PREF_CANTONESE_INPUT) {
        prefs.getString(ChineseInput.PREF_CANTONESE_INPUT, null) ?: "jyutping"
    }
    val mandarin = prefs.stringPref(Readings.PREF_MANDARIN_SCRIPT) { Readings.mandarinScript(prefs) }
    val meaning = prefs.boolPref(Readings.PREF_MEANING) { prefs.getBoolean(Readings.PREF_MEANING, true) }
    val toneColours = prefs.boolPref(Readings.PREF_TONE_COLOURS) { prefs.getBoolean(Readings.PREF_TONE_COLOURS, true) }
    val jyutpingTones = prefs.stringPref(Readings.PREF_JYUTPING_TONES) { prefs.getString(Readings.PREF_JYUTPING_TONES, null) ?: "Numbers" }
    val yaleTones = prefs.stringPref(Readings.PREF_YALE_TONES) { prefs.getString(Readings.PREF_YALE_TONES, null) ?: "Marks" }
    val pinyinTones = prefs.stringPref(Readings.PREF_PINYIN_TONES) { prefs.getString(Readings.PREF_PINYIN_TONES, null) ?: "Marks" }
    val size = prefs.floatPref(ChineseInput.PREF_CANDIDATE_SIZE) { prefs.getFloat(ChineseInput.PREF_CANDIDATE_SIZE, 1f) }
    var liveSize by remember(size.value) { mutableStateOf(size.value) }
    fun tone(v: String) = runCatching { app.lychee.core.ToneStyle.valueOf(v) }.getOrDefault(app.lychee.core.ToneStyle.Numbers)
    val display = app.lychee.core.DisplayPrefs(
        cantonese = if (cantonese.value == "yale") app.lychee.core.CantoneseRomanization.Yale else app.lychee.core.CantoneseRomanization.Jyutping,
        jyutpingTones = tone(jyutpingTones.value), yaleTones = tone(yaleTones.value), pinyinTones = tone(pinyinTones.value),
        showOtherLanguage = true, showMeaning = meaning.value, simplified = script.value == "simplified",
        // the bars read in pinyin whatever the cards use
        zhuyin = false, pinyin = true,
    )

    PageNote(stringResource(R.string.lychee_readings_intro))
    // the candidate bars as they will look, changing with every choice below
    CandidatePreview(liveSize, display, meaning.value)
    Group(stringResource(R.string.lychee_readings_group_writing)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_script), stringResource(R.string.lychee_readings_script_summary),
                options = listOf(
                    "traditional" to stringResource(R.string.lychee_script_traditional),
                    "simplified" to stringResource(R.string.lychee_script_simplified),
                ),
                selected = script.value, onSelect = script::set,
            )
        }
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_readings_cantonese), stringResource(R.string.lychee_readings_cantonese_summary),
                options = listOf("jyutping" to "Jyutping", "yale" to "Yale"),
                selected = cantonese.value, onSelect = cantonese::set,
            )
        }
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_readings_mandarin), stringResource(R.string.lychee_readings_mandarin_summary),
                options = listOf("pinyin" to "Pinyin", "zhuyin" to "Zhuyin", "both" to stringResource(R.string.lychee_pron_both)),
                selected = mandarin.value, onSelect = mandarin::set,
            )
        }
    }
    Group(stringResource(R.string.lychee_readings_group_tones)) {
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
        row {
            SwitchRow(it, stringResource(R.string.lychee_tone_colours), stringResource(R.string.lychee_tone_colours_summary),
                toneColours.value, toneColours::set)
        }
    }
    Group(stringResource(R.string.lychee_readings_group_shown)) {
        row {
            SwitchRow(it, stringResource(R.string.lychee_show_meaning), stringResource(R.string.lychee_readings_meaning_summary),
                meaning.value, meaning::set)
        }
        row {
            SliderRow(
                it, stringResource(R.string.lychee_candidate_size),
                (size.value * 100).roundToInt(), 70..160, 10, format = { v -> "$v%" },
                onSlide = { v -> liveSize = v / 100f },
            ) { v -> size.set(v / 100f) }
        }
    }
    val fuzzyCmn = prefs.boolPref(ChineseInput.PREF_FUZZY + "cmn") { prefs.getBoolean(ChineseInput.PREF_FUZZY + "cmn", false) }
    val fuzzyYue = prefs.boolPref(ChineseInput.PREF_FUZZY + "yue") { prefs.getBoolean(ChineseInput.PREF_FUZZY + "yue", false) }
    Group(stringResource(R.string.lychee_readings_group_typing)) {
        row {
            SwitchRow(it, stringResource(R.string.lychee_fuzzy_pinyin), stringResource(R.string.lychee_fuzzy_pinyin_summary),
                fuzzyCmn.value, fuzzyCmn::set)
        }
        row {
            SwitchRow(it, stringResource(R.string.lychee_fuzzy_jyutping), stringResource(R.string.lychee_fuzzy_jyutping_summary),
                fuzzyYue.value, fuzzyYue::set)
        }
    }
    val dictionaryHold = prefs.stringPref(ChineseInput.PREF_DICTIONARY_HOLD) {
        prefs.getString(ChineseInput.PREF_DICTIONARY_HOLD, null) ?: "pin"
    }
    val chineseOnKanji = prefs.boolPref(Readings.PREF_CHINESE_ON_KANJI) { prefs.getBoolean(Readings.PREF_CHINESE_ON_KANJI, true) }
    val other = prefs.stringPref(app.lychee.chinese.DictionaryPopup.PREF_OTHER_SCRIPT) {
        prefs.getString(app.lychee.chinese.DictionaryPopup.PREF_OTHER_SCRIPT, null) ?: "hyphens"
    }
    Group(stringResource(R.string.lychee_readings_group_cards)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_readings_hold), stringResource(R.string.lychee_readings_hold_summary),
                listOf(
                    "peek" to stringResource(R.string.lychee_readings_hold_peek),
                    "pin" to stringResource(R.string.lychee_readings_hold_pin),
                ),
                dictionaryHold.value, onSelect = dictionaryHold::set,
            )
        }
        row {
            Column {
            OtherScriptPreview(other.value, script.value == "simplified")
            ChoiceRow(
                it, stringResource(R.string.lychee_readings_other_script), stringResource(R.string.lychee_readings_other_script_summary),
                options = listOf(
                    "hyphens" to stringResource(R.string.lychee_readings_other_script_diff),
                    "full" to stringResource(R.string.lychee_readings_other_script_full),
                    "off" to stringResource(R.string.lychee_readings_other_script_off),
                ),
                selected = other.value, onSelect = other::set,
            )
            }
        }
        row {
            SwitchRow(it, stringResource(R.string.lychee_readings_kanji), stringResource(R.string.lychee_readings_kanji_summary),
                chineseOnKanji.value, chineseOnKanji::set)
        }
    }
}

// ---------------------------------------------------------------- preferences

@Composable
private fun PreferencesPage() {
    val prefs = prefs()
    val context = LocalContext.current
    val autoCap = prefs.boolPref(Settings.PREF_AUTO_CAP) { prefs.getBoolean(Settings.PREF_AUTO_CAP, true) }
    val doubleSpace = prefs.boolPref(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD) {
        prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true)
    }
    val clipboard = prefs.boolPref(app.lychee.clipboard.ClipboardStore.PREF_ENABLED) {
        prefs.getBoolean(app.lychee.clipboard.ClipboardStore.PREF_ENABLED, false)
    }

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
        // off unless turned on; turning it off forgets what was kept
        row {
            SwitchRow(
                it, stringResource(R.string.lychee_pref_clipboard), stringResource(R.string.lychee_pref_clipboard_summary),
                clipboard.value,
            ) { on ->
                clipboard.set(on)
                if (!on) app.lychee.clipboard.ClipboardStore.setEnabled(context, false)
            }
        }
    }
    CorrectionGroups()
    if (Settings.readFromBuildConfigIfGestureInputEnabled(context.resources)) GestureGroup()
}

/** The keys: the number row, the comma's neighbour, the space bar, the comma's menu, and how keys feel. */
@Composable
private fun KeysPage() {
    val prefs = prefs()
    val context = LocalContext.current
    val res = context.resources
    val vibrate = prefs.boolPref(Settings.PREF_VIBRATE_ON) { Settings.readVibrationEnabled(prefs, res) }
    val sound = prefs.boolPref(Settings.PREF_SOUND_ON) { Settings.readKeypressSoundEnabled(prefs, res) }
    val keySound = prefs.stringPref(app.lychee.ui.KeySounds.PREF) {
        prefs.getString(app.lychee.ui.KeySounds.PREF, null) ?: app.lychee.ui.KeySounds.DEFAULT
    }
    val popup = prefs.boolPref(Settings.PREF_POPUP_ON) { Settings.readKeyPreviewPopupEnabled(prefs, res) }
    val voice = prefs.boolPref(Settings.PREF_VOICE_INPUT_KEY) { prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, true) }
    val numberRow = prefs.boolPref(LycheeLayout.PREF_NUMBER_ROW) { prefs.getBoolean(LycheeLayout.PREF_NUMBER_ROW, true) }
    val commaKey = prefs.stringPref(LycheeLayout.PREF_COMMA_KEY) { prefs.getString(LycheeLayout.PREF_COMMA_KEY, null) ?: "combined" }
    val commaHold = prefs.stringPref(LycheeLayout.PREF_COMMA_HOLD) { prefs.getString(LycheeLayout.PREF_COMMA_HOLD, null) ?: LycheeLayout.HOLD_MENU }
    val hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator()
    val spacebar = prefs.stringPref(com.android.inputmethod.keyboard.MainKeyboardView.PREF_LYCHEE_SPACEBAR) {
        prefs.getString(com.android.inputmethod.keyboard.MainKeyboardView.PREF_LYCHEE_SPACEBAR, null) ?: "language"
    }
    val spaceHold = prefs.stringPref(app.lychee.layout.LanguageCard.PREF_HOLD) {
        prefs.getString(app.lychee.layout.LanguageCard.PREF_HOLD, null) ?: app.lychee.layout.LanguageCard.HOLD_TAP
    }
    // the key next to the comma: nothing, emoji or the next language
    val extra = when (commaKey.value) { "emoji" -> "emoji"; "language", "language_comma" -> "language"; else -> "none" }


    // the keys as they will be: the number row, the key by the comma, the spacebar's label
    val themeIds = res.getIntArray(R.array.keyboard_theme_ids)
    KeyboardPreview(
        palette = prefs.getString(app.lychee.ui.LycheeColorStyle.PREF, null) ?: app.lychee.ui.LycheeColorStyle.DEFAULT,
        roundness = prefs.getInt(app.lychee.ui.KeyStyle.PREF_ROUNDNESS, app.lychee.ui.KeyStyle.DEFAULT_ROUNDNESS),
        borders = prefs.getBoolean(app.lychee.ui.KeyStyle.PREF_BORDERS, true),
        raised = KeyboardTheme.getKeyboardTheme(context).mThemeId == themeIds.firstOrNull(),
        height = prefs.getFloat(LycheeLayout.PREF_HEIGHT_SCALE, 1f),
        numberRow = numberRow.value,
        spacebar = stringResource(if (spacebar.value == "method") R.string.lychee_pref_spacebar_method else R.string.lychee_pref_spacebar_language),
        comma = extra,
        voice = voice.value,
    )
    Group(stringResource(R.string.lychee_group_keys)) {
        row {
            SwitchRow(
                it, stringResource(R.string.lychee_pref_number_row),
                stringResource(R.string.lychee_pref_number_row_summary), numberRow.value, numberRow::set,
            )
        }
        row { SwitchRow(it, stringResource(R.string.voice_input), null, voice.value, voice::set) }
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_comma_key), null,
                listOf(
                    "none" to stringResource(R.string.lychee_pref_comma_key_none),
                    "emoji" to stringResource(R.string.lychee_pref_comma_key_emoji),
                    "language" to stringResource(R.string.lychee_pref_comma_key_language),
                ),
                extra,
                icons = listOf(R.drawable.ic_sym_close, R.drawable.ic_sym_mood, R.drawable.ic_sym_language),
                onSelect = { id -> commaKey.set(id) },
            )
        }
        row {
            // what holding the comma does: its popup of shortcuts, or one thing straight away
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_comma_hold), null,
                listOf(
                    LycheeLayout.HOLD_MENU to stringResource(R.string.lychee_pref_comma_hold_menu),
                    LycheeLayout.HOLD_EMOJI to stringResource(R.string.lychee_pref_comma_key_emoji),
                    LycheeLayout.HOLD_LANGUAGE to stringResource(R.string.lychee_pref_comma_key_language),
                    LycheeLayout.HOLD_NONE to stringResource(R.string.lychee_pref_comma_key_none),
                ),
                commaHold.value,
                icons = listOf(R.drawable.ic_sym_apps_outlined, R.drawable.ic_sym_mood, R.drawable.ic_sym_language, R.drawable.ic_sym_close),
                onSelect = { id -> commaHold.set(id) },
            )
        }
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
        row {
            // holding the space bar lists the keyboards: tap one, or slide onto one and let go
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_space_hold), null,
                listOf(
                    app.lychee.layout.LanguageCard.HOLD_TAP to stringResource(R.string.lychee_pref_space_hold_tap),
                    app.lychee.layout.LanguageCard.HOLD_SLIDE to stringResource(R.string.lychee_pref_space_hold_slide),
                ),
                spaceHold.value, onSelect = spaceHold::set,
            )
        }
    }
    Group(stringResource(R.string.lychee_group_feedback)) {
        if (hasVibrator) {
            row { SwitchRow(it, stringResource(R.string.vibrate_on_keypress), null, vibrate.value, vibrate::set) }
        }
        row { SwitchRow(it, stringResource(R.string.sound_on_keypress), null, sound.value, sound::set) }
        if (sound.value) {
            row {
                ChoiceRow(it, stringResource(R.string.lychee_pref_key_sound), null,
                    app.lychee.ui.KeySounds.STYLES.map { st -> st to stringResource(keySoundName(st)) },
                    keySound.value) { st ->
                    keySound.set(st)
                    previewKeySound(context, st)
                }
            }
        }
        if (Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            row { SwitchRow(it, stringResource(R.string.popup_on_keypress), null, popup.value, popup::set) }
        }
    }
    FeelGroups()
    OtherKeysGroup()
}

/** What the comma's long-press shows: the shortcuts switched on here, in order (the first is where it opens), then Settings. */
@Composable
private fun CommaMenuGroup(commaKey: String) {
    val context = LocalContext.current
    val layout = remember(commaKey) { LycheeLayout.get(context) }
    var chosen by remember(commaKey) { mutableStateOf(layout.chosenCommaMenu()) }
    Group(stringResource(R.string.lychee_pref_comma_menu)) {
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
        // Settings is always there, last
        row { NavRow(it, stringResource(R.string.lychee_sc_settings), stringResource(R.string.lychee_pref_comma_menu_settings), R.drawable.ic_sym_settings) {} }
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

    PageNote(stringResource(R.string.lychee_theme_note))
    val colors = prefs.stringPref(app.lychee.ui.LycheeColorStyle.PREF) {
        prefs.getString(app.lychee.ui.LycheeColorStyle.PREF, null) ?: app.lychee.ui.LycheeColorStyle.DEFAULT
    }
    val themeMode = prefs.stringPref(app.lychee.ui.KeyStyle.PREF_THEME_MODE) {
        prefs.getString(app.lychee.ui.KeyStyle.PREF_THEME_MODE, null) ?: app.lychee.ui.KeyStyle.MODE_SYSTEM
    }
    val borders = prefs.boolPref(app.lychee.ui.KeyStyle.PREF_BORDERS) { prefs.getBoolean(app.lychee.ui.KeyStyle.PREF_BORDERS, true) }
    val roundness = prefs.intPref(app.lychee.ui.KeyStyle.PREF_ROUNDNESS) {
        prefs.getInt(app.lychee.ui.KeyStyle.PREF_ROUNDNESS, app.lychee.ui.KeyStyle.DEFAULT_ROUNDNESS)
    }
    val mode = prefs.stringPref(LycheeLayout.PREF_MODE) { prefs.getString(LycheeLayout.PREF_MODE, null) ?: "normal" }
    val height = prefs.floatPref(LycheeLayout.PREF_HEIGHT_SCALE) { prefs.getFloat(LycheeLayout.PREF_HEIGHT_SCALE, 1f) }
    val columns = prefs.intPref(EmojiPanelView.PREF_COLUMNS) { prefs.getInt(EmojiPanelView.PREF_COLUMNS, 0) }
    // what the sliders show while they move, before they are let go
    var liveRound by remember(roundness.value) { mutableStateOf(roundness.value) }
    var liveHeight by remember(height.value) { mutableStateOf(height.value) }
    var liveColumns by remember(columns.value) { mutableStateOf(if (columns.value > 0) columns.value else 9) }

    // the keyboard as it will look, changing with every choice below (scrolls with the page)
    KeyboardPreview(colors.value, liveRound, borders.value, selected == ids.firstOrNull(), liveHeight,
        oneHanded = mode.value == "one_handed", themeMode = themeMode.value)
    Group(stringResource(R.string.lychee_pref_colors)) {
        row {
            PaletteGrid(
                listOf(
                    app.lychee.ui.LycheeColorStyle.LYCHEE to R.string.lychee_pref_colors_lychee,
                    app.lychee.ui.LycheeColorStyle.NEUTRAL to R.string.lychee_pref_colors_neutral,
                    app.lychee.ui.LycheeColorStyle.WALLPAPER to R.string.lychee_pref_colors_wallpaper,
                    app.lychee.ui.LycheeColorStyle.ROSE to R.string.lychee_pref_colors_rose,
                    app.lychee.ui.LycheeColorStyle.OCEAN to R.string.lychee_pref_colors_ocean,
                    app.lychee.ui.LycheeColorStyle.FOREST to R.string.lychee_pref_colors_forest,
                    app.lychee.ui.LycheeColorStyle.LAVENDER to R.string.lychee_pref_colors_lavender,
                    app.lychee.ui.LycheeColorStyle.SUNSET to R.string.lychee_pref_colors_sunset,
                    app.lychee.ui.LycheeColorStyle.MONO to R.string.lychee_pref_colors_mono,
                    app.lychee.ui.LycheeColorStyle.MIDNIGHT to R.string.lychee_pref_colors_midnight,
                    app.lychee.ui.LycheeColorStyle.BLACK to R.string.lychee_pref_colors_black,
                ),
                colors.value,
            ) { id -> colors.set(id) }
        }
    }
    Group(stringResource(R.string.lychee_group_key_style)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_key_shape), null,
                names.indices.map { i -> ids[i] to names[i] }, selected,
            ) { id ->
                selected = id
                KeyboardTheme.saveKeyboardThemeId(id, prefs)
            }
        }
        row {
            SwitchRow(it, stringResource(R.string.lychee_pref_key_borders), stringResource(R.string.lychee_pref_key_borders_summary),
                borders.value, borders::set)
        }
        row {
            SliderRow(it, stringResource(R.string.lychee_pref_key_roundness), roundness.value, 0..100, 5,
                format = { v -> "$v%" }, onSlide = { v -> liveRound = v }) { v -> roundness.set(v) }
        }
        row {
            SliderRow(
                it, stringResource(R.string.lychee_pref_height), (height.value * 100).roundToInt(),
                (LycheeLayout.MIN_HEIGHT * 100).roundToInt()..(LycheeLayout.MAX_HEIGHT * 100).roundToInt(), 5,
                format = { v -> "$v%" }, onSlide = { v -> liveHeight = v / 100f },
            ) { v -> height.set(v / 100f) }
        }
    }
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
            Column {
                SliderRow(
                    it, stringResource(R.string.lychee_pref_emoji_size), liveColumns,
                    EmojiPanelView.MIN_COLUMNS..EmojiPanelView.MAX_COLUMNS,
                    onSlide = { v -> liveColumns = v },
                ) { v -> columns.set(v) }
                // the emoji at that size
                EmojiPreview(liveColumns)
            }
        }
    }
    // dark mode on a schedule: the times every half hour in the phone's own clock format, and
    // today's sunset and sunrise where the phone's time zone is
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    fun label(m: Int): String = timeFormat.format(java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, m / 60); set(java.util.Calendar.MINUTE, m % 60)
    }.time)
    val times = remember { (0 until 48).map { it * 30 }.map { it to label(it) } }
    val from = prefs.intPref(app.lychee.ui.KeyStyle.PREF_DARK_FROM) {
        prefs.getInt(app.lychee.ui.KeyStyle.PREF_DARK_FROM, app.lychee.ui.KeyStyle.DEFAULT_DARK_FROM)
    }
    val to = prefs.intPref(app.lychee.ui.KeyStyle.PREF_DARK_TO) {
        prefs.getInt(app.lychee.ui.KeyStyle.PREF_DARK_TO, app.lychee.ui.KeyStyle.DEFAULT_DARK_TO)
    }
    val sun = remember { app.lychee.ui.DarkSchedule.sunTimes(context) }
    val sunTitle = stringResource(R.string.lychee_sun_today)
    val sunText = if (sun == null) stringResource(R.string.lychee_sun_polar)
        else stringResource(R.string.lychee_sun_times, label(sun.second), label(sun.first))
    Group(stringResource(R.string.lychee_group_appearance)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pref_theme_mode), stringResource(R.string.lychee_pref_theme_mode_summary),
                listOfNotNull(
                    app.lychee.ui.KeyStyle.MODE_SYSTEM to stringResource(R.string.lychee_theme_system),
                    app.lychee.ui.KeyStyle.MODE_LIGHT to stringResource(R.string.lychee_theme_light),
                    app.lychee.ui.KeyStyle.MODE_DARK to stringResource(R.string.lychee_theme_dark),
                    app.lychee.ui.KeyStyle.MODE_SUN to stringResource(R.string.lychee_theme_sun),
                    app.lychee.ui.KeyStyle.MODE_SCHEDULE to stringResource(R.string.lychee_theme_schedule),
                    // only for those who chose it before set times existed
                    (app.lychee.ui.KeyStyle.MODE_TIME to stringResource(R.string.lychee_theme_time))
                        .takeIf { themeMode.value == app.lychee.ui.KeyStyle.MODE_TIME },
                ),
                themeMode.value, onSelect = themeMode::set,
            )
        }
        if (themeMode.value == app.lychee.ui.KeyStyle.MODE_SCHEDULE) {
            row { ChoiceRow(it, stringResource(R.string.lychee_dark_from), null, times, from.value, onSelect = from::set) }
            row { ChoiceRow(it, stringResource(R.string.lychee_dark_to), null, times, to.value, onSelect = to::set) }
        }
        if (themeMode.value == app.lychee.ui.KeyStyle.MODE_SUN) {
            row { TextRow(it, sunTitle, sunText) }
        }
    }
}

/**
 * The palettes as a grid of swatches, like the phone's wallpaper colours: each a circle of its
 * background, keys and accent, its name under it; the chosen one ringed and ticked.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PaletteGrid(palettes: List<Pair<String, Int>>, chosen: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        palettes.forEach { (id, name) ->
            val swatch = remember(id) { app.lychee.ui.LycheeColorStyle.swatch(context, id) }
            val selected = id == chosen
            val ring = MaterialTheme.colorScheme.primary
            Column(
                Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPick(id) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
                    val r = size.minDimension / 2
                    if (selected) drawCircle(ring, r)
                    val inner = if (selected) r - 4.dp.toPx() else r
                    // the background, with the keys' colour in the lower half and the accent as a dot
                    drawCircle(androidx.compose.ui.graphics.Color(swatch[0]), inner)
                    drawArc(androidx.compose.ui.graphics.Color(swatch[1]), 0f, 180f, true,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - inner, center.y - inner),
                        size = androidx.compose.ui.geometry.Size(inner * 2, inner * 2))
                    drawCircle(androidx.compose.ui.graphics.Color(swatch[3]), inner * 0.32f)
                }
                Text(
                    stringResource(name), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** A palette to choose: its name, and a swatch of its colours (background, keys, accent). */
@Composable
private fun PaletteRow(shape: androidx.compose.ui.graphics.Shape, title: String, style: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // in the phone's light or dark, like these settings
    val swatch = remember(style) { app.lychee.ui.LycheeColorStyle.swatch(context, style) }
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        CompactRow(
            null, title,
            leading = { RadioButton(selected = selected, onClick = onClick) },
            trailing = {
                androidx.compose.foundation.Canvas(Modifier.size(width = 80.dp, height = 26.dp)) {
                    val h = size.height
                    // the background as a pill, with a letter key, a functional key and the accent on it
                    drawRoundRect(androidx.compose.ui.graphics.Color(swatch[0]),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
                    val r = h * 0.32f
                    listOf(swatch[1], swatch[2], swatch[3]).forEachIndexed { i, c ->
                        drawCircle(androidx.compose.ui.graphics.Color(c), r,
                            androidx.compose.ui.geometry.Offset(h * 0.55f + i * (r * 2.4f), h / 2))
                    }
                }
            },
        )
    }
}

@Composable
private fun RadioRow(shape: androidx.compose.ui.graphics.Shape, title: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        CompactRow(null, title, leading = { RadioButton(selected = selected, onClick = onClick) })
    }
}

// ---------------------------------------------------------------- text correction

@Composable
private fun CorrectionGroups() {
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

    // (your own words are in Settings > Personal dictionary; the phone's old word list is not repeated here)
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
private fun GestureGroup() {
    val prefs = prefs()
    val res = LocalContext.current.resources
    val gesture = prefs.boolPref(Settings.PREF_GESTURE_INPUT) { Settings.readGestureInputEnabled(prefs, res) }
    val floating = prefs.boolPref(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT) {
        prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true)
    }
    val trail = prefs.boolPref(Settings.PREF_GESTURE_PREVIEW_TRAIL) {
        prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true)
    }
    Group(stringResource(R.string.settings_screen_gesture)) {
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

/** How the keys feel: how long a hold takes, the key popup, and the strength of sound and vibration. */
@Composable
private fun FeelGroups() {
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

    Group(stringResource(R.string.lychee_group_timing)) {
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
}

/** Plays [style]'s key sound, a letter then the space bar, once it has loaded. */
private fun previewKeySound(context: Context, style: String) {
    app.lychee.ui.KeySounds.init(context)
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    if (style == app.lychee.ui.KeySounds.SYSTEM) {
        context.getSystemService(android.media.AudioManager::class.java)?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        return
    }
    main.postDelayed({ app.lychee.ui.KeySounds.play(0, -1f) }, 350)
    main.postDelayed({ app.lychee.ui.KeySounds.play(1, -1f) }, 520)
}

/** The keyboard's other options: split keyboard on a tablet, emoji from a physical keyboard. */
@Composable
private fun OtherKeysGroup() {
    val prefs = prefs()
    val split = prefs.boolPref(Settings.PREF_ENABLE_SPLIT_KEYBOARD) {
        prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false)
    }
    val emojiAlt = prefs.boolPref(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY) {
        prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true)
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

// ---------------------------------------------------------------- pronunciation

/**
 * The voice each language is spoken in (the phone's text-to-speech voices that work offline):
 * the engine's own choice, or one picked here, female or male where the voice's name says so.
 * Picking one says a sample in it.
 */
@Composable
private fun VoicesGroup(mandarinTaiwan: Boolean, english: String) {
    val context = LocalContext.current
    val prefs = prefs()
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        // Google's engine when it is installed (as the keyboard speaks with), else the phone's
        val engine = app.lychee.speech.Speech.newEngine(context) { status ->
            ready = status == android.speech.tts.TextToSpeech.SUCCESS
        }
        tts = engine
        onDispose { engine.shutdown() }
    }
    val languages = listOf(
        "yue-HK" to R.string.lychee_language_cantonese,
        (if (mandarinTaiwan) "zh-TW" else "zh-CN") to R.string.lychee_language_mandarin,
        "ja-JP" to R.string.lychee_language_japanese,
        "ko-KR" to R.string.lychee_language_korean,
        (if (english == app.lychee.speech.Pronunciation.UK) "en-GB" else "en-US") to R.string.lychee_storage_english,
    )
    val samples = mapOf("yue-HK" to "你好，歡迎", "zh-CN" to "你好，欢迎", "zh-TW" to "你好，歡迎",
        "ja-JP" to "こんにちは", "ko-KR" to "안녕하세요", "en-US" to "Hello, welcome", "en-GB" to "Hello, welcome")
    Group(stringResource(R.string.lychee_voices_title)) {
        val engine = tts
        if (!ready || engine == null) {
            row { TextRow(it, stringResource(R.string.lychee_voices_title), stringResource(R.string.lychee_voices_loading)) }
            return@Group
        }
        for ((tag, name) in languages) {
            row {
                val voices = remember(tag) { app.lychee.speech.Speech.voicesFor(engine, tag) }
                val key = app.lychee.speech.Speech.PREF_VOICE + tag
                val pref = prefs.stringPref(key) { prefs.getString(key, null) ?: "" }
                // relabelled once a voice is measured or renamed
                var labels by remember(tag) { mutableStateOf(0) }
                Column {
                ChoiceRow(
                    it, stringResource(name),
                    if (voices.isEmpty()) stringResource(R.string.lychee_voices_none) else stringResource(R.string.lychee_voices_summary),
                    listOf("" to stringResource(R.string.lychee_voices_auto)) +
                        voices.mapIndexed { i, v -> v.name to (labels.let { app.lychee.speech.Speech.voiceLabel(context, v, i) }) },
                    pref.value,
                    // open while voices are tried: each tap says the sample in that voice
                    keepOpen = true,
                ) { picked ->
                    pref.set(picked)
                    val voice = voices.firstOrNull { v -> v.name == picked }
                    if (voice != null && prefs.getFloat(app.lychee.speech.Speech.PREF_VOICE_HZ + voice.name, 0f) <= 0f) {
                        // first time: measured (how high or low it sounds), then said
                        app.lychee.speech.Speech.measure(context, engine, voice, samples[tag] ?: "") {
                            labels++
                            sayIn(engine, voice, tag, samples[tag] ?: "", context)
                        }
                    } else sayIn(engine, voice, tag, samples[tag] ?: "", context)
                }
                // the chosen voice's own name, pitch and speed
                voices.firstOrNull { v -> v.name == pref.value }?.let { voice ->
                    VoiceTuning(it, engine, voice, tag, samples[tag] ?: "")
                }
                }
            }
        }
    }
}

/** Says [text] in [voice] (or the language's default), with the voice's own pitch and speed. */
private fun sayIn(engine: android.speech.tts.TextToSpeech, voice: android.speech.tts.Voice?, tag: String, text: String,
                  context: android.content.Context) {
    runCatching {
        if (voice != null) engine.voice = voice else engine.language = java.util.Locale.forLanguageTag(tag)
        app.lychee.speech.Speech.tune(context, engine, voice)
        engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "lychee-sample")
    }
}

/**
 * A chosen voice made one's own: a name for it, its pitch and speed (both kept per voice and used
 * wherever Lychee speaks), and a button to hear it.
 */
@Composable
private fun VoiceTuning(shape: androidx.compose.ui.graphics.Shape, engine: android.speech.tts.TextToSpeech,
                        voice: android.speech.tts.Voice, tag: String, sample: String) {
    val context = LocalContext.current
    val prefs = prefs()
    val S = app.lychee.speech.Speech
    var name by remember(voice.name) { mutableStateOf(prefs.getString(S.PREF_VOICE_NAME + voice.name, null).orEmpty()) }
    var pitch by remember(voice.name) { mutableStateOf(prefs.getInt(S.PREF_VOICE_PITCH + voice.name, 100)) }
    var rate by remember(voice.name) { mutableStateOf(prefs.getInt(S.PREF_VOICE_RATE + voice.name, 100)) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = name,
            onValueChange = { v -> name = v.take(30); prefs.edit().putString(S.PREF_VOICE_NAME + voice.name, name).apply() },
            label = { Text("Your name for this voice") },
            placeholder = { Text(S.voiceCode(voice)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Pitch ${pitch}%", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = pitch.toFloat(), valueRange = 50f..200f, steps = 14,
            onValueChange = { pitch = it.roundToInt() },
            onValueChangeFinished = { prefs.edit().putInt(S.PREF_VOICE_PITCH + voice.name, pitch).apply(); sayIn(engine, voice, tag, sample, context) },
        )
        Text("Speed ${rate}%", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = rate.toFloat(), valueRange = 50f..200f, steps = 14,
            onValueChangeFinished = { prefs.edit().putInt(S.PREF_VOICE_RATE + voice.name, rate).apply(); sayIn(engine, voice, tag, sample, context) },
            onValueChange = { rate = it.roundToInt() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { sayIn(engine, voice, tag, sample, context) }) { Text("Play") }
            TextButton(onClick = {
                pitch = 100; rate = 100
                prefs.edit().remove(S.PREF_VOICE_PITCH + voice.name).remove(S.PREF_VOICE_RATE + voice.name).apply()
            }) { Text("Reset") }
        }
    }
}

/** How each language is said: Mandarin's region, English's, and the word card's voice. */
@Composable
private fun PronunciationPage() {
    val prefs = prefs()
    val P = app.lychee.speech.Pronunciation
    val mandarin = prefs.stringPref(P.PREF_MANDARIN) { prefs.getString(P.PREF_MANDARIN, null) ?: P.MAINLAND }
    val english = prefs.stringPref(P.PREF_ENGLISH) { prefs.getString(P.PREF_ENGLISH, null) ?: P.BOTH }
    val speak = prefs.stringPref(app.lychee.chinese.DictionaryPopup.PREF_SPEAK_LANG) {
        prefs.getString(app.lychee.chinese.DictionaryPopup.PREF_SPEAK_LANG, null) ?: "auto"
    }
    PageNote(stringResource(R.string.lychee_pronunciation_note))
    // how the choices read, changing as they are made
    PronunciationPreview(mandarin.value == P.TAIWAN, english.value)
    Group(stringResource(R.string.lychee_language_mandarin)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pron_region), stringResource(R.string.lychee_pron_mandarin_summary),
                listOf(
                    P.MAINLAND to stringResource(R.string.lychee_pron_mainland),
                    P.TAIWAN to stringResource(R.string.lychee_pron_taiwan),
                ),
                mandarin.value,
                onSelect = { v ->
                    mandarin.set(v)
                    app.lychee.speech.Speech.mandarinTaiwan = v == P.TAIWAN
                },
            )
        }
    }
    Group(stringResource(R.string.lychee_pron_english)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_pron_region), stringResource(R.string.lychee_pron_english_summary),
                listOf(
                    P.BOTH to stringResource(R.string.lychee_pron_both),
                    P.US to stringResource(R.string.lychee_pron_us),
                    P.UK to stringResource(R.string.lychee_pron_uk),
                ),
                english.value, onSelect = english::set,
            )
        }
    }
    VoicesGroup(mandarin.value == P.TAIWAN, english.value)
    Group(stringResource(R.string.lychee_pron_cards)) {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_card_speak_language), stringResource(R.string.lychee_card_speak_summary),
                options = listOf(
                    "auto" to stringResource(R.string.lychee_card_speak_auto),
                    "yue" to stringResource(R.string.lychee_card_cantonese),
                    "cmn" to stringResource(R.string.lychee_card_mandarin),
                ),
                selected = speak.value,
                onSelect = speak::set,
            )
        }
    }
}

// ---------------------------------------------------------------- about

@Composable
private fun AboutPage(openLicences: () -> Unit, openDiagnostics: () -> Unit) {
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
        row {
            NavRow(it, stringResource(R.string.lychee_diagnostics), stringResource(R.string.lychee_diagnostics_summary),
                R.drawable.ic_sym_settings, openDiagnostics)
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * What the keyboard's engines did lately (packs unpacked, Rime started, schemas chosen,
 * failures), to share when something doesn't work.
 */
@Composable
private fun DiagnosticsPage() {
    val context = LocalContext.current
    var text by remember { mutableStateOf(app.lychee.DiagLog.read(context)) }
    var crash by remember { mutableStateOf(app.lychee.CrashLog.read(context)) }
    val packs = remember {
        app.lychee.packs.Packs.catalog(context).filter { app.lychee.packs.Packs.isInstalled(context, it.id) }
            .joinToString("\n") { "${it.id} ${app.lychee.packs.Packs.installedVersion(context, it.id)}" }
    }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
    // handwriting samples are no longer collected: any kept from before are deleted, with their switch
    remember {
        app.lychee.handwriting.HandwritingSamples.clear(context)
        @Suppress("DEPRECATION") PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(app.lychee.handwriting.HandwritingSamples.PREF).apply()
    }
    // errors only: what failed (the log's lines that say so), then the last crash
    val errors = text.lines().filter { l -> listOf("fail", "error", "exception", "can't", "couldn't", "unavailable")
        .any { l.contains(it, ignoreCase = true) } }
    val report = "Lychee $version\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
        "Installed packs:\n$packs\n\n" + (crash?.let { "Last crash:\n$it\n\n" } ?: "") + "Log:\n$text"
    PageNote(stringResource(R.string.lychee_diagnostics_body))
    // a summary first: all well, or how many errors
    RaisedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    crash != null -> "Lychee crashed recently"
                    errors.isEmpty() -> "No errors"
                    else -> "${errors.size} error${if (errors.size == 1) "" else "s"}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (crash != null || errors.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text("Lychee $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            errors.takeLast(5).forEach { e ->
                Text(e, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }) { Text(stringResource(R.string.lychee_crash_share)) }
        FilledTonalButton(onClick = {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lychee report", report))
        }) { Text(stringResource(R.string.lychee_crash_copy)) }
        TextButton(onClick = {
            app.lychee.DiagLog.clear(context); app.lychee.CrashLog.clear(context); text = ""; crash = null
        }) { Text(stringResource(R.string.lychee_diagnostics_clear)) }
    }
    RaisedCard {
        Text(
            report,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LicencesPage() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val software = remember {
        context.resources.openRawResource(R.raw.lychee_software_licenses).bufferedReader().use { it.readText() }
    }
    val data = remember {
        runCatching { context.assets.open("lychee/data-licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val packs = remember {
        app.lychee.packs.Packs.catalog(context).filter { it.credit.isNotEmpty() }
            .map { it.name to "${it.credit}\n${it.license}" }
    }
    // a thank-you first: Lychee stands on open source and open data
    RaisedCard(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Built on open source and open data", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lychee exists thanks to the people who share their work: Android's keyboard, the Rime engine, " +
                    "dictionaries written by volunteers, open fonts. Each is listed here with its licence. " +
                    "Lychee's own dictionary data is shared under CC BY-SA 4.0, as its sources ask.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    LicenceGroup("Software", licenceEntries(software))
    LicenceGroup("Google ML Kit, in Lychee handwriting", listOf(
        "Google ML Kit Digital Ink Recognition" to "Used under Google's ML Kit terms, in the separate Lychee handwriting " +
            "app (its licence keeps it in an app of its own). Its models are downloaded from Google, never copied into Lychee.",
    ))
    if (data.isNotEmpty()) LicenceGroup("Dictionary data", licenceEntries(data))
    LicenceGroup("Add-on packs", packs)
    Spacer(Modifier.height(24.dp))
}

/** A titled group of licence entries: each its name, then where it comes from and its licence. */
@Composable
private fun LicenceGroup(title: String, entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    GroupHeader(title)
    entries.forEach { (name, body) ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    SectionDivider()
}

/**
 * The entries of a licences text ("  * Name — https://… — Licence. (c) …", with indented
 * continuation lines): each as its name and the rest.
 */
private fun licenceEntries(text: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, StringBuilder>>()
    for (line in text.lines()) {
        val t = line.trimEnd()
        when {
            t.trimStart().startsWith("* ") -> {
                val item = t.trimStart().removePrefix("* ")
                val cut = listOf(" — ", " (").map { item.indexOf(it) }.filter { it > 0 }.minOrNull()
                val name = if (cut != null) item.substring(0, cut) else item
                val rest = if (cut != null) item.substring(cut).removePrefix(" — ").trim() else ""
                out += name to StringBuilder(rest)
            }
            t.startsWith("    ") && out.isNotEmpty() -> out.last().second.append(" ").append(t.trim())
            else -> {}
        }
    }
    return out.map { (n, b) -> n to b.toString().trim() }
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
