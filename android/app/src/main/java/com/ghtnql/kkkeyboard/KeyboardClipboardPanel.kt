package com.ghtnql.kkkeyboard

import android.content.ClipDescription
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

class KeyboardClipboardPanel(
    private val context: Context,
    private val store: ClipboardHistoryStore,
    private val onPaste: (String) -> Unit,
) {
    private var popup: PopupWindow? = null
    private var content: LinearLayout? = null
    private var feedbackView: TextView? = null
    private var confirmClear = false

    val isShowing: Boolean
        get() = popup?.isShowing == true

    fun show(anchor: View) {
        popup?.dismiss()
        popup = null

        val density = context.resources.displayMetrics.density
        val dp44 = (44 * density + 0.5f).toInt()

        val anchorLocation = IntArray(2)
        val rootLocation = IntArray(2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            anchor.getLocationOnScreen(anchorLocation)
            anchor.rootView.getLocationOnScreen(rootLocation)
        } else {
            anchor.getLocationInWindow(anchorLocation)
            anchor.rootView.getLocationInWindow(rootLocation)
        }
        val rootWidth = anchor.rootView.width
        val belowTop = anchorLocation[1] + anchor.height
        val rootBottom = rootLocation[1] + anchor.rootView.height
        val availHeight = rootBottom - belowTop
        val safeWidth = rootWidth.coerceAtLeast(1)
        val safeHeight = availHeight.coerceAtLeast(dp44.coerceAtLeast(48))

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val titleView = TextView(context).apply {
            text = t("클립보드", "クリップボード", "Clipboard")
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(32, 33, 36))
        }
        header.addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val closeButton = Button(context).apply {
            text = t("닫기", "閉じる", "Close")
            contentDescription = text
            isFocusable = false
            isFocusableInTouchMode = false
            minimumWidth = dp44
            minWidth = dp44
            minimumHeight = dp44
            minHeight = dp44
            setOnClickListener { dismiss() }
        }
        header.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val feedback = TextView(context).apply {
            visibility = View.GONE
        }
        feedbackView = feedback
        container.addView(
            feedback,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val listContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        content = listContent
        val scrollView = ScrollView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            addView(
                listContent,
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
                content = null
                feedbackView = null
                confirmClear = false
            }
        }
        popup = window
        refresh()
        // Never showAsDropDown: it requests anchor scroll/focus in web editors.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.setIsLaidOutInScreen(true)
        val left = anchorLocation[0].coerceIn(
            rootLocation[0],
            (rootLocation[0] + anchor.rootView.width - safeWidth).coerceAtLeast(rootLocation[0]),
        )
        window.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.LEFT, left, belowTop)
    }

    fun dismiss() {
        popup?.dismiss()
    }

    fun refresh() {
        val listContent = content ?: return
        listContent.removeAllViews()
        feedbackView?.let {
            it.text = ""
            it.visibility = View.GONE
        }
        val density = context.resources.displayMetrics.density
        val dp44 = (44 * density + 0.5f).toInt()

        val current = readCurrent()
        listContent.addView(sectionLabel(t("현재 클립보드", "現在のクリップボード", "Current clipboard")))
        val preview = TextView(context).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val displayText: String? = when {
            current.error -> null
            !current.hasContent -> null
            current.sensitive -> t("민감한 내용 (미리보기 없음)", "機密コンテンツ（プレビューなし）", "Sensitive content (no preview)")
            else -> current.text
        }
        preview.text = displayText
            ?: if (current.error) {
                t("클립보드를 읽을 수 없음", "クリップボードを読み取れません", "Cannot read clipboard")
            } else {
                t("비어 있음", "空です", "Empty")
            }
        listContent.addView(
            preview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val currentButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val pasteCurrent = Button(context).apply {
            text = t("붙여넣기", "貼り付け", "Paste")
            isFocusable = false
            isFocusableInTouchMode = false
            // Reread fresh on click so a stale non-sensitive snapshot can never leak
            // a newly sensitive clipboard, and vice versa.
            isEnabled = current.hasContent && !current.error
            setOnClickListener {
                val fresh = readCurrent(forPaste = true)
                val freshText = fresh.text
                if (fresh.error || !fresh.hasContent || freshText.isNullOrEmpty()) {
                    refreshCurrentState()
                    setFeedback(t("붙여넣을 내용이 없음", "貼り付ける内容がありません", "Nothing to paste"))
                    return@setOnClickListener
                }
                val full = freshText
                if (!fresh.sensitive) store.use(full)
                dismiss()
                onPaste(full)
            }
        }
        val saveCurrent = Button(context).apply {
            text = t("저장", "保存", "Save current")
            isFocusable = false
            isFocusableInTouchMode = false
            // Sensitive content: explicit Paste only, never preview/save.
            // Blank current is safe: disabled.
            isEnabled = current.hasContent && !current.error && !current.sensitive &&
                !current.text.isNullOrEmpty()
            setOnClickListener {
                val fresh = readCurrent()
                val freshText = fresh.text
                if (fresh.error || !fresh.hasContent || freshText.isNullOrEmpty() || fresh.sensitive) {
                    refreshCurrentState()
                    setFeedback(t("저장할 수 없음", "保存できません", "Cannot save"))
                    return@setOnClickListener
                }
                val ok = runCatching { store.save(freshText) }.getOrDefault(false)
                if (!ok) {
                    setFeedback(t("저장할 텍스트가 너무 깁니다 (최대 10,000)", "保存するテキストが長すぎます（最大10,000）", "Text too long to save (maximum 10,000)"))
                    return@setOnClickListener
                }
                refresh()
            }
        }
        currentButtons.addView(
            pasteCurrent,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        currentButtons.addView(
            saveCurrent,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        listContent.addView(
            currentButtons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val entries = runCatching { store.list() }.getOrElse {
            setFeedback(t("기록을 불러올 수 없음", "履歴を読み込めません", "Cannot load history"))
            emptyList()
        }
        val pinned = entries.filter { it.pinned }
        val recent = entries.filter { !it.pinned }

        listContent.addView(sectionLabel(t("고정됨", "ピン留め", "Pinned")))
        if (pinned.isEmpty()) {
            listContent.addView(
                TextView(context).apply {
                    text = t("없음", "なし", "None")
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        } else {
            pinned.forEach { entry ->
                listContent.addView(rowView(entry, dp44))
            }
        }

        listContent.addView(sectionLabel(t("최근", "最近", "Recent")))
        if (recent.isEmpty()) {
            listContent.addView(
                TextView(context).apply {
                    text = t("없음", "なし", "None")
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        } else {
            recent.forEach { entry ->
                listContent.addView(rowView(entry, dp44))
            }
        }

        if (!confirmClear) {
            val clearButton = Button(context).apply {
                text = t("전체 삭제", "すべて削除", "Clear all")
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener {
                    confirmClear = true
                    refresh()
                }
            }
            listContent.addView(
                clearButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            listContent.addView(
                TextView(context).apply {
                    text = t("기록 전체(고정 포함)를 삭제할까요?", "履歴全体（ピン留め含む）を削除しますか？", "Delete all history including pins?")
                },
            )
            val confirmRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val confirmButton = Button(context).apply {
                text = t("삭제", "削除", "Delete")
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener {
                    // History only; system clipboard is left unchanged.
                    runCatching { store.clear() }
                    confirmClear = false
                    refresh()
                }
            }
            val cancelButton = Button(context).apply {
                text = t("취소", "キャンセル", "Cancel")
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener {
                    confirmClear = false
                    refresh()
                }
            }
            confirmRow.addView(
                confirmButton,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            confirmRow.addView(
                cancelButton,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            listContent.addView(
                confirmRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private data class CurrentClip(
        val text: String?,
        val sensitive: Boolean,
        val hasContent: Boolean,
        val error: Boolean,
    )

    private fun readCurrent(forPaste: Boolean = false): CurrentClip {
        return try {
            val manager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    ?: return CurrentClip(null, false, false, true)
            val clip = manager.primaryClip ?: return CurrentClip(null, false, false, false)
            if (clip.itemCount <= 0) return CurrentClip(null, false, false, false)
            // Plain text only: item.text, never coerce URI/intent.
            val sensitive = clip.description?.extras?.getBoolean(
                ClipDescription.EXTRA_IS_SENSITIVE,
                false,
            ) == true
            val raw = clip.getItemAt(0).text
            if (raw.isNullOrEmpty()) return CurrentClip(null, false, false, false)
            CurrentClip(if (sensitive && !forPaste) null else raw.toString(), sensitive, true, false)
        } catch (_: SecurityException) {
            CurrentClip(null, false, false, true)
        } catch (_: Exception) {
            CurrentClip(null, false, false, true)
        }
    }

    private fun rowView(entry: ClipboardEntry, dp44: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val fullText = entry.text
        val main = Button(context).apply {
            // Truncated preview; full underlying text preserved for onPaste/use.
            text = fullText
            contentDescription = fullText
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                dismiss()
                onPaste(fullText)
                runCatching { store.use(fullText) }
            }
        }
        row.addView(
            main,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val pinButton = Button(context).apply {
            text = if (entry.pinned) {
                t("해제", "解除", "Unpin")
            } else {
                t("고정", "固定", "Pin")
            }
            contentDescription = text
            isFocusable = false
            isFocusableInTouchMode = false
            minimumWidth = dp44
            minWidth = dp44
            minimumHeight = dp44
            minHeight = dp44
            setOnClickListener {
                val ok = runCatching { store.togglePin(fullText) }.getOrDefault(false)
                if (!ok) {
                    setFeedback(
                        t("고정 한도는 30개입니다", "固定に失敗（上限またはエラー）", "Pin failed (limit or error)"),
                    )
                    return@setOnClickListener
                }
                refresh()
            }
        }
        row.addView(
            pinButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        val deleteButton = Button(context).apply {
            text = t("삭제", "削除", "Delete")
            contentDescription = text
            isFocusable = false
            isFocusableInTouchMode = false
            minimumWidth = dp44
            minWidth = dp44
            minimumHeight = dp44
            minHeight = dp44
            setOnClickListener {
                runCatching { store.delete(fullText) }
                refresh()
            }
        }
        row.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return row
    }

    private fun sectionLabel(text: String): View {
        return TextView(context).apply {
            this.text = text
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun setFeedback(message: String) {
        val view = feedbackView ?: return
        view.text = message
        view.visibility = View.VISIBLE
    }

    private fun refreshCurrentState() {
        // Rebuild without auto-saving; the service owns capture later.
        if (popup?.isShowing == true) refresh()
    }

    private fun language(): String {
        return try {
            val config = context.resources.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.locales[0]?.language ?: ""
            } else {
                @Suppress("DEPRECATION")
                config.locale?.language ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun t(ko: String, ja: String, en: String): String {
        return when (language()) {
            "ko" -> ko
            "ja" -> ja
            else -> en
        }
    }
}
