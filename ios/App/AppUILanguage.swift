import Foundation

enum AppUILanguage: String, CaseIterable, Equatable {
    case korean = "ko"
    case japanese = "ja"
    case english = "en"

    static func from(languageIdentifier: String) -> AppUILanguage {
        let languageCode = languageIdentifier
            .lowercased()
            .replacingOccurrences(of: "_", with: "-")
            .split(separator: "-", maxSplits: 1)
            .first
            .map(String.init)

        guard let languageCode = languageCode else {
            return .english
        }

        switch languageCode {
        case korean.rawValue:
            return .korean
        case japanese.rawValue:
            return .japanese
        default:
            return .english
        }
    }

    static func preferred(from languageIdentifiers: [String]) -> AppUILanguage {
        guard let preferredIdentifier = languageIdentifiers.first else {
            return .english
        }
        return from(languageIdentifier: preferredIdentifier)
    }
}

struct AppUILanguagePreferences {
    private static let selectedLanguageKey = "app.uiLanguage"

    private let defaults: UserDefaults
    private let preferredLanguages: () -> [String]

    init(
        defaults: UserDefaults = .standard,
        preferredLanguages: @escaping () -> [String] = { Locale.preferredLanguages }
    ) {
        self.defaults = defaults
        self.preferredLanguages = preferredLanguages
    }

    func selectedLanguage() -> AppUILanguage {
        if let storedValue = defaults.string(forKey: Self.selectedLanguageKey),
           let storedLanguage = AppUILanguage(rawValue: storedValue) {
            return storedLanguage
        }
        return AppUILanguage.preferred(from: preferredLanguages())
    }

    func select(_ language: AppUILanguage) {
        defaults.set(language.rawValue, forKey: Self.selectedLanguageKey)
    }
}

struct AppLocalization {
    private let bundle: Bundle

    init(language: AppUILanguage, baseBundle: Bundle = .main) {
        if let path = baseBundle.path(forResource: language.rawValue, ofType: "lproj"),
           let localizedBundle = Bundle(path: path) {
            bundle = localizedBundle
        } else {
            bundle = baseBundle
        }
    }

    func string(_ key: String) -> String {
        bundle.localizedString(forKey: key, value: key, table: nil)
    }
}
