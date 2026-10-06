import Foundation

enum KeyboardOrientation: String, CaseIterable {
    case portrait
    case landscape
}

struct KeyboardLayoutProfile: Equatable {
    let height: Int
    let numberRowEnabled: Bool
    let cursorRowEnabled: Bool
}

struct KeyboardLayoutSettings {
    static let supportedHeights = [220, 260, 300]
    static let defaultHeight = 260
    static let minimumUsableRowHeight = 32
    static let verticalInsets = 16
    static let rowSpacing = 6
    private static let migrationKey = "appGroupMigration.layout.v1"

    private let defaults: UserDefaults
    private let readOnlySharedDefaults: UserDefaults?
    private let longPressReadOnly: Bool

    init(
        defaults: UserDefaults? = nil,
        legacyDefaults: UserDefaults = .standard,
        migrateLegacy: Bool = false,
        readOnlySharedDefaults: UserDefaults? = nil
    ) {
        let group = defaults == nil ? KeyboardSharedDefaults.availableDefaults() : nil
        let extensionReadOnly = defaults == nil && KeyboardSharedDefaults.isKeyboardExtension
        self.defaults = defaults ?? (extensionReadOnly ? legacyDefaults : group ?? legacyDefaults)
        self.readOnlySharedDefaults = readOnlySharedDefaults ?? (extensionReadOnly ? group : nil)
        self.longPressReadOnly = extensionReadOnly || readOnlySharedDefaults != nil
        if !extensionReadOnly && (defaults == nil || migrateLegacy), self.defaults !== legacyDefaults {
            migrateLegacySettings(from: legacyDefaults)
        }
    }

    var inputLayout: String { value(forKey: "keyboard.inputLayout") as? String ?? "cheonjiin" }
    func setInputLayout(_ value: String) {
        guard ["cheonjiin", "cheonjiin_plus", "qwerty", "hangul_flick"].contains(value) else { return }
        write(value, forKey: "keyboard.inputLayout")
    }
    // Symbols use the app's canonical App Group state. Extensions never create
    // their own symbol overrides under the no-Full-Access policy.
    func longPressOverride(layout: String, keyId: String) -> [String]? {
        let store = longPressReadOnly ? readOnlySharedDefaults : defaults
        return store?.object(forKey: "longpress_\(layout)_\(keyId)") as? [String]
    }
    func setLongPressSlots(layout: String, keyId: String, slots: [String]) {
        guard !longPressReadOnly else { return }
        let normalized = Array((slots + ["", "", ""]).prefix(3))
        write(normalized, forKey: "longpress_\(layout)_\(keyId)")
    }
    func resetLongPressSlots(layout: String, keyId: String) {
        guard !longPressReadOnly else { return }
        write("default", forKey: "longpress_\(layout)_\(keyId)")
    }
    var flickDistance: Int { min(32, max(12, value(forKey: "keyboard.flickDistance") as? Int ?? 20)) }
    func setFlickDistance(_ value: Int) { write(min(32, max(12, value)), forKey: "keyboard.flickDistance") }
    var cycleTimeout: Int { min(1600, max(400, value(forKey: "keyboard.cycleTimeout") as? Int ?? 900)) }
    func setCycleTimeout(_ value: Int) { write(min(1600, max(400, value)), forKey: "keyboard.cycleTimeout") }
    var hapticFeedbackEnabled: Bool { value(forKey: "keyboard.hapticFeedbackEnabled") as? Bool ?? true }
    func setHapticFeedbackEnabled(_ enabled: Bool) { write(enabled, forKey: "keyboard.hapticFeedbackEnabled") }

    func profile(for orientation: KeyboardOrientation) -> KeyboardLayoutProfile {
        let requestedHeight = requestedHeight(for: orientation)

        let numberKey = numberRowKey(for: orientation)
        let numberRowEnabled = value(forKey: numberKey) == nil
            ? true
            : (value(forKey: numberKey) as? Bool ?? false)

        let cursorKey = cursorRowKey(for: orientation)
        let cursorRowEnabled = value(forKey: cursorKey) == nil
            ? false
            : (value(forKey: cursorKey) as? Bool ?? false)

        let height = Self.renderedHeight(
            requestedHeight: requestedHeight,
            numberRowEnabled: numberRowEnabled,
            cursorRowEnabled: cursorRowEnabled
        )
        return KeyboardLayoutProfile(
            height: height,
            numberRowEnabled: numberRowEnabled,
            cursorRowEnabled: cursorRowEnabled
        )
    }

    static func renderedHeight(
        requestedHeight: Int,
        numberRowEnabled: Bool,
        cursorRowEnabled: Bool,
        settingsRowVisible: Bool = false,
        candidateRowVisible: Bool = false
    ) -> Int {
        // Candidate + two upper character rows + bottom character row + control row.
        let baseRows = 5
        let rowCount = baseRows
            + (numberRowEnabled ? 1 : 0)
            + (cursorRowEnabled ? 1 : 0)
            + (settingsRowVisible ? 1 : 0)
        let minimumHeight = verticalInsets
            + max(0, rowCount - 1) * rowSpacing
            + rowCount * minimumUsableRowHeight
        let baseHeight = max(requestedHeight, minimumHeight)
        guard candidateRowVisible else { return baseHeight }
        let rowHeight = (baseHeight - verticalInsets - max(0, rowCount - 1) * rowSpacing) / rowCount
        return baseHeight + rowHeight + rowSpacing
    }

    func setHeight(_ height: Int, for orientation: KeyboardOrientation) {
        guard Self.supportedHeights.contains(height) else { return }
        write(height, forKey: heightKey(for: orientation))
    }

    func setNumberRowEnabled(_ enabled: Bool, for orientation: KeyboardOrientation) {
        write(enabled, forKey: numberRowKey(for: orientation))
    }

    func setCursorRowEnabled(_ enabled: Bool, for orientation: KeyboardOrientation) {
        write(enabled, forKey: cursorRowKey(for: orientation))
    }

    func nextHeight(after current: Int) -> Int {
        guard let index = Self.supportedHeights.firstIndex(of: current) else {
            return Self.defaultHeight
        }
        let nextIndex = Self.supportedHeights.index(after: index)
        return nextIndex == Self.supportedHeights.endIndex
            ? Self.supportedHeights[0]
            : Self.supportedHeights[nextIndex]
    }

    func nextHeight(for orientation: KeyboardOrientation) -> Int {
        nextHeight(after: requestedHeight(for: orientation))
    }

    func requestedHeight(for orientation: KeyboardOrientation) -> Int {
        let storedHeight = value(forKey: heightKey(for: orientation)) as? Int ?? 0
        return Self.supportedHeights.contains(storedHeight) ? storedHeight : Self.defaultHeight
    }

    private func heightKey(for orientation: KeyboardOrientation) -> String {
        "layout.\(orientation.rawValue).height"
    }

    private func numberRowKey(for orientation: KeyboardOrientation) -> String {
        "layout.\(orientation.rawValue).numberRow"
    }

    private func cursorRowKey(for orientation: KeyboardOrientation) -> String {
        "layout.\(orientation.rawValue).cursorRow"
    }

    private func value(forKey key: String) -> Any? {
        guard let shared = readOnlySharedDefaults else { return defaults.object(forKey: key) }
        let local = defaults.object(forKey: key)
        let localRevision = defaults.double(forKey: key + ".modifiedAt")
        let sharedRevision = shared.double(forKey: key + ".modifiedAt")
        if local != nil && localRevision > sharedRevision { return local }
        return shared.object(forKey: key) ?? local
    }

    private func write(_ value: Any, forKey key: String) {
        defaults.set(value, forKey: key)
        defaults.set(Date().timeIntervalSince1970, forKey: key + ".modifiedAt")
    }

    private func migrateLegacySettings(from legacyDefaults: UserDefaults) {
        guard !legacyDefaults.bool(forKey: Self.migrationKey) else { return }
        for orientation in KeyboardOrientation.allCases {
            for key in [heightKey(for: orientation), numberRowKey(for: orientation), cursorRowKey(for: orientation)] {
                if defaults.object(forKey: key) == nil,
                   let value = legacyDefaults.object(forKey: key) {
                    defaults.set(value, forKey: key)
                }
            }
        }
        legacyDefaults.set(true, forKey: Self.migrationKey)
    }
}
