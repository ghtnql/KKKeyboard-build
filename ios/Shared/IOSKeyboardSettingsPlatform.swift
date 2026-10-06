import UIKit
import SharedUI

/// The same preference adapter serves the app and embedded common settings.
class IOSKeyboardSettingsPlatform: NSObject, AppPlatform {
    var keyboardStatus: KeyboardStatus { .active }
    var keyboardTestText = ""
    var learningItems: [SharedUI.LearningItem] { [] }
    var learningProgress: SharedUI.LearningProgress {
        SharedUI.LearningProgress(totalSessions: 0, bestAccuracy: 0, rainHighScore: 0, totalXp: 0)
    }
    var onKeyboardPicker: (() -> Void)?
    var onSettingsChanged: (() -> Void)?
    private var adMessage: String?
    private let uiLanguageKey = "app.uiLanguage"
    func readUiLanguage() -> String {
        // With Full Access disabled, extensions read App Group preferences but write locally.
        let localDefaults = UserDefaults.standard
        let sharedDefaults = KeyboardSharedDefaults.availableDefaults()
        let saved = Self.latestUiLanguage(local: localDefaults, shared: sharedDefaults)
        let tag = saved ?? Locale.preferredLanguages.first ?? "en"
        let base = tag.lowercased().replacingOccurrences(of: "_", with: "-").components(separatedBy: "-")[0]
        return ["ja", "ko", "en"].contains(base) ? base : "en"
    }
    // Newer explicit choices win across app and extension; equal revisions prefer App Group.
    static func latestUiLanguage(local: UserDefaults, shared: UserDefaults?) -> String? {
        let key = "app.uiLanguage"
        let localValue = local.string(forKey: key)
        guard let shared = shared else { return localValue }
        if localValue != nil && local.double(forKey: key + ".modifiedAt") > shared.double(forKey: key + ".modifiedAt") {
            return localValue
        }
        return shared.string(forKey: key) ?? localValue
    }
    func setUiLanguage(language: String) {
        guard ["ja", "ko", "en"].contains(language) else { return }
        let revision = Date().timeIntervalSince1970
        UserDefaults.standard.set(language, forKey: uiLanguageKey)
        UserDefaults.standard.set(revision, forKey: uiLanguageKey + ".modifiedAt")
        if !KeyboardSharedDefaults.isKeyboardExtension {
            let shared = KeyboardSharedDefaults.availableDefaults()
            shared?.set(language, forKey: uiLanguageKey)
            shared?.set(revision, forKey: uiLanguageKey + ".modifiedAt")
        }
        onSettingsChanged?()
    }
    func localized(_ ko: String, _ ja: String, _ en: String) -> String {
        switch readUiLanguage() { case "ja": return ja; case "ko": return ko; default: return en }
    }
    // Billing and ad-consent SDKs belong to the containing app only.
    var adRemovalOwnedForThemes: Bool? { nil }
    func readAdRemovalState() -> SharedUI.AdRemovalState {
        let owned = KeyboardSharedDefaults.availableDefaults()?.bool(forKey: "monetization.adRemoval.owned") ?? false
        return SharedUI.AdRemovalState(owned: owned, purchaseAvailable: false, price: nil, busy: false, messageCode: nil)
    }
    func refreshAdRemoval() {}
    func purchaseAdRemoval() {}
    func restoreAdRemoval() {}
    func openRefundInformation() {}
    func openPrivacyPolicy() {}
    func isAdPrivacyOptionsRequired() -> Bool { false }
    func showAdPrivacyOptions() {}
    func refreshKeyboardStatus() {}
    func openKeyboardSettings() {}
    func showKeyboardPicker() { onKeyboardPicker?() }
    func onKeyboardTestTextChanged(text: String) { keyboardTestText = text }
    func saveLearningProgress(progress: SharedUI.LearningProgress) {}
    func readHangulLearnedIds() -> [String] { UserDefaults.standard.stringArray(forKey: "hangul_learning.completed_ids") ?? [] }
    func saveHangulLearnedIds(ids: [String]) { UserDefaults.standard.set(Array(Set(ids)).sorted(), forKey: "hangul_learning.completed_ids") }
    func readHiraganaGuideSeen() -> Bool { UserDefaults.standard.bool(forKey: "hangul_learning.hiragana_guide_seen") }
    func saveHiraganaGuideSeen() { UserDefaults.standard.set(true, forKey: "hangul_learning.hiragana_guide_seen") }
    func readInputLayout() -> SharedUI.KeyboardInputLayout {
        switch KeyboardLayoutSettings().inputLayout {
        case "cheonjiin_plus": return .cheonjiinPlus
        case "qwerty": return .qwerty
        case "hangul_flick": return .hangulFlick
        default: return .cheonjiin
        }
    }
    func setInputLayout(layout: SharedUI.KeyboardInputLayout) {
        KeyboardLayoutSettings().setInputLayout(layout.persistedValue)
        onSettingsChanged?()
    }
    func canEditLongPressSymbols() -> Bool { !KeyboardSharedDefaults.isKeyboardExtension }
    func readLongPressSlots(layout: SharedUI.KeyboardInputLayout, keyId: String) -> [String] {
        LongPressCatalog.shared.slots(layoutId: layout.persistedValue, keyId: keyId,
            override: KeyboardLayoutSettings().longPressOverride(layout: layout.persistedValue, keyId: keyId))
    }
    func setLongPressSlots(layout: SharedUI.KeyboardInputLayout, keyId: String, slots: [String]) {
        guard canEditLongPressSymbols() else { return }
        KeyboardLayoutSettings().setLongPressSlots(layout: layout.persistedValue, keyId: keyId, slots: slots)
        onSettingsChanged?()
    }
    func resetLongPressSlots(layout: SharedUI.KeyboardInputLayout, keyId: String) {
        guard canEditLongPressSymbols() else { return }
        KeyboardLayoutSettings().resetLongPressSlots(layout: layout.persistedValue, keyId: keyId)
        onSettingsChanged?()
    }
    func readFlickDistance() -> Int32 { Int32(KeyboardLayoutSettings().flickDistance) }
    func setFlickDistance(distance: Int32) { KeyboardLayoutSettings().setFlickDistance(Int(distance)); onSettingsChanged?() }
    func readCheonjiinCycleTimeout() -> Int32 { Int32(KeyboardLayoutSettings().cycleTimeout) }
    func setCheonjiinCycleTimeout(timeout: Int32) { KeyboardLayoutSettings().setCycleTimeout(Int(timeout)); onSettingsChanged?() }
    func readHapticFeedbackEnabled() -> Bool { KeyboardLayoutSettings().hapticFeedbackEnabled }
    func setHapticFeedbackEnabled(enabled: Bool) {
        KeyboardLayoutSettings().setHapticFeedbackEnabled(enabled)
        onSettingsChanged?()
    }
    func readUserPhrases() -> [SharedUI.UserPhraseEntry] {
        UserPhraseStore().phrases().map {
            SharedUI.UserPhraseEntry(id: $0.id.uuidString, title: $0.title, content: $0.content)
        }
    }

    func createUserPhrase(title: String, content: String) -> Bool {
        do {
            _ = try UserPhraseStore().create(title: title, content: content)
            return true
        } catch {
            return false
        }
    }

    func updateUserPhrase(id: String, title: String, content: String) -> Bool {
        guard let uuid = UUID(uuidString: id) else { return false }
        do {
            try UserPhraseStore().update(id: uuid, title: title, content: content)
            return true
        } catch {
            return false
        }
    }

    func deleteUserPhrase(id: String) -> Bool {
        guard let uuid = UUID(uuidString: id) else { return false }
        do {
            try UserPhraseStore().delete(id: uuid)
            return true
        } catch {
            return false
        }
    }

    func readLayoutOptions(orientation: SharedUI.LayoutOrientation) -> SharedUI.LayoutOptions {
        let nativeOrientation = orientation == .portrait ? KeyboardOrientation.portrait : .landscape
        let settings = KeyboardLayoutSettings()
        // The rendered height may expand to fit extra rows; display the stored choice.
        let height: SharedUI.LayoutHeight
        switch settings.requestedHeight(for: nativeOrientation) {
        case 220: height = .compact
        case 300: height = .tall
        default: height = .normal
        }
        return SharedUI.LayoutOptions(
            height: height,
            numberRowEnabled: settings.profile(for: nativeOrientation).numberRowEnabled
        )
    }

    func setLayoutHeight(orientation: SharedUI.LayoutOrientation, height: SharedUI.LayoutHeight) {
        let nativeOrientation = orientation == .portrait ? KeyboardOrientation.portrait : .landscape
        KeyboardLayoutSettings().setHeight(Int(height.iosPoints), for: nativeOrientation)
        onSettingsChanged?()
    }

    func setNumberRowEnabled(orientation: SharedUI.LayoutOrientation, enabled: Bool) {
        let nativeOrientation = orientation == .portrait ? KeyboardOrientation.portrait : .landscape
        KeyboardLayoutSettings().setNumberRowEnabled(enabled, for: nativeOrientation)
        onSettingsChanged?()
    }

    func readThemes() -> [SharedUI.SharedTheme] {
        KeyboardThemeCatalog.shared.entries.map { entry in
            let palette = entry.palette
            func argb(_ keyPath: KeyPath<KeyboardThemePalette, String>) -> Int32 {
                guard let components = palette?.components(for: keyPath) else { return 0 }
                let value = UInt32(Int(components.a * 255) << 24 | Int(components.r * 255) << 16 |
                                   Int(components.g * 255) << 8 | Int(components.b * 255))
                return Int32(bitPattern: value)
            }
            return SharedUI.SharedTheme(
                id: entry.id,
                titleKo: entry.titleKo,
                kind: entry.kind,
                unlock: entry.unlock ?? "free",
                swatch: SharedUI.SharedThemeSwatch(
                    surface: argb(\.keyboardSurface), key: argb(\.keySurface),
                    text: argb(\.text), accent: argb(\.accent)),
                available: entry.isAvailable,
                previewAsset: entry.previewAsset
            )
        }
    }

    func readKeyboardTheme() -> SharedUI.KeyboardThemeChoice {
        switch KeyboardThemeSettings(verifiedAdRemovalOwned: adRemovalOwnedForThemes).read() {
        case .system: return .system
        case .light: return .light
        case .dark: return .dark
        case .seoulDay: return .seoulDay
        case .seoulNight: return .seoulNight
        }
    }

    func setKeyboardTheme(theme: SharedUI.KeyboardThemeChoice) {
        let nativeTheme: KeyboardTheme
        if theme == .light { nativeTheme = .light }
        else if theme == .dark { nativeTheme = .dark }
        else if theme == .seoulDay { nativeTheme = .seoulDay }
        else if theme == .seoulNight { nativeTheme = .seoulNight }
        else { nativeTheme = .system }
        KeyboardThemeSettings(verifiedAdRemovalOwned: adRemovalOwnedForThemes).set(nativeTheme)
        onSettingsChanged?()
    }

    func seoulThemeUnlockRemainingMillis() -> Int64 {
        KeyboardThemeSettings(verifiedAdRemovalOwned: adRemovalOwnedForThemes).remainingSeoulUnlockMillis()
    }


    func requestSeoulThemeAd(theme: SharedUI.KeyboardThemeChoice) -> Bool {
        adMessage = localized("본체 앱의 테마 화면에서 설정하세요.", "アプリのテーマ画面で設定してください。", "Set up this theme in the app’s themes screen.")
        return false
    }
    func isSeoulThemeAdPending() -> Bool { false }
    func prepareGameEndAd() {}
    func requestGameEndAd(onFinished: @escaping () -> Void) -> Bool { false }
    func seoulThemeAdMessage() -> String? { adMessage }
    func readGameAudioSettings() -> SharedUI.GameAudioSettings {
        SharedUI.GameAudioSettings(musicEnabled: true, effectsEnabled: true)
    }
    func setGameMusicEnabled(enabled: Bool) {}
    func setGameEffectsEnabled(enabled: Bool) {}
    func startGameAudio(track: SharedUI.GameMusicTrack) {}
    func stopGameAudio() {}
    func finishGameAudio() {}
    func playGameSound(effect: SharedUI.GameSoundEffect) {}
}
