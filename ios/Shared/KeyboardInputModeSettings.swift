import Foundation

public enum KeyboardInputLanguage: String {
    case korean
    case japanese
    case english

    public var next: KeyboardInputLanguage {
        switch self {
        case .korean: return .japanese
        case .japanese: return .english
        case .english: return .korean
        }
    }
}

public struct KeyboardInputModeSettings {
    private static let japaneseKey = "keyboard.inputMode.japanese"
    private static let languageKey = "keyboard.inputMode.language"

    private let defaults: UserDefaults
    private let preferredLanguages: () -> [String]

    public init(
        defaults: UserDefaults = .standard,
        preferredLanguages: @escaping () -> [String] = { Locale.preferredLanguages }
    ) {
        self.defaults = defaults
        self.preferredLanguages = preferredLanguages
    }

    public func language() -> KeyboardInputLanguage {
        if let raw = defaults.string(forKey: Self.languageKey),
           let stored = KeyboardInputLanguage(rawValue: raw) {
            return stored
        }
        if defaults.object(forKey: Self.japaneseKey) != nil {
            return defaults.bool(forKey: Self.japaneseKey) ? .japanese : .korean
        }
        guard let first = preferredLanguages().first else { return .korean }
        let normalized = first.replacingOccurrences(of: "_", with: "-").lowercased()
        if normalized.split(separator: "-").first == "ja" {
            return .japanese
        }
        return .korean
    }

    public func selectLanguage(_ language: KeyboardInputLanguage) {
        defaults.set(language.rawValue, forKey: Self.languageKey)
        defaults.set(language == .japanese, forKey: Self.japaneseKey)
    }

    public func japaneseMode() -> Bool {
        language() == .japanese
    }

    public func selectJapanese(_ enabled: Bool) {
        selectLanguage(enabled ? .japanese : .korean)
    }
}
