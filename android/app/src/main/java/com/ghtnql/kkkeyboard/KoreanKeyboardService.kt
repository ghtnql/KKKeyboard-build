package com.ghtnql.kkkeyboard

import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ghtnql.kkkeyboard.sharedcore.LongPressCatalog

class KoreanKeyboardService : InputMethodService() {
    private val composer = HangulComposer()
    private val candidateInput = CandidateInputBuffer()
    private val japaneseWord = JapaneseWordComposer()
    private val candidateLearning by lazy { JapaneseCandidateLearning.from(this) }
    private val cheonjiinInput = CheonjiinInput()
    private val cheonjiinComposer = CheonjiinComposer()
    private var compositionConnection: CompositionConnection? = null
    private val shiftedCharacterButtons = mutableListOf<Pair<Button, String>>()
    private val candidateButtons = mutableListOf<Button>()
    private val handler = Handler(Looper.getMainLooper())
    private val seoulExpiry = Runnable {
        val nextPalette = KeyboardThemeSettings.palette(this)
        if (nextPalette != palette) {
            palette = nextPalette
            keyboardRoot?.let { root ->
                applyKeyboardBackground(root)
                renderKeyboard(root, activeFieldMode)
            }
        }
        scheduleSeoulExpiry()
    }

    private fun scheduleSeoulExpiry() {
        handler.removeCallbacks(seoulExpiry)
        val remaining = KeyboardThemeSettings.seoulUnlockRemainingMillis(this)
        if (remaining > 0L && KeyboardThemeSettings.read(this) in listOf(KeyboardThemeMode.SEOUL_DAY, KeyboardThemeMode.SEOUL_NIGHT)) {
            handler.postDelayed(seoulExpiry, remaining + 50L)
        }
    }
    private var shiftEnabled = false
    private var shiftButton: Button? = null
    private var modeButton: Button? = null
    private var spaceButton: Button? = null
    private var enterButton: Button? = null
    private var modeLabel: TextView? = null
    private var symbolsEnabled = false
    private var symbolPage = 0
    private var deleting = false
    private var keyboardRoot: LinearLayout? = null
    private val toolbarMenu by lazy { KeyboardToolbarMenu(this) }
    private var menuBackPressed = false
    private var candidateRow: LinearLayout? = null
    private var candidateScroll: HorizontalScrollView? = null
    private var activeFieldMode = InputFieldMode.TEXT
    private var keyboardLanguage = KeyboardLanguage.KOREAN
    private var displayedCandidates: List<String> = emptyList()
    private var layoutOrientation = KeyboardOrientation.PORTRAIT
    private var keyHeightDp = KeyboardHeight.NORMAL.keyHeightDp
    private var numberRowEnabled = false
    private var inputLayout = InputLayout.CHEONJIIN
    private var longPressRevision = -1L
    private var flickDistanceDp = 20
    private var cheonjiinCycleTimeoutMs = 900
    private var hapticFeedbackEnabled = true
    private var palette = KeyboardThemeSettings.LIGHT
    private var loadedBackgroundAsset: String? = null
    private var loadedBackgroundBitmap: Bitmap? = null
    private val flickKeys = mutableListOf<FlickKeyView>()

    private val deleteRepeat = object : Runnable {
        override fun run() {
            if (!deleting) return
            withEditorBatch { currentInputConnection?.let(::handleBackspace) }
            handler.postDelayed(this, 55L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        keyboardLanguage = readInitialKeyboardLanguage()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Samsung DeX changes density, screen size and orientation at runtime. Re-read the
        // persisted user choice instead of letting a recreated/adapted input view fall back
        // to a desktop-looking QWERTY layout.
        layoutOrientation = KeyboardOrientation.fromConfigurationOrientation(newConfig.orientation)
        inputLayout = KeyboardLayoutSettings.readInputLayout(this)
        flickDistanceDp = KeyboardLayoutSettings.readFlickDistance(this)
        cheonjiinCycleTimeoutMs = KeyboardLayoutSettings.readCheonjiinCycleTimeout(this)
        hapticFeedbackEnabled = KeyboardLayoutSettings.readHapticFeedbackEnabled(this)
        palette = KeyboardThemeSettings.palette(this)
        applyLayoutPlan(layoutPlan(activeFieldMode, layoutOrientation))

        keyboardRoot?.let { root ->
            applyKeyboardBackground(root)
            renderKeyboard(root, activeFieldMode)
        }
        updateEnterButton()
        scheduleSeoulExpiry()
    }

    private fun readInitialKeyboardLanguage(): KeyboardLanguage {
        val saved = getSharedPreferences("keyboard_input_mode", MODE_PRIVATE)
            .getString("keyboard_language", null)
        KeyboardLanguage.entries.firstOrNull { it.name == saved }?.let { return it }
        return if (android.content.res.Resources.getSystem().configuration.locales[0].language == "ja") {
            KeyboardLanguage.JAPANESE
        } else {
            KeyboardLanguage.KOREAN
        }
    }

    override fun onCreateInputView(): View {
        JapaneseTransliterator.prepare()
        activeFieldMode = InputFieldModeResolver.fromInputType(currentInputEditorInfo?.inputType ?: 0)
        layoutOrientation = currentKeyboardOrientation()
        inputLayout = KeyboardLayoutSettings.readInputLayout(this)
        flickDistanceDp = KeyboardLayoutSettings.readFlickDistance(this)
        cheonjiinCycleTimeoutMs = KeyboardLayoutSettings.readCheonjiinCycleTimeout(this)
        hapticFeedbackEnabled = KeyboardLayoutSettings.readHapticFeedbackEnabled(this)
        palette = KeyboardThemeSettings.palette(this)
        applyLayoutPlan(layoutPlan(activeFieldMode, layoutOrientation))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            applyKeyboardWindowInsets(this)
            applyKeyboardBackground(this)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            keyboardRoot = this
            renderKeyboard(this, activeFieldMode)
        }
    }

    private fun applyKeyboardWindowInsets(root: View) {
        val baseHorizontalPadding = dp(4)
        val baseTopPadding = dp(6)
        val baseBottomPadding = dp(8)
        root.setPadding(baseHorizontalPadding, baseTopPadding, baseHorizontalPadding, baseBottomPadding)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val navigationBarBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(
                baseHorizontalPadding,
                baseTopPadding,
                baseHorizontalPadding,
                baseBottomPadding + navigationBarBottom,
            )
            insets
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        stopCursorRepeat()
        super.onStartInput(attribute, restarting)
        compositionConnection = null
        cancelFlickGestures()
        stopDeleteRepeat()
        if (!restarting) {
            symbolsEnabled = false
            symbolPage = 0
        }
        resetInputState()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        stopCursorRepeat()
        super.onStartInputView(info, restarting)
        stopDeleteRepeat()

        val nextMode = InputFieldModeResolver.fromInputType(info?.inputType ?: 0)
        val modeChanged = nextMode != activeFieldMode
        val nextOrientation = currentKeyboardOrientation()
        val orientationChanged = nextOrientation != layoutOrientation
        val nextPlan = layoutPlan(nextMode, nextOrientation)
        val heightChanged = nextPlan.keyHeightDp != keyHeightDp
        val numberRowChanged = nextPlan.numberRowEnabled != numberRowEnabled
        val nextLayout = KeyboardLayoutSettings.readInputLayout(this)
        val nextFlickDistance = KeyboardLayoutSettings.readFlickDistance(this)
        val nextCheonjiinTimeout = KeyboardLayoutSettings.readCheonjiinCycleTimeout(this)
        hapticFeedbackEnabled = KeyboardLayoutSettings.readHapticFeedbackEnabled(this)
        val nextPalette = KeyboardThemeSettings.palette(this)
        val themeChanged = nextPalette != palette
        val nextLongPressRevision = KeyboardLayoutSettings.readLongPressRevision(this)
        val longPressChanged = nextLongPressRevision != longPressRevision
        longPressRevision = nextLongPressRevision
        val inputLayoutChanged = nextLayout != inputLayout || nextFlickDistance != flickDistanceDp ||
            nextCheonjiinTimeout != cheonjiinCycleTimeoutMs
        inputLayout = nextLayout
        flickDistanceDp = nextFlickDistance
        cheonjiinCycleTimeoutMs = nextCheonjiinTimeout
        palette = nextPalette
        activeFieldMode = nextMode
        layoutOrientation = nextOrientation
        applyLayoutPlan(nextPlan)

        if (restarting && inputLayoutChanged && cheonjiinComposer.isActive) {
            currentInputConnection?.let(::commitPending)
        }
        if (!restarting) resetInputState()

        keyboardRoot?.let { root ->
            if (!restarting || modeChanged || orientationChanged || heightChanged || numberRowChanged || inputLayoutChanged || themeChanged || longPressChanged) {
                applyKeyboardBackground(root)
                renderKeyboard(root, nextMode)
            }
        }
        updateEnterButton()
        scheduleSeoulExpiry()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (compositionConnection?.acknowledge(newSelStart, newSelEnd, candidatesStart, candidatesEnd) == true) return
        if (japaneseWord.isActive) {
            if (newSelStart == candidatesEnd && newSelEnd == candidatesEnd) return
            japaneseWord.reset()
            cheonjiinInput.reset()
            clearCandidateTracking()
            currentInputConnection?.finishComposingText()
            return
        }
        if (cheonjiinComposer.isActive) {
            if (candidatesEnd >= 0 && newSelStart == candidatesEnd && newSelEnd == candidatesEnd) return
            cheonjiinComposer.reset()
            cheonjiinInput.reset()
            clearCandidateTracking()
            currentInputConnection?.finishComposingText()
            return
        }
        if (composer.currentText().isEmpty()) {
            cheonjiinInput.reset()
            return
        }
        if (newSelStart == candidatesEnd && newSelEnd == candidatesEnd) return
        composer.reset()
        cheonjiinInput.reset()
        clearCandidateTracking()
        currentInputConnection?.finishComposingText()
    }

    override fun onFinishInputView(finishingInput: Boolean) { toolbarMenu.dismiss(); menuBackPressed = false; stopCursorRepeat(); handler.removeCallbacks(seoulExpiry); cancelFlickGestures(); stopDeleteRepeat(); super.onFinishInputView(finishingInput) }
    override fun onWindowHidden() { toolbarMenu.dismiss(); menuBackPressed = false; stopCursorRepeat(); handler.removeCallbacks(seoulExpiry); cancelFlickGestures(); stopDeleteRepeat(); super.onWindowHidden() }
    override fun onFinishInput() { toolbarMenu.dismiss(); menuBackPressed = false; cancelFlickGestures(); stopDeleteRepeat(); resetInputState(); super.onFinishInput(); compositionConnection = null }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && toolbarMenu.isShowing) {
            toolbarMenu.dismiss()
            menuBackPressed = true
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && menuBackPressed) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && menuBackPressed) {
            menuBackPressed = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun getCurrentInputConnection(): InputConnection? {
        val connection = super.getCurrentInputConnection() ?: return null
        val cached = compositionConnection
        if (cached != null && cached.delegate === connection) return cached
        return CompositionConnection(connection, currentInputEditorInfo?.initialSelStart ?: -1, currentInputEditorInfo?.initialSelEnd ?: -1)
            .also { compositionConnection = it }
    }

    private inline fun withEditorBatch(action: () -> Unit) {
        val connection = currentInputConnection
        connection?.beginBatchEdit()
        try { action() } finally { connection?.endBatchEdit() }
    }
    override fun onDestroy() { toolbarMenu.dismiss(); stopCursorRepeat(); handler.removeCallbacks(seoulExpiry); stopDeleteRepeat(); keyboardRoot = null; loadedBackgroundBitmap = null; candidateRow = null; candidateScroll = null; candidateButtons.clear(); shiftedCharacterButtons.clear(); modeButton = null; spaceButton = null; enterButton = null; modeLabel = null; super.onDestroy() }

    private fun resetInputState() { composer.reset(); japaneseWord.reset(); cheonjiinInput.reset(); cheonjiinComposer.reset(); candidateInput.clear(); setShift(false); displayedCandidates = emptyList(); hideCandidateButtons() }

    private fun renderKeyboard(root: LinearLayout, mode: InputFieldMode) {
        toolbarMenu.dismiss()
        stopCursorRepeat()
        stopDeleteRepeat()
        cancelFlickGestures()
        flickKeys.clear()
        shiftedCharacterButtons.clear(); candidateButtons.clear(); shiftButton = null; modeButton = null; spaceButton = null; enterButton = null; modeLabel = null; candidateRow = null; candidateScroll = null; shiftEnabled = false; displayedCandidates = emptyList(); root.removeAllViews()
        when (mode) {
            InputFieldMode.NUMBER -> renderNumberKeyboard(root)
            InputFieldMode.PHONE -> renderPhoneKeyboard(root)
            InputFieldMode.EMAIL, InputFieldMode.URI, InputFieldMode.TEXT, InputFieldMode.PASSWORD -> renderTextKeyboard(root)
        }
        updateModeButtons()
        updateEnterButton()
        refreshCandidates(force = true)
    }

    private fun applyKeyboardBackground(root: View) {
        val asset = KeyboardThemeSettings.backgroundAsset(this, layoutOrientation)
        if (asset == null) {
            loadedBackgroundAsset = null
            loadedBackgroundBitmap = null
            root.setBackgroundColor(palette.keyboardSurface)
            return
        }
        // Cache key includes orientation because portrait night reuses the wide
        // seoul_night.jpg asset with a different in-memory crop than landscape.
        val cacheKey = asset + "|" + layoutOrientation.name
        if (cacheKey != loadedBackgroundAsset) {
            val decoded = runCatching { assets.open(asset).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            loadedBackgroundBitmap = if (decoded != null && shouldCropSeoulNightPortrait(asset)) {
                cropSeoulNightPortraitLeft(decoded)
            } else {
                decoded
            }
            loadedBackgroundAsset = cacheKey
        }
        val bitmap = loadedBackgroundBitmap
        if (bitmap == null) {
            root.setBackgroundColor(palette.keyboardSurface)
            return
        }
        val image = BitmapDrawable(resources, bitmap).apply { gravity = Gravity.FILL }
        val wash = if (asset.startsWith("seoul_day")) 0x1AFFFFFF else 0x18071428
        root.background = object : LayerDrawable(arrayOf(image, ColorDrawable(wash))) {
            override fun getMinimumWidth(): Int = 0
            override fun getMinimumHeight(): Int = 0
            override fun getIntrinsicWidth(): Int = -1
            override fun getIntrinsicHeight(): Int = -1
        }
    }

    private fun shouldCropSeoulNightPortrait(asset: String): Boolean =
        asset == "seoul_night.jpg" && layoutOrientation == KeyboardOrientation.PORTRAIT

    // Deterministic left crop of the 1440x480 wide night image: full height,
    // width = height * 8 / 5 (~768px), x=0. Tower at x~330 lands near center;
    // Namsan at x~1210 is excluded. Existing FILL gravity still applies after.
    private fun cropSeoulNightPortraitLeft(source: Bitmap): Bitmap {
        val cropWidth = minOf(source.width, (source.height * 8) / 5)
        if (cropWidth <= 0 || cropWidth >= source.width) return source
        return Bitmap.createBitmap(source, 0, 0, cropWidth, source.height)
    }

    private fun renderTextKeyboard(root: LinearLayout) {
        if (keyboardLanguage == KeyboardLanguage.JAPANESE && activeFieldMode.allowsCandidates) {
            root.addView(createJapaneseCandidateRow().also { candidateRow = it })
        }
        root.addView(createToolbarRow())
        if (numberRowEnabled) root.addView(createLiteralRow(TwoBeolsikLayout.auxiliaryNumberRow))
        val effectiveLayout = keyboardLanguage.effectiveLayout(inputLayout)
        if (effectiveLayout == InputLayout.HANGUL_FLICK && !symbolsEnabled) {
            renderFlickRows(root)
            return
        }
        if (effectiveLayout == InputLayout.CHEONJIIN_PLUS && !symbolsEnabled) {
            renderCheonjiinPlusRows(root)
            return
        }
        if (effectiveLayout == InputLayout.CHEONJIIN && !symbolsEnabled) {
            renderCheonjiinRows(root)
            return
        }
        if (symbolsEnabled) {
            val page = SymbolLayout.pages[symbolPage]
            root.addView(createLiteralRow(page.top))
            root.addView(createLiteralRow(page.middle))
            root.addView(createSymbolBottomRow(page.bottom))
        } else {
            val rows = if (keyboardLanguage == KeyboardLanguage.ENGLISH) {
                LatinQwertyLayout.characterRows
            } else {
                TwoBeolsikLayout.characterRows
            }
            rows.forEach { root.addView(createCharacterRow(it)) }
            root.addView(createBottomCharacterRow())
        }
        root.addView(createActionRow())
    }

    private fun renderFlickRows(root: LinearLayout) {
        HangulFlickLayout.rows.forEachIndexed { index, keys ->
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                val left = when (index) {
                    0 -> createCursorButton(false).apply { layoutParams = keyLayoutParams(1.1f) }
                    1 -> createCursorButton(true).apply { layoutParams = keyLayoutParams(1.1f) }
                    2 -> createSymbolsButton(1.1f)
                    else -> createModeButton(1.1f)
                }
                addView(left)
                keys.forEach { addView(createFlickKey(it, 1.9f)) }
                addView(when (index) {
                    0 -> createBackspaceButton(1.1f)
                    1 -> createEnterButton(1.1f)
                    2 -> {
                        val punctuation = when (activeFieldMode) {
                            InputFieldMode.EMAIL -> HangulFlickLayout.punctuation.copy(center = "@")
                            InputFieldMode.URI -> HangulFlickLayout.punctuation.copy(center = "/")
                            else -> HangulFlickLayout.punctuation
                        }
                        createFlickKey(punctuation, 1.1f, literal = true)
                    }
                    else -> createActionButton("␣", 1.1f) { currentInputConnection?.let(::handleSpace) }.also {
                        spaceButton = it
                        it.commitOnTouchDown()
                        it.contentDescription = getString(R.string.key_space)
                        it.tooltipText = it.contentDescription
                        it.setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP)
                    }
                })
            })
        }
    }

    private fun renderCheonjiinRows(root: LinearLayout) {
        CheonjiinInput.rows.forEachIndexed { index, keys ->
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                addView(when (index) {
                    0 -> createCursorButton(false)
                    1 -> createCursorButton(true).also {
                        it.contentDescription = getString(R.string.key_next_letter_or_cursor_right)
                    }
                    2 -> createLiteralButton("!", 1f)
                    else -> createLiteralButton("?", 1f)
                })
                if (index == 3) {
                    addView(createSymbolsButton(1f))
                    addView(createModeButton(1f))
                    addView(createCheonjiinKey(CheonjiinKey.IEUNG, 2f))
                    addView(createActionButton("␣", 2f) { currentInputConnection?.let(::handleSpace) }.also {
                        spaceButton = it
                        it.commitOnTouchDown()
                        it.contentDescription = getString(R.string.key_space)
                        it.tooltipText = it.contentDescription
                        it.setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP)
                    })
                } else {
                    keys.forEach { addView(createCheonjiinKey(it, 2f)) }
                }
                addView(when (index) {
                    0 -> createBackspaceButton(1f)
                    1 -> createEnterButton(1f)
                    2 -> createLiteralButton(when (activeFieldMode) {
                        InputFieldMode.EMAIL -> "@"
                        InputFieldMode.URI -> "/"
                        else -> ","
                    }, 1f)
                    else -> createLiteralButton(".", 1f)
                })
            })
        }
    }

    private fun createCheonjiinKey(key: CheonjiinKey, weight: Float) = createKeyButton(key.label, weight).apply {
        contentDescription = if (key.consonants.isEmpty()) key.label else key.consonants.toCharArray().joinToString(", ")
        setOnClickListener {
            withEditorBatch {
                val action = cheonjiinInput.input(key, SystemClock.uptimeMillis(), cheonjiinCycleTimeoutMs)
                currentInputConnection?.let { connection -> applyCheonjiinAction(connection, action) }
            }
        }
        installLongPress(InputLayout.CHEONJIIN, key.label)
    }

    private fun isCheonjiinLayout(layout: InputLayout) =
        layout == InputLayout.CHEONJIIN || layout == InputLayout.CHEONJIIN_PLUS

    private fun renderCheonjiinPlusRows(root: LinearLayout) {
        val rows = listOf(
            listOf("ㅣ", "·", "ㅡ"),
            listOf("ㄱ", "ㅋ", "ㄴ", "ㄹ", "ㄷ", "ㅌ"),
            listOf("ㅂ", "ㅍ", "ㅅ", "ㅎ", "ㅈ", "ㅊ"),
        )
        rows.forEachIndexed { index, labels ->
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                addView(when (index) {
                    0 -> createCursorButton(false)
                    1 -> createCursorButton(true)
                    else -> createLiteralButton("!", 1f)
                })
                labels.forEach { label -> addView(createCheonjiinPlusKey(label, if (index == 0) 2f else 1f)) }
                addView(when (index) {
                    0 -> createBackspaceButton()
                    1 -> createEnterButton(1f)
                    else -> createLiteralButton(",", 1f)
                })
            })
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            // Exact 8-column alignment with the consonant row:
            // ? / ?123 / 한/日 / ㅇ / ㅁ / Space×2 / .
            addView(createLiteralButton("?", 1f))
            addView(createSymbolsButton(1f))
            addView(createModeButton(1f))
            addView(createCheonjiinPlusKey("ㅇ", 1f))
            addView(createCheonjiinPlusKey("ㅁ", 1f))
            addView(createActionButton("␣", 2f) { currentInputConnection?.let(::handleSpace) }.also {
                spaceButton = it
                it.commitOnTouchDown()
                it.contentDescription = getString(R.string.key_space)
                it.tooltipText = it.contentDescription
                it.setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP)
            })
            addView(createLiteralButton(".", 1f))
        })
    }

    private fun createCheonjiinPlusKey(label: String, weight: Float) = createKeyButton(label, weight).apply {
        setOnClickListener {
            withEditorBatch {
                val vowel = CheonjiinKey.entries.firstOrNull { it.vowelStroke != null && it.label == label }
                val now = SystemClock.uptimeMillis()
                val action = if (vowel != null) {
                    cheonjiinInput.input(vowel, now, cheonjiinCycleTimeoutMs)
                } else {
                    cheonjiinInput.inputDirectConsonant(label.single(), now, cheonjiinCycleTimeoutMs)
                }
                currentInputConnection?.let { applyCheonjiinAction(it, action) }
            }
        }
        installLongPress(InputLayout.CHEONJIIN_PLUS, label)
    }

    private fun Button.installLongPress(layout: InputLayout, keyId: String) {
        val key = LongPressCatalog.key(layout.persistedValue, keyId) ?: return
        val slots = KeyboardLayoutSettings.readLongPressSlots(this@KoreanKeyboardService, layout, keyId)
        val choices = LongPressCatalog.choices(layout.persistedValue, keyId, slots)
        val hint = key.fixedJamo.ifEmpty { slots.firstOrNull { it.isNotEmpty() }.orEmpty() }
        val baseDescription = contentDescription ?: text
        contentDescription = if (choices.isNotEmpty()) "$baseDescription, ${choices.joinToString(", ")}" else baseDescription
        (this as LongPressKeyButton).configureLongPress(choices, hint) { choice ->
            performKeyHaptic(this)
            withEditorBatch {
                currentInputConnection?.let { connection ->
                    if (key.fixedJamo.isNotEmpty() && choice == key.fixedJamo) {
                        applyCheonjiinAction(connection, cheonjiinInput.inputDirectConsonant(choice.single()))
                    } else {
                        commitPending(connection)
                        clearCandidateTracking()
                        connection.commitText(choice, 1)
                        if (shiftEnabled) setShift(false)
                    }
                }
            }
        }
        tooltipText = contentDescription
    }

    private fun createFlickKey(key: FlickKey, weight: Float, literal: Boolean = false) = FlickKeyView(
        this, key, flickDistanceDp, palette,
        onInput = { label ->
            performKeyHaptic(keyboardRoot)
            withEditorBatch {
                if (literal) currentInputConnection?.let { connection ->
                    commitPending(connection)
                    clearCandidateTracking()
                    connection.commitText(label, 1)
                } else handleCharacter(label)
            }
        },
        onPreview = { label ->
            if (label != null) {
                candidateScroll?.visibility = View.GONE
                modeLabel?.apply { visibility = View.VISIBLE; text = label; textSize = 24f }
            } else {
                modeLabel?.textSize = 14f
                updateModeButtons()
                modeLabel?.visibility = View.VISIBLE
                candidateScroll?.visibility = if (displayedCandidates.isEmpty()) View.GONE else View.VISIBLE
            }
        },
    ).apply { layoutParams = keyLayoutParams(weight); styleKey(this, control = literal) }.also { flickKeys += it }

    private fun cancelFlickGestures() { flickKeys.forEach { it.cancelGesture() } }

    private fun showLayoutMenu(anchor: View) {
        toolbarMenu.show(anchor, InputLayout.entries.map { layout ->
            getString(layoutLabelResource(layout)) to (inputLayout == layout)
        }) { index ->
            cheonjiinInput.reset()
            inputLayout = InputLayout.entries[index]
            KeyboardLayoutSettings.writeInputLayout(this, inputLayout)
            keyboardRoot?.let { renderKeyboard(it, activeFieldMode) }
        }
    }

    private fun showPhraseMenu(anchor: View) {
        val phrases = UserPhraseStore.read(this)
        if (phrases.isEmpty()) {
            toolbarMenu.dismiss()
            Toast.makeText(this, R.string.phrases_empty_keyboard, Toast.LENGTH_SHORT).show()
            return
        }
        toolbarMenu.show(anchor, phrases.map { it.title to false }) { index ->
            val selected = phrases.getOrNull(index)?.let { shown ->
                UserPhraseStore.read(this).firstOrNull { it.id == shown.id }
            } ?: return@show
            insertPhrase(selected.content)
        }
    }

    private fun insertPhrase(content: String) {
        val connection = currentInputConnection ?: return
        withEditorBatch {
            commitPending(connection)
            clearCandidateTracking()
            connection.commitText(content, 1)
        }
    }

    private fun layoutLabelResource(layout: InputLayout) = when (layout) {
        InputLayout.QWERTY -> R.string.layout_qwerty
        InputLayout.CHEONJIIN -> R.string.layout_cheonjiin
        InputLayout.CHEONJIIN_PLUS -> R.string.layout_cheonjiin_plus
        InputLayout.HANGUL_FLICK -> R.string.layout_flick
    }

    private fun renderNumberKeyboard(root: LinearLayout) {
        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9")).forEach { root.addView(createLiteralRow(it)) }
        root.addView(createLiteralRow(listOf("-","0",".")))
        root.addView(createCompactActionRow())
    }

    private fun renderPhoneKeyboard(root: LinearLayout) {
        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","#")).forEach { root.addView(createLiteralRow(it)) }
        root.addView(createLiteralRow(listOf("+","-","(",")")))
        root.addView(createCompactActionRow())
    }

    private fun createJapaneseCandidateRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER_VERTICAL
        val candidateHeight = dp(minOf(keyHeightDp, 44) + 2)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, candidateHeight,
        ).apply { bottomMargin = dp(2) }
        val candidates = LinearLayout(this@KoreanKeyboardService).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        repeat(JapaneseDictionary.MAX_CANDIDATES) {
            val button = createActionButton("", 1f) {}
            button.visibility = View.GONE
            button.isSingleLine = true
            button.setPadding(dp(12), 0, dp(12), 0)
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, candidateHeight,
            )
            button.setOnClickListener { withEditorBatch { selectCandidate(button.text.toString()) } }
            candidateButtons += button
            candidates.addView(button)
        }
        addView(HorizontalScrollView(this@KoreanKeyboardService).apply {
            candidateScroll = this
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, candidateHeight, 1f)
            addView(candidates)
        })
    }

    private fun createToolbarRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val toolbarHeight = dp(minOf(keyHeightDp, 44) + 2)
        if (keyboardLanguage.effectiveLayout(inputLayout) == InputLayout.QWERTY || symbolsEnabled) {
            addView(createCursorButton(false).apply {
                if (symbolsEnabled) text = "◀"
                layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
            })
            addView(createCursorButton(true).apply {
                if (symbolsEnabled) text = "▶"
                layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
            })
        }
        addView(TextView(this@KoreanKeyboardService).apply {
            modeLabel = this
            textSize = 14f
            setTextColor(palette.text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, toolbarHeight, 1f)
        })
        addView(createActionButton("◐", 0f) {}.also {
            it.contentDescription = getString(R.string.theme_title)
            it.tooltipText = it.contentDescription
            it.layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
            it.setOnClickListener { openThemeScreen() }
        })
        if (activeFieldMode != InputFieldMode.PASSWORD) {
            addView(createActionButton("▤", 0f) {}.also {
                it.id = R.id.key_phrases
                it.contentDescription = getString(R.string.key_phrases)
                it.tooltipText = it.contentDescription
                it.setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, TypedValue.COMPLEX_UNIT_SP)
                it.layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
                it.setOnClickListener { view -> showPhraseMenu(view) }
            })
        }
        addView(createActionButton("⌨", 0f) {}.also {
            it.contentDescription = getString(R.string.input_layout)
            it.tooltipText = it.contentDescription
            it.setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, TypedValue.COMPLEX_UNIT_SP)
            it.layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
            it.setOnClickListener { view -> showLayoutMenu(view) }
        })
        addView(createNextKeyboardButton(0f).also {
            it.layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
        })
        addView(createActionButton("⚙", 0f) { openKeyboardSettings() }.also {
            it.contentDescription = getString(R.string.keyboard_settings)
            it.setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, TypedValue.COMPLEX_UNIT_SP)
            it.layoutParams = LinearLayout.LayoutParams(dp(44), toolbarHeight)
        })
    }
    private fun createCharacterRow(labels: List<String>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        val inset = (TwoBeolsikLayout.ROW_UNITS - labels.size) / 2f
        if (inset > 0f) addView(View(this@KoreanKeyboardService), LinearLayout.LayoutParams(0, 1, inset))
        labels.forEach { addView(createCharacterButton(it)) }
        if (inset > 0f) addView(View(this@KoreanKeyboardService), LinearLayout.LayoutParams(0, 1, inset))
    }
    private fun createBottomCharacterRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER
        addView(createActionButton("⇧", TwoBeolsikLayout.EDGE_KEY_UNITS) { toggleShift() }.also {
            shiftButton = it
            it.textSize = 24f
            it.setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP)
            it.contentDescription = getString(R.string.key_shift)
        })
        val labels = if (keyboardLanguage == KeyboardLanguage.ENGLISH) {
            LatinQwertyLayout.bottomRow
        } else {
            TwoBeolsikLayout.bottomRow
        }
        labels.forEach { addView(createCharacterButton(it)) }
        addView(createBackspaceButton(TwoBeolsikLayout.EDGE_KEY_UNITS))
    }
    private fun createSymbolBottomRow(labels: List<String>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        addView(createActionButton("${symbolPage + 1}/${SymbolLayout.pages.size}", TwoBeolsikLayout.EDGE_KEY_UNITS) {
            symbolPage = (symbolPage + 1) % SymbolLayout.pages.size
            keyboardRoot?.let { renderKeyboard(it, activeFieldMode) }
        }.also { it.contentDescription = getString(R.string.key_symbol_page) })
        labels.forEach { addView(createLiteralButton(it)) }
        addView(createBackspaceButton(TwoBeolsikLayout.EDGE_KEY_UNITS))
    }
    private fun createLiteralRow(labels: List<String>) = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; isBaselineAligned=false; gravity=Gravity.CENTER; labels.forEach { addView(createLiteralButton(it)) } }
    private var cursorRepeatRunnable: Runnable? = null
    private var cursorRepeatToken = 0
    private var cursorRepeatOwner: android.view.View? = null
    private var cursorRepeatKeyCode = 0
    private var cursorRepeatHoldStarted = false
    private var cursorRepeatCancelled = false
    private var cursorRepeatPointerId = -1

    private fun configureCursorRepeat(button: android.view.View, keyCode: Int) {
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    stopCursorRepeat()
                    cursorRepeatCancelled = false
                    cursorRepeatHoldStarted = false
                    cursorRepeatPointerId = event.getPointerId(0)
                    cursorRepeatOwner = v
                    cursorRepeatKeyCode = keyCode
                    v.isPressed = true
                    cursorRepeatToken += 1
                    val token = cursorRepeatToken
                    val owner = v
                    val runnable = object : Runnable {
                        override fun run() {
                            if (token != cursorRepeatToken || owner !== cursorRepeatOwner) return
                            if (!owner.isEnabled) {
                                owner.isPressed = false
                                stopCursorRepeat()
                                return
                            }
                            if (!cursorRepeatHoldStarted) {
                                currentInputConnection?.let(::commitPending)
                                clearCandidateTracking()
                            }
                            cursorRepeatHoldStarted = true
                            moveCursor(cursorRepeatKeyCode)
                            handler.postDelayed(this, 50L)
                        }
                    }
                    cursorRepeatRunnable = runnable
                    handler.postDelayed(runnable, 350L)
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    // Single primary finger only: second finger cancels the whole gesture, never clicks.
                    cursorRepeatCancelled = true
                    stopCursorRepeat()
                    v.isPressed = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val idx = event.findPointerIndex(cursorRepeatPointerId)
                    if (cursorRepeatOwner !== v) return@setOnTouchListener true
                    if (idx < 0) {
                        cursorRepeatCancelled = true
                        stopCursorRepeat()
                        v.isPressed = false
                        return@setOnTouchListener true
                    }
                    val slop = dp(12).toFloat()
                    if (event.getX(idx) < -slop || event.getX(idx) > v.width + slop ||
                        event.getY(idx) < -slop || event.getY(idx) > v.height + slop
                    ) {
                        cursorRepeatCancelled = true
                        stopCursorRepeat()
                        v.isPressed = false
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (cursorRepeatOwner !== v) return@setOnTouchListener true
                    val owner = cursorRepeatOwner
                    val held = cursorRepeatHoldStarted
                    val cancelled = cursorRepeatCancelled
                    val isPrimary = event.getPointerId(event.actionIndex) == cursorRepeatPointerId
                    stopCursorRepeat()
                    v.isPressed = false
                    // Consume whole gesture: short tap fires exactly one OnClick, held release fires none.
                    if (!held && !cancelled && isPrimary && owner === v) {
                        v.performClick()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (cursorRepeatOwner === v) stopCursorRepeat()
                    v.isPressed = false
                    true
                }
                else -> true
            }
        }
    }

    private fun stopCursorRepeat() {
        cursorRepeatToken += 1
        cursorRepeatRunnable?.let(handler::removeCallbacks)
        cursorRepeatRunnable = null
        cursorRepeatOwner?.isPressed = false
        cursorRepeatOwner = null
        cursorRepeatHoldStarted = false
        cursorRepeatPointerId = -1
    }

    private fun createCursorButton(right: Boolean) = createActionButton(if (right) "→" else "←", 1f) {
        moveCursor(if (right) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }.also {
        configureCursorRepeat(it, if (right) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        it.contentDescription = getString(if (right) R.string.key_cursor_right else R.string.key_cursor_left)
        it.tooltipText = it.contentDescription
    }
    private fun createActionRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER
        addView(createSymbolsButton(1.4f))
        addView(createModeButton(1.4f))
        val punctuation = when (activeFieldMode) {
            InputFieldMode.EMAIL -> "@"
            InputFieldMode.URI -> "/"
            else -> ","
        }
        addView(createLiteralButton(punctuation, .8f))
        addView(createActionButton("", 4.1f) { currentInputConnection?.let(::handleSpace) }.also {
            spaceButton = it
            it.commitOnTouchDown()
            it.contentDescription = getString(R.string.key_space)
            styleKey(it, control = false)
        })
        addView(createLiteralButton(".", .8f))
        addView(createEnterButton(1.5f))
    }

    private fun createSymbolsButton(weight: Float) = createActionButton(if (symbolsEnabled) lettersButtonLabel() else "?123", weight) {
            cheonjiinInput.reset()
            symbolsEnabled = !symbolsEnabled
            symbolPage = 0
            keyboardRoot?.let { renderKeyboard(it, activeFieldMode) }
        }.also {
            it.textSize = 12f
            it.setAutoSizeTextTypeUniformWithConfiguration(10, 12, 1, TypedValue.COMPLEX_UNIT_SP)
            it.contentDescription = getString(if (symbolsEnabled) R.string.key_letters else R.string.key_symbols)
        }

    private fun createModeButton(weight: Float): LongPressKeyButton =
        createActionButton("", weight) { cycleKeyboardLanguage() }.also {
            modeButton = it
            it.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP)
            it.configureLongPress(listOf("한국어", "日本語", "English"), "") { label ->
                selectKeyboardLanguage(
                    when (label) {
                        "日本語" -> KeyboardLanguage.JAPANESE
                        "English" -> KeyboardLanguage.ENGLISH
                        else -> KeyboardLanguage.KOREAN
                    }
                )
            }
        }

    private fun createCompactActionRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER
        addView(createCursorButton(false))
        addView(createCursorButton(true))
        addView(createNextKeyboardButton(1f))
        addView(createEnterButton(1.3f))
        addView(createBackspaceButton())
    }

    private fun performKeyHaptic(view: View?) {
        if (hapticFeedbackEnabled) view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun createKeyButton(label: String, weight: Float = 1f, control: Boolean = false) = object : LongPressKeyButton(this@KoreanKeyboardService) {
        override fun performClick(): Boolean {
            performKeyHaptic(this)
            return super.performClick()
        }
    }.apply {
        text = label
        textSize = if (control) 14f else 20f
        isAllCaps = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(2), 0, dp(2), 0)
        layoutParams = keyLayoutParams(weight)
        styleKey(this, control)
        setAutoSizeTextTypeUniformWithConfiguration(10, if (control) 14 else 20, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    private fun styleKey(button: Button, control: Boolean, accent: Boolean = false) {
        val seoulNight = palette === KeyboardThemeSettings.SEOUL_NIGHT
        val seoulDay = palette === KeyboardThemeSettings.SEOUL_DAY
        if (seoulNight || seoulDay) {
            fun glass(pressed: Boolean, selected: Boolean): LayerDrawable {
                val emphasis = accent || selected
                val shadow = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (seoulNight) Color.argb(92, 8, 13, 36) else Color.argb(49, 67, 100, 128))
                }
                val fill = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, when {
                    seoulNight && emphasis -> intArrayOf(Color.argb(220, 101, 137, 195), Color.argb(179, 69, 83, 149))
                    seoulNight && control -> intArrayOf(Color.argb(179, 74, 85, 130), Color.argb(153, 45, 56, 99))
                    seoulNight -> intArrayOf(Color.argb(172, 137, 143, 184), Color.argb(143, 66, 78, 127))
                    emphasis -> intArrayOf(Color.argb(237, 131, 193, 225), Color.argb(204, 68, 132, 180))
                    control -> intArrayOf(Color.argb(226, 242, 250, 255), Color.argb(187, 198, 224, 240))
                    else -> intArrayOf(Color.argb(222, 255, 254, 250), Color.argb(177, 224, 241, 250))
                }).apply {
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1).coerceAtLeast(1), if (seoulNight) Color.argb(225, 190, 207, 255) else Color.argb(221, 126, 177, 208))
                    if (pressed) alpha = 205
                }
                val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
                    if (seoulNight) Color.argb(73, 242, 243, 255) else Color.argb(126, 255, 255, 255),
                    Color.TRANSPARENT,
                )).apply { cornerRadius = dp(9).toFloat() }
                return LayerDrawable(arrayOf(shadow, fill, sheen)).apply {
                    setLayerInset(1, 0, 0, 0, dp(2))
                    setLayerInset(2, dp(1), dp(1), dp(1), dp(3))
                }
            }
            val background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_activated), glass(pressed = false, selected = true))
                addState(intArrayOf(android.R.attr.state_pressed), glass(pressed = true, selected = false))
                addState(intArrayOf(), glass(pressed = false, selected = false))
            }
            button.backgroundTintList = null
            button.background = InsetDrawable(RippleDrawable(ColorStateList.valueOf(palette.ripple), background, null), dp(1))
            button.setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
                intArrayOf(if (seoulNight) Color.WHITE else Color.rgb(17, 54, 82),
                    if (seoulNight) Color.WHITE else if (accent) Color.rgb(17, 54, 82) else Color.rgb(28, 48, 67)),
            ))
            button.stateListAnimator = null
            button.elevation = 0f
            return
        }
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(color)
            setStroke(dp(1).coerceAtLeast(1), if (accent) palette.accent else palette.border)
        }
        val surface = if (accent) palette.accent else if (control) palette.controlSurface else palette.keySurface
        val background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated), shape(palette.accent))
            addState(intArrayOf(), shape(surface))
        }
        button.backgroundTintList = null
        button.background = InsetDrawable(RippleDrawable(ColorStateList.valueOf(palette.ripple), background, null), dp(1))
        button.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(palette.onAccent, if (accent) palette.onAccent else palette.text),
        ))
        button.stateListAnimator = null
        button.elevation = 0f
    }

    private fun createCharacterButton(baseLabel: String) = createKeyButton(baseLabel).apply {
        setOnClickListener { withEditorBatch { handleCharacter(baseLabel) } }
        val key = LongPressCatalog.keys("qwerty").firstOrNull {
            if (keyboardLanguage == KeyboardLanguage.ENGLISH) it.id == baseLabel else it.label == baseLabel
        }
        if (key != null) installLongPress(InputLayout.QWERTY, key.id)
    }.also { if (hasShiftVariant(baseLabel)) shiftedCharacterButtons += it to baseLabel }

    private fun createLiteralButton(label: String, weight: Float = 1f) = createKeyButton(label, weight).apply {
        setOnClickListener {
            withEditorBatch { currentInputConnection?.let { connection ->
                commitPending(connection)
                clearCandidateTracking()
                connection.commitText(label, 1)
            } }
        }
        commitOnTouchDown()
    }

    private fun Button.commitOnTouchDown() {
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performClick()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) {
                        view.isPressed = false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.isPressed = false
            }
            true
        }
    }

    private fun createActionButton(label: String, weight: Float, onClick: () -> Unit) = createKeyButton(label, weight, control = true).apply {
        setOnClickListener { withEditorBatch { onClick() } }
    }
    private fun createNextKeyboardButton(weight:Float)=createActionButton("🌐",weight){currentInputConnection?.let{connection->commitPending(connection);clearCandidateTracking()};switchToNextKeyboard()}.also{it.contentDescription=getString(R.string.select_keyboard);it.setAutoSizeTextTypeUniformWithConfiguration(14,20,1,TypedValue.COMPLEX_UNIT_SP)}

    private fun createBackspaceButton(weight: Float = 1f) = createActionButton("⌫", weight) {
        currentInputConnection?.let(::handleBackspace)
    }.apply {
        textSize = 24f
        setAutoSizeTextTypeUniformWithConfiguration(14, 24, 1, TypedValue.COMPLEX_UNIT_SP)
        contentDescription = getString(R.string.key_delete)
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopDeleteRepeat()
                    view.isPressed = true
                    view.performClick()
                    deleting = true
                    this@KoreanKeyboardService.handler.postDelayed(deleteRepeat, 400L)
                }
                MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) {
                    view.isPressed = false
                    stopDeleteRepeat()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    stopDeleteRepeat()
                }
            }
            true
        }
    }

    private fun createEnterButton(weight: Float) = createActionButton("↵", weight) {
        currentInputConnection?.let(::handleEnter)
    }.also {
        enterButton = it
        styleKey(it, control = true, accent = true)
        it.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    private fun updateEnterButton() {
        val button = enterButton ?: return
        val info = currentInputEditorInfo
        val action = EnterActionResolver.actionId(info?.imeOptions ?: 0, info?.actionLabel, info?.actionId ?: 0)
        val label = when {
            japaneseWord.isActive -> getString(R.string.key_convert)
            action != null && !info?.actionLabel.isNullOrEmpty() -> info?.actionLabel.toString()
            action == EditorInfo.IME_ACTION_SEARCH -> getString(R.string.key_search)
            action == EditorInfo.IME_ACTION_SEND -> getString(R.string.key_send)
            action == EditorInfo.IME_ACTION_DONE -> getString(R.string.key_done)
            action == EditorInfo.IME_ACTION_GO -> getString(R.string.key_go)
            action == EditorInfo.IME_ACTION_NEXT -> "→"
            action == EditorInfo.IME_ACTION_PREVIOUS -> "←"
            else -> "↵"
        }
        if (button.text.toString() != label || button.contentDescription == null) {
            button.text = label
            val maxTextSize = if (label == "↵" || label == "→" || label == "←") 24 else 14
            button.setAutoSizeTextTypeUniformWithConfiguration(10, maxTextSize, 1, TypedValue.COMPLEX_UNIT_SP)
            button.contentDescription = when (label) {
                "→" -> getString(R.string.key_next)
                "←" -> getString(R.string.key_previous)
                "↵" -> getString(R.string.key_return)
                else -> label
            }
            button.tooltipText = button.contentDescription
        }
        val spaceDescription = getString(if (japaneseWord.isActive) R.string.key_convert else R.string.key_space)
        if (spaceButton?.contentDescription != spaceDescription) spaceButton?.contentDescription = spaceDescription
    }

    private fun updateModeButtons() {
        val effectiveLanguage = if (keyboardLanguage == KeyboardLanguage.JAPANESE && !activeFieldMode.allowsCandidates) {
            KeyboardLanguage.KOREAN
        } else keyboardLanguage
        val label = getString(when (effectiveLanguage) {
            KeyboardLanguage.KOREAN -> R.string.mode_korean
            KeyboardLanguage.JAPANESE -> R.string.mode_japanese
            KeyboardLanguage.ENGLISH -> R.string.mode_english
        })
        modeLabel?.text = label
        spaceButton?.text = if (keyboardLanguage.effectiveLayout(inputLayout) != InputLayout.QWERTY && !symbolsEnabled) "␣" else label
        modeButton?.apply {
            isEnabled = true
            alpha = 1f
            isActivated = effectiveLanguage != KeyboardLanguage.KOREAN
            text = when (effectiveLanguage) {
                KeyboardLanguage.KOREAN -> "한/日"
                KeyboardLanguage.JAPANESE -> "ABC"
                KeyboardLanguage.ENGLISH -> getString(R.string.mode_korean_short)
            }
            contentDescription = getString(when (effectiveLanguage) {
                KeyboardLanguage.KOREAN -> if (activeFieldMode.allowsCandidates) R.string.switch_to_japanese else R.string.switch_to_english
                KeyboardLanguage.JAPANESE -> R.string.switch_to_english
                KeyboardLanguage.ENGLISH -> R.string.switch_to_korean
            })
            tooltipText = contentDescription
        }
    }
    private fun stopDeleteRepeat(){deleting=false;handler.removeCallbacks(deleteRepeat)}
    private fun handleCharacter(baseLabel: String) {
        val connection = currentInputConnection ?: return
        if (keyboardLanguage == KeyboardLanguage.ENGLISH) {
            val character = LatinQwertyLayout.labelFor(baseLabel, shiftEnabled)
            commitPending(connection)
            clearCandidateTracking()
            connection.commitText(character, 1)
            if (shiftEnabled) setShift(false)
            return
        }
        val character = TwoBeolsikLayout.labelFor(baseLabel, shiftEnabled).singleOrNull() ?: return
        cheonjiinInput.reset()
        handleJamo(connection, character)
        if (shiftEnabled) setShift(false)
    }

    private fun handleJamo(connection: InputConnection, character: Char, replaceLast: Boolean = false) {
        if (keyboardLanguage == KeyboardLanguage.JAPANESE && activeFieldMode.allowsCandidates) {
            val accepted = if (replaceLast) japaneseWord.replaceLast(character) else japaneseWord.input(character)
            if (!accepted) {
                commitPending(connection)
                japaneseWord.input(character)
            }
            connection.setComposingText(japaneseWord.text, 1)
        } else {
            val edit = if (replaceLast) composer.replaceLast(character) else composer.input(character)
            if (activeFieldMode.allowsCandidates) candidateInput.apply(edit)
            applyEdit(connection, edit)
        }
        refreshCandidates()
    }

    private fun applyCheonjiinAction(connection: InputConnection, action: CheonjiinAction) {
        if (keyboardLanguage != KeyboardLanguage.JAPANESE || !activeFieldMode.allowsCandidates) {
            applyKoreanCheonjiinAction(connection, action)
            return
        }
        when (action) {
            CheonjiinAction.None -> Unit
            is CheonjiinAction.Append -> handleJamo(connection, action.character)
            is CheonjiinAction.ReplaceLast -> handleJamo(connection, action.character, replaceLast = true)
            CheonjiinAction.RemoveLast -> if (!deleteComposedJamo(connection)) {
                connection.deleteSurroundingTextInCodePoints(1, 0)
            }
        }
    }
    private fun applyKoreanCheonjiinAction(connection: InputConnection, action: CheonjiinAction) {
        if (action == CheonjiinAction.None) return
        // Some editors change their text without a selection callback. Never replay over it.
        val activeText = cheonjiinComposer.text
        val observedText = if (cheonjiinComposer.isActive) connection.getTextBeforeCursor(activeText.length, 0) else null
        val changedExternally = observedText != null && observedText.toString() != activeText
        if (changedExternally) {
            cheonjiinComposer.reset()
            cheonjiinInput.reset()
            clearCandidateTracking()
            connection.finishComposingText()
        }
        when (action) {
            is CheonjiinAction.Append -> {
                if (cheonjiinComposer.isFull) {
                    connection.commitText(cheonjiinComposer.text, 1)
                    cheonjiinComposer.reset()
                }
                if (composer.currentText().isNotEmpty()) {
                    val pending = composer.flush()
                    connection.commitText(pending, 1)
                }
                cheonjiinComposer.append(action.character)
            }
            is CheonjiinAction.ReplaceLast -> {
                if (!cheonjiinComposer.replaceLast(action.character)) cheonjiinComposer.append(action.character)
            }
            CheonjiinAction.RemoveLast -> {
                if (!cheonjiinComposer.removeLast()) {
                    if (!deleteComposedJamo(connection)) connection.deleteSurroundingTextInCodePoints(1, 0)
                    return
                }
            }
            CheonjiinAction.None -> Unit
        }
        connection.setComposingText(cheonjiinComposer.text, 1)
        if (!cheonjiinComposer.isActive) connection.finishComposingText()
        refreshCandidates()
    }
    private fun toggleShift()=setShift(!shiftEnabled)
    private fun setShift(enabled:Boolean){if(shiftEnabled==enabled)return;shiftEnabled=enabled;shiftedCharacterButtons.forEach{(button,baseLabel)->button.text=characterLabelFor(baseLabel,enabled)};shiftButton?.isActivated=enabled}
    private fun cycleKeyboardLanguage() {
        val next = keyboardLanguage.next().let { candidate ->
            if (candidate == KeyboardLanguage.JAPANESE && !activeFieldMode.allowsCandidates) candidate.next() else candidate
        }
        selectKeyboardLanguage(next)
    }
    private fun selectKeyboardLanguage(language: KeyboardLanguage) {
        stopCursorRepeat()
        currentInputConnection?.let(::commitPending)
        clearCandidateTracking()
        keyboardLanguage = if (language == KeyboardLanguage.JAPANESE && !activeFieldMode.allowsCandidates) {
            KeyboardLanguage.KOREAN
        } else {
            language
        }
        // Persists only keyboard_language; saved inputLayout is left untouched
        // so English renders as a temporary QWERTY overlay.
        getSharedPreferences("keyboard_input_mode", MODE_PRIVATE).edit()
            .putString("keyboard_language", keyboardLanguage.name).apply()
        symbolsEnabled = false
        symbolPage = 0
        cheonjiinInput.reset()
        applyLayoutPlan(layoutPlan(activeFieldMode, layoutOrientation))
        keyboardRoot?.let { renderKeyboard(it, activeFieldMode) }
    }

    private fun moveCursor(keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isCheonjiinLayout(inputLayout) &&
            keyboardLanguage == KeyboardLanguage.JAPANESE && activeFieldMode.allowsCandidates && japaneseWord.isActive
        ) {
            cheonjiinInput.reset()
            japaneseWord.finishSyllable()
            return
        }
        val connection = currentInputConnection ?: return
        commitPending(connection)
        clearCandidateTracking()
        sendDownUpKeyEvents(keyCode)
    }
    private fun switchToNextKeyboard(){(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()}
    private fun openKeyboardSettings(){currentInputConnection?.let(::commitPending);clearCandidateTracking();startActivity(Intent(this,ComposeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_shared_settings",true))}
    private fun openThemeScreen(){currentInputConnection?.let(::commitPending);clearCandidateTracking();startActivity(Intent(this,ComposeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_themes",true))}

    private fun refreshCandidates(force: Boolean = false) {
        updateEnterButton()
        val row = candidateRow ?: return
        val candidates = if (activeFieldMode.allowsCandidates && keyboardLanguage == KeyboardLanguage.JAPANESE) {
            candidateLearning.rank(japaneseWord.text, japaneseWord.suggestions())
        } else emptyList()
        if (!force && candidates == displayedCandidates) return
        displayedCandidates = candidates
        candidateScroll?.scrollTo(0, 0)
        candidateButtons.forEachIndexed { index, button ->
            val candidate = candidates.getOrNull(index)
            button.text = candidate.orEmpty()
            button.visibility = if (candidate == null) View.GONE else View.VISIBLE
        }
        row.visibility = View.VISIBLE
        modeLabel?.visibility = View.VISIBLE
        candidateScroll?.visibility = if (candidates.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun selectCandidate(candidate: String) {
        if (!activeFieldMode.allowsCandidates || !japaneseWord.isActive) return
        if (candidate !in japaneseWord.suggestions()) return
        val source = japaneseWord.text
        val connection = currentInputConnection ?: return
        // commitText replaces the entire composing span, including earlier syllables.
        if (connection.commitText(candidate, 1)) {
            candidateLearning.recordSelection(source, candidate)
            japaneseWord.reset()
            cheonjiinInput.reset()
            clearCandidateTracking()
        }
    }
    private fun clearCandidateTracking(){candidateInput.clear();displayedCandidates=emptyList();hideCandidateButtons()}
    private fun hideCandidateButtons(){candidateButtons.forEach{button->button.text="";button.visibility=View.GONE};modeLabel?.visibility=View.VISIBLE;candidateScroll?.visibility=View.GONE;updateEnterButton()}

    private fun handleEnter(connection: InputConnection) {
        if (japaneseWord.isActive) {
            val candidate = candidateLearning.rank(japaneseWord.text, japaneseWord.candidates()).firstOrNull()
            if (candidate != null) selectCandidate(candidate) else commitPending(connection)
            clearCandidateTracking()
            return
        }
        commitPending(connection)
        clearCandidateTracking()
        val editorInfo = currentInputEditorInfo
        val actionId = EnterActionResolver.actionId(
            editorInfo?.imeOptions ?: 0,
            editorInfo?.actionLabel,
            editorInfo?.actionId ?: 0,
        )
        if (actionId != null) connection.performEditorAction(actionId)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    private fun handleSpace(connection: InputConnection) {
        if (japaneseWord.isActive) {
            val candidate = candidateLearning.rank(japaneseWord.text, japaneseWord.candidates()).firstOrNull()
            if (candidate != null) selectCandidate(candidate) else commitPending(connection)
            clearCandidateTracking()
            return
        }
        commitPending(connection)
        connection.commitText(" ", 1)
        clearCandidateTracking()
    }

    private fun handleBackspace(connection: InputConnection) {
        val selectedText = connection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            composer.reset()
            japaneseWord.reset()
            cheonjiinInput.reset()
            cheonjiinComposer.reset()
            clearCandidateTracking()
            connection.finishComposingText()
            connection.commitText("", 1)
            return
        }
        if (isCheonjiinLayout(keyboardLanguage.effectiveLayout(inputLayout)) && !symbolsEnabled) {
            val cheonjiinEdit = cheonjiinInput.backspace()
            if (cheonjiinEdit != null) {
                applyCheonjiinAction(connection, cheonjiinEdit)
                return
            }
            if (cheonjiinComposer.isActive) {
                cheonjiinComposer.removeLast()
                connection.setComposingText(cheonjiinComposer.text, 1)
                if (!cheonjiinComposer.isActive) connection.finishComposingText()
                refreshCandidates()
                return
            }
        } else {
            cheonjiinInput.reset()
        }
        if (deleteComposedJamo(connection)) return
        connection.deleteSurroundingTextInCodePoints(1, 0)
        if (activeFieldMode.allowsCandidates) candidateInput.removeCommittedCodePoint()
        refreshCandidates()
    }

    private fun deleteComposedJamo(connection: InputConnection): Boolean {
        if (japaneseWord.backspace()) {
            connection.setComposingText(japaneseWord.text, 1)
            if (!japaneseWord.isActive) connection.finishComposingText()
            refreshCandidates()
            return true
        }
        val edit = composer.backspace()
        if (!edit.consumed) return false
        if (edit.composing.isNullOrEmpty()) {
            connection.setComposingText("", 1)
            connection.finishComposingText()
            if (candidateInput.hasCommittedToken()) clearCandidateTracking()
            return true
        }
        connection.setComposingText(edit.composing, 1)
        refreshCandidates()
        return true
    }
    private fun applyEdit(connection:InputConnection,edit:HangulComposer.Edit){if(edit.commit.isNotEmpty())connection.commitText(edit.commit,1);if(edit.composing!=null)connection.setComposingText(edit.composing,1)}
    private fun commitPending(connection: InputConnection) {
        cheonjiinInput.reset()
        if (japaneseWord.isActive) {
            connection.commitText(japaneseWord.text, 1)
            japaneseWord.reset()
            return
        }
        if (cheonjiinComposer.isActive) {
            connection.commitText(cheonjiinComposer.text, 1)
            cheonjiinComposer.reset()
            return
        }
        val pending = composer.flush()
        if (pending.isNotEmpty()) connection.commitText(pending, 1)
        else connection.finishComposingText()
    }

    private fun lettersButtonLabel(): String = if (keyboardLanguage == KeyboardLanguage.ENGLISH) "ABC" else "가나다"
    private fun hasShiftVariant(label: String): Boolean = if (keyboardLanguage == KeyboardLanguage.ENGLISH) {
        LatinQwertyLayout.hasShiftVariant(label)
    } else {
        TwoBeolsikLayout.hasShiftVariant(label)
    }
    private fun characterLabelFor(label: String, shifted: Boolean): String = if (keyboardLanguage == KeyboardLanguage.ENGLISH) {
        LatinQwertyLayout.labelFor(label, shifted)
    } else {
        TwoBeolsikLayout.labelFor(label, shifted)
    }

    private fun layoutPlan(mode:InputFieldMode,orientation:KeyboardOrientation):KeyboardRowPlan{val requestedHeightDp=KeyboardLayoutSettings.readHeight(this,orientation).keyHeightDp;val requestedNumberRow=KeyboardLayoutSettings.readNumberRowEnabled(this,orientation);val configuredHeightDp=resources.configuration.screenHeightDp;val availableHeightDp=if(configuredHeightDp>0)configuredHeightDp else (resources.displayMetrics.heightPixels/resources.displayMetrics.density).toInt().coerceAtLeast(1);val baseRowCount=KeyboardBaseRows.forMode(mode)+(if(keyboardLanguage==KeyboardLanguage.JAPANESE&&mode.allowsCandidates)1 else 0);val supportsNumberRow=mode!=InputFieldMode.NUMBER&&mode!=InputFieldMode.PHONE;return KeyboardRowSizing.plan(requestedHeightDp,availableHeightDp,baseRowCount,requestedNumberRow,false,supportsNumberRow)}
    private fun applyLayoutPlan(plan:KeyboardRowPlan){keyHeightDp=plan.keyHeightDp;numberRowEnabled=plan.numberRowEnabled}
    private fun currentKeyboardOrientation()=if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)KeyboardOrientation.LANDSCAPE else KeyboardOrientation.PORTRAIT
    // Insets are visual only, so the whole evenly spaced cell remains tappable.
    private fun keyLayoutParams(weight:Float=1f)=LinearLayout.LayoutParams(0,dp(keyHeightDp + 2),weight)
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
