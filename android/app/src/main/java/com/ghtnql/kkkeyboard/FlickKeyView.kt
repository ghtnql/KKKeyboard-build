package com.ghtnql.kkkeyboard

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import kotlin.math.abs

/** A gesture emits exactly one jamo on release, with no repeat timer or inter-key timeout. */
@SuppressLint("ViewConstructor") // Programmatic key instances, never inflated from XML.
class FlickKeyView(
    context: Context,
    val flickKey: FlickKey,
    private val thresholdDp: Int,
    private val palette: KeyboardPalette,
    private val onInput: (String) -> Unit,
    private val onPreview: (String?) -> Unit,
) : Button(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var pointerId = -1
    private var originX = 0f
    private var originY = 0f
    private var direction = FlickDirection.CENTER
    private var clickLabel: String? = null
    private val density = resources.displayMetrics.density
    private val centerTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
    private val hintTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)

    init {
        text = flickKey.center
        isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, 0, 0, 0)
        isClickable = true
        isFocusable = true
        contentDescription = flickKey.center
    }

    override fun onDraw(canvas: Canvas) {
        FlickDirection.entries.forEach { slot ->
            val label = flickKey.label(slot) ?: return@forEach
            val x = when (slot) {
                FlickDirection.LEFT -> width * .16f
                FlickDirection.RIGHT -> width * .84f
                else -> width * .5f
            }
            val y = when (slot) {
                FlickDirection.UP -> height * .17f
                FlickDirection.DOWN -> height * .83f
                else -> height * .5f
            }
            val selected = pointerId >= 0 && direction == slot
            ink.textSize = if (slot == FlickDirection.CENTER) minOf(centerTextSize, height * .36f, width * .34f)
                else minOf(hintTextSize, height * .21f, width * .22f)
            if (selected) {
                ink.color = palette.flickSelected
                val halfWidth = ink.measureText(label) / 2 + 4 * density
                canvas.drawRoundRect(x - halfWidth, y - ink.textSize * .65f, x + halfWidth, y + ink.textSize * .65f, 3 * density, 3 * density, ink)
            }
            ink.color = if (selected) palette.accent else if (slot == FlickDirection.CENTER) palette.text else palette.flickHint
            val metrics = ink.fontMetrics
            canvas.drawText(label, x, y - (metrics.ascent + metrics.descent) / 2, ink)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelGesture()
                pointerId = event.getPointerId(event.actionIndex)
                originX = event.x; originY = event.y
                direction = FlickDirection.CENTER
                isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onPreview(flickKey.center)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> updateDirection(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != pointerId) return true
                updateDirection(event)
                val label = if (pointerId >= 0) flickKey.label(direction) else null
                cancelGesture()
                if (label != null) {
                    clickLabel = label
                    try { performClick() } finally { clickLabel = null }
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun updateDirection(event: MotionEvent) {
        if (pointerId < 0) return
        val index = event.findPointerIndex(pointerId)
        if (index < 0) { cancelGesture(); return }
        val dx = event.getX(index) - originX
        val dy = event.getY(index) - originY
        if (abs(dx) > maxOf(width * 2f, 96 * density) || abs(dy) > maxOf(height * 2f, 96 * density)) {
            cancelGesture()
            return
        }
        val next = FlickDirectionResolver.resolve(dx, dy, thresholdDp * density, direction)
        if (next != direction) {
            direction = next
            onPreview(flickKey.label(direction) ?: "×")
            invalidate()
        }
    }

    fun cancelGesture() {
        val active = pointerId >= 0
        pointerId = -1
        isPressed = false
        if (active) {
            parent?.requestDisallowInterceptTouchEvent(false)
            onPreview(null)
            invalidate()
        }
    }

    override fun performClick(): Boolean {
        if (!isEnabled) return false
        super.performClick()
        onInput(clickLabel ?: flickKey.center)
        return true
    }

    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        directionalActions.forEach { (direction, action) ->
            flickKey.label(direction)?.let { label ->
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(action.first, context.getString(action.second, label)))
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val direction = directionalActions.entries.firstOrNull { it.value.first == action }?.key
        val label = direction?.let(flickKey::label)
        if (isEnabled && label != null) { onInput(label); return true }
        return super.performAccessibilityAction(action, arguments)
    }

    companion object {
        private val directionalActions = mapOf(
            FlickDirection.LEFT to (R.id.flick_left to R.string.flick_left),
            FlickDirection.UP to (R.id.flick_up to R.string.flick_up),
            FlickDirection.RIGHT to (R.id.flick_right to R.string.flick_right),
            FlickDirection.DOWN to (R.id.flick_down to R.string.flick_down),
        )
    }
}
