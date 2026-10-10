package com.ghtnql.kkkeyboard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.InputFilter
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private enum class Screen { HOME, PRACTICE_SELECT, PRACTICE, RAIN_SELECT, RAIN, CAFE_SELECT, CAFE, RESULT, SETTINGS }

    private var screen = Screen.HOME
    private lateinit var learningItems: List<LearningItem>
    private var practiceSession: PracticeSession? = null
    private var practiceStyle = PracticeStyle.BASIC
    private val handler = Handler(Looper.getMainLooper())
    private var rainLoop: Runnable? = null
    private var rainFrameView: View? = null
    private var cafeLoop: Runnable? = null
    private var cafeFrameView: View? = null
    private var rainSoundPlayer: RainSoundPlayer? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppUiLanguageSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        if (BuildConfig.EXPOSE_GAMES) learningItems = LearningContent.load(this)
        showHome()
    }

    override fun onResume() {
        super.onResume()
        findViewById<CafeSceneView>(R.id.cafe_scene)?.resumeCustomer()
        if (::learningItems.isInitialized && screen == Screen.HOME) showHome()
        if (screen == Screen.RAIN) rainLoop?.let { loop ->
            rainFrameView?.postOnAnimation(loop) ?: handler.post(loop)
        }
        if (screen == Screen.CAFE) cafeLoop?.let { loop ->
            cafeFrameView?.postDelayed(loop, CAFE_FRAME_MS) ?: handler.postDelayed(loop, CAFE_FRAME_MS)
        }
    }

    override fun onPause() {
        findViewById<CafeSceneView>(R.id.cafe_scene)?.pauseCustomer()
        rainLoop?.let(handler::removeCallbacks)
        rainLoop?.let { rainFrameView?.removeCallbacks(it) }
        cafeLoop?.let(handler::removeCallbacks)
        cafeLoop?.let { cafeFrameView?.removeCallbacks(it) }
        rainSoundPlayer?.pauseAll()
        super.onPause()
    }

    @Deprecated("Uses the platform back behavior on API 26+")
    @SuppressLint("GestureBackNavigation") // Legacy screen retained only for migration rollback.
    override fun onBackPressed() {
        if (screen == Screen.HOME) super.onBackPressed() else showHome()
    }

    private fun showHome() {
        stopRainLoop()
        screen = Screen.HOME
        val content = page()
        val enabled = isKeyboardEnabled()
        content.addView(header(getString(R.string.home_title), settingsAction = true))

        content.addView(TextView(this).apply {
            text = getString(if (enabled) R.string.keyboard_ready else R.string.keyboard_not_ready)
            textSize = 16f
            setTextColor(if (enabled) COLOR_SUCCESS else COLOR_WARNING)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(8))
        })
        if (!enabled) {
            content.addView(primaryButton(getString(R.string.open_keyboard_settings)) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })
        }
        content.addView(secondaryButton(getString(R.string.select_keyboard)) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        })

        if (BuildConfig.EXPOSE_GAMES) {
            sectionTitle(content, getString(R.string.practice_title))
            content.addView(primaryButton(getString(R.string.practice_start)) { showPracticeSelection() })
            content.addView(secondaryButton(getString(R.string.rain_title)) { showRainSelection() })
            content.addView(secondaryButton(getString(R.string.cafe_title)) { showCafeSelection() })

            val progress = LearningProgressStore.read(this)
            sectionTitle(content, getString(R.string.progress_title))
            content.addView(TextView(this).apply {
                id = R.id.progress_summary
                text = getString(R.string.progress_summary, progress.totalSessions, progress.bestAccuracy, progress.totalXp)
                textSize = 16f
                setTextColor(COLOR_TEXT_MUTED)
                setPadding(0, dp(6), 0, dp(24))
            })
        }
        setPage(content)
    }

    private fun showPracticeSelection() {
        stopRainLoop()
        screen = Screen.PRACTICE_SELECT
        val content = page()
        content.addView(header(getString(R.string.practice_choose), backAction = true))
        content.addView(TextView(this).apply {
            text = getString(R.string.practice_choose_detail)
            textSize = 16f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(8), 0, dp(18))
        })
        content.addView(primaryButton(getString(R.string.practice_korean)) { startPractice(PracticeMode.KOREAN_TYPING) })
        content.addView(secondaryButton(getString(R.string.practice_japanese_to_hangul)) { startPractice(PracticeMode.JAPANESE_TO_HANGUL) })
        sectionTitle(content, getString(R.string.sentence_title))
        content.addView(secondaryButton(getString(R.string.sentence_start)) {
            startPractice(PracticeMode.KOREAN_TYPING, PracticeStyle.SHORT_SENTENCE)
        })
        setPage(content)
    }

    private fun startPractice(mode: PracticeMode, style: PracticeStyle = PracticeStyle.BASIC) {
        practiceStyle = style
        val gameType = if (style == PracticeStyle.SHORT_SENTENCE) "sentence" else "typing"
        val selected = LearningContent.forMode(learningItems, mode, gameType)
        practiceSession = PracticeSession(mode, selected, System.currentTimeMillis(), style = style)
        showPractice()
    }

    private fun showPractice(feedback: Int? = null) {
        screen = Screen.PRACTICE
        val session = requireNotNull(practiceSession)
        val item = requireNotNull(session.currentItem)
        val sentenceMode = practiceStyle == PracticeStyle.SHORT_SENTENCE
        val content = page()
        content.addView(header(getString(if (sentenceMode) R.string.sentence_title else R.string.practice_title), backAction = true))
        content.addView(TextView(this).apply {
            val currentItemNumber: Int = session.currentIndex + 1
            val itemCount: Int = session.items.size
            text = getString(R.string.practice_progress, currentItemNumber, itemCount)
            textSize = 14f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(12), 0, dp(16))
        })
        content.addView(TextView(this).apply {
            id = R.id.practice_style
            text = if (sentenceMode) getString(R.string.sentence_original) else ""
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_ACCENT)
            visibility = if (sentenceMode) View.VISIBLE else View.GONE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }, matchWrap())
        val prompt = TextView(this).apply {
            id = R.id.practice_prompt
            text = item.sourceText
            textSize = if (sentenceMode) 26f else 34f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT)
            setLineSpacing(dp(5).toFloat(), 1f)
            setPadding(dp(12), if (sentenceMode) dp(18) else dp(28), dp(12), dp(10))
        }
        content.addView(prompt, matchWrap())
        item.meaningHint?.let { hint ->
            content.addView(TextView(this).apply {
                text = hint
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_MUTED)
                setPadding(0, 0, 0, dp(20))
            }, matchWrap())
        }
        val answer = EditText(this).apply {
            id = R.id.practice_answer
            hint = getString(if (sentenceMode) R.string.sentence_answer_hint else R.string.practice_answer_hint)
            textSize = if (sentenceMode) 18f else 20f
            inputType = InputType.TYPE_CLASS_TEXT or if (sentenceMode) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            isSingleLine = !sentenceMode
            if (sentenceMode) {
                minLines = 2
                maxLines = 3
                gravity = Gravity.TOP or Gravity.START
                filters = arrayOf(InputFilter.LengthFilter(item.sourceText.length + 10))
            }
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.WHITE, Color.rgb(215, 222, 226))
        }
        content.addView(answer, matchWrap(top = 10))
        val feedbackView = TextView(this).apply {
            id = R.id.practice_feedback
            text = feedback?.let(::getString).orEmpty()
            textSize = 15f
            setTextColor(if (feedback == R.string.practice_correct) COLOR_SUCCESS else COLOR_ERROR)
            setPadding(0, dp(10), 0, dp(4))
        }
        content.addView(feedbackView)
        fun updateSentencePrompt(typed: CharSequence) {
            if (!sentenceMode) return
            val target = item.sourceText
            val styled = SpannableString(target)
            target.indices.forEach { index ->
                val color = when {
                    index >= typed.length -> COLOR_TEXT
                    target[index] == typed[index] -> COLOR_SUCCESS
                    else -> COLOR_ERROR
                }
                styled.setSpan(ForegroundColorSpan(color), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (typed.length < target.length) {
                styled.setSpan(StyleSpan(Typeface.BOLD), typed.length, typed.length + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(COLOR_ACCENT), typed.length, typed.length + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            prompt.text = styled
        }
        answer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                updateSentencePrompt(value ?: "")
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })
        updateSentencePrompt("")
        fun submit() {
            if (answer.text.isBlank()) return
            val attempt = session.submit(answer.text.toString())
            if (attempt.completed) {
                finishPractice(session.result(System.currentTimeMillis()))
            } else if (attempt.correct) {
                showPractice(R.string.practice_correct)
            } else if (sentenceMode) {
                showPractice(R.string.sentence_next_with_errors)
            } else {
                feedbackView.text = getString(R.string.practice_retry)
                feedbackView.setTextColor(COLOR_ERROR)
                answer.selectAll()
            }
        }
        answer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        content.addView(primaryButton(getString(R.string.practice_check)) { submit() })
        setPage(content)
        answer.requestFocus()
    }

    private fun finishPractice(result: TypingSessionResult) {
        LearningProgressStore.recordPractice(this, result)
        showResult(result)
    }

    private fun showResult(result: TypingSessionResult) {
        stopRainLoop()
        screen = Screen.RESULT
        val content = page()
        content.addView(header(getString(R.string.result_title), backAction = true))
        content.addView(TextView(this).apply {
            id = R.id.result_accuracy
            text = getString(R.string.result_accuracy, result.accuracy)
            textSize = 42f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_ACCENT)
            gravity = Gravity.CENTER
            setPadding(0, dp(38), 0, dp(24))
        }, matchWrap())
        listOf(
            getString(R.string.result_cpm, result.cpm),
            getString(R.string.result_errors, result.errorCount),
            getString(R.string.result_combo, result.maxCombo),
            getString(R.string.result_score, result.score),
            getString(R.string.result_time, result.durationMs / 1000f),
        ).forEach { value ->
            content.addView(TextView(this).apply {
                text = value
                textSize = 18f
                setTextColor(COLOR_TEXT)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
        content.addView(primaryButton(getString(R.string.practice_again)) {
            when {
                result.modeId.startsWith("sentence_") -> startPractice(PracticeMode.KOREAN_TYPING, PracticeStyle.SHORT_SENTENCE)
                result.modeId.startsWith("cafe_") -> showCafeSelection()
                result.modeId.startsWith("rain_") -> showRainSelection()
                else -> showPracticeSelection()
            }
        })
        content.addView(secondaryButton(getString(R.string.back_home)) { showHome() })
        setPage(content)
    }

    private fun showRainSelection() {
        stopRainLoop()
        screen = Screen.RAIN_SELECT
        val content = page()
        content.addView(header(getString(R.string.rain_choose), backAction = true))
        content.addView(TextView(this).apply {
            text = getString(R.string.hangul_input_principle)
            textSize = 16f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(8), 0, dp(18))
        })
        content.addView(primaryButton(getString(R.string.practice_korean)) {
            startRain(PracticeMode.KOREAN_TYPING)
        })
        content.addView(secondaryButton(getString(R.string.practice_japanese_to_hangul)) {
            startRain(PracticeMode.JAPANESE_TO_HANGUL)
        })
        setPage(content)
    }

    private fun showCafeSelection() {
        stopRainLoop()
        screen = Screen.CAFE_SELECT
        val content = page()
        content.addView(header(getString(R.string.cafe_choose), backAction = true))
        content.addView(TextView(this).apply {
            text = getString(R.string.cafe_choose_detail)
            textSize = 16f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(8), 0, dp(18))
        })
        var difficulty = CafeDifficulty.NORMAL
        content.addView(TextView(this).apply {
            text = getString(R.string.cafe_difficulty_label)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            setPadding(0, 0, 0, dp(6))
        })
        val difficultyGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        listOf(
            CafeDifficulty.RELAXED to R.string.cafe_difficulty_relaxed,
            CafeDifficulty.NORMAL to R.string.cafe_difficulty_normal,
            CafeDifficulty.RUSH to R.string.cafe_difficulty_rush,
        ).forEach { (value, label) ->
            difficultyGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(label)
                textSize = 14f
                gravity = Gravity.CENTER
                isChecked = value == difficulty
                setOnCheckedChangeListener { _, checked -> if (checked) difficulty = value }
            }, RadioGroup.LayoutParams(0, dp(44), 1f))
        }
        content.addView(difficultyGroup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        content.addView(TextView(this).apply {
            text = getString(R.string.cafe_difficulty_detail)
            textSize = 13f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(16))
        })
        content.addView(primaryButton(getString(R.string.practice_korean)) {
            startCafe(PracticeMode.KOREAN_TYPING, difficulty)
        })
        content.addView(secondaryButton(getString(R.string.practice_japanese_to_hangul)) {
            startCafe(PracticeMode.JAPANESE_TO_HANGUL, difficulty)
        })
        setPage(content)
    }

    private fun startCafe(mode: PracticeMode, difficulty: CafeDifficulty) {
        val totalOrders = 5
        val items = LearningContent.forMode(learningItems, mode, "cafe")
        val game = CafeGame(mode, items, System.currentTimeMillis(), totalOrders, difficulty)
        screen = Screen.CAFE
        rainSoundPlayer?.release()
        val soundPlayer = RainSoundPlayer(this).also { rainSoundPlayer = it }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(34, 48, 43))
            applyGameInsets(this)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            setBackgroundColor(Color.rgb(38, 69, 58))
        }
        toolbar.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.back_home)
            setOnClickListener { showHome() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply {
            text = getString(R.string.cafe_title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        lateinit var muteButton: ImageButton
        fun refreshMuteButton() {
            muteButton.setImageResource(
                if (soundPlayer.isMuted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off,
            )
            muteButton.contentDescription = getString(
                if (soundPlayer.isMuted) R.string.rain_sound_on else R.string.rain_sound_off,
            )
        }
        muteButton = ImageButton(this).apply {
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { soundPlayer.toggleMuted(); refreshMuteButton() }
        }
        refreshMuteButton()
        toolbar.addView(muteButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.rgb(240, 245, 237))
        }
        val orderCountView = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(39, 61, 53))
        }
        val comboView = TextView(this).apply {
            id = R.id.cafe_difficulty
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(187, 74, 68))
        }
        val scoreView = TextView(this).apply {
            id = R.id.cafe_score
            textSize = 14f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(39, 61, 53))
        }
        hud.addView(orderCountView, LinearLayout.LayoutParams(0, dp(40), 1f))
        hud.addView(comboView, LinearLayout.LayoutParams(dp(90), dp(40)))
        hud.addView(scoreView, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(hud, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        val patienceBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = R.id.cafe_patience
            max = 1_000
            progress = max
            progressTintList = ColorStateList.valueOf(Color.rgb(83, 167, 118))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(226, 216, 197))
            contentDescription = getString(R.string.cafe_patience_description, 100)
        }
        root.addView(patienceBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(7)))

        val sceneContainer = FrameLayout(this)
        val cafeScene = CafeSceneView(this).apply { id = R.id.cafe_scene }
        sceneContainer.addView(cafeScene, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val orderBubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(9), dp(18), dp(9))
            background = roundedBackground(Color.argb(238, 255, 253, 247), Color.rgb(74, 119, 101))
        }
        val orderCaption = TextView(this).apply {
            text = getString(R.string.cafe_order_label)
            textSize = 12f
            setTextColor(Color.rgb(91, 105, 98))
            gravity = Gravity.CENTER
        }
        val orderPrompt = TextView(this).apply {
            id = R.id.cafe_order
            textSize = 24f
            maxWidth = dp(124)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(37, 49, 45))
            gravity = Gravity.CENTER
        }
        val orderHint = TextView(this).apply {
            textSize = 13f
            maxWidth = dp(124)
            setTextColor(Color.rgb(99, 105, 101))
            gravity = Gravity.CENTER
        }
        orderBubble.addView(orderCaption)
        orderBubble.addView(orderPrompt)
        orderBubble.addView(orderHint)
        sceneContainer.addView(orderBubble, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(12)
            marginStart = dp(12)
            marginEnd = dp(12)
        })
        root.addView(sceneContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val answerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(38, 69, 58))
        }
        val answer = EditText(this).apply {
            id = R.id.cafe_answer
            hint = getString(R.string.cafe_answer_hint)
            textSize = 20f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBackground(Color.WHITE, Color.rgb(149, 177, 164))
        }
        answerRow.addView(answer, LinearLayout.LayoutParams(0, dp(52), 1f))
        val submitButton = ImageButton(this).apply {
            id = R.id.cafe_submit
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.cafe_submit)
        }
        answerRow.addView(submitButton, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(4) })
        root.addView(answerRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))

        var acceptingOrder = false
        var resettingAnswer = false
        var orderHidden = false
        val difficultyName = getString(when (difficulty) {
            CafeDifficulty.RELAXED -> R.string.cafe_difficulty_relaxed
            CafeDifficulty.NORMAL -> R.string.cafe_difficulty_normal
            CafeDifficulty.RUSH -> R.string.cafe_difficulty_rush
        })
        // Keep the IME open during the performance, but do not accept another submission or edits.
        answer.filters = arrayOf(InputFilter { _, _, _, dest, start, end ->
            if (acceptingOrder || resettingAnswer) null else dest.subSequence(start, end)
        })

        fun updatePatience() {
            val total = game.currentOrderTotalTimeMs
            val percent = if (total <= 0L) 0 else (game.currentOrderRemainingTimeMs * 100 / total).toInt()
            patienceBar.progress = percent * 10
            patienceBar.progressTintList = ColorStateList.valueOf(when {
                percent > 55 -> Color.rgb(83, 167, 118)
                percent > 25 -> Color.rgb(232, 164, 72)
                else -> Color.rgb(211, 79, 73)
            })
            patienceBar.contentDescription = getString(R.string.cafe_patience_description, percent)
        }

        fun renderOrder() {
            val order = game.currentOrder ?: return
            acceptingOrder = false
            orderHidden = false
            submitButton.isEnabled = false
            resettingAnswer = true
            answer.text.clear()
            resettingAnswer = false
            orderCountView.text = getString(R.string.cafe_order_count, game.currentIndex + 1, totalOrders)
            scoreView.text = getString(R.string.cafe_score, game.score)
            comboView.text = if (game.combo >= 2) getString(R.string.cafe_combo, game.combo) else difficultyName
            orderPrompt.text = order.sourceText
            orderHint.text = order.meaningHint.orEmpty()
            orderHint.visibility = if (order.meaningHint.isNullOrBlank()) View.GONE else View.VISIBLE
            orderBubble.background = roundedBackground(Color.argb(238, 255, 253, 247), Color.rgb(74, 119, 101))
            orderBubble.alpha = 0f
            patienceBar.progress = patienceBar.max
            cafeScene.enterCustomer(order, game.currentIndex) {
                if (screen != Screen.CAFE || !root.isAttachedToWindow) return@enterCustomer
                acceptingOrder = true
                submitButton.isEnabled = true
                orderBubble.alpha = 1f
                game.beginOrder()
                updatePatience()
                answer.requestFocus()
            }
        }

        fun finishCafe() {
            if (screen != Screen.CAFE || !root.isAttachedToWindow) return
            val result = game.result(System.currentTimeMillis())
            LearningProgressStore.recordPractice(this, result)
            showResult(result)
        }

        fun resolveOrder(correct: Boolean, completed: Boolean, scoreGained: Int, expectedAnswer: String, timedOut: Boolean) {
            acceptingOrder = false
            submitButton.isEnabled = false
            scoreView.text = getString(R.string.cafe_score, game.score)
            comboView.text = if (game.combo >= 2) getString(R.string.cafe_combo, game.combo) else difficultyName
            if (!correct) {
                soundPlayer.playError()
                orderBubble.alpha = 1f
                orderBubble.background = roundedBackground(Color.rgb(255, 240, 237), Color.rgb(218, 83, 73))
                orderPrompt.text = getString(if (timedOut) R.string.cafe_timeout else R.string.cafe_customer_disappointed)
                orderHint.text = getString(R.string.cafe_expected_answer, expectedAnswer)
            } else {
                soundPlayer.playHit(game.combo)
                orderPrompt.text = getString(R.string.cafe_complete)
                orderHint.text = "+$scoreGained"
            }
            orderHint.visibility = View.VISIBLE
            resettingAnswer = true
            answer.text.clear()
            resettingAnswer = false
            cafeScene.resolveCustomer(correct) {
                if (screen != Screen.CAFE || !root.isAttachedToWindow) return@resolveCustomer
                if (completed) {
                    soundPlayer.playFinish()
                    root.postDelayed({ finishCafe() }, 320L)
                } else {
                    renderOrder()
                }
            }
        }

        fun submit() {
            if (!acceptingOrder || answer.text.isBlank()) return
            val expectedAnswer = game.currentOrder?.acceptedAnswers?.first().orEmpty()
            val submission = game.submit(answer.text.toString())
            resolveOrder(submission.correct, submission.completed, submission.scoreGained, expectedAnswer, timedOut = false)
        }

        var lastCafeFrame = 0L
        val loop = object : Runnable {
            override fun run() {
                if (screen != Screen.CAFE || !root.isAttachedToWindow) return
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastCafeFrame
                val delta = if (lastCafeFrame == 0L || elapsed > 250L) 0L else elapsed.coerceIn(0L, 100L)
                lastCafeFrame = now
                if (acceptingOrder) {
                    val expectedAnswer = game.currentOrder?.acceptedAnswers?.first().orEmpty()
                    val tick = game.tick(delta)
                    updatePatience()
                    if (!orderHidden && game.shouldHideCurrentOrder) {
                        orderHidden = true
                        orderPrompt.text = "•••"
                        orderHint.text = getString(R.string.cafe_order_hidden)
                        orderHint.visibility = View.VISIBLE
                    }
                    if (tick?.timedOut == true) {
                        resolveOrder(correct = false, completed = tick.completed, scoreGained = 0, expectedAnswer, timedOut = true)
                    }
                }
                root.postDelayed(this, CAFE_FRAME_MS)
            }
        }
        cafeLoop = loop
        cafeFrameView = root

        answer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                if (!acceptingOrder) return
                game.currentOrder?.let { cafeScene.showOrder(it, value?.toString().orEmpty()) }
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })
        answer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        submitButton.setOnClickListener { submit() }

        setContentView(root)
        root.requestApplyInsets()
        renderOrder()
        soundPlayer.playStart()
        root.postDelayed(loop, CAFE_FRAME_MS)
        answer.requestFocus()
        answer.post { getSystemService(InputMethodManager::class.java)?.showSoftInput(answer, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun startRain(mode: PracticeMode) {
        val items = LearningContent.forMode(learningItems, mode, "rain")
        val startedAt = System.currentTimeMillis()
        val game = RainGame(mode, items, startedAt)
        screen = Screen.RAIN
        rainSoundPlayer?.release()
        val soundPlayer = RainSoundPlayer(this).also { rainSoundPlayer = it }
        var paused = false
        var gameplayStarted = false
        var countdownRemainingMs = 3_000L
        var lastFrame = 0L

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 33, 39))
            applyGameInsets(this)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            setBackgroundColor(Color.rgb(20, 43, 50))
        }
        toolbar.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.back_home)
            setOnClickListener { showHome() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply {
            text = getString(R.string.rain_title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        lateinit var muteButton: ImageButton
        fun refreshMuteButton() {
            muteButton.setImageResource(
                if (soundPlayer.isMuted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off,
            )
            muteButton.contentDescription = getString(
                if (soundPlayer.isMuted) R.string.rain_sound_on else R.string.rain_sound_off,
            )
        }
        muteButton = ImageButton(this).apply {
            id = R.id.rain_mute
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { soundPlayer.toggleMuted(); refreshMuteButton() }
        }
        refreshMuteButton()
        toolbar.addView(muteButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        val pauseButton = ImageButton(this).apply {
            id = R.id.rain_pause
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.rain_pause)
        }
        toolbar.addView(pauseButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

        val hudRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.rgb(225, 236, 232))
        }
        fun hudText(viewId: Int, textGravity: Int) = TextView(this).apply {
            id = viewId
            textSize = 14f
            gravity = textGravity
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(28, 47, 50))
        }
        val scoreView = hudText(R.id.rain_hud, Gravity.START or Gravity.CENTER_VERTICAL)
        val timeView = hudText(R.id.rain_time, Gravity.CENTER)
        val livesView = hudText(R.id.rain_lives, Gravity.END or Gravity.CENTER_VERTICAL)
        hudRow.addView(scoreView, LinearLayout.LayoutParams(0, dp(40), 1f))
        hudRow.addView(timeView, LinearLayout.LayoutParams(dp(64), dp(40)))
        hudRow.addView(livesView, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(hudRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))

        val scene = FrameLayout(this)
        val arena = RainArenaView(this).apply { practiceMode = game.mode }
        rainFrameView = arena
        scene.addView(arena, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val comboView = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedBackground(Color.argb(205, 16, 83, 76), Color.TRANSPARENT)
        }
        scene.addView(comboView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(10)
        })
        root.addView(scene, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val answerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(20, 43, 50))
        }
        val answer = EditText(this).apply {
            id = R.id.rain_answer
            hint = getString(R.string.rain_answer_hint)
            textSize = 20f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBackground(Color.WHITE, Color.rgb(157, 178, 179))
        }
        answerRow.addView(answer, LinearLayout.LayoutParams(0, dp(52), 1f))
        val submitButton = ImageButton(this).apply {
            id = R.id.rain_submit
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.rain_submit)
        }
        answerRow.addView(submitButton, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(4) })
        root.addView(answerRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))

        fun updateHud() {
            scoreView.text = getString(R.string.rain_score, game.score)
            timeView.text = getString(R.string.rain_time, (game.remainingMs + 999L) / 1000L)
            val lives = buildString {
                repeat(3) { index ->
                    if (index > 0) append(' ')
                    append(if (index < game.lives) '♥' else '♡')
                }
            }
            livesView.text = getString(R.string.rain_lives, lives)
            comboView.visibility = if (game.combo >= 2) View.VISIBLE else View.INVISIBLE
            if (game.combo >= 2) comboView.text = getString(R.string.rain_combo, game.combo)
        }

        fun submit() {
            if (!gameplayStarted || paused || answer.text.isBlank()) return
            val submission = game.submit(answer.text.toString())
            if (submission.correct) {
                submission.removedTargetId?.let { arena.celebrate(it, submission.scoreGained) }
                soundPlayer.playHit(game.combo)
                answer.text.clear()
                comboView.scaleX = 0.86f
                comboView.scaleY = 0.86f
                comboView.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            } else {
                soundPlayer.playError()
                arena.showWrongAnswer()
                answer.background = roundedBackground(Color.rgb(255, 244, 242), Color.rgb(220, 74, 68))
                answer.postDelayed({
                    if (screen == Screen.RAIN) answer.background = roundedBackground(Color.WHITE, Color.rgb(157, 178, 179))
                }, 260L)
                answer.selectAll()
            }
            arena.updateTargets(game.targets, answer.text.toString())
            updateHud()
        }
        answer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        answer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                arena.updateTargets(game.targets, value?.toString().orEmpty())
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })
        submitButton.setOnClickListener { submit() }
        pauseButton.setOnClickListener {
            paused = true
            soundPlayer.pauseAll()
            AlertDialog.Builder(this)
                .setTitle(R.string.rain_paused)
                .setPositiveButton(R.string.rain_resume, null)
                .setNegativeButton(R.string.rain_end) { _, _ -> game.finish(); finishRain(game) }
                .setNeutralButton(if (soundPlayer.isMuted) R.string.rain_sound_on else R.string.rain_sound_off) { _, _ ->
                    soundPlayer.toggleMuted()
                    refreshMuteButton()
                }
                .setOnDismissListener {
                    if (screen == Screen.RAIN) {
                        paused = false
                        lastFrame = SystemClock.elapsedRealtime()
                    }
                }
                .show()
        }

        setContentView(root)
        root.requestApplyInsets()
        updateHud()
        answer.requestFocus()
        answer.post { getSystemService(InputMethodManager::class.java)?.showSoftInput(answer, InputMethodManager.SHOW_IMPLICIT) }

        val loop = object : Runnable {
            override fun run() {
                if (screen != Screen.RAIN) return
                val now = SystemClock.elapsedRealtime()
                val rawDelta = if (lastFrame == 0L) 0L else now - lastFrame
                val delta = if (rawDelta in 0L..500L) rawDelta else 0L
                lastFrame = now
                if (!paused) {
                    if (countdownRemainingMs > 0L) {
                        countdownRemainingMs = (countdownRemainingMs - delta).coerceAtLeast(0L)
                        val count = ((countdownRemainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                        arena.updateTargets(emptyList(), answer.text.toString(), count)
                        if (countdownRemainingMs == 0L) {
                            gameplayStarted = true
                            soundPlayer.playStart()
                        }
                    } else {
                        val tick = game.tick(delta)
                        tick.missedTargetIds.forEach { targetId ->
                            arena.splash(targetId)
                            soundPlayer.playSplash()
                        }
                        arena.updateTargets(game.targets, answer.text.toString())
                    }
                }
                updateHud()
                if (game.isFinished) {
                    rainLoop = null
                    soundPlayer.playFinish()
                    handler.postDelayed({ finishRain(game) }, 360L)
                } else {
                    arena.postOnAnimation(this)
                }
            }
        }
        rainLoop = loop
        arena.postOnAnimation(loop)
    }

    private fun finishRain(game: RainGame) {
        if (screen != Screen.RAIN) return
        val result = game.result(System.currentTimeMillis())
        LearningProgressStore.recordRain(this, result)
        showResult(result)
    }

    private fun stopRainLoop() {
        rainLoop?.let(handler::removeCallbacks)
        rainLoop?.let { rainFrameView?.removeCallbacks(it) }
        cafeLoop?.let(handler::removeCallbacks)
        cafeLoop?.let { cafeFrameView?.removeCallbacks(it) }
        rainLoop = null
        rainFrameView = null
        cafeLoop = null
        cafeFrameView = null
        rainSoundPlayer?.release()
        rainSoundPlayer = null
    }

    private fun showSettings() {
        stopRainLoop()
        screen = Screen.SETTINGS
        val content = page()
        addUiLanguageControls(content)
        addThemeControls(content)
        addInputLayoutControls(content)
        addKeyboardLanguageOrderControls(content)
        addOrientationControls(content)
        addPhraseControls(content)
        sectionTitle(content, getString(R.string.keyboard_test_title))
        content.addView(EditText(this).apply {
            id = R.id.keyboard_test_field
            hint = getString(R.string.keyboard_test_hint)
            textSize = 18f
            minLines = 3
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.WHITE, Color.rgb(224, 228, 232))
        }, matchWrap(top = 8))
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_apply_note)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(dp(8), dp(24), dp(8), dp(24))
        }, matchWrap())
        content.isFocusableInTouchMode = true
        content.requestFocus()
        setSettingsPage(content)
    }

    private fun header(title: String, backAction: Boolean = false, settingsAction: Boolean = false): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (backAction) addView(Button(this@MainActivity).apply {
                text = "‹"
                textSize = 28f
                contentDescription = getString(R.string.back_home)
                setOnClickListener { showHome() }
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 25f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (settingsAction) addView(Button(this@MainActivity).apply {
                id = R.id.open_app_settings
                text = "⚙"
                textSize = 22f
                contentDescription = getString(R.string.keyboard_settings)
                setOnClickListener { showSettings() }
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
        }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(24), dp(22), dp(36))
        setBackgroundColor(COLOR_BACKGROUND)
    }

    private fun setPage(content: LinearLayout) {
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setOnApplyWindowInsetsListener { view, insets ->
                val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    maxOf(
                        insets.getInsets(WindowInsets.Type.systemBars()).bottom,
                        insets.getInsets(WindowInsets.Type.ime()).bottom,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                view.setPadding(0, 0, 0, bottom)
                insets
            }
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun applyGameInsets(view: View) {
        view.setOnApplyWindowInsetsListener { target, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                target.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    maxOf(systemBars.bottom, ime.bottom),
                )
            } else {
                @Suppress("DEPRECATION")
                target.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
    }

    private fun setSettingsPage(content: LinearLayout) {
        val scroll = ScrollView(this).apply {
            id = R.id.settings_scroll
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = true
            setPadding(0, 0, 0, dp(32))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(0, 0, 0, dp(32) + insets.systemWindowInsetBottom)
                insets
            }
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            addView(header(getString(R.string.keyboard_settings), backAction = true).apply {
                setPadding(dp(12), dp(10), dp(18), dp(8))
                setBackgroundColor(Color.WHITE)
                elevation = dp(2).toFloat()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70)))
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun sectionTitle(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            setPadding(0, dp(26), 0, dp(4))
        })
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        setTextColor(Color.WHITE); setBackgroundColor(COLOR_ACCENT); minHeight = dp(52)
        setOnClickListener { action() }
    }.also { it.layoutParams = matchWrap(top = 8) }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        setTextColor(COLOR_TEXT); minHeight = dp(52)
        setOnClickListener { action() }
    }.also { it.layoutParams = matchWrap(top = 8) }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun isKeyboardEnabled(): Boolean =
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.any { it.serviceInfo.packageName == packageName } == true

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun addInputLayoutControls(root: LinearLayout) {
        settingSection(root, getString(R.string.settings_input_behavior), null) { section ->
            section.addView(settingLabel(getString(R.string.input_layout)))
            val layouts = InputLayout.entries
            section.addView(segmentedControl(
                layouts.map { layout -> getString(when (layout) {
                    InputLayout.QWERTY -> R.string.layout_qwerty
                    InputLayout.CHEONJIIN -> R.string.layout_cheonjiin
                    InputLayout.CHEONJIIN_PLUS -> R.string.layout_cheonjiin_plus
                    InputLayout.HANGUL_FLICK -> R.string.layout_flick
                }) },
                layouts.indexOf(KeyboardLayoutSettings.readInputLayout(this)),
            ) { index -> KeyboardLayoutSettings.writeInputLayout(this, layouts[index]) })
        }

        settingSection(root, getString(R.string.settings_timing), null) { section ->
            val distance = KeyboardLayoutSettings.readFlickDistance(this)
            val distanceLabel = settingValue(getString(R.string.flick_distance, distance))
            section.addView(distanceLabel)
            section.addView(SeekBar(this).apply {
                min = 12; max = 32; progress = distance
                contentDescription = distanceLabel.text
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        distanceLabel.text = getString(R.string.flick_distance, progress)
                        seekBar.contentDescription = distanceLabel.text
                        if (fromUser) KeyboardLayoutSettings.writeFlickDistance(this@MainActivity, progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

            val timeout = KeyboardLayoutSettings.readCheonjiinCycleTimeout(this)
            val timeoutLabel = settingValue(getString(R.string.cheonjiin_cycle_timeout, timeout)).apply {
                setPadding(0, dp(12), 0, 0)
            }
            section.addView(timeoutLabel)
            section.addView(SeekBar(this).apply {
                min = 4; max = 16; progress = timeout / 100
                contentDescription = timeoutLabel.text
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val millis = progress * 100
                        timeoutLabel.text = getString(R.string.cheonjiin_cycle_timeout, millis)
                        seekBar.contentDescription = timeoutLabel.text
                        if (fromUser) KeyboardLayoutSettings.writeCheonjiinCycleTimeout(this@MainActivity, millis)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        }
    }

    private fun addUiLanguageControls(root: LinearLayout) {
        settingSection(
            root,
            getString(R.string.settings_ui_language),
            getString(R.string.settings_ui_language_description),
        ) { section ->
            val languages = AppUiLanguage.entries
            section.addView(segmentedControl(
                languages.map { language -> getString(when (language) {
                    AppUiLanguage.KOREAN -> R.string.ui_language_korean
                    AppUiLanguage.JAPANESE -> R.string.ui_language_japanese
                    AppUiLanguage.ENGLISH -> R.string.ui_language_english
                }) },
                languages.indexOf(AppUiLanguageSettings.read(this)),
            ) { index ->
                val selected = languages[index]
                if (selected != AppUiLanguageSettings.read(this)) {
                    AppUiLanguageSettings.write(this, selected)
                    recreate()
                }
            })
        }
    }

    private fun addKeyboardLanguageOrderControls(root: LinearLayout) {
        settingSection(root, "키보드 입력 언어 순서", "언어 버튼을 길게 눌러 드래그하면 전환 순서를 바꿀 수 있습니다.") { section ->
            section.addView(settingLabel("드래그하여 순서 변경"))
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun label(language: KeyboardLanguage) = when (language) {
                KeyboardLanguage.KOREAN -> "한국어"
                KeyboardLanguage.JAPANESE -> "日本語"
                KeyboardLanguage.ENGLISH -> "English"
            }
            fun render() {
                list.removeAllViews()
                KeyboardLanguageOrderSettings.read(this).forEach { language ->
                    val row = TextView(this).apply {
                        text = "☰  ${label(language)}"
                        textSize = 16f
                        gravity = Gravity.CENTER_VERTICAL
                        setTextColor(COLOR_TEXT)
                        setPadding(dp(14), 0, dp(14), 0)
                        minHeight = dp(48)
                        background = roundedBackground(COLOR_SEGMENT, Color.TRANSPARENT)
                        contentDescription = "${label(language)}. 길게 눌러 이동"
                        tag = language
                        setOnLongClickListener {
                            startDragAndDrop(ClipData.newPlainText("keyboard-language", language.name),
                                View.DragShadowBuilder(this), language.name, 0)
                            true
                        }
                        setOnDragListener { view, event ->
                            if (event.action == android.view.DragEvent.ACTION_DROP) {
                                val from = event.localState as? String ?: return@setOnDragListener true
                                val to = (view.tag as? KeyboardLanguage) ?: return@setOnDragListener true
                                val order = KeyboardLanguageOrderSettings.read(this@MainActivity).toMutableList()
                                if (from == to.name) return@setOnDragListener true
                                val index = order.indexOf(to).coerceAtLeast(0)
                                order.remove(KeyboardLanguage.valueOf(from))
                                order.add(index.coerceAtMost(order.size), KeyboardLanguage.valueOf(from))
                                KeyboardLanguageOrderSettings.write(this@MainActivity, order)
                                render()
                            }
                            event.action == android.view.DragEvent.ACTION_DRAG_STARTED || event.action == android.view.DragEvent.ACTION_DRAG_ENTERED || event.action == android.view.DragEvent.ACTION_DROP
                        }
                    }
                    list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })
                }
            }
            render()
            section.addView(list)
        }
    }

    private fun addThemeControls(root: LinearLayout) {
        settingSection(root, getString(R.string.theme_title), getString(R.string.settings_theme_description)) { section ->
            val modes = KeyboardThemeMode.entries.take(3)
            section.addView(segmentedControl(
                modes.map { mode -> getString(when (mode) {
                    KeyboardThemeMode.SYSTEM -> R.string.theme_system
                    KeyboardThemeMode.LIGHT -> R.string.theme_light
                    KeyboardThemeMode.DARK -> R.string.theme_dark
                    KeyboardThemeMode.SEOUL_DAY -> R.string.theme_seoul_day
                    KeyboardThemeMode.SEOUL_NIGHT -> R.string.theme_seoul_night
                }) },
                modes.indexOf(KeyboardThemeSettings.read(this)),
            ) { index -> KeyboardThemeSettings.write(this, modes[index]) })
        }
    }

    private fun addOrientationControls(root: LinearLayout) {
        settingSection(root, getString(R.string.settings_screen_layout), getString(R.string.settings_screen_description)) { section ->
            val orientations = KeyboardOrientation.entries
            val detail = LinearLayout(this).apply {
                id = R.id.settings_orientation_content
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(16), 0, 0)
            }
            fun render(orientation: KeyboardOrientation) {
                detail.removeAllViews()
                val heights = KeyboardHeight.entries
                val height = KeyboardLayoutSettings.readHeight(this, orientation)
                detail.addView(settingLabel(getString(R.string.settings_key_height)))
                detail.addView(segmentedControl(
                    heights.map { getString(when (it) {
                        KeyboardHeight.COMPACT -> R.string.height_compact
                        KeyboardHeight.NORMAL -> R.string.height_normal
                        KeyboardHeight.TALL -> R.string.height_tall
                    }) },
                    heights.indexOf(height),
                ) { index -> KeyboardLayoutSettings.writeHeight(this, orientation, heights[index]) })

                detail.addView(LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(18), 0, dp(4))
                    addView(LinearLayout(this@MainActivity).apply {
                        this.orientation = LinearLayout.VERTICAL
                        addView(settingLabel(getString(R.string.settings_number_row)))
                        addView(TextView(this@MainActivity).apply {
                            text = getString(R.string.settings_number_row_description)
                            textSize = 13f
                            setTextColor(COLOR_TEXT_MUTED)
                        })
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(Switch(this@MainActivity).apply {
                        isChecked = KeyboardLayoutSettings.readNumberRowEnabled(this@MainActivity, orientation)
                        contentDescription = getString(R.string.settings_number_row)
                        setOnCheckedChangeListener { _, checked ->
                            KeyboardLayoutSettings.writeNumberRowEnabled(this@MainActivity, orientation, checked)
                        }
                    })
                })
            }
            section.addView(segmentedControl(
                listOf(getString(R.string.orientation_portrait), getString(R.string.orientation_landscape)), 0,
            ) { index -> render(orientations[index]) })
            section.addView(detail)
            render(KeyboardOrientation.PORTRAIT)
        }
    }

    private fun addPhraseControls(root: LinearLayout) {
        val phrases = UserPhraseStore.read(this)
        settingSection(
            root,
            getString(R.string.phrases_title),
            getString(R.string.phrases_description, UserPhraseStore.MAX_PHRASES),
        ) { section ->
            val list = LinearLayout(this).apply {
                id = R.id.phrase_list
                orientation = LinearLayout.VERTICAL
            }
            if (phrases.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = getString(R.string.phrases_empty)
                    textSize = 14f
                    setTextColor(COLOR_TEXT_MUTED)
                    setPadding(0, 0, 0, dp(8))
                })
            } else {
                phrases.forEachIndexed { index, phrase ->
                    list.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        if (index > 0) setPadding(0, dp(12), 0, 0)
                        addView(TextView(this@MainActivity).apply {
                            text = phrase.title
                            textSize = 16f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(COLOR_TEXT)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = phrase.content.replace('\n', ' ')
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            textSize = 13f
                            setTextColor(COLOR_TEXT_MUTED)
                            setPadding(0, dp(2), 0, dp(4))
                        })
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            addView(Button(this@MainActivity).apply {
                                text = getString(R.string.phrase_edit)
                                isAllCaps = false
                                contentDescription = getString(R.string.phrase_edit_named, phrase.title)
                                setOnClickListener { showPhraseEditor(phrase) }
                            }, LinearLayout.LayoutParams(0, dp(44), 1f))
                            addView(Button(this@MainActivity).apply {
                                text = getString(R.string.phrase_delete)
                                isAllCaps = false
                                contentDescription = getString(R.string.phrase_delete_named, phrase.title)
                                setOnClickListener { confirmPhraseDelete(phrase) }
                            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
                        })
                    })
                }
            }
            section.addView(list)
            section.addView(primaryButton(getString(R.string.phrase_add)) {
                showPhraseEditor(null)
            }.apply {
                id = R.id.phrase_add
                isEnabled = phrases.size < UserPhraseStore.MAX_PHRASES
                alpha = if (isEnabled) 1f else .5f
            })
        }
    }

    private fun showPhraseEditor(phrase: UserPhrase?) {
        val titleInput = EditText(this).apply {
            id = R.id.phrase_title_input
            hint = getString(R.string.phrase_title_hint)
            setText(phrase?.title.orEmpty())
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(UserPhraseStore.MAX_TITLE_LENGTH))
        }
        val contentInput = EditText(this).apply {
            id = R.id.phrase_content_input
            hint = getString(R.string.phrase_content_hint)
            setText(phrase?.content.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
            maxLines = 6
            filters = arrayOf(InputFilter.LengthFilter(UserPhraseStore.MAX_CONTENT_LENGTH))
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            addView(titleInput, matchWrap())
            addView(contentInput, matchWrap(top = 10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(if (phrase == null) R.string.phrase_add else R.string.phrase_edit))
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.phrase_save, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text.toString()
            val content = contentInput.text.toString()
            when (UserPhraseStore.validationError(title, content)) {
                PhraseValidationError.EMPTY_TITLE -> titleInput.error = getString(R.string.phrase_title_required)
                PhraseValidationError.EMPTY_CONTENT -> contentInput.error = getString(R.string.phrase_content_required)
                PhraseValidationError.TITLE_TOO_LONG -> titleInput.error = getString(R.string.phrase_title_too_long, UserPhraseStore.MAX_TITLE_LENGTH)
                PhraseValidationError.CONTENT_TOO_LONG -> contentInput.error = getString(R.string.phrase_content_too_long, UserPhraseStore.MAX_CONTENT_LENGTH)
                null -> {
                    val saved = if (phrase == null) {
                        UserPhraseStore.add(this, title, content) != null
                    } else {
                        UserPhraseStore.update(this, phrase.id, title, content)
                    }
                    if (saved) {
                        dialog.dismiss()
                        showSettings()
                    } else {
                        Toast.makeText(this, R.string.phrase_save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun confirmPhraseDelete(phrase: UserPhrase) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.phrase_delete)
            .setMessage(getString(R.string.phrase_delete_confirmation, phrase.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.phrase_delete, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            UserPhraseStore.delete(this, phrase.id)
            dialog.dismiss()
            showSettings()
        }
    }

    private fun settingSection(root: LinearLayout, title: String, description: String?, build: (LinearLayout) -> Unit) {
        sectionTitle(root, title)
        description?.let { root.addView(TextView(this).apply {
            text = it; textSize = 14f; setTextColor(COLOR_TEXT_MUTED); setPadding(0, 0, 0, dp(8))
        }) }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(Color.WHITE, Color.rgb(224, 228, 232))
            build(this)
        }, matchWrap())
    }

    private fun settingLabel(label: String) = TextView(this).apply {
        text = label; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(COLOR_TEXT)
        setPadding(0, 0, 0, dp(8))
    }

    private fun settingValue(label: String) = TextView(this).apply {
        text = label; textSize = 15f; setTextColor(COLOR_TEXT)
    }

    private fun segmentedControl(labels: List<String>, selected: Int, onSelected: (Int) -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val buttons = labels.mapIndexed { index, label ->
                Button(this@MainActivity).apply {
                    text = label; isAllCaps = false; textSize = 14f
                    minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                    setPadding(dp(4), 0, dp(4), 0)
                    isSelected = index == selected
                }.also { addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (index > 0) marginStart = dp(6) }) }
            }
            fun refresh(active: Int) = buttons.forEachIndexed { index, button ->
                val chosen = index == active
                button.isSelected = chosen
                button.setTextColor(if (chosen) Color.WHITE else COLOR_TEXT)
                button.background = roundedBackground(if (chosen) COLOR_ACCENT else COLOR_SEGMENT, Color.TRANSPARENT)
            }
            buttons.forEachIndexed { index, button -> button.setOnClickListener { refresh(index); onSelected(index) } }
            refresh(selected)
        }

    private fun roundedBackground(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(fill)
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    companion object {
        private const val CAFE_FRAME_MS = 16L
        private val COLOR_BACKGROUND = Color.rgb(247, 248, 250)
        private val COLOR_TEXT = Color.rgb(31, 36, 42)
        private val COLOR_TEXT_MUTED = Color.rgb(91, 101, 111)
        private val COLOR_ACCENT = Color.rgb(22, 105, 84)
        private val COLOR_SEGMENT = Color.rgb(235, 238, 241)
        private val COLOR_SUCCESS = Color.rgb(24, 122, 82)
        private val COLOR_WARNING = Color.rgb(173, 91, 16)
        private val COLOR_ERROR = Color.rgb(183, 45, 54)
    }
}
