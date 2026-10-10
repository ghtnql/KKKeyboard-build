package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

internal val defaultKeyboardLanguageOrder = listOf("korean", "japanese", "english")

internal fun normalizeKeyboardLanguageOrder(order: List<String>): List<String> =
    (order.map { it.trim().lowercase() }.filter { it in defaultKeyboardLanguageOrder } +
        defaultKeyboardLanguageOrder).distinct()

internal fun moveKeyboardLanguage(order: List<String>, language: String, destination: Int): List<String> {
    val result = normalizeKeyboardLanguageOrder(order).toMutableList()
    val source = result.indexOf(language)
    if (source < 0) return result
    result.add(destination.coerceIn(0, result.lastIndex), result.removeAt(source))
    return result
}

/** Long press claims the drag; ordinary swipes remain available to the settings scroll. */
@Composable
internal fun KeyboardLanguageOrderControl(platform: AppPlatform, uiLanguage: UiLanguage) {
    fun text(ko: String, ja: String, en: String) = when (uiLanguage) {
        UiLanguage.KO -> ko
        UiLanguage.JA -> ja
        UiLanguage.EN -> en
    }
    var order by remember(platform) { mutableStateOf(normalizeKeyboardLanguageOrder(platform.readKeyboardLanguageOrder())) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    fun save(next: List<String>) {
        platform.setKeyboardLanguageOrder(next)
        order = normalizeKeyboardLanguageOrder(platform.readKeyboardLanguageOrder())
    }
    Text(text("키보드 언어 순서", "キーボードの言語順序", "Keyboard language order"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text(text("길게 누른 뒤 끌어서 순서를 바꾸세요", "長押ししてドラッグで順序を変更", "Long press and drag to change the order"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().testTag("keyboard-language-order")) {
        order.forEachIndexed { index, language ->
            key(language) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp)
                        .zIndex(if (dragging == language) 1f else 0f)
                        .offset { IntOffset(0, if (dragging == language) dragY.roundToInt() else 0) }
                        .testTag("keyboard-language-order-$language")
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(text("위로 이동", "上へ移動", "Move up")) {
                                    if (index == 0) false else { save(moveKeyboardLanguage(order, language, index - 1)); true }
                                },
                                CustomAccessibilityAction(text("아래로 이동", "下へ移動", "Move down")) {
                                    if (index == order.lastIndex) false else { save(moveKeyboardLanguage(order, language, index + 1)); true }
                                },
                            )
                        }
                        .pointerInput(language) {
                            val rowHeight = 52.dp.toPx()
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = language; dragY = 0f },
                                onDragCancel = { dragging = null; dragY = 0f },
                                onDragEnd = {
                                    val source = order.indexOf(language)
                                    save(moveKeyboardLanguage(order, language, source + (dragY / rowHeight).roundToInt()))
                                    dragging = null
                                    dragY = 0f
                                },
                                onDrag = { change, amount -> change.consume(); dragY += amount.y },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${index + 1}.")
                    Text(when (language) {
                        "korean" -> text("한국어", "韓国語", "Korean")
                        "japanese" -> text("일본어", "日本語", "Japanese")
                        else -> text("영어", "英語", "English")
                    }, modifier = Modifier.weight(1f))
                    Text("☰", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
