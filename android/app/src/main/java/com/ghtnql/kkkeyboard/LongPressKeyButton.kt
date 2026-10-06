package com.ghtnql.kkkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.ghtnql.kkkeyboard.sharedcore.LongPressGestureState

/** A delayed tap and a hold share one owner, so releasing a hold cannot also type a tap. */
open class LongPressKeyButton(context: Context) : Button(context) {
    private val gesture = LongPressGestureState()
    private val timer = Handler(Looper.getMainLooper())
    private var choices: List<String> = emptyList()
    private var onChoice: (String) -> Unit = {}
    private var popup: PopupWindow? = null
    private var choiceViews: List<TextView> = emptyList()
    private var selected = 0
    private var popupLeft = 0
    private var popupWidth = 1
    private var holding = false
    private var downX = 0f
    private var downY = 0f
    private var selecting = false
    private var hint = ""
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hold = Runnable {
        if (!isEnabled) { cancelPress(); return@Runnable }
        if (choices.isNotEmpty() && gesture.hold()) {
            holding = true
            selected = 0
            showChoices()
        }
    }

    fun configureLongPress(values: List<String>, representativeHint: String, onSelected: (String) -> Unit) {
        cancelPress()
        choices = values
        hint = representativeHint
        onChoice = onSelected
        isLongClickable = values.isNotEmpty()
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                choices.forEachIndexed { index, value ->
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(CHOICE_ACTION_BASE + index, value))
                }
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val value = choices.getOrNull(action - CHOICE_ACTION_BASE)
                if (value != null) {
                    cancelPress()
                    if (!host.isEnabled) return false
                    onChoice(value)
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        setOnTouchListener { view, event ->
            if (!view.isEnabled) { cancelPress(); return@setOnTouchListener true }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelPress()
                    gesture.down()
                    downX = event.rawX
                    downY = event.rawY
                    selecting = false
                    view.isPressed = true
                    timer.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (holding) {
                        if (kotlin.math.abs(event.rawX - downX) > touchSlop ||
                            kotlin.math.abs(event.rawY - downY) > touchSlop) selecting = true
                        if (selecting) {
                            val index = ((event.rawX - popupLeft) / popupWidth * choices.size).toInt().coerceIn(0, choices.lastIndex)
                            if (index != selected) { selected = index; updateSelection() }
                        }
                    } else if (event.x < -touchSlop || event.y < -touchSlop ||
                        event.x > width + touchSlop || event.y > height + touchSlop) cancelPress()
                }
                MotionEvent.ACTION_UP -> {
                    timer.removeCallbacks(hold)
                    if (holding) {
                        val value = choices[selected]
                        gesture.release()
                        cancelPress()
                        onChoice(value)
                    } else {
                        val tap = gesture.release()
                        cancelPress()
                        if (tap) view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> cancelPress()
            }
            true
        }
        invalidate()
    }

    private val touchSlop get() = ViewConfiguration.get(context).scaledTouchSlop

    private fun showChoices() {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val cellHeight = (48 * density).toInt()
        // Measure widest label at the popup text size so multi-script labels
        // (Korean, Japanese, English) fit on one line. Single-line + no
        // ellipsis: when clamped to the host width the text shrinks instead.
        val popupSp = if (choices.any { it.length > 1 }) 18f else 22f
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = popupSp * metrics.scaledDensity
        }
        val widestText = choices.maxOfOrNull { measurePaint.measureText(it) } ?: 0f
        val horizontalPadding = (16 * density).toInt()
        val desiredCell = maxOf((48 * density).toInt(), (widestText + horizontalPadding).toInt() + 1)
        val available = if (rootView.width > 0) rootView.width else metrics.widthPixels
        // Clamp total popup to the host viewport; rederive the ACTUAL per-cell
        // width so the selected-index math below stays exact.
        val perCell = maxOf(1, minOf(desiredCell, available / choices.size.coerceAtLeast(1)))
        val fittedSp = if (perCell < desiredCell && widestText > 0f) {
            (popupSp * (perCell - horizontalPadding).coerceAtLeast(1) / (widestText + 1f)).coerceIn(11f, popupSp)
        } else popupSp
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(40, 45, 55))
            elevation = 8 * density
        }
        choiceViews = choices.map { value ->
            TextView(context).apply {
                text = value
                setSingleLine(true)
                maxLines = 1
                ellipsize = null
                textSize = fittedSp
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                contentDescription = value
                row.addView(this, LinearLayout.LayoutParams(perCell, cellHeight))
            }
        }
        val location = IntArray(2).also(::getLocationOnScreen)
        val rootLocation = IntArray(2).also(rootView::getLocationOnScreen)
        popupWidth = perCell * choices.size
        popupLeft = (location[0] + width / 2 - popupWidth / 2)
            .coerceIn(rootLocation[0], (rootLocation[0] + rootView.width - popupWidth).coerceAtLeast(rootLocation[0]))
        popup = PopupWindow(row, popupWidth, cellHeight, false).apply {
            isTouchable = false
            isClippingEnabled = true
            // getLocationOnScreen/rootLocation/popupLeft above are screen
            // coordinates, so force FLAG_LAYOUT_IN_SCREEN (API 29+) before
            // showAtLocation, whose x/y are otherwise window-relative.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setIsLaidOutInScreen(true)
            showAtLocation(rootView, Gravity.TOP or Gravity.LEFT, popupLeft, (location[1] - cellHeight).coerceAtLeast(rootLocation[1]))
        }
        updateSelection()
    }

    private fun updateSelection() {
        choiceViews.forEachIndexed { index, view ->
            view.setBackgroundColor(if (index == selected) Color.rgb(40, 110, 170) else Color.TRANSPARENT)
        }
    }

    private fun cancelPress() {
        timer.removeCallbacks(hold)
        gesture.cancel()
        holding = false
        isPressed = false
        popup?.dismiss()
        popup = null
        choiceViews = emptyList()
    }

    override fun onDetachedFromWindow() { cancelPress(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        // TextView may leave its canvas translated to the centered text baseline.
        // Hints are positioned in the key's coordinates, independently of that label.
        val saved = canvas.save()
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
        if (hint.isEmpty()) return
        hintPaint.color = currentTextColor
        hintPaint.alpha = 160
        // Keep secondary hints small even when the main label uses a large font scale.
        hintPaint.textSize = 10 * resources.displayMetrics.density
        hintPaint.textAlign = Paint.Align.RIGHT
        val shortHint = hint.take(3)
        canvas.drawText(shortHint, width - 5 * resources.displayMetrics.density, 12 * resources.displayMetrics.density, hintPaint)
    }

    private companion object { const val CHOICE_ACTION_BASE = 0x01020000 }
}
