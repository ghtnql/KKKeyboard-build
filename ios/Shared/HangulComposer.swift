import Foundation
#if canImport(SharedUI)
import SharedUI
#else
import SharedCore
#endif

struct HangulEdit: Equatable {
    let commit: String
    let composing: String?
    let consumed: Bool

    init(commit: String = "", composing: String? = nil, consumed: Bool = true) {
        self.commit = commit
        self.composing = composing
        self.consumed = consumed
    }
}

final class HangulComposer {
    private var initial = -1
    private var medial = -1
    private var finalIndex = -1

    func input(_ character: Character) -> HangulEdit {
        let initialIndex = Self.initialIndex(of: character)
        if initialIndex >= 0 {
            return inputConsonant(character, newInitial: initialIndex)
        }

        let medialIndex = Self.medialIndex(of: character)
        if medialIndex >= 0 {
            return inputVowel(character, newMedial: medialIndex)
        }

        let pending = flush()
        return HangulEdit(commit: pending + String(character))
    }

    func backspace() -> HangulEdit {
        if finalIndex > 0 {
            let currentFinal = Self.finals[finalIndex]
            if let split = Self.compoundFinalSplit[currentFinal] {
                finalIndex = Self.finalIndex(of: split.0)
            } else {
                finalIndex = -1
            }
        } else if medial >= 0 {
            let currentMedial = Self.vowels[medial]
            if let base = Self.compoundMedialBase[currentMedial] {
                medial = Self.medialIndex(of: base)
            } else {
                medial = -1
            }
        } else if initial >= 0 {
            initial = -1
        } else {
            return HangulEdit(consumed: false)
        }

        let text = currentText()
        return HangulEdit(composing: text.isEmpty ? nil : text)
    }

    func flush() -> String {
        let text = currentText()
        reset()
        return text
    }

    func reset() {
        initial = -1
        medial = -1
        finalIndex = -1
    }

    func currentText() -> String {
        if initial < 0 {
            return medial >= 0 ? String(Self.vowels[medial]) : ""
        }
        if medial < 0 {
            return String(Self.initials[initial])
        }

        let jong = finalIndex > 0 ? finalIndex : 0
        return HangulProbe().compose(
            initialIndex: Int32(initial), vowelIndex: Int32(medial), finalIndex: Int32(jong)
        )
    }

    private func inputConsonant(_ character: Character, newInitial: Int) -> HangulEdit {
        if initial < 0 {
            if medial >= 0 {
                let commit = currentText()
                medial = -1
                initial = newInitial
                return HangulEdit(commit: commit, composing: currentText())
            }
            initial = newInitial
            return HangulEdit(composing: currentText())
        }

        if medial < 0 {
            let commit = currentText()
            initial = newInitial
            return HangulEdit(commit: commit, composing: currentText())
        }

        if finalIndex < 0 {
            let candidateFinal = Self.finalIndex(of: character)
            if candidateFinal > 0 {
                finalIndex = candidateFinal
                return HangulEdit(composing: currentText())
            }
        } else {
            let currentFinal = Self.finals[finalIndex]
            if let compound = Self.compoundFinal(currentFinal, character) {
                finalIndex = Self.finalIndex(of: compound)
                return HangulEdit(composing: currentText())
            }
        }

        let commit = currentText()
        initial = newInitial
        medial = -1
        finalIndex = -1
        return HangulEdit(commit: commit, composing: currentText())
    }

    private func inputVowel(_ character: Character, newMedial: Int) -> HangulEdit {
        if initial < 0 {
            if medial < 0 {
                medial = newMedial
                return HangulEdit(composing: currentText())
            }

            let current = Self.vowels[medial]
            if let compound = Self.compoundMedial(current, character) {
                medial = Self.medialIndex(of: compound)
                return HangulEdit(composing: currentText())
            }

            let commit = currentText()
            medial = newMedial
            return HangulEdit(commit: commit, composing: currentText())
        }

        if medial < 0 {
            medial = newMedial
            return HangulEdit(composing: currentText())
        }

        if finalIndex > 0 {
            let finalCharacter = Self.finals[finalIndex]
            if let split = Self.compoundFinalSplit[finalCharacter] {
                finalIndex = Self.finalIndex(of: split.0)
                let commit = currentText()
                initial = Self.initialIndex(of: split.1)
                medial = newMedial
                finalIndex = -1
                return HangulEdit(commit: commit, composing: currentText())
            }

            finalIndex = -1
            let commit = currentText()
            initial = Self.initialIndex(of: finalCharacter)
            medial = newMedial
            return HangulEdit(commit: commit, composing: currentText())
        }

        let current = Self.vowels[medial]
        if let compound = Self.compoundMedial(current, character) {
            medial = Self.medialIndex(of: compound)
            return HangulEdit(composing: currentText())
        }

        let commit = currentText()
        initial = -1
        medial = newMedial
        finalIndex = -1
        return HangulEdit(commit: commit, composing: currentText())
    }

    private static let initials: [Character] = [
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ",
        "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    ]

    private static let vowels: [Character] = [
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ",
        "ㅙ", "ㅚ", "ㅛ", "ㅜ", "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    ]

    private static let finals: [Character] = [
        "\0", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ",
        "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ",
        "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    ]

    // O(1) hot-path lookup avoids scanning the full jamo arrays for every key press.
    private static func initialIndex(of character: Character) -> Int {
        switch character {
        case "ㄱ": return 0
        case "ㄲ": return 1
        case "ㄴ": return 2
        case "ㄷ": return 3
        case "ㄸ": return 4
        case "ㄹ": return 5
        case "ㅁ": return 6
        case "ㅂ": return 7
        case "ㅃ": return 8
        case "ㅅ": return 9
        case "ㅆ": return 10
        case "ㅇ": return 11
        case "ㅈ": return 12
        case "ㅉ": return 13
        case "ㅊ": return 14
        case "ㅋ": return 15
        case "ㅌ": return 16
        case "ㅍ": return 17
        case "ㅎ": return 18
        default: return -1
        }
    }

    private static func medialIndex(of character: Character) -> Int {
        switch character {
        case "ㅏ": return 0
        case "ㅐ": return 1
        case "ㅑ": return 2
        case "ㅒ": return 3
        case "ㅓ": return 4
        case "ㅔ": return 5
        case "ㅕ": return 6
        case "ㅖ": return 7
        case "ㅗ": return 8
        case "ㅘ": return 9
        case "ㅙ": return 10
        case "ㅚ": return 11
        case "ㅛ": return 12
        case "ㅜ": return 13
        case "ㅝ": return 14
        case "ㅞ": return 15
        case "ㅟ": return 16
        case "ㅠ": return 17
        case "ㅡ": return 18
        case "ㅢ": return 19
        case "ㅣ": return 20
        default: return -1
        }
    }

    private static func finalIndex(of character: Character) -> Int {
        switch character {
        case "ㄱ": return 1
        case "ㄲ": return 2
        case "ㄳ": return 3
        case "ㄴ": return 4
        case "ㄵ": return 5
        case "ㄶ": return 6
        case "ㄷ": return 7
        case "ㄹ": return 8
        case "ㄺ": return 9
        case "ㄻ": return 10
        case "ㄼ": return 11
        case "ㄽ": return 12
        case "ㄾ": return 13
        case "ㄿ": return 14
        case "ㅀ": return 15
        case "ㅁ": return 16
        case "ㅂ": return 17
        case "ㅄ": return 18
        case "ㅅ": return 19
        case "ㅆ": return 20
        case "ㅇ": return 21
        case "ㅈ": return 22
        case "ㅊ": return 23
        case "ㅋ": return 24
        case "ㅌ": return 25
        case "ㅍ": return 26
        case "ㅎ": return 27
        default: return -1
        }
    }

    // Switch-based compound lookup avoids constructing/hash-looking-up Pair keys per input.
    private static func compoundMedial(_ first: Character, _ second: Character) -> Character? {
        switch first {
        case "ㅗ":
            switch second {
            case "ㅏ": return "ㅘ"
            case "ㅐ": return "ㅙ"
            case "ㅣ": return "ㅚ"
            default: return nil
            }
        case "ㅜ":
            switch second {
            case "ㅓ": return "ㅝ"
            case "ㅔ": return "ㅞ"
            case "ㅣ": return "ㅟ"
            default: return nil
            }
        case "ㅡ":
            return second == "ㅣ" ? "ㅢ" : nil
        default:
            return nil
        }
    }

    private static let compoundMedialBase: [Character: Character] = [
        "ㅘ": "ㅗ", "ㅙ": "ㅗ", "ㅚ": "ㅗ", "ㅝ": "ㅜ", "ㅞ": "ㅜ",
        "ㅟ": "ㅜ", "ㅢ": "ㅡ"
    ]

    private static func compoundFinal(_ first: Character, _ second: Character) -> Character? {
        switch first {
        case "ㄱ":
            return second == "ㅅ" ? "ㄳ" : nil
        case "ㄴ":
            switch second {
            case "ㅈ": return "ㄵ"
            case "ㅎ": return "ㄶ"
            default: return nil
            }
        case "ㄹ":
            switch second {
            case "ㄱ": return "ㄺ"
            case "ㅁ": return "ㄻ"
            case "ㅂ": return "ㄼ"
            case "ㅅ": return "ㄽ"
            case "ㅌ": return "ㄾ"
            case "ㅍ": return "ㄿ"
            case "ㅎ": return "ㅀ"
            default: return nil
            }
        case "ㅂ":
            return second == "ㅅ" ? "ㅄ" : nil
        default:
            return nil
        }
    }

    private static let compoundFinalSplit: [Character: (Character, Character)] = [
        "ㄳ": ("ㄱ", "ㅅ"), "ㄵ": ("ㄴ", "ㅈ"), "ㄶ": ("ㄴ", "ㅎ"),
        "ㄺ": ("ㄹ", "ㄱ"), "ㄻ": ("ㄹ", "ㅁ"), "ㄼ": ("ㄹ", "ㅂ"),
        "ㄽ": ("ㄹ", "ㅅ"), "ㄾ": ("ㄹ", "ㅌ"), "ㄿ": ("ㄹ", "ㅍ"),
        "ㅀ": ("ㄹ", "ㅎ"), "ㅄ": ("ㅂ", "ㅅ")
    ]
}
