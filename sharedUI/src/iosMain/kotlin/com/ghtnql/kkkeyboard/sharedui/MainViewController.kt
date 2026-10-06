package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

class SharedAppNavigation {
    internal var settingsRequest by mutableIntStateOf(0)
    internal var themeRequest by mutableIntStateOf(0)
    fun openSettings() { settingsRequest++ }
    fun openThemes() { themeRequest++ }
}

fun MainViewController(platform: AppPlatform): UIViewController = MainViewControllerWithNavigation(platform, SharedAppNavigation())

fun MainViewControllerWithNavigation(platform: AppPlatform, navigation: SharedAppNavigation): UIViewController = ComposeUIViewController {
    KKKeyboardApp(platform, settingsNavigationRequest = navigation.settingsRequest, themeNavigationRequest = navigation.themeRequest)
}

fun KeyboardSettingsViewController(platform: AppPlatform, themes: Boolean, onClose: () -> Unit): UIViewController = ComposeUIViewController {
    KKKeyboardApp(platform, themeNavigationRequest = if (themes) 1 else 0, keyboardExtensionMode = true, onCloseSettings = onClose)
}
