package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One consistent reading order for every typing surface. */
@Composable
internal fun PracticePromptView(
    translations: PracticeTranslations,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val strings = LocalUiStrings.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        PracticePromptLine(strings.koreanMeaningLabel, translations.koreanMeaning, compact, textColor)
        PracticePromptLine(strings.hangulReadingLabel, translations.hangulPronunciation, compact, textColor)
        PracticePromptLine(strings.japaneseLabel, translations.japaneseOriginal, compact, textColor)
    }
}

@Composable
private fun PracticePromptLine(label: String, value: String?, compact: Boolean, textColor: Color) {
    val labelWidth = when (LocalUiStrings.current.language) {
        UiLanguage.KO -> if (compact) 48.dp else 64.dp
        UiLanguage.JA -> if (compact) 72.dp else 84.dp
        UiLanguage.EN -> if (compact) 84.dp else 104.dp
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        Text(label, Modifier.width(labelWidth),
            color = textColor.copy(alpha = 0.75f), fontWeight = FontWeight.Medium,
            fontSize = if (compact) 10.sp else 12.sp, lineHeight = if (compact) 13.sp else 17.sp)
        Text(value ?: "—", color = textColor, fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 12.sp else 17.sp,
            lineHeight = if (compact) 15.sp else 23.sp)
    }
}
