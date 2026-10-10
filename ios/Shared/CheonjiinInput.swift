import Foundation

enum CheonjiinKey: CaseIterable {
    case i, dot, eu, giyeok, nieun, digeut, bieup, siot, jieut, ieung

    var label: String {
        switch self {
        case .i: return "ㅣ"
        case .dot: return "·"
        case .eu: return "ㅡ"
        case .giyeok: return "ㄱㅋ"
        case .nieun: return "ㄴㄹ"
        case .digeut: return "ㄷㅌ"
        case .bieup: return "ㅂㅍ"
        case .siot: return "ㅅㅎ"
        case .jieut: return "ㅈㅊ"
        case .ieung: return "ㅇㅁ"
        }
    }

    var consonants: [Character] {
        switch self {
        case .giyeok: return Array("ㄱㅋㄲ")
        case .nieun: return Array("ㄴㄹ")
        case .digeut: return Array("ㄷㅌㄸ")
        case .bieup: return Array("ㅂㅍㅃ")
        case .siot: return Array("ㅅㅎㅆ")
        case .jieut: return Array("ㅈㅊㅉ")
        case .ieung: return Array("ㅇㅁ")
        default: return []
        }
    }

    var vowelStroke: Character? {
        switch self {
        case .i: return "ㅣ"
        case .dot: return "·"
        case .eu: return "ㅡ"
        default: return nil
        }
    }

    static let rows: [[CheonjiinKey]] = [
        [.i, .dot, .eu], [.giyeok, .nieun, .digeut],
        [.bieup, .siot, .jieut], [.ieung]
    ]
}

enum CheonjiinAction: Equatable {
    case none
    case append(Character)
    case replaceLast(Character)
    case removeLast
}

/// Converts three vowel strokes and grouped consonants to jamo edits.
final class CheonjiinInput {
    private var vowelStrokes = ""
    private var vowelEmitted = false
    private var lastConsonant: CheonjiinKey?
    private var consonantIndex = 0
    private var lastConsonantAt: TimeInterval = -.infinity
    private var lastDirectConsonant: Character?
    private var lastDirectConsonantAt: TimeInterval = -.infinity

    var hasPendingInput: Bool { !vowelStrokes.isEmpty || lastConsonant != nil }

    func input(_ key: CheonjiinKey, at time: TimeInterval, cycleTimeout: TimeInterval = 0.9) -> CheonjiinAction {
        if let stroke = key.vowelStroke { return inputVowelStroke(stroke) }
        vowelStrokes = ""
        vowelEmitted = false
        lastDirectConsonant = nil
        lastDirectConsonantAt = -.infinity
        let consonants = key.consonants
        let canCycle = key == lastConsonant && time - lastConsonantAt <= cycleTimeout
        consonantIndex = canCycle ? (consonantIndex + 1) % consonants.count : 0
        lastConsonant = key
        lastConsonantAt = time
        let character = consonants[consonantIndex]
        return canCycle ? .replaceLast(character) : .append(character)
    }

    /// Held/custom direct consonants insert literally and reset tap cycling.
    func inputDirectConsonant(_ character: Character) -> CheonjiinAction {
        reset()
        return .append(character)
    }

    /// Plus-layout taps turn ㄱ/ㄷ/ㅂ/ㅅ/ㅈ into tense consonants on a quick second tap.
    func inputDirectConsonant(_ character: Character, at time: TimeInterval,
                              doubleTapTimeout: TimeInterval) -> CheonjiinAction {
        vowelStrokes = ""
        vowelEmitted = false
        lastConsonant = nil
        consonantIndex = 0
        lastConsonantAt = -.infinity

        let tense = Self.tenseByBase[character]
        let canDoubleTap = tense != nil &&
            character == lastDirectConsonant &&
            time - lastDirectConsonantAt <= doubleTapTimeout
        if canDoubleTap {
            lastDirectConsonant = nil
            lastDirectConsonantAt = -.infinity
            return .replaceLast(tense!)
        }
        lastDirectConsonant = tense == nil ? nil : character
        lastDirectConsonantAt = tense == nil ? -.infinity : time
        return .append(character)
    }

    /// Nil means the editor should perform its ordinary backspace action.
    func backspace() -> CheonjiinAction? {
        guard !vowelStrokes.isEmpty else {
            reset()
            return nil
        }
        let hadEmittedVowel = vowelEmitted
        vowelStrokes.removeLast()
        let previousVowel = Self.vowels[vowelStrokes]
        vowelEmitted = previousVowel != nil
        if !hadEmittedVowel { return .none }
        if let previousVowel { return .replaceLast(previousVowel) }
        return .removeLast
    }

    func reset() {
        vowelStrokes = ""
        vowelEmitted = false
        lastConsonant = nil
        consonantIndex = 0
        lastConsonantAt = -.infinity
        lastDirectConsonant = nil
        lastDirectConsonantAt = -.infinity
    }

    private func inputVowelStroke(_ stroke: Character) -> CheonjiinAction {
        lastConsonant = nil
        lastDirectConsonant = nil
        lastDirectConsonantAt = -.infinity
        let candidate = vowelStrokes + String(stroke)
        if Self.vowelPrefixes.contains(candidate) { return acceptVowelSequence(candidate) }
        vowelStrokes = ""
        vowelEmitted = false
        return acceptVowelSequence(String(stroke))
    }

    private func acceptVowelSequence(_ sequence: String) -> CheonjiinAction {
        guard Self.vowelPrefixes.contains(sequence) else { return .none }
        let hadEmittedVowel = vowelEmitted
        vowelStrokes = sequence
        guard let vowel = Self.vowels[sequence] else { return .none }
        vowelEmitted = true
        return hadEmittedVowel ? .replaceLast(vowel) : .append(vowel)
    }

    private static let tenseByBase: [Character: Character] = [
        "ㄱ": "ㄲ", "ㄷ": "ㄸ", "ㅂ": "ㅃ", "ㅅ": "ㅆ", "ㅈ": "ㅉ"
    ]

    private static let vowels: [String: Character] = [
        "ㅣ": "ㅣ", "ㅡ": "ㅡ", "ㅣ·": "ㅏ", "ㅣ··": "ㅑ", "·ㅣ": "ㅓ", "··ㅣ": "ㅕ",
        "·ㅡ": "ㅗ", "··ㅡ": "ㅛ", "ㅡ·": "ㅜ", "ㅡ··": "ㅠ", "ㅣ·ㅣ": "ㅐ",
        "ㅣ··ㅣ": "ㅒ", "·ㅣㅣ": "ㅔ", "··ㅣㅣ": "ㅖ", "·ㅡㅣ·": "ㅘ",
        "·ㅡㅣ·ㅣ": "ㅙ", "·ㅡㅣ": "ㅚ", "ㅡ··ㅣ": "ㅝ", "ㅡ··ㅣㅣ": "ㅞ",
        "ㅡ·ㅣ": "ㅟ", "ㅡㅣ": "ㅢ"
    ]
    private static let vowelPrefixes: Set<String> = {
        Set(CheonjiinInput.vowels.keys.flatMap { sequence in
            (1...sequence.count).map { String(sequence.prefix($0)) }
        })
    }()
}
