import Foundation

enum KeyboardTheme: String {
    case system
    case light = "basic_light"
    case dark = "basic_dark"
    case seoulDay = "seoul_day"
    case seoulNight = "seoul_night"
}

struct KeyboardThemeSettings {
    private static let key = "keyboard.theme"
    private static let seoulExpiryKey = "keyboard.theme.seoulUnlockExpiresAt"
    private static let seoulUnlockDuration: TimeInterval = 24 * 60 * 60
    private let defaults: UserDefaults
    private let sharedReadOnlyDefaults: UserDefaults?
    private let canGrantUnlock: Bool

    var canGrantSeoulUnlock: Bool { canGrantUnlock }

    init(defaults: UserDefaults? = nil, verifiedAdRemovalOwned: Bool? = nil) {
        let group = defaults == nil ? KeyboardSharedDefaults.availableDefaults() : nil
        let extensionReadOnly = defaults == nil && KeyboardSharedDefaults.isKeyboardExtension
        self.defaults = defaults ?? (extensionReadOnly ? .standard : group ?? .standard)
        self.sharedReadOnlyDefaults = extensionReadOnly ? group : nil
        self.canGrantUnlock = defaults != nil || (!extensionReadOnly && group != nil)
    }

    func read() -> KeyboardTheme {
        let localRevision = defaults.double(forKey: Self.key + ".modifiedAt")
        let sharedRevision = sharedReadOnlyDefaults?.double(forKey: Self.key + ".modifiedAt") ?? 0
        let local = defaults.string(forKey: Self.key)
        let stored = local != nil && localRevision > sharedRevision ? local
            : sharedReadOnlyDefaults?.string(forKey: Self.key) ?? local
        let theme = KeyboardTheme(rawValue: stored ?? "") ?? .system
        if remainingSeoulUnlockMillis() == 0 {
            switch theme {
            case .seoulDay, .seoulNight:
                let fallback: KeyboardTheme = theme == .seoulDay ? .light : .dark
                if !KeyboardSharedDefaults.isKeyboardExtension {
                    defaults.set(fallback.rawValue, forKey: Self.key)
                    defaults.removeObject(forKey: Self.seoulExpiryKey)
                }
                return fallback
            default: break
            }
        }
        return theme
    }

    func set(_ theme: KeyboardTheme) {
        if (theme == .seoulDay || theme == .seoulNight), remainingSeoulUnlockMillis() == 0 {
            return
        }
        defaults.set(theme.rawValue, forKey: Self.key)
        defaults.set(Date().timeIntervalSince1970, forKey: Self.key + ".modifiedAt")
    }

    func remainingSeoulUnlockMillis(now: Date = Date()) -> Int64 {
        let expiry = sharedReadOnlyDefaults?.object(forKey: Self.seoulExpiryKey) as? Date
            ?? defaults.object(forKey: Self.seoulExpiryKey) as? Date
        guard let expiry else { return 0 }
        let remaining = expiry.timeIntervalSince(now) * 1_000
        return remaining.isFinite && remaining > 0 ? Int64(min(remaining, Double(Int64.max))) : 0
    }

    /// Called only after the app receives the rewarded-ad reward callback.
    @discardableResult
    func grantSeoulUnlockAndSelect(_ theme: KeyboardTheme, now: Date = Date()) -> Bool {
        guard canGrantUnlock, theme == .seoulDay || theme == .seoulNight else { return false }
        defaults.set(now.addingTimeInterval(Self.seoulUnlockDuration), forKey: Self.seoulExpiryKey)
        defaults.set(theme.rawValue, forKey: Self.key)
        defaults.set(Date().timeIntervalSince1970, forKey: Self.key + ".modifiedAt")
        return true
    }
}
