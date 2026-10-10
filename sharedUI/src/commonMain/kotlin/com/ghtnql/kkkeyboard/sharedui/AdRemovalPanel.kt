package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AdPrivacyPanel(
    language: UiLanguage,
    onPrivacyPolicy: () -> Unit,
    privacyOptionsRequired: Boolean,
    onPrivacyOptions: () -> Unit,
) {
    val policy = when (language) {
        UiLanguage.KO -> "개인정보처리방침"
        UiLanguage.JA -> "プライバシーポリシー"
        UiLanguage.EN -> "Privacy policy"
    }
    val options = when (language) {
        UiLanguage.KO -> "개인정보 옵션"
        UiLanguage.JA -> "プライバシーオプション"
        UiLanguage.EN -> "Privacy options"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                Text(policy)
            }
            if (privacyOptionsRequired) {
                TextButton(onClick = onPrivacyOptions, modifier = Modifier.fillMaxWidth()) {
                    Text(options)
                }
            }
        }
    }
}
