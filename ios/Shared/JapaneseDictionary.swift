import Foundation
#if canImport(SharedUI)
import SharedUI
#else
import SharedCore
#endif

final class JapaneseDictionary {
    struct Entry: Decodable {
        let id: String
        let priority: Int
        let aliases: [String]
        let surfaces: [String]
    }

    private struct Pack: Decodable {
        let version: Int
        let entries: [Entry]
    }

    enum InvalidPack: Error { case invalidData }

    static let maxCandidates = 8
    let entries: [Entry]
    private var exactIndex: [String: [Entry]] = [:]
    private var prefixIndex: [String: [Entry]] = [:]
    private var tolerantExactIndex: [String: [Entry]] = [:]
    private var tolerantPrefixIndex: [String: [Entry]] = [:]
    private var maxAliasLength = 0
    private let pronunciationMatcher = JapanesePronunciationMatcher()

    static let shared: JapaneseDictionary = {
        guard let url = Bundle(for: JapaneseDictionary.self).url(forResource: "ja_common", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let dictionary = try? JapaneseDictionary(data: data) else {
            assertionFailure("Missing or invalid bundled Japanese dictionary")
            return JapaneseDictionary(entries: [])
        }
        return dictionary
    }()

    convenience init(data: Data) throws {
        let pack = try JSONDecoder().decode(Pack.self, from: data)
        guard pack.version == 1,
              Set(pack.entries.map(\.id)).count == pack.entries.count,
              pack.entries.allSatisfy({ entry in
                  !entry.id.isEmpty && entry.priority >= 0 && !entry.aliases.isEmpty &&
                      !entry.surfaces.isEmpty && entry.surfaces.allSatisfy { !$0.isEmpty } &&
                      entry.aliases.allSatisfy { alias in
                          (1...JapaneseTransliterator.maxInputLength).contains(alias.count) &&
                              alias.unicodeScalars.allSatisfy { (0xAC00...0xD7A3).contains($0.value) }
                      }
              }) else { throw InvalidPack.invalidData }
        self.init(entries: pack.entries)
    }

    private init(entries: [Entry]) {
        self.entries = entries
        for entry in entries.sorted(by: { $0.priority == $1.priority ? $0.id < $1.id : $0.priority < $1.priority }) {
            for alias in entry.aliases {
                exactIndex[alias, default: []].append(entry)
                maxAliasLength = max(maxAliasLength, alias.count)
                addPrefixes(index: &prefixIndex, keys: Self.phoneticKeys(alias), entry: entry)

                let tolerantAlias = pronunciationMatcher.canonicalize(input: alias)
                if !(tolerantExactIndex[tolerantAlias]?.contains { $0.id == entry.id } ?? false) {
                    tolerantExactIndex[tolerantAlias, default: []].append(entry)
                }
                addPrefixes(index: &tolerantPrefixIndex, keys: Self.phoneticKeys(tolerantAlias), entry: entry)
                let characters = Array(alias)
                for boundary in characters.indices {
                    let base = Self.phoneticKeys(pronunciationMatcher.canonicalize(input: String(characters.prefix(boundary))))
                    let next = Self.phoneticKeys(pronunciationMatcher.canonicalize(input: String(characters[boundary])))
                    if next.count > 1 {
                        for length in 1..<next.count {
                            let key = base + String(next.prefix(length))
                            if key.count >= 4 && !(tolerantPrefixIndex[key]?.contains { $0.id == entry.id } ?? false) {
                                tolerantPrefixIndex[key, default: []].append(entry)
                            }
                        }
                    }
                }
            }
        }
    }

    private func literalSurfaces(_ input: String) -> [String] {
        Self.unique(exactIndex[input]?.flatMap(\.surfaces) ?? [])
    }

    private func tolerantSurfaces(_ input: String) -> [String] {
        let tolerant = pronunciationMatcher.canonicalize(input: input)
        return Self.unique(tolerantExactIndex[tolerant]?.flatMap(\.surfaces) ?? [])
    }

    func exact(_ input: String) -> [String] {
        let direct = literalSurfaces(input)
        return direct.isEmpty ? tolerantSurfaces(input) : direct
    }

    func completions(_ input: String) -> [String] {
        let direct = prefixIndex[Self.phoneticKeys(input)]?.flatMap(\.surfaces) ?? []
        let tolerantInput = pronunciationMatcher.canonicalize(input: input)
        let keys = Self.phoneticKeys(tolerantInput)
        // A composing next onset can still be attached to the previous syllable.
        // Prefix-only folding keeps exact dictionary codas significant.
        var prefixVariants = [keys]
        if let last = keys.last, last == "ㅋ" || last == "ㅌ" {
            prefixVariants.append(String(keys.dropLast()) + (last == "ㅋ" ? "ㄱ" : "ㄷ"))
        }
        let tolerant = prefixVariants.flatMap { tolerantPrefixIndex[$0]?.flatMap(\.surfaces) ?? [] }
        // Literal spellings stay first; equivalent pronunciation candidates follow.
        return Array(Self.unique(direct + tolerant).prefix(Self.maxCandidates))
    }

    private func addPrefixes(index: inout [String: [Entry]], keys: String, entry: Entry) {
        guard keys.count > 4 else { return }
        for length in 4..<keys.count {
            let prefix = String(keys.prefix(length))
            if !(index[prefix]?.contains { $0.id == entry.id } ?? false) {
                index[prefix, default: []].append(entry)
            }
        }
    }

    func longestMatch(_ input: [Character], offset: Int) -> (length: Int, surfaces: [String])? {
        let remaining = input.count - offset
        let literalMaximum = min(maxAliasLength, remaining)
        if literalMaximum >= 2 {
            for length in stride(from: literalMaximum, through: 2, by: -1) {
                let surfaces = literalSurfaces(String(input[offset..<(offset + length)]))
                if !surfaces.isEmpty { return (length, surfaces) }
            }
        }
        let tolerantMaximum = min(JapaneseTransliterator.maxInputLength, remaining)
        if tolerantMaximum >= 2 {
            for length in stride(from: tolerantMaximum, through: 2, by: -1) {
                let surfaces = tolerantSurfaces(String(input[offset..<(offset + length)]))
                if !surfaces.isEmpty { return (length, surfaces) }
            }
        }
        return nil
    }

    static func unique(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.filter { seen.insert($0).inserted }
    }

    // Include final consonants so an intermediate Hangul syllable stays a prefix.
    static func phoneticKeys(_ input: String) -> String {
        var result = ""
        for scalar in input.unicodeScalars {
            if (0xAC00...0xD7A3).contains(scalar.value) {
                let value = Int(scalar.value - 0xAC00)
                result.append(initials[value / 588])
                result += medials[(value % 588) / 28]
                result += finals[value % 28]
            } else {
                result.unicodeScalars.append(scalar)
            }
        }
        return result
    }

    private static let initials = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
    private static let medials = ["ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅗㅏ", "ㅗㅐ", "ㅗㅣ", "ㅛ", "ㅜ", "ㅜㅓ", "ㅜㅔ", "ㅜㅣ", "ㅠ", "ㅡ", "ㅡㅣ", "ㅣ"]
    private static let finals = ["", "ㄱ", "ㄲ", "ㄱㅅ", "ㄴ", "ㄴㅈ", "ㄴㅎ", "ㄷ", "ㄹ", "ㄹㄱ", "ㄹㅁ", "ㄹㅂ", "ㄹㅅ", "ㄹㅌ", "ㄹㅍ", "ㄹㅎ", "ㅁ", "ㅂ", "ㅂㅅ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]
}
