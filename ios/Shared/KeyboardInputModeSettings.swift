import Foundation

public enum KeyboardInputLanguage: String, CaseIterable {
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

    public func next(in order: [KeyboardInputLanguage]) -> KeyboardInputLanguage {
        let normalized = KeyboardInputModeSettings.normalize(order)
        let index = normalized.firstIndex(of: self) ?? 0
        return normalized[(index + 1) % normalized.count]
    }
}

public struct KeyboardInputModeSettings {
    private static let japaneseKey = "keyboard.inputMode.japanese"
    private static let languageKey = "keyboard.inputMode.language"
    private static let orderKey = "keyboard.inputMode.order"

    private let defaults: UserDefaults
    private let preferredLanguages: () -> [String]
    private let sharedDefaults: UserDefaults?
    private let isKeyboardExtension: Bool

    public init(
        defaults: UserDefaults = .standard,
        preferredLanguages: @escaping () -> [String] = { Locale.preferredLanguages },
        sharedDefaults: UserDefaults? = nil,
        isKeyboardExtension: Bool? = nil
    ) {
        self.defaults = defaults
        self.preferredLanguages = preferredLanguages
        self.sharedDefaults = sharedDefaults ?? (defaults === UserDefaults.standard ? KeyboardSharedDefaults.availableDefaults() : nil)
        self.isKeyboardExtension = isKeyboardExtension ?? KeyboardSharedDefaults.isKeyboardExtension
    }

    public static func normalize(_ values: [KeyboardInputLanguage]) -> [KeyboardInputLanguage] {
        var result: [KeyboardInputLanguage] = []
        values.forEach { if !result.contains($0) { result.append($0) } }
        KeyboardInputLanguage.allCases.forEach { if !result.contains($0) { result.append($0) } }
        return result
    }

    public func languageOrder() -> [KeyboardInputLanguage] {
        let localOrder = defaults.stringArray(forKey: Self.orderKey)
        let localRevision = defaults.double(forKey: Self.orderKey + ".modifiedAt")
        let sharedRevision = sharedDefaults?.double(forKey: Self.orderKey + ".modifiedAt") ?? 0
        let raw: [String]
        if let localOrder = localOrder, localRevision > sharedRevision {
            raw = localOrder
        } else {
            raw = sharedDefaults?.stringArray(forKey: Self.orderKey) ?? localOrder ?? []
        }
        return Self.normalize(raw.compactMap {
            KeyboardInputLanguage(rawValue: $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
        })
    }

    public func selectLanguageOrder(_ order: [KeyboardInputLanguage]) {
        let raw = Self.normalize(order).map(\.rawValue)
        let revision = Date().timeIntervalSince1970
        defaults.set(raw, forKey: Self.orderKey)
        defaults.set(revision, forKey: Self.orderKey + ".modifiedAt")
        // The keyboard uses local overrides without requiring Full Access.
        // A later containing-app edit supersedes them through its shared revision.
        if !isKeyboardExtension {
            sharedDefaults?.set(raw, forKey: Self.orderKey)
            sharedDefaults?.set(revision, forKey: Self.orderKey + ".modifiedAt")
        }
    }

    public func nextLanguage(after language: KeyboardInputLanguage) -> KeyboardInputLanguage {
        language.next(in: languageOrder())
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
