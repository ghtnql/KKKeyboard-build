package com.ghtnql.kkkeyboard

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import java.util.ArrayDeque

/** Tracks only offsets, never editor text, to recognize delayed acknowledgements of our edits. */
internal class CompositionConnection(
    val delegate: InputConnection,
    initialSelectionStart: Int,
    initialSelectionEnd: Int,
) : InputConnectionWrapper(delegate, false) {
    private data class Position(val start: Int, val end: Int, val composingStart: Int = -1, val composingEnd: Int = -1)

    private var position = Position(initialSelectionStart, initialSelectionEnd)
    private val pending = ArrayDeque<Position>()
    private var batchDepth = 0
    private var batchChanged = false

    fun acknowledge(start: Int, end: Int, composingStart: Int, composingEnd: Int): Boolean {
        val update = Position(start, end, composingStart, composingEnd)
        val index = pending.indexOf(update)
        if (index >= 0) {
            repeat(index + 1) { pending.removeFirst() }
            return true
        }
        if (update == position) return true
        // A cursor move, selection, or editor-side change is not an IME acknowledgement.
        pending.clear()
        position = update
        return false
    }

    override fun beginBatchEdit(): Boolean {
        if (batchDepth++ == 0) batchChanged = false
        return super.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        if (batchDepth > 0 && --batchDepth == 0 && batchChanged) rememberPosition()
        // Queue the final position before an editor can synchronously report it.
        return super.endBatchEdit()
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        predictReplacement(text.length, newCursorPosition, composing = true)
        return super.setComposingText(text, newCursorPosition).also { if (!it) invalidatePosition() }
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        predictReplacement(text.length, newCursorPosition, composing = false)
        return super.commitText(text, newCursorPosition).also { if (!it) invalidatePosition() }
    }

    override fun finishComposingText(): Boolean {
        updatePosition(position.copy(composingStart = -1, composingEnd = -1))
        return super.finishComposingText().also { if (!it) invalidatePosition() }
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        invalidatePosition()
        return super.setSelection(start, end)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        // UTF-16 offsets cannot be predicted from a code-point count without reading editor text.
        invalidatePosition()
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        invalidatePosition()
        return super.sendKeyEvent(event)
    }

    private fun predictReplacement(length: Int, newCursorPosition: Int, composing: Boolean) {
        if (newCursorPosition != 1 || position.start < 0 || position.end < 0) {
            invalidatePosition()
            return
        }
        val start = if (position.composingStart >= 0) position.composingStart else minOf(position.start, position.end)
        val end = start + length
        updatePosition(Position(end, end, if (composing && length > 0) start else -1, if (composing && length > 0) end else -1))
    }

    private fun updatePosition(next: Position) {
        if (next == position) return
        position = next
        if (batchDepth > 0) batchChanged = true else rememberPosition()
    }

    private fun rememberPosition() {
        if (position.start < 0 || position.end < 0 || pending.peekLast() == position) return
        if (pending.size == 128) pending.removeFirst()
        pending.addLast(position)
    }

    private fun invalidatePosition() {
        pending.clear()
        position = Position(-1, -1)
        batchChanged = false
    }
}
