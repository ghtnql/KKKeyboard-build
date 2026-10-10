package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.painterResource
import kkkeyboard.sharedui.generated.resources.Res
import kkkeyboard.sharedui.generated.resources.theme_preview_basic_light
import kkkeyboard.sharedui.generated.resources.theme_preview_basic_dark
import kkkeyboard.sharedui.generated.resources.theme_preview_seoul_day
import kkkeyboard.sharedui.generated.resources.theme_preview_seoul_night
import kkkeyboard.sharedui.generated.resources.theme_taegeuk_artwork

private enum class Page { ONBOARDING, HOME, SETTINGS, THEMES, ADVANCED_SETTINGS, PHRASES, PHRASE_EDITOR, TEST, JAMO, SELECT, PRACTICE, RAIN, CAFE, FINISHING, RESULT, PROGRESS }
private enum class Activity { BASIC, SENTENCE, CONVERT, RAIN, CAFE }


@Composable
fun KKKeyboardApp(
    platform: AppPlatform,
    settingsNavigationRequest: Int = 0,
    themeNavigationRequest: Int = 0,
    openAdvancedSettings: Boolean = false,
    advancedSettingsContent: (@Composable () -> Unit)? = null,
    keyboardExtensionMode: Boolean = false,
    standaloneSettingsMode: Boolean = false,
    onCloseSettings: (() -> Unit)? = null,
) {
    var uiLanguage by remember(platform) { mutableStateOf(UiLanguage.fromTag(platform.readUiLanguage())) }
    val strings = remember(uiLanguage) { UiStrings(uiLanguage) }
    var page by remember { mutableStateOf(if (keyboardExtensionMode) { if (themeNavigationRequest > 0) Page.THEMES else Page.SETTINGS } else if (openAdvancedSettings && advancedSettingsContent != null) Page.ADVANCED_SETTINGS else if (themeNavigationRequest > 0) Page.THEMES else if (settingsNavigationRequest > 0) Page.SETTINGS else if (platform.keyboardStatus == KeyboardStatus.ACTIVE) Page.HOME else Page.ONBOARDING) }
    var lastHandledSettingsRequest by remember { mutableIntStateOf(settingsNavigationRequest) }
    var lastHandledThemeRequest by remember { mutableIntStateOf(themeNavigationRequest) }
    LaunchedEffect(settingsNavigationRequest) {
        if (settingsNavigationRequest > lastHandledSettingsRequest) page = Page.SETTINGS
        lastHandledSettingsRequest = settingsNavigationRequest
    }
    LaunchedEffect(themeNavigationRequest) {
        if (themeNavigationRequest > lastHandledThemeRequest) page = Page.THEMES
        lastHandledThemeRequest = themeNavigationRequest
    }
    var activity by remember { mutableStateOf(Activity.BASIC) }
    var mode by remember { mutableStateOf(PracticeMode.KOREAN) }
    var difficulty by remember { mutableStateOf(CafeDifficulty.NORMAL) }
    var status by remember { mutableStateOf(platform.keyboardStatus) }
    var progress by remember { mutableStateOf(platform.learningProgress) }
    var testText by remember { mutableStateOf(platform.keyboardTestText) }
    var answer by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<TypingSessionResult?>(null) }
    var resultRecorded by remember { mutableStateOf(false) }
    var sessionGeneration by remember { mutableIntStateOf(0) }
    var practice by remember { mutableStateOf<PracticeSession?>(null) }
    var rain by remember { mutableStateOf<RainSession?>(null) }
    var cafe by remember { mutableStateOf<CafeSession?>(null) }
    var cafeReaction by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val translationCatalog = remember(platform.learningItems) { PracticeTranslationCatalog(platform.learningItems) }
    var startedAt by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var phrases by remember { mutableStateOf(platform.readUserPhrases()) }
    var editingPhrase by remember { mutableStateOf<UserPhraseEntry?>(null) }
    var phraseTitle by remember { mutableStateOf("") }
    var phraseContent by remember { mutableStateOf("") }
    var phraseError by remember { mutableStateOf("") }
    var confirmPhraseDelete by remember { mutableStateOf(false) }
    var layoutOptions by remember { mutableStateOf(LayoutOrientation.entries.associateWith(platform::readLayoutOptions)) }
    var inputLayout by remember { mutableStateOf(platform.readInputLayout()) }
    var hapticFeedbackEnabled by remember { mutableStateOf(platform.readHapticFeedbackEnabled()) }
    var flickDistance by remember { mutableIntStateOf(platform.readFlickDistance().coerceIn(12, 32)) }
    var cycleTimeout by remember { mutableIntStateOf(platform.readCheonjiinCycleTimeout().coerceIn(400, 1600)) }
    var inputLayoutMenuExpanded by remember { mutableStateOf(false) }
    var gameAudioSettings by remember { mutableStateOf(platform.readGameAudioSettings()) }
    var showGameSoundSettings by remember { mutableStateOf(false) }
    var keyboardTheme by remember { mutableStateOf(platform.readKeyboardTheme()) }
    var seoulUnlockRemaining by remember { mutableStateOf(platform.seoulThemeUnlockRemainingMillis()) }
    var seoulAdPending by remember { mutableStateOf(platform.isSeoulThemeAdPending()) }
    var seoulAdMessage by remember { mutableStateOf(platform.seoulThemeAdMessage()) }
    var privacyOptionsRequired by remember(platform) { mutableStateOf(platform.isAdPrivacyOptionsRequired()) }
    var seoulAdChoice by remember { mutableStateOf<KeyboardThemeChoice?>(null) }

    LaunchedEffect(platform) {
        // Poll on every page: rewards, expired unlocks, and app resume affect the entire app.
        while (true) {
            privacyOptionsRequired = platform.isAdPrivacyOptionsRequired()
            keyboardTheme = platform.readKeyboardTheme()
            seoulUnlockRemaining = platform.seoulThemeUnlockRemainingMillis()
            seoulAdPending = platform.isSeoulThemeAdPending()
            seoulAdMessage = platform.seoulThemeAdMessage()
            delay(1_000)
        }
    }

    DisposableEffect(platform) { onDispose { if (!keyboardExtensionMode) platform.stopGameAudio() } }
    fun record(finalResult: TypingSessionResult, naturallyCompleted: Boolean = false) {
        if (resultRecorded) return
        if (!keyboardExtensionMode) {
            if (naturallyCompleted && finalResult.completed) platform.finishGameAudio()
            else platform.stopGameAudio()
        }
        resultRecorded = true
        result = finalResult
        progress = progress.record(finalResult)
        platform.saveLearningProgress(progress)
        page = if (naturallyCompleted && finalResult.completed && !keyboardExtensionMode) Page.FINISHING else Page.RESULT
    }
    fun selectedItems(): List<LearningItem> = if (activity == Activity.CAFE) {
        platform.learningItems.forCafe(mode, difficulty)
    } else {
        platform.learningItems.forMode(mode, when (activity) {
            Activity.BASIC -> "typing"; Activity.SENTENCE -> "sentence"; Activity.CONVERT -> "convert"
            Activity.RAIN -> "rain"; Activity.CAFE -> "cafe"
        })
    }
    fun start() {
        val items = selectedItems()
        if (items.isEmpty()) { feedback = strings.noSentencesForMode; return }
        if (!keyboardExtensionMode) platform.prepareGameEndAd()
        sessionGeneration++
        answer = ""; feedback = ""; revision = 0; resultRecorded = false; startedAt = TimeSource.Monotonic.markNow()
        practice = null; rain = null; cafe = null; cafeReaction = null
        when (activity) {
            Activity.BASIC -> { practice = PracticeSession(mode, PracticeStyle.BASIC, items.shuffled().take(10)); page = Page.PRACTICE }
            Activity.SENTENCE -> { practice = PracticeSession(mode, PracticeStyle.SENTENCE, items.shuffled().take(10)); page = Page.PRACTICE }
            Activity.CONVERT -> { practice = PracticeSession(mode, PracticeStyle.CONVERT, items.shuffled().take(10)); page = Page.PRACTICE }
            Activity.RAIN -> { rain = RainSession(mode, items.shuffled()); page = Page.RAIN }
            Activity.CAFE -> { cafe = CafeSession(mode, items.shuffled(), difficulty); page = Page.CAFE }
        }
        if (!keyboardExtensionMode) {
            platform.startGameAudio(when (activity) { Activity.RAIN -> GameMusicTrack.RAIN; Activity.CAFE -> GameMusicTrack.CAFE; else -> GameMusicTrack.PRACTICE })
            platform.playGameSound(GameSoundEffect.START)
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun dismissKeyboard() {
        // The extension is the host's keyboard, not an app-owned input field.
        // Hiding it here ends the host edit session when opening or touching settings.
        if (keyboardExtensionMode) return
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    fun back() {
        showGameSoundSettings = false
        if (!keyboardExtensionMode) platform.stopGameAudio()
        dismissKeyboard()
        if ((keyboardExtensionMode || standaloneSettingsMode) && page == Page.SETTINGS) {
            feedback = ""
            onCloseSettings?.invoke()
            return
        }
        page = when (page) {
            Page.PHRASE_EDITOR -> Page.PHRASES
            Page.PHRASES -> Page.SETTINGS
            Page.ADVANCED_SETTINGS, Page.THEMES -> Page.SETTINGS
            Page.PRACTICE, Page.RAIN, Page.CAFE, Page.FINISHING, Page.RESULT -> Page.SELECT
            else -> Page.HOME
        }
        feedback = ""
    }
    SystemBackHandler(enabled = page != Page.HOME && page != Page.ONBOARDING, onBack = ::back)
    fun openPhraseEditor(phrase: UserPhraseEntry?) {
        editingPhrase = phrase
        phraseTitle = phrase?.title.orEmpty()
        phraseContent = phrase?.content.orEmpty()
        phraseError = ""
        page = Page.PHRASE_EDITOR
    }

    LaunchedEffect(page) {
        if (page != Page.PRACTICE && page != Page.RAIN && page != Page.CAFE) showGameSoundSettings = false
        if (!keyboardExtensionMode && page == Page.JAMO) {
            platform.startGameAudio(GameMusicTrack.PRACTICE)
            platform.playGameSound(GameSoundEffect.START)
        }
        if (!keyboardExtensionMode && page != Page.JAMO && page != Page.PRACTICE && page != Page.RAIN && page != Page.CAFE && page != Page.FINISHING && page != Page.RESULT) platform.stopGameAudio()
        if (page != Page.PRACTICE && page != Page.RAIN && page != Page.CAFE) dismissKeyboard()
        if (page == Page.FINISHING) {
            val finishingGeneration = sessionGeneration
            val presented = try {
                platform.requestGameEndAd {
                    if (page == Page.FINISHING && sessionGeneration == finishingGeneration) page = Page.RESULT
                }
            } catch (_: Exception) { false }
            if (!presented && page == Page.FINISHING) page = Page.RESULT
        }
        if (page == Page.SETTINGS) {
            inputLayout = platform.readInputLayout()
            hapticFeedbackEnabled = platform.readHapticFeedbackEnabled()
            flickDistance = platform.readFlickDistance().coerceIn(12, 32)
            cycleTimeout = platform.readCheonjiinCycleTimeout().coerceIn(400, 1600)
        }
    }
    val appTheme = appThemeSpec(keyboardTheme, isSystemInDarkTheme())
    CompositionLocalProvider(LocalUiStrings provides strings, LocalAppTheme provides appTheme) {
    MaterialTheme(colorScheme = appTheme.colorScheme) {
        val pageScroll = remember(page) { ScrollState(0) }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (page != Page.RAIN && page != Page.CAFE) {
                    ThemeBackdrop(appTheme, Modifier.fillMaxWidth().height(160.dp))
                }
                val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                // UIKit has already allocated the extension's keyboard viewport.
                // Applying the host's IME inset again leaves no room for the scroll area.
                Column(Modifier.fillMaxSize().then(
                    if (keyboardExtensionMode) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                )) {
                    if (page != Page.HOME && page != Page.ONBOARDING) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                modifier = Modifier.testTag("keyboard.settings.back").semantics { contentDescription = strings.backDescription },
                                onClick = {
                                    back()
                                },
                            ) { Text(strings.back, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                            Text(
                                strings.pageTitle(page.name),
                                modifier = Modifier.weight(1f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            if (keyboardExtensionMode || standaloneSettingsMode) {
                                TextButton(
                                    modifier = Modifier.width(64.dp).testTag("keyboard.settings.close").semantics { contentDescription = strings.close },
                                    onClick = { onCloseSettings?.invoke() },
                                ) { Text(strings.close) }
                            } else if (page in listOf(Page.PRACTICE, Page.RAIN, Page.CAFE)) {
                                TextButton(
                                    modifier = Modifier.width(96.dp).semantics { contentDescription = strings.soundSettingsDescription },
                                    onClick = { gameAudioSettings = platform.readGameAudioSettings(); showGameSoundSettings = true },
                                ) { Text(strings.sound, maxLines = 1) }
                            } else {
                                Spacer(Modifier.width(64.dp))
                            }
                        }
                    }
                val isGame = page == Page.RAIN || page == Page.CAFE
                Column(
                    Modifier.weight(1f)
                        .testTag("keyboard-settings-scroll")
                        .then(if (isGame) Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp) else Modifier.verticalScroll(pageScroll).padding(20.dp))
                        .pointerInput(page) { detectTapGestures(onTap = { dismissKeyboard() }) },
                    verticalArrangement = if (isGame) Arrangement.spacedBy(4.dp) else Arrangement.spacedBy(16.dp),
                ) {
                when (page) {
                    Page.ONBOARDING -> {
                        Header(strings.appName, strings.onboardingSubtitle)
                        Panel { Text(strings.onboardingSteps) }
                        StatusLine(status)
                        Button(onClick = { platform.openKeyboardSettings() }, modifier = Modifier.fillMaxWidth()) { Text(strings.openKeyboardSettings) }
                        OutlinedButton(onClick = { platform.showKeyboardPicker() }, modifier = Modifier.fillMaxWidth()) { Text(strings.chooseKeyboard) }
                        OutlinedButton(onClick = { platform.refreshKeyboardStatus(); status = platform.keyboardStatus }, modifier = Modifier.fillMaxWidth()) { Text(strings.refreshStatus) }
                        Button(onClick = { page = Page.HOME }, modifier = Modifier.fillMaxWidth()) { Text(strings.basicPractice) }
                    }
                    Page.HOME -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.homeTitle, modifier = Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            TextButton(modifier = Modifier.semantics { contentDescription = strings.settingsTitle }, onClick = {
                                layoutOptions = LayoutOrientation.entries.associateWith(platform::readLayoutOptions)
                                keyboardTheme = platform.readKeyboardTheme()
                                page = Page.SETTINGS
                            }) { Text("⚙", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface) }
                        }
                        val keyboardReady = platform.keyboardStatus == KeyboardStatus.ENABLED || platform.keyboardStatus == KeyboardStatus.ACTIVE
                        Text(
                            if (keyboardReady) strings.keyboardReady else strings.keyboardNotReady,
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (keyboardReady) LocalAppTheme.current.success else LocalAppTheme.current.warning,
                        )
                        if (!keyboardReady) HomeButton(strings.openKeyboardSettings, primary = true) { platform.openKeyboardSettings() }
                        HomeButton(strings.chooseKeyboard) { platform.showKeyboardPicker() }

                        Text(strings.checkKeyboard, modifier = Modifier.padding(top = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        HomeButton(strings.testTitle) { page = Page.TEST }

                        Text(strings.hangulBasicsTitle, modifier = Modifier.padding(top = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        HomeButton(strings.hangulLearning, primary = true) { page = Page.JAMO }
                        Text(strings.typingPractice, modifier = Modifier.padding(top = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (platform.learningItems.isEmpty()) {
                            Text(strings.noLearningContent, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            HomeButton(strings.wordPractice) { activity = Activity.BASIC; mode = PracticeMode.KOREAN; page = Page.SELECT }
                            HomeButton(strings.styleSentence) { activity = Activity.SENTENCE; page = Page.SELECT }
                            HomeButton(strings.styleConvert) { activity = Activity.CONVERT; mode = PracticeMode.JAPANESE; page = Page.SELECT }
                            Text(strings.gamesTitle, modifier = Modifier.padding(top = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            HomeButton(strings.rainTitle) { activity = Activity.RAIN; page = Page.SELECT }
                            HomeButton(strings.cafeTitle) { activity = Activity.CAFE; page = Page.SELECT }
                        }
                        Text(strings.practiceRecord, modifier = Modifier.padding(top = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(strings.homeRecordLine(progress.totalSessions, progress.bestAccuracy, progress.totalXp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HomeButton(strings.viewRecords) { page = Page.PROGRESS }
                    }
                    Page.SETTINGS -> {
                        Header(strings.settingsTitle, strings.settingsSubtitle)
                        Panel {
                            Text(strings.uiLanguageTitle, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiLanguage.entries.forEach { language ->
                                    FilterChip(colors = appFilterChipColors(), selected = uiLanguage == language, onClick = {
                                        platform.setUiLanguage(language.name.lowercase())
                                        uiLanguage = language
                                    }, label = { Text(strings.languageName(language)) })
                                }
                            }
                        }
                        Panel {
                            KeyboardLanguageOrderControl(platform, uiLanguage)
                        }
                        if (!keyboardExtensionMode) StatusLine(status)
                        if (!keyboardExtensionMode) {
                            Button(onClick = { platform.openKeyboardSettings() }, modifier = Modifier.fillMaxWidth()) { Text(strings.systemKeyboardSettings) }
                        }
                        OutlinedButton(onClick = { platform.showKeyboardPicker() }, modifier = Modifier.fillMaxWidth()) { Text(strings.switchKeyboard) }
                        if (!keyboardExtensionMode) OutlinedButton(onClick = { platform.refreshKeyboardStatus(); status = platform.keyboardStatus }, modifier = Modifier.fillMaxWidth()) { Text(strings.refreshStatus) }
                        NavButton(strings.themesTitle, strings.keyboardThemesSubtitle) { page = Page.THEMES }
                        if (!keyboardExtensionMode && platform.learningItems.isNotEmpty()) {
                            Panel {
                                Text(strings.gameSound, fontWeight = FontWeight.Bold)
                                GameSoundControls(
                                    settings = gameAudioSettings,
                                    onMusic = { platform.setGameMusicEnabled(it); gameAudioSettings = platform.readGameAudioSettings() },
                                    onEffects = { platform.setGameEffectsEnabled(it); gameAudioSettings = platform.readGameAudioSettings() },
                                )
                            }
                        }
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(strings.haptic, modifier = Modifier.weight(1f))
                                Switch(checked = hapticFeedbackEnabled, onCheckedChange = { enabled ->
                                    platform.setHapticFeedbackEnabled(enabled)
                                    hapticFeedbackEnabled = platform.readHapticFeedbackEnabled()
                                }, modifier = Modifier.semantics { contentDescription = strings.haptic })
                            }
                            Text(strings.hapticNote, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Text(strings.hangulLayout, fontWeight = FontWeight.Bold)
                            Box {
                                OutlinedButton(
                                    onClick = { inputLayoutMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = strings.layoutSelectionDescription },
                                ) { Text("${strings.text(inputLayout.title)} ▾") }
                                DropdownMenu(expanded = inputLayoutMenuExpanded, onDismissRequest = { inputLayoutMenuExpanded = false }) {
                                    KeyboardInputLayout.entries.forEach { choice ->
                                        DropdownMenuItem(text = { Text(strings.text(choice.title)) }, onClick = {
                                            platform.setInputLayout(choice)
                                            inputLayout = platform.readInputLayout()
                                            inputLayoutMenuExpanded = false
                                        })
                                    }
                                }
                            }
                            Text(strings.flickDistanceLabel(flickDistance))
                            Slider(
                                value = flickDistance.toFloat(),
                                onValueChange = { flickDistance = it.roundToInt().coerceIn(12, 32) },
                                onValueChangeFinished = {
                                    platform.setFlickDistance(flickDistance)
                                    flickDistance = platform.readFlickDistance().coerceIn(12, 32)
                                },
                                valueRange = 12f..32f,
                                modifier = Modifier.semantics { contentDescription = strings.flickDistanceDescription },
                            )
                            Text(strings.cycleTimeoutLabel(cycleTimeout))
                            Slider(
                                value = cycleTimeout.toFloat(),
                                onValueChange = { cycleTimeout = it.roundToInt().coerceIn(400, 1600) },
                                onValueChangeFinished = {
                                    platform.setCheonjiinCycleTimeout(cycleTimeout)
                                    cycleTimeout = platform.readCheonjiinCycleTimeout().coerceIn(400, 1600)
                                },
                                valueRange = 400f..1600f,
                                modifier = Modifier.semantics { contentDescription = strings.cycleTimeoutDescription },
                            )
                        }
                        LongPressSettings(platform, inputLayout)
                        LayoutOrientation.entries.forEach { orientation ->
                            val options = layoutOptions.getValue(orientation)
                            Panel {
                                Text(if (orientation == LayoutOrientation.PORTRAIT) strings.portrait else strings.landscape, fontWeight = FontWeight.Bold)
                                Text(strings.keyboardHeight)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LayoutHeight.entries.forEach { height ->
                                        FilterChip(colors = appFilterChipColors(),
                                            selected = options.height == height,
                                            onClick = {
                                                platform.setLayoutHeight(orientation, height)
                                                layoutOptions = layoutOptions + (orientation to platform.readLayoutOptions(orientation))
                                            },
                                            label = { Text(when (height) {
                                                LayoutHeight.COMPACT -> strings.heightCompact
                                                LayoutHeight.NORMAL -> strings.heightNormal
                                                LayoutHeight.TALL -> strings.heightTall
                                            }) },
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(strings.numberRow, modifier = Modifier.weight(1f))
                                    Switch(modifier = Modifier.testTag("keyboard.settings.number-row.${orientation.name.lowercase()}"), checked = options.numberRowEnabled, onCheckedChange = { enabled ->
                                        platform.setNumberRowEnabled(orientation, enabled)
                                        layoutOptions = layoutOptions + (orientation to platform.readLayoutOptions(orientation))
                                    })
                                }
                            }
                        }
                        if (!keyboardExtensionMode && advancedSettingsContent != null) {
                            OutlinedButton(onClick = { page = Page.ADVANCED_SETTINGS }, modifier = Modifier.fillMaxWidth()) {
                                Text(strings.advancedInput)
                            }
                        }
                        if (!keyboardExtensionMode) {
                            OutlinedButton(onClick = { phrases = platform.readUserPhrases(); page = Page.PHRASES }, modifier = Modifier.fillMaxWidth()) { Text(strings.phrasesTitle) }
                        }
                        if (!keyboardExtensionMode) {
                            AdPrivacyPanel(
                                language = uiLanguage,
                                onPrivacyPolicy = platform::openPrivacyPolicy,
                                privacyOptionsRequired = privacyOptionsRequired,
                                onPrivacyOptions = platform::showAdPrivacyOptions,
                            )
                        }
                    }
                    Page.THEMES -> {
                        Header(strings.themesTitle, strings.themesSubtitle)
                        val themes = remember { platform.readThemes() }
                        if (themes.isEmpty()) Panel { Text(strings.themesEmpty) }
                        themes.forEach { theme ->
                            Panel {
                                Text(strings.text(theme.titleKo), fontWeight = FontWeight.Bold)
                                ThemePreview(theme)
                                val lockedSeoul = (theme.id == "seoul_day" || theme.id == "seoul_night") && seoulUnlockRemaining <= 0L
                                Text(when {
                                    !theme.available -> strings.themeUpcoming
                                    theme.id == "system" -> strings.themeFollowsSystem
                                    keyboardExtensionMode && lockedSeoul -> when (uiLanguage) {
                                        UiLanguage.KO -> "본체 앱의 테마 화면에서 설정하세요."
                                        UiLanguage.JA -> "アプリのテーマ画面で設定してください。"
                                        UiLanguage.EN -> "Set up this theme in the app’s themes screen."
                                    }
                                    theme.id == "seoul_day" || theme.id == "seoul_night" ->
                                        if (seoulUnlockRemaining > 0L) strings.trialRemaining(seoulUnlockRemaining) else strings.text("광고를 보면 24시간 이용")
                                    else -> strings.themeFree
                                })
                                val choice = when (theme.id) {
                                    "system" -> KeyboardThemeChoice.SYSTEM
                                    "basic_light" -> KeyboardThemeChoice.LIGHT
                                    "basic_dark" -> KeyboardThemeChoice.DARK
                                    "seoul_day" -> KeyboardThemeChoice.SEOUL_DAY
                                    "seoul_night" -> KeyboardThemeChoice.SEOUL_NIGHT
                                    else -> null
                                }
                                if (theme.available && choice != null && !(keyboardExtensionMode && lockedSeoul)) {
                                    Button(onClick = {
                                        if ((choice == KeyboardThemeChoice.SEOUL_DAY || choice == KeyboardThemeChoice.SEOUL_NIGHT) && seoulUnlockRemaining <= 0L) {
                                            seoulAdChoice = choice
                                        } else {
                                            platform.setKeyboardTheme(choice)
                                            keyboardTheme = platform.readKeyboardTheme()
                                        }
                                    }, enabled = !seoulAdPending, modifier = Modifier.fillMaxWidth().testTag("keyboard.theme.apply.${theme.id}")) {
                                        Text(when {
                                            keyboardTheme == choice -> strings.statusActive
                                            (choice == KeyboardThemeChoice.SEOUL_DAY || choice == KeyboardThemeChoice.SEOUL_NIGHT) && seoulUnlockRemaining <= 0L -> strings.themeSeoulAdCta
                                            else -> strings.themeApply
                                        })
                                    }
                                }
                            }
                        }
                        if (!keyboardExtensionMode) {
                            if (seoulAdPending) Text(strings.themeAdPreparing)
                            seoulAdMessage?.let { Text(strings.seoulAdStatus(it)) }
                        }
                    }
                    Page.ADVANCED_SETTINGS -> {
                        advancedSettingsContent?.invoke()
                    }
                    Page.PHRASES -> {
                        Header(strings.phrasesTitle, strings.phrasesSubtitle)
                        Text(strings.savedPhrases(phrases.size))
                        Button(onClick = { openPhraseEditor(null) }, enabled = phrases.size < 50, modifier = Modifier.fillMaxWidth()) { Text(strings.newPhrase) }
                        phrases.forEach { phrase ->
                            Panel {
                                Text(phrase.title, fontWeight = FontWeight.Bold)
                                Text(phrase.content, maxLines = 3)
                                OutlinedButton(onClick = { openPhraseEditor(phrase) }) { Text(strings.edit) }
                            }
                        }
                    }
                    Page.PHRASE_EDITOR -> {
                        Header(if (editingPhrase == null) strings.newPhrase else strings.editPhraseTitle, strings.phraseEditorHint)
                        OutlinedTextField(colors = appTextFieldColors(), value = phraseTitle, onValueChange = { phraseTitle = it; phraseError = "" },
                            label = { Text(strings.titleLabel) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(colors = appTextFieldColors(), value = phraseContent, onValueChange = { phraseContent = it; phraseError = "" },
                            label = { Text(strings.contentLabel) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Text(strings.phraseLength(phraseTitle.length, phraseContent.length))
                        if (phraseError.isNotEmpty()) Text(phraseError, color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            phraseError = when {
                                phraseTitle.isBlank() -> strings.titleRequired
                                phraseContent.isBlank() -> strings.contentRequired
                                phraseTitle.trim().length > 40 -> strings.titleTooLong
                                phraseContent.length > 500 -> strings.contentTooLong
                                else -> ""
                            }
                            if (phraseError.isEmpty()) {
                                val saved = editingPhrase?.let { platform.updateUserPhrase(it.id, phraseTitle, phraseContent) }
                                    ?: platform.createUserPhrase(phraseTitle, phraseContent)
                                if (saved) { phrases = platform.readUserPhrases(); page = Page.PHRASES }
                                else phraseError = strings.phraseSaveFailed
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(strings.save) }
                        if (editingPhrase != null) OutlinedButton(onClick = { confirmPhraseDelete = true }) { Text(strings.delete) }
                    }
                    Page.TEST -> {
                        Header(strings.testTitle, strings.testSubtitle)
                        OutlinedTextField(colors = appTextFieldColors(), value = testText, onValueChange = { testText = it; platform.onKeyboardTestTextChanged(it) },
                            label = { Text(strings.inputTestLabel) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        Text(strings.testChars(testText.length))
                        OutlinedButton(onClick = { testText = ""; platform.onKeyboardTestTextChanged("") }) { Text(strings.clear) }
                    }
                    Page.JAMO -> JamoLearningScreen(
                        platform = platform,
                        onClose = ::back,
                        onEffect = { if (!keyboardExtensionMode) platform.playGameSound(it) },
                    )
                    Page.SELECT -> {
                        Header(when (activity) { Activity.BASIC -> strings.styleBasic; Activity.SENTENCE -> strings.styleSentence; Activity.CONVERT -> strings.styleConvert; Activity.RAIN -> strings.rainTitle; Activity.CAFE -> strings.cafeTitle },
                            if (activity == Activity.SENTENCE) strings.selectSubtitleSentence else strings.selectSubtitleDefault)
                        if (activity == Activity.SENTENCE) Text(strings.sentenceRandomNote)
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PracticeMode.entries.forEach { candidate -> FilterChip(colors = appFilterChipColors(), selected = mode == candidate, onClick = { mode = candidate }, label = { Text(strings.text(candidate.title)) }) }
                        }
                        val count = selectedItems().size
                        Text(if (activity == Activity.CAFE) strings.availableCafeOrders(count) else strings.availableSentences(count))
                        if (activity == Activity.CAFE) {
                            Text(strings.difficultyLabel, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CafeDifficulty.entries.forEach { choice -> FilterChip(colors = appFilterChipColors(), selected = difficulty == choice, onClick = { difficulty = choice }, label = { Text(strings.difficulty(choice.name)) }) }
                            }
                            Text(strings.cafeDifficultyDescription(difficulty))
                        }
                        Button(onClick = ::start, enabled = count > 0, modifier = Modifier.fillMaxWidth()) { Text(strings.start) }
                        if (feedback.isNotEmpty()) Text(feedback, color = MaterialTheme.colorScheme.error)
                    }
                    Page.PRACTICE -> {
                        val session = practice
                        if (session != null) {
                            val item = session.currentItem
                            Header(strings.text(session.style.title), strings.practiceHeader(session.currentIndex + 1, session.items.size, session.score))
                            if (item != null) {
                                if (session.style == PracticeStyle.SENTENCE) SentencePromptView(session.currentSentencePrompt!!, translationCatalog)
                                else Prompt(item, translationCatalog)
                                val submitPractice: () -> Unit = submit@ {
                                    if (answer.isBlank() || session.currentItem == null) return@submit
                                    val expected = session.expectedAnswer
                                    val correct = session.submit(answer)
                                    if (!keyboardExtensionMode) platform.playGameSound(if (correct) GameSoundEffect.HIT else GameSoundEffect.ERROR)
                                    feedback = if (correct) strings.answerCorrect else strings.answerRetry(expected)
                                    answer = ""; revision++
                                    if (session.currentItem == null) record(session.result(startedAt.elapsedNow().inWholeMilliseconds), naturallyCompleted = true)
                                }
                                AnswerField(answer, { answer = it }, strings.answerLabelDefault, submitPractice)
                                Button(onClick = submitPractice, enabled = answer.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(strings.confirm) }
                                Text(feedback)
                            }
                            OutlinedButton(onClick = { record(session.result(startedAt.elapsedNow().inWholeMilliseconds)) }) { Text(strings.finishPractice) }
                        }
                    }
                    Page.RAIN -> {
                        @Suppress("UNUSED_VARIABLE") val currentFrame = revision
                        val session = rain
                        if (session != null) {
                            LaunchedEffect(session) {
                                while (!session.finished) {
                                    delay(100)
                                    val previousLives = session.lives
                                    session.tick(100)
                                    if (session.lives < previousLives && !keyboardExtensionMode) platform.playGameSound(GameSoundEffect.MISS)
                                    revision++
                                }
                                record(session.result(), naturallyCompleted = true)
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(strings.rainTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(strings.rainHud(session.remainingMs / 1000, session.lives, session.score), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            }
                            RainGameScene(session.targets, answer, feedback, session.mode, translationCatalog, Modifier.weight(1f).fillMaxWidth())
                            val submitRain: () -> Unit = submit@ {
                                if (answer.isBlank() || session.finished) return@submit
                                val correct = session.submit(answer)
                                if (!keyboardExtensionMode) platform.playGameSound(if (!correct) GameSoundEffect.ERROR else if (session.combo % 5 == 0) GameSoundEffect.COMBO else GameSoundEffect.HIT)
                                feedback = if (correct) strings.rainHit else strings.rainMiss; answer = ""; revision++
                            }
                            AnswerField(answer, { answer = it }, strings.rainAnswerLabel, submitRain)
                            Text(feedback, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                            if (!imeVisible) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = submitRain, enabled = answer.isNotBlank(), modifier = Modifier.weight(1f)) { Text(strings.submitInput) }
                                    OutlinedButton(onClick = { session.finish(); record(session.result()) }, modifier = Modifier.weight(1f)) { Text(strings.endGame) }
                                }
                            }
                        }
                    }
                    Page.CAFE -> {
                        @Suppress("UNUSED_VARIABLE") val currentFrame = revision
                        val session = cafe
                        if (session != null) {
                            LaunchedEffect(cafeReaction) {
                                if (cafeReaction != null) { delay(700); cafeReaction = null }
                            }
                            LaunchedEffect(session) {
                                while (!session.finished) {
                                    delay(100)
                                    val previousOrder = session.currentIndex
                                    if (session.tick(100)) {
                                        cafeReaction = previousOrder to false
                                        if (!keyboardExtensionMode) platform.playGameSound(GameSoundEffect.MISS)
                                        feedback = strings.timeUp; answer = ""
                                    }
                                    revision++
                                }
                                record(session.result(startedAt.elapsedNow().inWholeMilliseconds), naturallyCompleted = true)
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(strings.cafeTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(strings.cafeOrder(session.currentIndex + 1, session.orderCount, session.score), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            }
                            CafeGameScene(session, answer, cafeReaction, translationCatalog, Modifier.weight(1f).fillMaxWidth())
                            val submitCafe: () -> Unit = submit@ {
                                if (answer.isBlank() || session.finished || session.currentItem == null) return@submit
                                val previousOrder = session.currentIndex
                                val correct = session.submit(answer)
                                if (!keyboardExtensionMode) platform.playGameSound(if (!correct) GameSoundEffect.ERROR else if (session.combo % 5 == 0) GameSoundEffect.COMBO else GameSoundEffect.HIT)
                                cafeReaction = previousOrder to correct
                                feedback = if (correct) strings.orderSuccess else strings.orderNext
                                answer = ""; revision++
                                if (session.finished) record(session.result(startedAt.elapsedNow().inWholeMilliseconds), naturallyCompleted = true)
                            }
                            AnswerField(answer, { answer = it }, strings.cafeOrderLabel, submitCafe)
                            Text(feedback, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                            if (!imeVisible) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = submitCafe, enabled = answer.isNotBlank(), modifier = Modifier.weight(1f)) { Text(strings.submit) }
                                    OutlinedButton(onClick = { record(session.result(startedAt.elapsedNow().inWholeMilliseconds)) }, modifier = Modifier.weight(1f)) { Text(strings.leaveCafe) }
                                }
                            }
                        }
                    }
                    Page.FINISHING -> Text(strings.finishingDone)
                    Page.RESULT -> {
                        val report = result
                        Header(strings.resultTitle, strings.resultSubtitle)
                        if (report != null) Panel {
                            Text(strings.resultScore(report.score), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(strings.resultAccuracy(report.accuracy, report.cpm))
                            Text(strings.resultCombo(report.maxCombo, report.errorCount))
                            Text(strings.resultXp(report.score / 10 + if (report.completed && !report.modeId.startsWith("rain_")) 20 else 0))
                        }
                        Button(onClick = { page = Page.SELECT }, modifier = Modifier.fillMaxWidth()) { Text(strings.retry) }
                    }
                    Page.PROGRESS -> {
                        Header(strings.progressTitle, strings.progressSubtitle)
                        Panel {
                            Text(strings.totalXp(progress.totalXp), style = MaterialTheme.typography.headlineMedium)
                            Text(strings.completedSessions(progress.totalSessions))
                            Text(strings.bestAccuracy(progress.bestAccuracy))
                            Text(strings.rainHighScore(progress.rainHighScore))
                        }
                    }
                }
            }
            }
            if (confirmPhraseDelete) {
                AlertDialog(onDismissRequest = { confirmPhraseDelete = false },
                    title = { Text(strings.deletePhraseTitle) }, text = { Text(strings.deletePhraseBody) },
                    confirmButton = { TextButton(onClick = {
                        confirmPhraseDelete = false
                        val deleted = editingPhrase?.let { platform.deleteUserPhrase(it.id) } == true
                        if (deleted) { phrases = platform.readUserPhrases(); page = Page.PHRASES }
                        else phraseError = strings.phraseDeleteFailed
                    }) { Text(strings.delete) } },
                    dismissButton = { TextButton(onClick = { confirmPhraseDelete = false }) { Text(strings.cancel) } })
            }
            if (showGameSoundSettings && !keyboardExtensionMode && page in listOf(Page.PRACTICE, Page.RAIN, Page.CAFE)) {
                AlertDialog(
                    onDismissRequest = { showGameSoundSettings = false },
                    title = { Text(strings.gameSound) },
                    text = {
                        GameSoundControls(
                            settings = gameAudioSettings,
                            onMusic = { platform.setGameMusicEnabled(it); gameAudioSettings = platform.readGameAudioSettings() },
                            onEffects = { platform.setGameEffectsEnabled(it); gameAudioSettings = platform.readGameAudioSettings() },
                        )
                    },
                    confirmButton = { TextButton(onClick = { showGameSoundSettings = false }) { Text(strings.close) } },
                )
            }
            seoulAdChoice?.let { choice ->
                AlertDialog(
                    onDismissRequest = { seoulAdChoice = null },
                    title = { Text(strings.seoulAdTitle(strings.text(choice.title))) },
                    text = { Text(strings.themeSeoulAdBody) },
                    confirmButton = { TextButton(onClick = {
                        seoulAdChoice = null
                        platform.requestSeoulThemeAd(choice)
                        seoulAdPending = platform.isSeoulThemeAdPending()
                        seoulAdMessage = platform.seoulThemeAdMessage()
                    }) { Text(strings.watchAd) } },
                    dismissButton = { TextButton(onClick = { seoulAdChoice = null }) { Text(strings.cancel) } },
                )
            }
        }
    }
}
}
}



@Composable private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Panel(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = { content() }) }
}

@Composable private fun ThemePreview(theme: SharedTheme) {
    val strings = LocalUiStrings.current
    val systemDark = isSystemInDarkTheme()
    val preview = when (theme.previewAsset) {
        "preview_basic_light.png" -> Res.drawable.theme_preview_basic_light
        "preview_basic_dark.png" -> Res.drawable.theme_preview_basic_dark
        "preview_seoul_day.png" -> Res.drawable.theme_preview_seoul_day
        "preview_seoul_night.png" -> Res.drawable.theme_preview_seoul_night
        "taegeuk_artwork.png" -> Res.drawable.theme_taegeuk_artwork
        else -> if (theme.id == "system") {
            if (systemDark) Res.drawable.theme_preview_basic_dark else Res.drawable.theme_preview_basic_light
        } else null
    }
    if (preview != null) {
        Image(
            painter = painterResource(preview),
            contentDescription = strings.themePreviewDescription(theme.titleKo, theme.available),
            modifier = Modifier.fillMaxWidth().aspectRatio(if (theme.id == "taegeuk_default") 1.5f else 824f / 268f)
                .background(Color(theme.swatch?.surface ?: 0xFFECEEF1.toInt()), RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
        Text(if (theme.id == "taegeuk_default") strings.previewThemeImage else strings.previewQwerty, style = MaterialTheme.typography.labelSmall)
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(824f / 268f)
                .background(Color(theme.swatch?.surface ?: 0xFFECEEF1.toInt()), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(strings.previewPending, color = Color(theme.swatch?.text ?: 0xFF20242A.toInt())) }
    }
}

@Composable private fun NavButton(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun HomeButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(10.dp),
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else LocalAppTheme.current.surfaceMuted,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable private fun StatusLine(status: KeyboardStatus) {
    val strings = LocalUiStrings.current
    Panel { Text(strings.statusLine(status.name), color = when (status) {
        KeyboardStatus.ACTIVE, KeyboardStatus.ENABLED -> LocalAppTheme.current.success
        else -> LocalAppTheme.current.warning
    }) }
}

@Composable private fun Prompt(item: LearningItem, catalog: PracticeTranslationCatalog) {
    val strings = LocalUiStrings.current
    Panel {
        PracticePromptView(catalog.resolve(item))
        Text(if (item.sourceLanguage == "ja") strings.jaInputHint else strings.typeAsShown)
    }
}

@Composable private fun SentencePromptView(prompt: SentencePrompt, catalog: PracticeTranslationCatalog) {
    val strings = LocalUiStrings.current
    Panel {
        PracticePromptView(catalog.resolve(prompt.item))
        Text(if (prompt.target == SentenceTarget.JAPANESE)
            strings.sentenceJaHint(prompt.copyText)
            else strings.sentenceKoHint(prompt.copyText),
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun AnswerField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    onSubmit: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    OutlinedTextField(colors = appTextFieldColors(),
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onPreviewKeyEvent { event ->
            if (event.key == Key.Enter) {
                if (event.type == KeyEventType.KeyUp) onSubmit()
                true
            } else false
        },
    )
}

@Composable
private fun GameSoundControls(
    settings: GameAudioSettings,
    onMusic: (Boolean) -> Unit,
    onEffects: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.bgm, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.musicEnabled,
                onCheckedChange = onMusic,
                modifier = Modifier.semantics { contentDescription = strings.bgmDescription },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.sfx, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.effectsEnabled,
                onCheckedChange = onEffects,
                modifier = Modifier.semantics { contentDescription = strings.sfxDescription },
            )
        }
    }
}


@Composable
private fun LongPressSettings(platform: AppPlatform, inputLayout: KeyboardInputLayout) {
    val strings = LocalUiStrings.current
    val editable = platform.canEditLongPressSymbols()
    val layouts = listOf(KeyboardInputLayout.CHEONJIIN, KeyboardInputLayout.CHEONJIIN_PLUS, KeyboardInputLayout.QWERTY)
    var layout by remember(platform, inputLayout) { mutableStateOf(inputLayout.takeIf { it in layouts } ?: KeyboardInputLayout.CHEONJIIN) }
    val keys = com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog.keys(layout.persistedValue)
    var keyId by remember(layout) { mutableStateOf(keys.first().id) }
    var slots by remember(platform, layout, keyId) { mutableStateOf(platform.readLongPressSlots(layout, keyId)) }
    var layoutMenu by remember { mutableStateOf(false) }
    var keyMenu by remember { mutableStateOf(false) }
    Panel {
        Text(strings.longPressTitle, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { layoutMenu = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = strings.longPressLayout }) {
                    Text("${strings.text(layout.title)} ▾")
                }
                DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                    layouts.forEach { choice ->
                        DropdownMenuItem(text = { Text(strings.text(choice.title)) }, onClick = { layout = choice; layoutMenu = false })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { keyMenu = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = strings.longPressKey }) {
                    Text("${keys.first { it.id == keyId }.label} ▾")
                }
                DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                    keys.forEach { key ->
                        DropdownMenuItem(text = { Text(key.label) }, onClick = { keyId = key.id; keyMenu = false })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                OutlinedTextField(value = slots.getOrElse(index) { "" }, onValueChange = { value ->
                    val updated = com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog.slots(layout.persistedValue, keyId, slots).toMutableList()
                    updated[index] = value
                    platform.setLongPressSlots(layout, keyId, updated)
                    slots = platform.readLongPressSlots(layout, keyId)
                }, label = { Text(strings.longPressSlot(index + 1)) }, singleLine = true, readOnly = !editable,
                    modifier = Modifier.weight(1f).semantics { contentDescription = strings.longPressSlot(index + 1) })
            }
        }
        Text(strings.longPressNote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!editable) Text(strings.longPressEditInApp, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = editable, onClick = { platform.resetLongPressSlots(layout, keyId); slots = platform.readLongPressSlots(layout, keyId) }) {
            Text(strings.longPressReset)
        }
    }
}
