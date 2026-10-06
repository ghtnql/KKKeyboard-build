package com.ghtnql.kkkeyboard

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import kotlin.math.min

class KeyboardToolbarMenu(private val context: Context) {
    private var popup: PopupWindow? = null

    val isShowing: Boolean
        get() = popup?.isShowing == true

    fun dismiss() {
        popup?.dismiss()
    }

    fun show(anchor: View, items: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
        // Dismiss previous popup before showing a new one.
        popup?.dismiss()
        popup = null

        val density = context.resources.displayMetrics.density
        val dp48 = (48 * density + 0.5f).toInt()
        val dp240 = (240 * density + 0.5f).toInt()

        val rootWidth = anchor.rootView.width
        val anchorLocation = IntArray(2)
        val rootLocation = IntArray(2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            anchor.getLocationOnScreen(anchorLocation)
            anchor.rootView.getLocationOnScreen(rootLocation)
        } else {
            anchor.getLocationInWindow(anchorLocation)
            anchor.rootView.getLocationInWindow(rootLocation)
        }
        val rootHeight = anchor.rootView.height - (anchorLocation[1] - rootLocation[1]) - anchor.height
        val width = if (rootWidth > 0) min(dp240, rootWidth) else dp240
        val desiredHeight = items.size * dp48 + dp48
        val height = if (rootHeight > 0) min(desiredHeight, rootHeight) else desiredHeight

        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(dp48.coerceAtLeast(48))

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val choicesLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        items.forEachIndexed { index, (title, selected) ->
            val label = if (selected) "✓ $title" else title
            val button = Button(context).apply {
                text = label
                contentDescription = label
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener {
                    // Dismiss FIRST, then invoke the callback.
                    dismiss()
                    onSelect(index)
                }
            }
            choicesLayout.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val scrollView = ScrollView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            addView(
                choicesLayout,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        container.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val closeButton = Button(context).apply {
            text = context.getString(android.R.string.cancel)
            contentDescription = text
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener { dismiss() }
        }
        container.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val window = PopupWindow(container, safeWidth, safeHeight, false).apply {
            isFocusable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode =
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            isOutsideTouchable = true
        }
        window.setOnDismissListener {
            if (popup === window) {
                popup = null
            }
        }
        popup = window
        // Avoid the anchor-scroll request performed by showAsDropDown in web editors.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.setIsLaidOutInScreen(true)
        val left = anchorLocation[0].coerceIn(rootLocation[0],
            (rootLocation[0] + anchor.rootView.width - safeWidth).coerceAtLeast(rootLocation[0]))
        window.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.LEFT, left, anchorLocation[1] + anchor.height)
    }
}
