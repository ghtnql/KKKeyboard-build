enum TwoBeolsikLayout {
    static let characterRows: [[String]] = [
        ["ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"],
        ["ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"]
    ]

    static let bottomRow: [String] = ["ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"]

    private static let shiftedKeys: [String: String] = [
        "ㅂ": "ㅃ",
        "ㅈ": "ㅉ",
        "ㄷ": "ㄸ",
        "ㄱ": "ㄲ",
        "ㅅ": "ㅆ",
        "ㅐ": "ㅒ",
        "ㅔ": "ㅖ"
    ]

    static func label(for baseLabel: String, shifted: Bool) -> String {
        guard shifted else { return baseLabel }
        return shiftedKeys[baseLabel] ?? baseLabel
    }

    static func hasShiftVariant(_ baseLabel: String) -> Bool {
        shiftedKeys[baseLabel] != nil
    }
}

enum LatinQwertyLayout {
    static let characterRows: [[String]] = [
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l"]
    ]

    static let bottomRow: [String] = ["z", "x", "c", "v", "b", "n", "m"]

    static func label(for baseLabel: String, shifted: Bool) -> String {
        guard shifted else { return baseLabel }
        return baseLabel.uppercased()
    }

    static func hasShiftVariant(_ baseLabel: String) -> Bool {
        guard baseLabel.count == 1, let scalar = baseLabel.unicodeScalars.first else { return false }
        return scalar.value >= 97 && scalar.value <= 122
    }
}
