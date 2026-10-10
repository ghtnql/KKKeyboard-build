import Foundation
#if canImport(SharedUI)
import SharedUI
#else
import SharedCore
#endif

/// Local Hangul-pronunciation -> Japanese candidate provider.
enum JapaneseTransliterator {
    private static let kanaTransliterator = HangulKanaTransliterator()

    private static var dictionary: JapaneseDictionary { JapaneseDictionary.shared }

    private static let lexicon: JapaneseKanaLexicon = {
        guard let url = Bundle(for: JapaneseDictionary.self).url(forResource: "ja_lexicon", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            preconditionFailure("Missing bundled Japanese general lexicon")
        }
        return JapaneseKanaLexicon(tsv: text)
    }()

    static let maxInputLength = 64

    static func prepare() { _ = dictionary; _ = lexicon }

    static func candidates(for inputHangul: String) -> [String] {
        candidatesExact(for: inputHangul.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    static func candidatesExact(for inputHangul: String) -> [String] {
        guard let input = normalized(inputHangul) else { return [] }
        let exact = dictionary.exact(input)
        if !exact.isEmpty { return Array(exact.prefix(JapaneseDictionary.maxCandidates)) }
        guard transliterate(input) != nil else { return [] }
        let whole = (input.count == 1 || input.count <= 2 && !dictionary.completions(input).isEmpty) ? [] : generalConversions(input, exactOnly: true)
        // A known full word can commit directly; sentence guesses remain explicit choices.
        let ordered = !whole.isEmpty ? whole + readings(input) : segmented(input) + readings(input) + generalConversions(input)
        return Array(JapaneseDictionary.unique(ordered)
            .prefix(JapaneseDictionary.maxCandidates))
    }

    static func suggestionsExact(for inputHangul: String) -> [String] {
        guard let input = normalized(inputHangul) else { return [] }
        let exact = dictionary.exact(input)
        let segments = exact.isEmpty ? segmented(input) : []
        let predictions = dictionary.completions(input)
        let literal = readings(input)
        if input.count == 1 && exact.isEmpty && predictions.isEmpty {
            return Array(JapaneseDictionary.unique(literal + generalConversions(input)).prefix(JapaneseDictionary.maxCandidates))
        }
        let general = generalConversions(input)
        if exact.isEmpty && predictions.isEmpty && segments.isEmpty && generalConversions(input, exactOnly: true).isEmpty {
            return Array(JapaneseDictionary.unique(literal + general).prefix(JapaneseDictionary.maxCandidates))
        }
        let preferred = JapaneseDictionary.unique(exact + Array(predictions.prefix(3)) + segments + general)
        let tail = Array(literal.filter { !preferred.contains($0) }.prefix(4))
        return Array(preferred.prefix(JapaneseDictionary.maxCandidates - tail.count)) + tail
    }

    private static func generalConversions(_ input: String, exactOnly: Bool = false) -> [String] {
        let kana = JapaneseDictionary.unique(kanaTransliterator.transliterateCandidates(input: input) +
            [kanaTransliterator.transliterateTolerant(input: input)].compactMap { $0 })
        for reading in kana {
            let result = exactOnly ? lexicon.exactCandidates(reading: reading) : lexicon.candidates(reading: reading)
            if exactOnly || !result.isEmpty { return result }
        }
        return []
    }

    private static func normalized(_ input: String) -> String? {
        guard !input.isEmpty, input.count <= maxInputLength,
              input.first != " ", input.last != " ",
              input.unicodeScalars.allSatisfy({
                  $0.value == 32 || (0xAC00...0xD7A3).contains($0.value) || (0x3131...0x314E).contains($0.value)
              }) else { return nil }
        return input.replacingOccurrences(of: " ", with: "")
    }

    private static func segmented(_ input: String) -> [String] {
        let characters = Array(input)
        var offset = 0
        var usedDictionary = false
        var paths = [""]
        while offset < characters.count {
            let pieces: [String]
            if let match = dictionary.longestMatch(characters, offset: offset) {
                pieces = match.surfaces
                offset += match.length
                usedDictionary = true
            } else {
                guard let kana = transliterate(String(characters[offset])) else { return [] }
                pieces = [kana]
                offset += 1
            }
            paths = Array(JapaneseDictionary.unique(paths.flatMap { prefix in pieces.map { prefix + $0 } }).prefix(3))
        }
        return usedDictionary ? paths : []
    }

    private static func readings(_ input: String) -> [String] {
        JapaneseDictionary.unique(
            kanaTransliterator.transliterateCandidates(input: input) +
                [kanaTransliterator.transliterateTolerant(input: input)].compactMap { $0 }
        ).flatMap { [$0, katakana(from: $0)] }
    }

    private static func transliterate(_ input: String) -> String? {
        kanaTransliterator.transliterate(input: input)
    }

    private static func katakana(from hiragana: String) -> String {
        var result = ""
        for scalar in hiragana.unicodeScalars {
            if (0x3041...0x3096).contains(scalar.value),
               let converted = UnicodeScalar(scalar.value + 0x60) {
                result.unicodeScalars.append(converted)
            } else {
                result.unicodeScalars.append(scalar)
            }
        }
        return result
    }
}
