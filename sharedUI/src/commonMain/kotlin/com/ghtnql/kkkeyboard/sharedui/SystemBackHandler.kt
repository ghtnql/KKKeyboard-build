package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.runtime.Composable

@Composable
internal expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
