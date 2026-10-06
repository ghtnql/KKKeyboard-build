package com.ghtnql.kkkeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.GameAudioSettings
import com.ghtnql.kkkeyboard.sharedui.GameMusicTrack
import com.ghtnql.kkkeyboard.sharedui.GameSoundEffect
import com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp
import com.ghtnql.kkkeyboard.sharedui.KeyboardStatus
import com.ghtnql.kkkeyboard.sharedui.KeyboardThemeChoice
import com.ghtnql.kkkeyboard.sharedui.SharedTheme
import com.ghtnql.kkkeyboard.sharedui.SharedThemeSwatch
import com.ghtnql.kkkeyboard.sharedui.LayoutHeight
import com.ghtnql.kkkeyboard.sharedui.LayoutOptions
import com.ghtnql.kkkeyboard.sharedui.LayoutOrientation
import com.ghtnql.kkkeyboard.sharedui.UserPhraseEntry
import com.ghtnql.kkkeyboard.sharedui.LearningItem as SharedLearningItem
import com.ghtnql.kkkeyboard.sharedui.LearningProgress as SharedLearningProgress
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.json.JSONArray

private const val RESTORE_ADVANCED_SETTINGS = "restore_advanced_settings"

/** Android host for the same Compose application rendered by the iOS app target. */
class ComposeMainActivity : ComponentActivity() {
    private lateinit var platform: AndroidAppPlatform
    private var settingsNavigationRequest by mutableIntStateOf(0)
    private var themeNavigationRequest by mutableIntStateOf(0)
    private var restoreAdvancedSettingsOnRecreate = false

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppUiLanguageSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("open_shared_settings", false) == true) settingsNavigationRequest++
        if (intent?.getBooleanExtra("open_themes", false) == true) themeNavigationRequest++
        platform = AndroidAppPlatform()
        setContentView(ComposeView(this).apply {
            setContent {
                KKKeyboardApp(
                    platform = platform,
                    settingsNavigationRequest = settingsNavigationRequest,
                    themeNavigationRequest = themeNavigationRequest,
                    openAdvancedSettings = savedInstanceState?.getBoolean(RESTORE_ADVANCED_SETTINGS) == true,
                    advancedSettingsContent = { AdvancedSettingsContent() },
                )
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(RESTORE_ADVANCED_SETTINGS, restoreAdvancedSettingsOnRecreate)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_shared_settings", false)) settingsNavigationRequest++
        if (intent.getBooleanExtra("open_themes", false)) themeNavigationRequest++
    }

    override fun onResume() {
        super.onResume()
        if (::platform.isInitialized) platform.refreshKeyboardStatus()
        if (::platform.isInitialized) platform.onResumeAudio()
        if (::platform.isInitialized) platform.refreshMonetization()
    }

    override fun onPause() {
        if (::platform.isInitialized) platform.onPauseAudio()
        super.onPause()
    }

    override fun onDestroy() {
        if (::platform.isInitialized) platform.disposeGameEndAd()
        if (::platform.isInitialized) platform.releaseAudio()
        if (::platform.isInitialized) platform.closeMonetization()
        super.onDestroy()
    }

    @Composable
    private fun AdvancedSettingsContent() {
        var uiLanguage by remember { mutableStateOf(AppUiLanguageSettings.read(this)) }
        val localizedContext = remember(uiLanguage) { AppUiLanguageSettings.wrap(this) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(localizedContext.getString(R.string.settings_ui_language), fontWeight = FontWeight.Bold)
                AppUiLanguage.entries.forEach { language ->
                    val label = localizedContext.getString(when (language) {
                        AppUiLanguage.KOREAN -> R.string.ui_language_korean
                        AppUiLanguage.JAPANESE -> R.string.ui_language_japanese
                        AppUiLanguage.ENGLISH -> R.string.ui_language_english
                    })
                    FilterChip(
                        selected = uiLanguage == language,
                        onClick = {
                            if (uiLanguage != language) {
                                AppUiLanguageSettings.write(this@ComposeMainActivity, language)
                                uiLanguage = language
                                restoreAdvancedSettingsOnRecreate = true
                                recreate()
                            }
                        },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private inner class AndroidAppPlatform : AppPlatform {
        override fun readUiLanguage(): String = AppUiLanguageSettings.read(this@ComposeMainActivity).persistedValue
        override fun setUiLanguage(language: String) {
            AppUiLanguage.fromPersistedValue(language)?.let { AppUiLanguageSettings.write(this@ComposeMainActivity, it) }
        }

        private val consent by lazy { AdConsentController(this@ComposeMainActivity) }
        private fun canRequestAds(): Boolean = !BuildConfig.MOCK_ADS && consent.canRequestAds
        init {
            // Initial release has no paid entitlement, including stale test ownership.
            getSharedPreferences("ad_removal_entitlement", MODE_PRIVATE).edit()
                .putBoolean("owned", false).apply()
            if (!BuildConfig.MOCK_ADS) {
                consent.onChanged = { gameEndAdGateway.invalidate() }
            }
        }
        override fun isAdPrivacyOptionsRequired(): Boolean =
            if (BuildConfig.MOCK_ADS) false else consent.privacyOptionsRequired
        override fun showAdPrivacyOptions() {
            if (!BuildConfig.MOCK_ADS) consent.showPrivacyOptions()
        }
        override fun openPrivacyPolicy() = openPolicy("privacy-policy")
        private fun openPolicy(name: String) {
            val suffix = if (readUiLanguage() == "ja") "-ja" else ""
            val url = android.net.Uri.parse("https://ghtnql.github.io/KKKeyboard-policy/$name$suffix.html")
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
        }
        fun refreshMonetization() { if (!BuildConfig.MOCK_ADS) consent.refresh() }
        fun closeMonetization() { if (!BuildConfig.MOCK_ADS) consent.close() }
        private val gameEndAdGateway = GoogleGameEndAdGateway(
            this@ComposeMainActivity, BuildConfig.INTERSTITIAL_AD_UNIT_ID, ::canRequestAds,
        )
        private val gameEndAd = GameEndAdController(
            getSharedPreferences("game_end_ads", MODE_PRIVATE),
            gameEndAdGateway,
        )
        override fun prepareGameEndAd() { if (!BuildConfig.MOCK_ADS) gameEndAd.prepare() }
        override fun requestGameEndAd(onFinished: () -> Unit): Boolean {
            if (BuildConfig.MOCK_ADS) return false
            return gameEndAd.onCompletedRound(onFinished)
        }
        fun disposeGameEndAd() = gameEndAd.dispose()
        private val gameAudio = GameAudioController(this@ComposeMainActivity)
        override fun readGameAudioSettings(): GameAudioSettings = gameAudio.readSettings()
        override fun setGameMusicEnabled(enabled: Boolean) = gameAudio.setMusicEnabled(enabled)
        override fun setGameEffectsEnabled(enabled: Boolean) = gameAudio.setEffectsEnabled(enabled)
        override fun startGameAudio(track: GameMusicTrack) = gameAudio.start(track)
        override fun stopGameAudio() = gameAudio.stop()
        override fun finishGameAudio() = gameAudio.finish()
        override fun playGameSound(effect: GameSoundEffect) = gameAudio.play(effect)
        fun onResumeAudio() = gameAudio.onResume()
        fun onPauseAudio() = gameAudio.onPause()
        fun releaseAudio() = gameAudio.release()
        private var seoulAdPending = false
        private var seoulAdMessage: String? = null
        override var keyboardStatus by mutableStateOf(readKeyboardStatus())
            private set
        override var keyboardTestText by mutableStateOf("")
            private set
        override val learningItems: List<SharedLearningItem> = if (BuildConfig.EXPOSE_GAMES) {
            val sentenceLines = JSONArray(assets.open("learning_items.json").bufferedReader().use { it.readText() })
            val triads = (0 until sentenceLines.length()).map { sentenceLines.getJSONObject(it) }
                .filter { it.has("japaneseText") && it.has("japaneseHangulPronunciation") && it.has("koreanText") }
                .associateBy { it.getString("id") }
            LearningContent.load(this@ComposeMainActivity).map { item ->
                val triad = triads[item.id]
                SharedLearningItem(
                    id = item.id,
                    category = item.category,
                    difficulty = item.difficulty,
                    sourceLanguage = item.sourceLanguage,
                    sourceText = item.sourceText,
                    targetLanguage = item.targetLanguage,
                    acceptedAnswers = item.acceptedAnswers,
                    meaningHint = item.meaningHint,
                    enabledModes = item.enabledModes.toList(),
                    gameTypes = item.gameTypes.toList(),
                    japaneseText = triad?.optString("japaneseText"),
                    japaneseHangulPronunciation = triad?.optString("japaneseHangulPronunciation"),
                    koreanText = triad?.optString("koreanText"),
                )
            }
        } else emptyList()
        override var learningProgress by mutableStateOf(readProgress())
            private set

        override fun refreshKeyboardStatus() {
            keyboardStatus = readKeyboardStatus()
        }

        override fun openKeyboardSettings() {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        override fun showKeyboardPicker() {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }

        override fun onKeyboardTestTextChanged(text: String) {
            keyboardTestText = text
        }

        override fun saveLearningProgress(progress: SharedLearningProgress) {
            getSharedPreferences("learning_progress", MODE_PRIVATE).edit()
                .putInt("total_sessions", progress.totalSessions)
                .putInt("best_accuracy", progress.bestAccuracy)
                .putInt("rain_high_score", progress.rainHighScore)
                .putInt("total_xp", progress.totalXp)
                .apply()
            learningProgress = progress
        }

        override fun readHangulLearnedIds(): List<String> =
            getSharedPreferences("hangul_learning", MODE_PRIVATE).getStringSet("completed_ids", emptySet())?.toList().orEmpty()

        override fun readHiraganaGuideSeen(): Boolean =
            getSharedPreferences("hangul_learning", MODE_PRIVATE).getBoolean("hiragana_guide_seen", false)

        override fun saveHiraganaGuideSeen() {
            getSharedPreferences("hangul_learning", MODE_PRIVATE).edit()
                .putBoolean("hiragana_guide_seen", true).apply()
        }

        override fun saveHangulLearnedIds(ids: List<String>) {
            getSharedPreferences("hangul_learning", MODE_PRIVATE).edit()
                .putStringSet("completed_ids", ids.toSet())
                .apply()
        }

        override fun readUserPhrases(): List<UserPhraseEntry> =
            UserPhraseStore.read(this@ComposeMainActivity).map { UserPhraseEntry(it.id, it.title, it.content) }

        override fun createUserPhrase(title: String, content: String): Boolean =
            UserPhraseStore.add(this@ComposeMainActivity, title, content) != null

        override fun updateUserPhrase(id: String, title: String, content: String): Boolean =
            UserPhraseStore.update(this@ComposeMainActivity, id, title, content)

        override fun deleteUserPhrase(id: String): Boolean =
            UserPhraseStore.delete(this@ComposeMainActivity, id)

        override fun readInputLayout(): com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout =
            com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout.valueOf(KeyboardLayoutSettings.readInputLayout(this@ComposeMainActivity).name)
        override fun setInputLayout(layout: com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout) {
            KeyboardLayoutSettings.writeInputLayout(this@ComposeMainActivity, InputLayout.valueOf(layout.name))
        }
        override fun readLongPressSlots(layout: com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout, keyId: String): List<String> =
            KeyboardLayoutSettings.readLongPressSlots(this@ComposeMainActivity, InputLayout.valueOf(layout.name), keyId)
        override fun setLongPressSlots(layout: com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout, keyId: String, slots: List<String>) {
            KeyboardLayoutSettings.writeLongPressSlots(this@ComposeMainActivity, InputLayout.valueOf(layout.name), keyId, slots)
        }
        override fun resetLongPressSlots(layout: com.ghtnql.kkkeyboard.sharedui.KeyboardInputLayout, keyId: String) {
            KeyboardLayoutSettings.resetLongPressSlots(this@ComposeMainActivity, InputLayout.valueOf(layout.name), keyId)
        }
        override fun readFlickDistance(): Int = KeyboardLayoutSettings.readFlickDistance(this@ComposeMainActivity)
        override fun setFlickDistance(distance: Int) { KeyboardLayoutSettings.writeFlickDistance(this@ComposeMainActivity, distance) }
        override fun readCheonjiinCycleTimeout(): Int = KeyboardLayoutSettings.readCheonjiinCycleTimeout(this@ComposeMainActivity)
        override fun setCheonjiinCycleTimeout(timeout: Int) { KeyboardLayoutSettings.writeCheonjiinCycleTimeout(this@ComposeMainActivity, timeout) }

        override fun readHapticFeedbackEnabled(): Boolean = KeyboardLayoutSettings.readHapticFeedbackEnabled(this@ComposeMainActivity)
        override fun setHapticFeedbackEnabled(enabled: Boolean) {
            KeyboardLayoutSettings.writeHapticFeedbackEnabled(this@ComposeMainActivity, enabled)
        }

        override fun readLayoutOptions(orientation: LayoutOrientation): LayoutOptions {
            val nativeOrientation = orientation.toNativeOrientation()
            return LayoutOptions(
                LayoutHeight.valueOf(KeyboardLayoutSettings.readHeight(this@ComposeMainActivity, nativeOrientation).name),
                KeyboardLayoutSettings.readNumberRowEnabled(this@ComposeMainActivity, nativeOrientation),
            )
        }

        override fun setLayoutHeight(orientation: LayoutOrientation, height: LayoutHeight) {
            KeyboardLayoutSettings.writeHeight(
                this@ComposeMainActivity, orientation.toNativeOrientation(), KeyboardHeight.valueOf(height.name),
            )
        }

        override fun setNumberRowEnabled(orientation: LayoutOrientation, enabled: Boolean) {
            KeyboardLayoutSettings.writeNumberRowEnabled(this@ComposeMainActivity, orientation.toNativeOrientation(), enabled)
        }

        override fun readKeyboardTheme(): KeyboardThemeChoice =
            KeyboardThemeChoice.valueOf(KeyboardThemeSettings.read(this@ComposeMainActivity).name)

        override fun setKeyboardTheme(theme: KeyboardThemeChoice) {
            KeyboardThemeSettings.write(this@ComposeMainActivity, KeyboardThemeMode.valueOf(theme.name))
        }

        override fun seoulThemeUnlockRemainingMillis(): Long =
            KeyboardThemeSettings.seoulUnlockRemainingMillis(this@ComposeMainActivity)

        override fun isSeoulThemeAdPending(): Boolean = seoulAdPending

        override fun seoulThemeAdMessage(): String? = seoulAdMessage

        override fun readThemes(): List<SharedTheme> = ThemeCatalog.load(this@ComposeMainActivity).orEmpty().map { entry ->
            val palette = entry.palette ?: KeyboardThemeSettings.LIGHT
            SharedTheme(
                id = entry.id,
                titleKo = entry.titleKo,
                kind = entry.kind,
                unlock = entry.unlock,
                swatch = SharedThemeSwatch(
                    surface = palette.keyboardSurface,
                    key = palette.keySurface,
                    text = palette.text,
                    accent = palette.accent,
                ),
                available = entry.available,
                previewAsset = entry.previewAsset,
            )
        }

        override fun requestSeoulThemeAd(theme: KeyboardThemeChoice): Boolean {
            if (theme != KeyboardThemeChoice.SEOUL_DAY && theme != KeyboardThemeChoice.SEOUL_NIGHT) return false
            if (seoulAdPending) return false
            if (BuildConfig.MOCK_ADS) {
                seoulAdMessage = null
                KeyboardThemeSettings.grantSeoulUnlock(this@ComposeMainActivity)
                KeyboardThemeSettings.write(this@ComposeMainActivity, KeyboardThemeMode.valueOf(theme.name))
                seoulAdMessage = "서울 테마가 24시간 열렸습니다."
                return true
            }
            if (!canRequestAds()) {
                seoulAdMessage = "광고 개인정보 선택이 완료되지 않았습니다."
                consent.refresh()
                return false
            }
            val adUnitId = BuildConfig.REWARDED_AD_UNIT_ID
            if (adUnitId.isBlank()) {
                seoulAdMessage = "광고 설정이 준비되지 않았습니다."
                return false
            }
            seoulAdPending = true
            seoulAdMessage = null
            MobileAds.initialize(this@ComposeMainActivity)
            RewardedAd.load(this@ComposeMainActivity, adUnitId, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        seoulAdPending = false
                        seoulAdMessage = "광고를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
                    }

                    override fun onAdLoaded(ad: RewardedAd) {
                        if (isFinishing || isDestroyed || !canRequestAds()) {
                            seoulAdPending = false
                            return
                        }
                        var rewarded = false
                        var shown = false
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                shown = true
                                gameEndAd.noteFullscreenShown()
                            }
                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                seoulAdPending = false
                                seoulAdMessage = "광고를 표시하지 못했습니다. 다시 시도해 주세요."
                            }

                            override fun onAdDismissedFullScreenContent() {
                                if (shown) gameEndAd.noteFullscreenShown()
                                seoulAdPending = false
                                if (!rewarded) seoulAdMessage = "광고 보상이 완료되지 않아 테마가 잠겨 있습니다."
                            }
                        }
                        ad.show(this@ComposeMainActivity) {
                            if (!rewarded) {
                                rewarded = true
                                KeyboardThemeSettings.grantSeoulUnlock(this@ComposeMainActivity)
                                KeyboardThemeSettings.write(this@ComposeMainActivity, KeyboardThemeMode.valueOf(theme.name))
                                seoulAdMessage = "서울 테마가 24시간 열렸습니다."
                            }
                        }
                    }
                })
            return true
        }

        private fun LayoutOrientation.toNativeOrientation(): KeyboardOrientation = when (this) {
            LayoutOrientation.PORTRAIT -> KeyboardOrientation.PORTRAIT
            LayoutOrientation.LANDSCAPE -> KeyboardOrientation.LANDSCAPE
        }

        private fun readProgress(): SharedLearningProgress = LearningProgressStore.read(this@ComposeMainActivity).let {
            SharedLearningProgress(it.totalSessions, it.bestAccuracy, it.rainHighScore, it.totalXp)
        }

        private fun readKeyboardStatus(): KeyboardStatus {
            val manager = getSystemService(InputMethodManager::class.java) ?: return KeyboardStatus.UNKNOWN
            val enabled = manager.enabledInputMethodList.any { it.serviceInfo.packageName == packageName }
            if (!enabled) return KeyboardStatus.DISABLED
            val selectedId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            return if (selectedId?.startsWith("$packageName/") == true) KeyboardStatus.ACTIVE else KeyboardStatus.ENABLED
        }
    }
}
