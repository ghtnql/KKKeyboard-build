package com.ghtnql.kkkeyboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Insets
import android.os.Looper
import android.text.InputType
import android.text.Selection
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardViewTest {
    private lateinit var controller: ServiceController<KoreanKeyboardService>
    private lateinit var activity: ActivityController<Activity>
    private lateinit var service: KoreanKeyboardService
    private lateinit var root: LinearLayout
    private lateinit var connection: RecordingConnection

    private class RecordingConnection(view: View) : BaseInputConnection(view, true) {
        var lastAction: Int? = null
        val cursorEvents = mutableListOf<Int>()
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_DPAD_LEFT && event.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return super.sendKeyEvent(event)
            }
            if (event.action == KeyEvent.ACTION_DOWN) cursorEvents += event.keyCode
            return true
        }
        var selectionListener: ((IntArray) -> Unit)? = null
        var deferSelectionUpdates = false
        var supportsBatches = true
        private val updates = mutableListOf<IntArray>()
        private var batchDepth = 0
        private var lastSelection = intArrayOf(0, 0, -1, -1)

        override fun beginBatchEdit(): Boolean { if (!supportsBatches) return false; batchDepth++; return true }
        override fun endBatchEdit(): Boolean {
            if (!supportsBatches) return false
            batchDepth--
            if (batchDepth == 0) reportSelection()
            return batchDepth > 0
        }
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean =
            super.commitText(text, newCursorPosition).also { reportSelection() }
        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean =
            super.setComposingText(text, newCursorPosition).also { reportSelection() }
        override fun finishComposingText(): Boolean = super.finishComposingText().also { reportSelection() }
        override fun setSelection(start: Int, end: Int): Boolean = super.setSelection(start, end).also { reportSelection() }

        private fun reportSelection() {
            if (batchDepth > 0 || selectionListener == null) return
            val text = editable ?: return
            val next = intArrayOf(Selection.getSelectionStart(text), Selection.getSelectionEnd(text), getComposingSpanStart(text), getComposingSpanEnd(text))
            if (next.contentEquals(lastSelection)) return
            val update = intArrayOf(lastSelection[0], lastSelection[1], *next)
            lastSelection = next
            if (deferSelectionUpdates) updates += update else selectionListener?.invoke(update)
        }

        fun deliverSelectionUpdates(coalesced: Boolean = false) {
            val pending = updates.toList()
            updates.clear()
            (if (coalesced) pending.takeLast(1) else pending).forEach { selectionListener?.invoke(it) }
        }
        override fun performEditorAction(editorAction: Int): Boolean {
            lastAction = editorAction
            return true
        }
    }

    @After fun tearDown() {
        if (::service.isInitialized) UserPhraseStore.clear(service)
        if (::controller.isInitialized) controller.destroy()
        if (::activity.isInitialized) activity.pause().stop().destroy()
    }

    private fun start(inputType: Int = InputType.TYPE_CLASS_TEXT, action: Int = EditorInfo.IME_ACTION_NONE, inputLayout: InputLayout = InputLayout.QWERTY, haptics: Boolean = true) {
        controller = Robolectric.buildService(KoreanKeyboardService::class.java).create()
        service = controller.get()
        KeyboardLayoutSettings.writeInputLayout(service, inputLayout)
        KeyboardLayoutSettings.writeHapticFeedbackEnabled(service, haptics)
        val info = EditorInfo().apply { this.inputType = inputType; imeOptions = action; initialSelStart = 0; initialSelEnd = 0 }
        connection = RecordingConnection(View(service))
        ReflectionHelpers.setField(service, "mInputEditorInfo", info)
        ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
        service.onStartInput(info, false)
        root = service.onCreateInputView() as LinearLayout
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        activity.get().setContentView(root)
        layout()
    }

    private fun layout(width: Int = service.resources.configuration.screenWidthDp) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun buttons(view: View = root): List<Button> {
        if (view.visibility != View.VISIBLE) return emptyList()
        if (view is Button) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
    }

    private fun key(label: String) = buttons().single { it.text.toString() == label }
    private fun click(label: String) { assertTrue(key(label).performClick()); layout() }
    private fun bounds(button: Button) = Rect().also {
        button.getDrawingRect(it)
        root.offsetDescendantRectToMyCoords(button, it)
    }

    private fun snapshot(name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("build/reports/keyboard/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val symbolBounds = bounds(key("?123"))
        assertTrue(bitmap.getPixel(0, 0) != bitmap.getPixel(symbolBounds.centerX(), symbolBounds.top + symbolBounds.height() / 4))
        val letter = bounds(buttons().first { it.text.toString() in setOf("ㅂ", "ㅂㅍ", "q") })
        val darkPixels = (letter.left + 4 until letter.right - 4).sumOf { x ->
            (letter.top + 4 until letter.bottom - 4).count { y -> Color.red(bitmap.getPixel(x, y)) < 100 }
        }
        assertTrue("Key text must actually render", darkPixels > 5)
        bitmap.recycle()
    }

    @Test fun inputActivationRequestsKeyboardHapticWhenEnabled() {
        start(inputLayout = InputLayout.CHEONJIIN)
        val button = key("ㄱㅋ")
        button.performClick()
        assertEquals(android.view.HapticFeedbackConstants.KEYBOARD_TAP,
            org.robolectric.Shadows.shadowOf(button).lastHapticFeedbackPerformed())
        assertEquals("ㄱ", connection.editable.toString())
    }

    @Test fun disablingHapticsKeepsTypingAndDoesNotRequestFeedback() {
        start(inputLayout = InputLayout.CHEONJIIN, haptics = false)
        val button = key("ㄱㅋ")
        val previousFeedback = org.robolectric.Shadows.shadowOf(button).lastHapticFeedbackPerformed()
        button.performClick()
        assertEquals(previousFeedback, org.robolectric.Shadows.shadowOf(button).lastHapticFeedbackPerformed())
        assertEquals("ㄱ", connection.editable.toString())
    }

    @Test fun cheonjiinCommitsAtReleaseAndNeverDuplicatesOnHoldOrCancel() {
        start(inputLayout = InputLayout.CHEONJIIN)
        val button = key("ㄱㅋ")
        fun send(action: Int) = MotionEvent.obtain(1L, 1L, action, button.width / 2f, button.height / 2f, 0).also {
            button.dispatchTouchEvent(it); it.recycle()
        }
        send(MotionEvent.ACTION_DOWN)
        assertEquals("", connection.editable.toString())
        send(MotionEvent.ACTION_UP)
        assertEquals("ㄱ", connection.editable.toString())
        send(MotionEvent.ACTION_DOWN)
        send(MotionEvent.ACTION_CANCEL)
        assertEquals("ㄱ", connection.editable.toString())
        send(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals("ㄱ", connection.editable.toString())
        send(MotionEvent.ACTION_UP)
        assertEquals("ㄱ4", connection.editable.toString())
        assertFalse(button.isPressed)
    }

    @Test fun disabledLongPressKeyRejectsAccessibleSymbolAction() {
        start(inputLayout = InputLayout.CHEONJIIN_PLUS)
        val button = key("ㅋ")
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain()
        button.accessibilityDelegate.onInitializeAccessibilityNodeInfo(button, node)
        val action = node.actionList.first { it.label?.toString() == "ㄲ" }
        button.isEnabled = false
        assertFalse(button.accessibilityDelegate.performAccessibilityAction(button, action.id, null))
        assertEquals("", connection.editable.toString())
        button.isEnabled = true
        assertTrue(button.accessibilityDelegate.performAccessibilityAction(button, action.id, null))
        assertEquals("ㄲ", connection.editable.toString())
        node.recycle()
    }

    @Test fun qwertyHoldSuppressesTapAndShortTapTypesOnce() {
        start()
        val button = key("ㅂ")
        fun send(action: Int) = MotionEvent.obtain(1L, 1L, action, button.width / 2f, button.height / 2f, 0).also {
            button.dispatchTouchEvent(it); it.recycle()
        }
        send(MotionEvent.ACTION_DOWN)
        assertEquals("", connection.editable.toString())
        send(MotionEvent.ACTION_UP)
        assertEquals("ㅂ", connection.editable.toString())
        send(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        send(MotionEvent.ACTION_UP)
        assertEquals("ㅂ1", connection.editable.toString())
    }

    @Test fun configuredChoicesPreserveEmptySlotAndSlideCanSelectThirdSymbol() {
        start(inputLayout = InputLayout.CHEONJIIN)
        KeyboardLayoutSettings.writeLongPressSlots(service, InputLayout.CHEONJIIN, "ㅣ", listOf("", "€", "?"))
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        service.onStartInput(info, true)
        service.onStartInputView(info, true)
        layout()
        val button = key("ㅣ")
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain()
        button.accessibilityDelegate.onInitializeAccessibilityNodeInfo(button, node)
        val choiceLabels = node.actionList.mapNotNull { it.label?.toString() }
        assertTrue(choiceLabels.toString(), choiceLabels.containsAll(listOf("€", "?")))
        assertTrue(button.contentDescription.toString().contains("€"))
        fun send(action: Int, x: Float = button.width / 2f, y: Float = button.height / 2f) {
            MotionEvent.obtain(1L, 1L, action, x, y, 0).also { button.dispatchTouchEvent(it); it.recycle() }
        }
        send(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        val popup = ReflectionHelpers.getField<android.widget.PopupWindow>(button, "popup")
        val menu = popup.contentView as LinearLayout
        assertEquals(listOf("€", "?"), (0 until menu.childCount).map { (menu.getChildAt(it) as android.widget.TextView).text.toString() })
        val left = ReflectionHelpers.getField<Int>(button, "popupLeft")
        val width = ReflectionHelpers.getField<Int>(button, "popupWidth")
        send(MotionEvent.ACTION_MOVE, (left + width - 1).toFloat(), -60f)
        send(MotionEvent.ACTION_UP)
        assertEquals("?", connection.editable.toString())
        send(MotionEvent.ACTION_UP)
        assertEquals("?", connection.editable.toString())
    }

    @Test fun cheonjiinPlusBottomRowAlignsIeungMieumUnderSiotHieuh() {
        start(inputLayout = InputLayout.CHEONJIIN_PLUS)
        layout(393)

        val bang = bounds(key("!"))
        val question = bounds(key("?"))
        val mode = bounds(key("한/日"))
        val ieung = bounds(key("ㅇ"))
        val mieum = bounds(key("ㅁ"))
        val space = bounds(key("␣"))
        val period = bounds(key("."))

        fun near(a: Int, b: Int) = kotlin.math.abs(a - b) <= 2
        assertTrue(near(bang.left, question.left))
        assertTrue(near(bang.right, question.right))
        assertTrue(near(bounds(key("?123")).left, bounds(key("ㅂ")).left))
        assertTrue(near(bounds(key("?123")).right, bounds(key("ㅂ")).right))
        assertTrue(near(mode.left, bounds(key("ㅍ")).left))
        assertTrue(near(mode.right, bounds(key("ㅍ")).right))
        assertTrue(near(ieung.left, bounds(key("ㅅ")).left))
        assertTrue(near(ieung.right, bounds(key("ㅅ")).right))
        assertTrue(near(mieum.left, bounds(key("ㅎ")).left))
        assertTrue(near(mieum.right, bounds(key("ㅎ")).right))
        assertTrue(near(space.left, bounds(key("ㅈ")).left))
        assertTrue(near(space.right, bounds(key("ㅊ")).right))
        assertTrue(near(period.left, bounds(key(",")).left))
        assertTrue(near(period.right, bounds(key(",")).right))

        click("!")
        click("?")
        assertEquals("!?", connection.editable.toString())
        click("한/日")
        assertNotEquals(KeyboardLanguage.KOREAN, ReflectionHelpers.getField<KeyboardLanguage>(service, "keyboardLanguage"))
    }

    @Test fun plusKeysUseDirectJamoAndDefaultHoldChoosesFixedTense() {
        start(inputLayout = InputLayout.CHEONJIIN_PLUS)
        click("ㄱ"); click("ㅣ"); click("·")
        assertEquals("가", connection.editable.toString())
        click("ㅋ")
        assertEquals("갘", connection.editable.toString())
        click("ㅣ"); click("·")
        assertEquals("가카", connection.editable.toString())
        val button = key("ㅍ")
        fun send(action: Int) = MotionEvent.obtain(1L, 1L, action, button.width / 2f, button.height / 2f, 0).also {
            button.dispatchTouchEvent(it); it.recycle()
        }
        send(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        send(MotionEvent.ACTION_UP)
        assertEquals("가카ㅃ", connection.editable.toString())
        snapshot("cheonjiin-plus")
        assertEquals("ㄲ", ReflectionHelpers.getField<String>(key("ㅋ"), "hint"))
        assertTrue(ReflectionHelpers.getField<android.graphics.Paint>(key("ㅋ"), "hintPaint").textSize > 0f)
    }

    @Test fun cheonjiinOverlappingFingersReachBothKeysThroughRoot() {
        start(inputLayout = InputLayout.CHEONJIIN)
        val locations = listOf(bounds(key("ㄱㅋ")), bounds(key("ㅣ")))
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply {
            this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        val coords = Array(2) { id -> MotionEvent.PointerCoords().apply {
            x = locations[id].exactCenterX(); y = locations[id].exactCenterY()
            pressure = 1f; size = 1f
        } }
        fun send(action: Int, count: Int) {
            val event = MotionEvent.obtain(1L, 2L, action, count, properties, coords,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            root.dispatchTouchEvent(event); event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
        assertEquals("", connection.editable.toString())
        send(MotionEvent.ACTION_POINTER_UP, 2)
        // Remaining pointer id 1 needs a one-pointer event preserving its coordinates.
        val remaining = MotionEvent.obtain(1L, 3L, MotionEvent.ACTION_UP, 1,
            arrayOf(properties[1]), arrayOf(coords[1]), 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        root.dispatchTouchEvent(remaining); remaining.recycle()
        assertEquals("기", connection.editable.toString())
        val dot = key("·")
        MotionEvent.obtain(3L, 3L, MotionEvent.ACTION_DOWN, dot.width / 2f, dot.height / 2f, 0).also {
            dot.dispatchTouchEvent(it); it.recycle()
        }
        MotionEvent.obtain(3L, 4L, MotionEvent.ACTION_UP, dot.width / 2f, dot.height / 2f, 0).also {
            dot.dispatchTouchEvent(it); it.recycle()
        }
        assertEquals("가", connection.editable.toString())
    }

    @Test fun lettersHaveEqualPitchWithStaggerAndWideDelete() {
        start()
        val letterWidth = key("ㅂ").width
        (TwoBeolsikLayout.characterRows.flatten() + TwoBeolsikLayout.bottomRow).forEach {
            assertTrue("Uneven key: $it", abs(key(it).width - letterWidth) <= 1)
        }
        assertTrue(abs(bounds(key("ㅁ")).left - bounds(key("ㅂ")).left - letterWidth / 2) <= 1)
        assertTrue(abs(key("⇧").width - key("⌫").width) <= 2)
        assertTrue(key("⌫").width >= letterWidth * 1.45)
        assertEquals(bounds(key("ㅡ")).right, bounds(key("⌫")).left)
        assertTrue(key("한국어").width >= root.width * .39)
        snapshot("portrait-360")
    }

    @Test fun narrowAndWideScreensKeepAllKeysAndLabelsInsideBounds() {
        start()
        listOf(320, 360, 412, 800).forEach { width ->
            layout(width)
            buttons().forEach { button ->
                val rect = bounds(button)
                assertTrue("Horizontal overflow: ${button.text}", rect.left >= 0 && rect.right <= width)
                assertTrue("Vertical overflow: ${button.text}", rect.top >= 0 && rect.bottom <= root.height)
                assertTrue("Clipped label: ${button.text}", button.paint.measureText(button.text.toString()) <= button.width - button.totalPaddingLeft - button.totalPaddingRight + 1)
            }
        }
        layout(320)
        snapshot("portrait-320")
    }

    @Test fun landscapeFitsAndKeepsFooterReachable() {
        RuntimeEnvironment.setQualifiers("ko-rKR-w800dp-h360dp-land-mdpi")
        start()
        assertTrue(root.height < 300)
        assertEquals(5, root.childCount)
        snapshot("landscape-800")
    }

    @Test fun navigationBarInsetIsAddedToBasePaddingWithoutAccumulating() {
        start()
        assertEquals(8, root.paddingBottom)

        root.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                .build(),
        )
        assertEquals(32, root.paddingBottom)

        root.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 40))
                .build(),
        )
        assertEquals(48, root.paddingBottom)

        root.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.NONE)
                .build(),
        )
        assertEquals(8, root.paddingBottom)
    }

    @Test fun largerSystemFontStillFitsNarrowKeys() {
        RuntimeEnvironment.setFontScale(1.3f)
        start()
        layout(320)
        buttons().forEach { button ->
            assertTrue("Clipped large text: ${button.text}", button.paint.measureText(button.text.toString()) <= button.width - button.totalPaddingLeft - button.totalPaddingRight + 1)
        }
        snapshot("portrait-large-font")
    }

    @Test fun restartingEditorPreservesSymbolPageAndUpdatesAction() {
        start()
        click("?123")
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEARCH }
        ReflectionHelpers.setField(service, "mInputEditorInfo", info)
        service.onStartInput(info, true)
        service.onStartInputView(info, true)
        layout()
        assertNotNull(key("가나다"))
        click("검색")
        assertEquals(EditorInfo.IME_ACTION_SEARCH, connection.lastAction)
        click("가나다")
        assertNotNull(key("ㅂ"))
    }

    @Test fun shiftedLettersResetAfterOneKeyAndAccessibleDeleteWorks() {
        start()
        click("⇧")
        assertTrue(key("⇧").isActivated)
        click("ㄲ")
        click("ㅏ")
        assertEquals("까", connection.editable.toString())
        assertFalse(key("⇧").isActivated)
        click("⌫")
        assertEquals("ㄲ", connection.editable.toString())
    }

    @Test fun symbolPagesKeepHeightAndCompositionAndReturnToLetters() {
        start()
        click("ㄱ"); click("ㅏ")
        val height = root.height
        val deleteBounds = bounds(key("⌫"))
        click("?123")
        assertEquals(height, root.height)
        assertEquals(deleteBounds, bounds(key("⌫")))
        assertEquals("가", connection.editable.toString())
        assertNotNull(key("◀"))
        assertNotNull(key("▶"))
        click("1"); click("!"); click("1/3"); click("_"); click("←"); click("↑"); click("2/3"); click("♡")
        assertEquals("가1!_←↑♡", connection.editable.toString())
        click("가나다")
        click("ㄴ"); click("ㅏ")
        assertEquals("가1!_←↑♡나", connection.editable.toString())
        assertEquals(height, root.height)
    }

    @Test fun japanesePredictionKeepsKeysFixedAndSelectionCommitsWholeWord() {
        start(action = EditorInfo.IME_ACTION_SEND)
        click("한/日")
        val deleteBounds = bounds(key("⌫"))
        listOf("ㅇ", "ㅏ", "ㄹ", "ㅣ").forEach(::click)
        assertEquals("아리", connection.editable.toString())
        assertEquals(deleteBounds, bounds(key("⌫")))
        assertNotNull(key("ありがとう"))
        assertNotNull(key("변환"))
        snapshot("japanese-candidates")
        click("ありがとう")
        assertEquals("ありがとう", connection.editable.toString())
        click("전송")
        assertEquals(EditorInfo.IME_ACTION_SEND, connection.lastAction)
    }

    @Test fun japaneseCandidatesUseFullWidthAboveToolbarWithoutTypingHeightJumps() {
        start()
        val koreanHeight = root.height
        click("한/日")
        val japaneseHeight = root.height
        assertTrue("Japanese reserves an extra candidate row", japaneseHeight > koreanHeight)
        listOf("ㅇ", "ㅏ", "ㄹ", "ㅣ").forEach(::click)
        val scroll = ReflectionHelpers.getField<android.widget.HorizontalScrollView>(service, "candidateScroll")
        assertEquals("Candidates use full keyboard content width", root.width - root.paddingLeft - root.paddingRight, scroll.width)
        assertTrue("Candidate row is above tools", bounds(key("ありがとう")).bottom <= bounds(key("◐")).top)
        assertEquals(japaneseHeight, root.height)
        snapshot("japanese-candidates-full-width")
        click("ありがとう")
        assertEquals("ありがとう", connection.editable.toString())
        assertEquals("Selection keeps the reserved row", japaneseHeight, root.height)
        click("ABC")
        assertEquals("English removes the Japanese row", koreanHeight, root.height)
    }

    @Test fun japaneseCandidateRowFitsShortLandscapeAndNarrowWidth() {
        RuntimeEnvironment.setQualifiers("ko-rKR-w640dp-h240dp-land-mdpi")
        start()
        click("한/日")
        assertTrue("Extra candidate row must fit landscape height", root.height <= 240)
        assertTrue(bounds(key("↵")).bottom <= root.height - root.paddingBottom)
        listOf("ㅇ", "ㅏ", "ㄹ", "ㅣ").forEach(::click)
        val scroll = ReflectionHelpers.getField<android.widget.HorizontalScrollView>(service, "candidateScroll")
        assertEquals(root.width - root.paddingLeft - root.paddingRight, scroll.width)
        snapshot("japanese-candidates-landscape")
        layout(320)
        assertEquals(root.width - root.paddingLeft - root.paddingRight, scroll.width)
        assertTrue(bounds(key("ありがとう")).bottom <= bounds(key("◐")).top)
        snapshot("japanese-candidates-narrow")
    }

    @Test fun latinModeUsesQwertyCommitsCaseAndReturnsToHangul() {
        start(inputLayout = InputLayout.QWERTY)
        click("한/日")
        click("ABC")
        assertNotNull(key("q"))
        assertTrue(buttons().none { it.text == "ㅂ" })
        snapshot("latin-qwerty")
        click("q")
        click("⇧")
        click("W")
        click("e")
        assertEquals("qWe", connection.editable.toString())

        click("한글")
        assertNotNull(key("ㅂ"))
        click("ㄱ"); click("ㅏ")
        assertEquals("qWe가", connection.editable.toString())
    }

    @Test fun latinModeTemporarilyUsesQwertyThenRestoresCheonjiin() {
        start(inputLayout = InputLayout.CHEONJIIN)
        click("한/日")
        click("ABC")
        assertNotNull(key("q"))
        assertEquals(InputLayout.CHEONJIIN, KeyboardLayoutSettings.readInputLayout(service))
        click("a"); click("b"); click("c")

        click("한글")
        assertNotNull(key("ㄱㅋ"))
        assertEquals(InputLayout.CHEONJIIN, KeyboardLayoutSettings.readInputLayout(service))
        snapshot("latin-restored-cheonjiin")
        click("ㅇㅁ"); click("ㅣ")
        assertEquals("abc이", connection.editable.toString())
    }

    @Test fun cheonjiinCyclesIntoCompoundFinalAndSendsCompleteWord() {
        start(action = EditorInfo.IME_ACTION_SEND, inputLayout = InputLayout.CHEONJIIN)
        observeEditorSelection()
        click("ㅇㅁ"); click("ㅣ"); click("·"); click("ㄴㄹ")
        click("ㅅㅎ")
        assertEquals("안ㅅ", connection.editable.toString())
        click("ㅅㅎ")
        assertEquals("않", connection.editable.toString())
        click("ㅇㅁ"); click("ㅣ"); click("·")
        assertEquals("않아", connection.editable.toString())
        click("전송")
        assertEquals("않아", connection.editable.toString())
        assertEquals(EditorInfo.IME_ACTION_SEND, connection.lastAction)
    }

    @Test fun cheonjiinCyclesRieulCompoundFinalsAndBackspaceRestoresPreviousSyllable() {
        start(inputLayout = InputLayout.CHEONJIIN)
        click("ㅅㅎ"); click("ㅣ"); click("ㄴㄹ"); click("ㄴㄹ")
        click("ㅅㅎ"); click("ㅅㅎ")
        assertEquals("싫", connection.editable.toString())
        click("⌫")
        assertEquals("실", connection.editable.toString())
    }

    @Test fun cheonjiinCursorMoveEndsReplayBeforeNextKey() {
        start(inputLayout = InputLayout.CHEONJIIN)
        observeEditorSelection()
        click("ㅇㅁ"); click("ㅣ"); click("·"); click("ㄴㄹ"); click("ㅅㅎ")
        assertEquals("안ㅅ", connection.editable.toString())
        connection.setSelection(0, 0)
        click("ㅅㅎ")
        assertEquals("ㅅ안ㅅ", connection.editable.toString())
    }

    @Test fun latinModeTemporarilyUsesQwertyThenRestoresHangulFlick() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        click("한/日")
        click("ABC")
        assertNotNull(key("q"))
        assertEquals(InputLayout.HANGUL_FLICK, KeyboardLayoutSettings.readInputLayout(service))
        click("x"); click("y"); click("z")

        click("한글")
        assertTrue(key("ㄱ") is FlickKeyView)
        assertEquals(InputLayout.HANGUL_FLICK, KeyboardLayoutSettings.readInputLayout(service))
        snapshot("latin-restored-flick")
        flickJamo("ㅇ"); flickJamo("ㅏ")
        assertEquals("xyz아", connection.editable.toString())
    }

    @Test fun punctuationAndSpaceCommitKoreanText() {
        start()
        click("ㄱ"); click("ㅏ"); click(","); click("한국어"); click(".")
        assertEquals("가, .", connection.editable.toString())
    }

    private fun observeEditorSelection(deferred: Boolean = false) {
        connection.deferSelectionUpdates = deferred
        connection.selectionListener = { service.onUpdateSelection(it[0], it[1], it[2], it[3], it[4], it[5]) }
    }

    @Test fun editorSelectionCallbacksDoNotSplitKoreanSyllables() {
        start()
        observeEditorSelection()
        "ㅇㅣㄹㅂㅜㄹㅓ ㄱㅗㅊㅣㅈㅣ ㅇㅏㄴㅎㅇㅡㅁㅕㄴ ㅇㅣㄹㅓㅎㄱㅔ ㅈㅓㄱㅎㅣㄴㅡㄴ ㅅㅜㅈㅜㄴ".forEach {
            click(if (it == ' ') "한국어" else it.toString())
        }
        assertEquals("일부러 고치지 않으면 이렇게 적히는 수준", connection.editable.toString())
    }

    @Test fun lateSpaceAcknowledgementDoesNotResetNewComposition() {
        start()
        observeEditorSelection(deferred = true)
        click("ㄱ"); click("ㅏ"); click("한국어"); click("ㄴ")
        connection.deliverSelectionUpdates()
        click("ㅏ")
        connection.deliverSelectionUpdates()
        assertEquals("가 나", connection.editable.toString())
    }

    @Test fun pausingBetweenJamoNeverExpiresComposition() {
        start()
        observeEditorSelection()
        click("ㅇ")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        click("ㅏ"); click("ㄴ")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        click("ㅎ"); click("ㅇ"); click("ㅡ"); click("ㅁ"); click("ㅕ"); click("ㄴ")
        assertEquals("않으면", connection.editable.toString())
    }

    @Test fun realCursorMoveAndSelectionStillEndComposition() {
        start()
        observeEditorSelection()
        click("ㄱ"); click("ㅏ")
        connection.setSelection(0, 0)
        click("ㄴ"); click("ㅏ")
        assertEquals("나가", connection.editable.toString())
        connection.setSelection(0, 2)
        click("ㅂ"); click("ㅏ")
        assertEquals("바", connection.editable.toString())
    }

    @Test fun editorEndingCompositionAtSameCursorIsRespected() {
        start()
        observeEditorSelection()
        click("ㄱ"); click("ㅏ")
        connection.finishComposingText()
        click("ㄴ"); click("ㅏ")
        assertEquals("가나", connection.editable.toString())
    }

    @Test fun editorsWithoutBatchSupportStillKeepComposition() {
        start()
        connection.supportsBatches = false
        observeEditorSelection()
        "ㅇㅏㄴㅎㅇㅡㅁㅕㄴ".forEach { click(it.toString()) }
        assertEquals("않으면", connection.editable.toString())
    }

    @Test fun coalescedAcknowledgementsKeepFinalConsonantEditable() {
        start()
        observeEditorSelection(deferred = true)
        "ㅇㅏㄴㅎㅇㅡㅁㅕ".forEach { click(it.toString()) }
        connection.deliverSelectionUpdates(coalesced = true)
        click("ㄴ")
        connection.deliverSelectionUpdates()
        assertEquals("않으면", connection.editable.toString())
    }

    @Test fun delayedJapaneseCandidateAcknowledgementKeepsNextWordComposing() {
        start()
        observeEditorSelection(deferred = true)
        click("한/日")
        "ㅇㅏㄹㅣ".forEach { click(it.toString()) }
        click("ありがとう")
        click("ㄴ")
        connection.deliverSelectionUpdates()
        click("ㅏ")
        assertEquals("ありがとう나", connection.editable.toString())
    }

    @Test fun spaceConvertsReadingWithoutAcceptingPredictedCompletion() {
        start()
        click("한/日")
        listOf("ㅇ", "ㅏ", "ㄹ", "ㅣ").forEach(::click)
        click("?123"); click("가나다")
        assertNotNull(key("ありがとう"))
        click("日本語")
        assertEquals("あり", connection.editable.toString())
        click("日本語")
        assertEquals("あり ", connection.editable.toString())
    }

    @Test fun numberFieldKeepsNumericRowsAndWorkingDelete() {
        start(InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_DONE)
        assertEquals(KeyboardBaseRows.forMode(InputFieldMode.NUMBER), root.childCount)
        click("1"); click("2"); click("⌫")
        assertEquals("1", connection.editable.toString())
        click("완료")
        assertEquals(EditorInfo.IME_ACTION_DONE, connection.lastAction)
    }

    @Test fun phoneFieldKeepsPhonePunctuation() {
        start(InputType.TYPE_CLASS_PHONE)
        assertEquals(KeyboardBaseRows.forMode(InputFieldMode.PHONE), root.childCount)
        click("+"); click("8"); click("2")
        assertEquals("+82", connection.editable.toString())
    }

    @Test fun emailHasInlineAtAndNoExtraRow() {
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT)
        assertEquals(5, root.childCount)
        click("@")
        assertEquals("@", connection.editable.toString())
        buttons().single { it.contentDescription == service.getString(R.string.key_next) }.performClick()
        assertEquals(EditorInfo.IME_ACTION_NEXT, connection.lastAction)
    }

    @Test fun uriHasInlineSlashAndNoExtraRow() {
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        assertEquals(5, root.childCount)
        click("/")
        assertEquals("/", connection.editable.toString())
    }

    @Test fun passwordKeepsSystemPickerAndLatinModeButNeverOffersCandidates() {
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertNotNull(key("🌐"))
        assertNull(root.findViewById<View>(R.id.key_phrases))
        click("ㅇ"); click("ㅏ")
        assertEquals("아", connection.editable.toString())
        assertTrue(buttons().none { it.text == "ありがとう" })
        click("한/日")
        assertNotNull(key("q"))
        click("q")
        assertEquals("아q", connection.editable.toString())
    }

    private fun toolbarPopup(): android.widget.PopupWindow {
        val menu = ReflectionHelpers.getField<Lazy<KeyboardToolbarMenu>>(service, "toolbarMenu\$delegate").value
        return ReflectionHelpers.getField(menu, "popup")
    }

    private fun menuButtons(popup: android.widget.PopupWindow): List<Button> {
        fun collect(view: View): List<Button> = when (view) {
            is Button -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
            else -> emptyList()
        }
        return collect(popup.contentView)
    }

    private fun chooseMenuItem(title: String) {
        assertTrue(menuButtons(toolbarPopup()).single { it.text.toString().removePrefix("✓ ") == title }.performClick())
    }

    @Test fun defaultPhraseMenuContainsTenItemsAndInsertsJapaneseText() {
        start()
        UserPhraseStore.clear(service)
        assertTrue(root.findViewById<Button>(R.id.key_phrases).performClick())
        val popup = toolbarPopup()
        val items = menuButtons(popup).dropLast(1)
        assertEquals(10, items.size)
        assertEquals("인사", items[0].text.toString())
        assertFalse(popup.isFocusable)
        assertEquals(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED, popup.inputMethodMode)
        assertTrue(items[0].performClick())
        assertFalse(popup.isShowing)
        assertEquals("こんにちは", connection.editable.toString())
    }

    @Test fun customPhraseMenuCommitsPendingCompositionBeforeExactInsertion() {
        start()
        val phrase = UserPhraseStore.add(service, "빠른 인사", "안녕하세요.\n감사합니다.")
        assertNotNull(phrase)
        click("ㄱ"); click("ㅏ")

        assertTrue(root.findViewById<Button>(R.id.key_phrases).performClick())
        chooseMenuItem("빠른 인사")

        assertEquals("가안녕하세요.\n감사합니다.", connection.editable.toString())
        click("ㄴ"); click("ㅏ")
        assertEquals("가안녕하세요.\n감사합니다.나", connection.editable.toString())
    }

    @Test fun toolbarMenusDoNotTakeEditorFocusAndBackClosesOnlyMenu() {
        start()
        click("⌨")
        val popup = toolbarPopup()
        assertFalse(popup.isFocusable)
        assertEquals(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED, popup.inputMethodMode)
        assertEquals(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED, popup.softInputMode)
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertFalse(popup.isShowing)
        assertTrue(service.onKeyUp(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        click("ㄱ"); click("ㅏ")
        assertEquals("가", connection.editable.toString())
        click("▤")
        val phrases = toolbarPopup()
        assertFalse(phrases.isFocusable)
        assertEquals(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED, phrases.inputMethodMode)
        menuButtons(phrases).last().performClick()
        assertFalse(phrases.isShowing)
    }

    @Test fun toolbarMenuClosesWhenInputViewEndsOrKeyboardRendersAgain() {
        start()
        click("▤")
        val phrases = toolbarPopup()
        service.onFinishInputView(false)
        assertFalse(phrases.isShowing)
        click("⌨")
        val layouts = toolbarPopup()
        click("?123")
        assertFalse(layouts.isShowing)
        assertEquals("", connection.editable.toString())
    }

    @Test fun deleteRepeatsOnlyWhileHeldAndStopsOnCancel() {
        start()
        connection.commitText("123456789", 1)
        val button = key("⌫")
        fun touch(action: Int) {
            val event = MotionEvent.obtain(0, 0, action, 10f, 10f, 0)
            button.dispatchTouchEvent(event)
            event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN)
        assertEquals("12345678", connection.editable.toString())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(455))
        assertEquals("123456", connection.editable.toString())
        touch(MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("123456", connection.editable.toString())
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("12345", connection.editable.toString())
    }

    private fun flick(view: FlickKeyView, dx: Float = 0f, dy: Float = 0f, cancel: Boolean = false) {
        val x = view.width * .5f
        val y = view.height * .5f
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 10, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, x, y)
        send(MotionEvent.ACTION_MOVE, x + dx, y + dy)
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x + dx, y + dy)
        layout()
    }

    private fun flickJamo(label: String) {
        val view = buttons().filterIsInstance<FlickKeyView>().first { v -> FlickDirection.entries.any { v.flickKey.label(it) == label } }
        val direction = FlickDirection.entries.first { view.flickKey.label(it) == label }
        val dx = when (direction) { FlickDirection.LEFT -> -36f; FlickDirection.RIGHT -> 36f; else -> 0f }
        val dy = when (direction) { FlickDirection.UP -> -36f; FlickDirection.DOWN -> 36f; else -> 0f }
        flick(view, dx, dy)
    }

    @Test fun flickAllDirectionsEmitExactlyOneCharacterOnRelease() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        observeEditorSelection()
        listOf("ㅗ", "ㅛ", "ㅘ", "ㅙ", "ㅚ").forEach { label ->
            flickJamo(label)
            click("␣")
        }
        assertEquals("ㅗ ㅛ ㅘ ㅙ ㅚ ", connection.editable.toString())
    }

    @Test fun flickComposesFullSentenceWithDelayedEditorCallbacks() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        observeEditorSelection(deferred = true)
        "ㅇㅣㄹㅂㅜㄹㅓ ㄱㅗㅊㅣㅈㅣ ㅇㅏㄴㅎㅇㅡㅁㅕㄴ ㅇㅣㄹㅓㅎㄱㅔ ㅈㅓㄱㅎㅣㄴㅡㄴ ㅅㅜㅈㅜㄴ".forEach {
            if (it == ' ') click("␣") else flickJamo(it.toString())
            connection.deliverSelectionUpdates()
        }
        assertEquals("일부러 고치지 않으면 이렇게 적히는 수준", connection.editable.toString())
    }

    @Test fun flickRepeatedTapsNeedNoWaitingAndEmptyDirectionsDoNotInput() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        val giyeok = key("ㄱ") as FlickKeyView
        flick(giyeok); flick(giyeok); flick(giyeok)
        flick(giyeok, 36f, 0f)
        flick(giyeok, -36f, 0f, cancel = true)
        flick(giyeok, 500f, 0f)
        assertEquals("ㄱㄱㄱ", connection.editable.toString())
    }

    @Test fun flickAccessibilityActionsReachNonCentralLetters() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        val giyeok = key("ㄱ") as FlickKeyView
        assertTrue(giyeok.performAccessibilityAction(R.id.flick_up, null))
        flickJamo("ㅏ")
        assertEquals("까", connection.editable.toString())
        assertTrue(giyeok.createAccessibilityNodeInfo().actionList.any { it.id == R.id.flick_left })
    }

    @Test fun flickJapanesePredictionUsesExistingWordComposer() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        observeEditorSelection()
        click("한/日")
        "ㅇㅏㄹㅣ".forEach { flickJamo(it.toString()) }
        assertNotNull(key("ありがとう"))
        snapshot("flick-japanese")
        click("ありがとう")
        assertEquals("ありがとう", connection.editable.toString())
    }

    @Test fun flickAndQwertySwitchWithoutLosingComposingText() {
        start()
        observeEditorSelection()
        click("ㄱ")
        click("⌨")
        chooseMenuItem(service.getString(R.string.layout_flick))
        layout()
        assertEquals(InputLayout.HANGUL_FLICK, KeyboardLayoutSettings.readInputLayout(service))
        flickJamo("ㅏ")
        assertEquals("가", connection.editable.toString())
        click("?123"); click("가나다")
        assertTrue(key("ㄱ") is FlickKeyView)
        click("⌨")
        chooseMenuItem(service.getString(R.string.layout_qwerty))
        layout()
        click("ㄴ")
        assertEquals("간", connection.editable.toString())
    }

    @Test fun flickFitsSmallPortraitAndLandscapeWithoutAddingHeight() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        assertEquals(5, root.childCount)
        listOf(320, 360, 412, 800).forEach { width ->
            layout(width)
            buttons().forEach { button ->
                val rect = bounds(button)
                assertTrue("Flick overflow: ${button.text}", rect.left >= 0 && rect.right <= width && rect.top >= 0 && rect.bottom <= root.height)
            }
            val mainKeys = buttons().filterIsInstance<FlickKeyView>().filter { it.flickKey.center != "." }
            assertTrue(mainKeys.all { it.width >= 70 && it.height >= 44 })
            val left = bounds(key("←"))
            val right = bounds(key("→"))
            assertEquals(left.left, right.left)
            assertEquals(left.bottom, right.top)
            assertTrue(abs(left.width() - right.width()) <= 1)
            assertEquals(bounds(key("⌫")).left, bounds(key("↵")).left)
            snapshot("flick-$width")
        }
    }

    @Test fun flickDistanceSettingIsClampedAndUsedWhenReopeningKeyboard() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        KeyboardLayoutSettings.writeFlickDistance(service, 100)
        assertEquals(32, KeyboardLayoutSettings.readFlickDistance(service))
        service.onStartInputView(service.currentInputEditorInfo, true)
        layout()
        flick(key("ㄱ") as FlickKeyView, -20f, 0f)
        assertEquals("ㄱ", connection.editable.toString())
        KeyboardLayoutSettings.writeFlickDistance(service, 0)
        assertEquals(12, KeyboardLayoutSettings.readFlickDistance(service))
        service.onStartInputView(service.currentInputEditorInfo, true)
        layout()
        flick(key("ㄱ") as FlickKeyView, -20f, 0f)
        assertEquals("ㄱㅋ", connection.editable.toString())
    }

    @Test fun flickLandscapeAndLargeFontRemainInsideKeyboard() {
        RuntimeEnvironment.setQualifiers("ko-rKR-w800dp-h360dp-land-mdpi")
        RuntimeEnvironment.setFontScale(1.3f)
        start(inputLayout = InputLayout.HANGUL_FLICK)
        assertTrue(root.height < 300)
        snapshot("flick-landscape")
    }

    @Test fun holdingFlickShowsPreviewButHidingKeyboardCancelsInput() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        val view = key("ㄱ") as FlickKeyView
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(0, 10, action, view.width / 2f, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, view.height / 2f)
        send(MotionEvent.ACTION_MOVE, view.height / 2f - 36)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals("", connection.editable.toString())
        layout()
        snapshot("flick-preview")
        service.onWindowHidden()
        send(MotionEvent.ACTION_UP, view.height / 2f - 36)
        assertEquals("", connection.editable.toString())
    }

    @Test fun extraFingerCannotCommitOrDuplicateTheActiveFlick() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        val view = key("ㄱ") as FlickKeyView
        val primary = MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val secondary = MotionEvent.PointerProperties().apply { id = 8; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val point = MotionEvent.PointerCoords().apply { x = view.width / 2f; y = view.height / 2f; pressure = 1f; size = 1f }
        fun send(action: Int, two: Boolean) {
            val event = MotionEvent.obtain(0, 10, action, if (two) 2 else 1,
                if (two) arrayOf(primary, secondary) else arrayOf(primary),
                if (two) arrayOf(point, point) else arrayOf(point), 0, 0, 1f, 1f, 0, 0, 0, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, false)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), true)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), true)
        assertEquals("", connection.editable.toString())
        send(MotionEvent.ACTION_UP, false)
        assertEquals("ㄱ", connection.editable.toString())
    }

    @Test fun draggingAcrossNeighborKeepsOriginalKeyAsGestureOwner() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        val rect = bounds(key("ㄱ"))
        fun send(action: Int, dx: Float) {
            val event = MotionEvent.obtain(0, 10, action, rect.exactCenterX() + dx, rect.exactCenterY(), 0)
            root.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        send(MotionEvent.ACTION_MOVE, -70f)
        send(MotionEvent.ACTION_UP, -70f)
        assertEquals("ㅋ", connection.editable.toString())
    }

    @Test fun cheonjiinComposesKoreanAndDeletesOneVowelStrokeAtATime() {
        start(inputLayout = InputLayout.CHEONJIIN)
        observeEditorSelection()
        click("ㅅㅎ"); click("ㅅㅎ")
        click("ㅣ"); click("·"); click("ㄴㄹ")
        click("ㄱㅋ"); click("ㅡ"); click("ㄴㄹ"); click("ㄴㄹ")
        assertEquals("한글", connection.editable.toString())

        click("ㅅㅎ"); click("ㅣ"); click("·"); click("·")
        assertEquals("한글샤", connection.editable.toString())
        click("⌫")
        assertEquals("한글사", connection.editable.toString())
        click("⌫")
        assertEquals("한글시", connection.editable.toString())
        snapshot("cheonjiin-korean")
    }

    @Test fun qwertyCursorKeysCommitCompositionAndIgnoreLegacyExtraRowSetting() {
        start()
        observeEditorSelection()
        service.getSharedPreferences("keyboard_layout", 0).edit()
            .putBoolean("cursor_row_portrait", true).apply()
        service.onStartInputView(service.currentInputEditorInfo, true)
        layout(320)
        assertEquals(5, root.childCount)
        assertEquals(44, key("←").width)
        assertEquals(bounds(key("←")).top, bounds(key("→")).top)
        click("ㄱ"); click("ㅏ"); click("←"); click("→")
        assertEquals("가", connection.editable.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(requireNotNull(connection.editable)))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT), connection.cursorEvents)
        click("?123")
        assertNotNull(key("◀"))
        assertNotNull(key("▶"))
        assertEquals(5, root.childCount)
    }

    @Test fun cheonjiinFeedsExistingJapaneseWordCandidates() {
        start(inputLayout = InputLayout.CHEONJIIN)
        observeEditorSelection()
        click("한/日")
        click("ㅇㅁ"); click("ㅣ"); click("·")
        click("ㄴㄹ"); click("ㄴㄹ"); click("ㅣ")
        assertEquals("아리", connection.editable.toString())
        assertNotNull(key("ありがとう"))
        click("ありがとう")
        assertEquals("ありがとう", connection.editable.toString())
    }

    @Test fun cheonjiinCoreKeysStayLargeOnNarrowAndLandscapeScreens() {
        start(inputLayout = InputLayout.CHEONJIIN)
        assertEquals(5, root.childCount)
        listOf(320, 360, 412, 800).forEach { width ->
            layout(width)
            listOf("ㅣ", "·", "ㅡ", "ㄱㅋ", "ㄴㄹ", "ㄷㅌ", "ㅂㅍ", "ㅅㅎ", "ㅈㅊ", "ㅇㅁ").forEach {
                val rect = bounds(key(it))
                assertTrue("Cheonjiin overflow: $it", rect.left >= 0 && rect.right <= width)
                assertTrue("Cheonjiin key too small: $it", rect.width() >= 70 && rect.height() >= 44)
            }
            val left = bounds(key("←"))
            val right = bounds(key("→"))
            assertEquals(left.left, right.left)
            assertEquals(left.bottom, right.top)
            assertEquals(left.height(), right.height())
            assertTrue(abs(left.width() - right.width()) <= 1)
            assertEquals(bounds(key("⌫")).left, bounds(key("↵")).left)
            val bottomTop = bounds(key("␣")).top
            listOf("?", "?123", "한/日", "ㅇㅁ", ".").forEach { assertEquals(bottomTop, bounds(key(it)).top) }
            assertTrue(bounds(key("␣")).width() >= bounds(key(".")).width() * 2 - 1)
        }
        layout(360)
        snapshot("cheonjiin-reference-360")
    }

    @Test fun japaneseLayoutSettingsFitNarrowScreenAndPersistSelection() {
        RuntimeEnvironment.setQualifiers("ja-rJP-w320dp-h800dp-mdpi")
        RuntimeEnvironment.setFontScale(1.3f)
        val settings = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        try {
            settings.get().findViewById<Button>(R.id.open_app_settings).performClick()
            val decor = settings.get().window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 320, 800)
            val layoutLabels = setOf("QWERTY", "天地人", "ハングルフリック")
            val choices = buttons(decor).filter { it.text.toString() in layoutLabels }
            assertEquals(3, choices.size)
            choices.forEach { assertTrue(it.width > 0 && it.right <= (it.parent as View).width) }
            choices.last().performClick()
            assertEquals(InputLayout.HANGUL_FLICK, KeyboardLayoutSettings.readInputLayout(settings.get()))
            choices[1].performClick()
            assertEquals(InputLayout.CHEONJIIN, KeyboardLayoutSettings.readInputLayout(settings.get()))
            choices.first().performClick()
            assertEquals(InputLayout.QWERTY, KeyboardLayoutSettings.readInputLayout(settings.get()))
        } finally { settings.pause().stop().destroy() }
    }

// Test methods to integrate inside the existing KeyboardViewTest body
// (Robolectric). Uses only its available private helpers: start(...),
// key(title), click(title), layout(width), bounds(view), service,
// connection.editable. Not a full class.

@Test fun plusThirdRowLeftBangInsertsLiteral() {
    start(inputLayout = InputLayout.CHEONJIIN_PLUS)
    layout(width = 393)
    val bang = bounds(key("!"))
    val bieup = bounds(key("ㅂ"))
    // Same row: shared vertical center; first column sits left of ㅂ.
    assertTrue(Math.abs(bieup.centerY() - bang.centerY()) <= 2)
    assertTrue(bang.right <= bieup.left + 2)
    click("!")
    assertTrue(connection.editable.toString().endsWith("!"))
}

@Test fun plusBottomRowOrderWeightsAndSymbolRoundTrip() {
    start(inputLayout = InputLayout.CHEONJIIN_PLUS)
    layout(width = 393)
    // Order left to right: ? / ?123 / mode / ㅇ / ㅁ / space / .
    val titles = listOf("?", "?123", "한/日", "ㅇ", "ㅁ", "␣", ".")
    val boxes = titles.associateWith { bounds(key(it)) }
    val tops = boxes.values.map { it.centerY() }
    assertTrue(tops.max() - tops.min() <= 2)
    var prevRight = Int.MIN_VALUE
    titles.forEach {
        val box = boxes.getValue(it)
        assertTrue(box.left + 2 >= prevRight)
        prevRight = box.right
    }
    // 8 weight-columns: ?(1) ?123(1) mode(1) ㅇ(1) ㅁ(1) space(2) .(1).
    // First-column center aligns with third-row "!" center; last-column
    // center aligns with third-row "," center.
    assertTrue(Math.abs(bounds(key("!")).centerX() - boxes.getValue("?").centerX()) <= 2)
    assertTrue(Math.abs(bounds(key(",")).centerX() - boxes.getValue(".").centerX()) <= 2)
    // Mid-column centers align against the consonant row (ㅅ/ㅎ area).
    assertTrue(Math.abs(bounds(key("ㅅ")).centerX() - boxes.getValue("ㅇ").centerX()) <= 3)
    assertTrue(Math.abs(bounds(key("ㅎ")).centerX() - boxes.getValue("ㅁ").centerX()) <= 3)
    // Direct "?" insert.
    click("?")
    assertTrue(connection.editable.toString().endsWith("?"))
    // ?123 opens the symbol page, then letters return via 가나다.
    click("?123")
    assertNotNull(key("가나다"))
    click("가나다")
    assertNotNull(key("ㅂ"))
}

@Test fun modeLongPressDirectLanguageSelection() {
    start(inputLayout = InputLayout.CHEONJIIN_PLUS)
    layout(width = 393)
    val modeView = key("한/日")
    val englishAction = modeView.createAccessibilityNodeInfo().actionList
        .firstOrNull { it.label == "English" }
        ?: error("English custom action missing on mode key")
    modeView.performAccessibilityAction(englishAction.id, null)
    // Render replaces all buttons, so re-layout and reacquire live views.
    layout(width = 393)
    // Temporary English QWERTY: Latin key appears ...
    assertNotNull(key("q"))
    // ... and the selection is persisted as ENGLISH ...
    assertEquals(
        "ENGLISH",
        service.getSharedPreferences("keyboard_input_mode", 0)
            .getString("keyboard_language", null),
    )
    // ... while the preferred Korean layout setting is preserved.
    assertEquals(
        InputLayout.CHEONJIIN_PLUS,
        KeyboardLayoutSettings.readInputLayout(service),
    )
    // Switch back via the current (new-render) mode key's "한국어" action.
    val englishModeView = key(service.getString(R.string.mode_korean_short))
    val koreanAction = englishModeView.createAccessibilityNodeInfo().actionList
        .firstOrNull { it.label == "한국어" }
        ?: error("한국어 custom action missing on mode key")
    englishModeView.performAccessibilityAction(koreanAction.id, null)
    layout(width = 393)
    // Plus layout restored.
    assertNotNull(key("ㅂ"))
    assertNotNull(key("!"))
}

@Test fun modeLongPressDirectJapaneseOnNormalField() {
    start(inputLayout = InputLayout.CHEONJIIN_PLUS)
    layout(width = 393)
    val modeView = key("한/日")
    val japaneseAction = modeView.createAccessibilityNodeInfo().actionList
        .firstOrNull { it.label == "日本語" }
        ?: error("日本語 custom action missing on mode key")
    modeView.performAccessibilityAction(japaneseAction.id, null)
    assertEquals(
        "JAPANESE",
        service.getSharedPreferences("keyboard_input_mode", 0)
            .getString("keyboard_language", null),
    )
}

// NOTE: the native physical hold-slide-release gesture itself is not
// exercised here (no known MotionEvent + ShadowLooper helper); coverage
// goes through the same TalkBack custom actions the gesture feeds.


    private fun cursorTouch(button: View, action: Int, x: Float = button.width / 2f) {
        MotionEvent.obtain(1L, 1L, action, x, button.height / 2f, 0).also {
            button.dispatchTouchEvent(it); it.recycle()
        }
    }

    @Test fun cursorHoldRepeatsAndReleaseStopsWithoutExtraStep() {
        start(inputLayout = InputLayout.CHEONJIIN_PLUS)
        val button = key("←")
        cursorTouch(button, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(340))
        assertTrue(connection.cursorEvents.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(210))
        assertTrue(connection.cursorEvents.size >= 4)
        assertTrue(connection.cursorEvents.all { it == KeyEvent.KEYCODE_DPAD_LEFT })
        val steps = connection.cursorEvents.size
        cursorTouch(button, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(steps, connection.cursorEvents.size)
    }

    @Test fun cursorShortTapOnceAndCancelHideStopRepeat() {
        start(inputLayout = InputLayout.CHEONJIIN)
        val button = key("→")
        cursorTouch(button, MotionEvent.ACTION_DOWN)
        cursorTouch(button, MotionEvent.ACTION_UP)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT), connection.cursorEvents)
        cursorTouch(button, MotionEvent.ACTION_DOWN)
        cursorTouch(button, MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(1, connection.cursorEvents.size)
        cursorTouch(button, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        val steps = connection.cursorEvents.size
        service.onFinishInputView(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(steps, connection.cursorEvents.size)
        cursorTouch(button, MotionEvent.ACTION_UP)
        assertEquals(steps, connection.cursorEvents.size)
    }

    @Test fun cursorMovingOutsideCancelsAndOldReleaseCannotStopNewOwner() {
        start(inputLayout = InputLayout.HANGUL_FLICK)
        val left = key("←"); val right = key("→")
        cursorTouch(left, MotionEvent.ACTION_DOWN)
        cursorTouch(left, MotionEvent.ACTION_MOVE, -100f)
        cursorTouch(left, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertTrue(connection.cursorEvents.isEmpty())
        cursorTouch(left, MotionEvent.ACTION_DOWN)
        cursorTouch(right, MotionEvent.ACTION_DOWN)
        cursorTouch(left, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450))
        assertTrue(connection.cursorEvents.size >= 2)
        assertTrue(connection.cursorEvents.all { it == KeyEvent.KEYCODE_DPAD_RIGHT })
        cursorTouch(right, MotionEvent.ACTION_UP)
    }

    @Test fun languageHoldSlideChoosesEnglishAndDoesNotAlsoCycleOnRelease() {
        start(inputLayout = InputLayout.CHEONJIIN_PLUS)
        val button = key("한/日")
        cursorTouch(button, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        val left = ReflectionHelpers.getField<Int>(button, "popupLeft")
        val width = ReflectionHelpers.getField<Int>(button, "popupWidth")
        cursorTouch(button, MotionEvent.ACTION_MOVE, (left + width - 1).toFloat())
        cursorTouch(button, MotionEvent.ACTION_UP)
        layout()
        assertEquals("ENGLISH", service.getSharedPreferences("keyboard_input_mode", 0).getString("keyboard_language", null))
        assertNotNull(key("q"))
        assertEquals(InputLayout.CHEONJIIN_PLUS, KeyboardLayoutSettings.readInputLayout(service))
    }
}
