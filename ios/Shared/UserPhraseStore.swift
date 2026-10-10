import Foundation

private final class UserPhraseBundleToken {}

enum KeyboardSharedDefaults {
    static let appGroupIdentifier = "group.com.ghtnql.kkkeyboard"
    static var isKeyboardExtension: Bool { Bundle.main.bundleURL.pathExtension == "appex" }

    static func availableDefaults() -> UserDefaults? {
        guard FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupIdentifier
        ) != nil else { return nil }
        return UserDefaults(suiteName: appGroupIdentifier)
    }
}

struct UserPhrase: Codable, Equatable, Identifiable {
    let id: UUID
    var title: String
    var content: String

    init(id: UUID = UUID(), title: String, content: String) {
        self.id = id
        self.title = title
        self.content = content
    }
}

enum UserPhraseStoreError: Error, Equatable {
    case emptyTitle
    case emptyContent
    case titleTooLong
    case contentTooLong
    case phraseLimitReached
    case phraseNotFound
    case encodingFailed
}

struct UserPhraseStore {
    static let maximumPhraseCount = 50
    static let maximumTitleLength = 40
    static let maximumContentLength = 500

    private static let storageKey = "userPhrases.v1"
    private static let migrationKey = "appGroupMigration.userPhrases.v1"
    private static let hiddenSharedIDsKey = "userPhrases.hiddenSharedIDs.v1"
    private static let defaultsSeededKey = "userPhrases.defaultSeeded.v1"
    private static let defaultPhrases: [UserPhrase]? = {
        let bundles = [Bundle.main, Bundle(for: UserPhraseBundleToken.self)]
        guard let url = bundles.compactMap({ $0.url(forResource: "default_phrases", withExtension: "json") }).first,
              let data = try? Data(contentsOf: url),
              let phrases = try? JSONDecoder().decode([UserPhrase].self, from: data),
              phrases.count == 10,
              Set(phrases.map(\.id)).count == phrases.count,
              phrases.allSatisfy({ Self.validationError(title: $0.title, content: $0.content) == nil }) else {
            return nil
        }
        return phrases
    }()

    private let defaults: UserDefaults
    private let readOnlySharedDefaults: UserDefaults?
    private let includeBundledDefaults: Bool
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(
        defaults: UserDefaults? = nil,
        legacyDefaults: UserDefaults = .standard,
        migrateLegacy: Bool = false,
        readOnlySharedDefaults: UserDefaults? = nil,
        seedDefaults: Bool = true
    ) {
        let group = defaults == nil ? KeyboardSharedDefaults.availableDefaults() : nil
        let extensionReadOnly = defaults == nil && KeyboardSharedDefaults.isKeyboardExtension
        self.defaults = defaults ?? (extensionReadOnly ? legacyDefaults : group ?? legacyDefaults)
        self.readOnlySharedDefaults = readOnlySharedDefaults ?? (extensionReadOnly ? group : nil)
        self.includeBundledDefaults = seedDefaults
        encoder = JSONEncoder()
        decoder = JSONDecoder()
        if !extensionReadOnly && (defaults == nil || migrateLegacy), self.defaults !== legacyDefaults {
            migrateLegacyPhrases(from: legacyDefaults)
        }
        if seedDefaults { seedDefaultPhrasesIfNeeded() }
    }

    func phrases() -> [UserPhrase] {
        let local = decodedPhrases(from: defaults)
        let shared = effectiveSharedPhrases()
        let hidden = Set(defaults.stringArray(forKey: Self.hiddenSharedIDsKey) ?? [])
        var seen = Set<UUID>()
        return (local + shared.filter { !hidden.contains($0.id.uuidString) }).filter { phrase in
            guard seen.insert(phrase.id).inserted else { return false }
            return Self.validationError(title: phrase.title, content: phrase.content) == nil
        }.prefix(Self.maximumPhraseCount).map { $0 }
    }

    @discardableResult
    func create(title: String, content: String) throws -> UserPhrase {
        try Self.validate(title: title, content: content)
        var current = phrases()
        guard current.count < Self.maximumPhraseCount else {
            throw UserPhraseStoreError.phraseLimitReached
        }

        let phrase = UserPhrase(title: title.trimmingCharacters(in: .whitespacesAndNewlines), content: content)
        current.append(phrase)
        try save(current)
        return phrase
    }

    func update(id: UUID, title: String, content: String) throws {
        try Self.validate(title: title, content: content)
        var current = phrases()
        guard let index = current.firstIndex(where: { $0.id == id }) else {
            throw UserPhraseStoreError.phraseNotFound
        }

        current[index].title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        current[index].content = content
        try save(current)
    }

    func delete(id: UUID) throws {
        var current = phrases()
        guard let index = current.firstIndex(where: { $0.id == id }) else {
            throw UserPhraseStoreError.phraseNotFound
        }
        current.remove(at: index)
        try save(current)
        if effectiveSharedPhrases().contains(where: { $0.id == id })
            || (Self.defaultPhrases?.contains(where: { $0.id == id }) ?? false) {
            var hidden = Set(defaults.stringArray(forKey: Self.hiddenSharedIDsKey) ?? [])
            hidden.insert(id.uuidString)
            defaults.set(Array(hidden).sorted(), forKey: Self.hiddenSharedIDsKey)
        }
    }

    private static func validate(title: String, content: String) throws {
        if let error = validationError(title: title, content: content) {
            throw error
        }
    }

    private static func validationError(title: String, content: String) -> UserPhraseStoreError? {
        if title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .emptyTitle
        }
        if content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .emptyContent
        }
        if title.trimmingCharacters(in: .whitespacesAndNewlines).count > maximumTitleLength {
            return .titleTooLong
        }
        if content.count > maximumContentLength {
            return .contentTooLong
        }
        return nil
    }

    private func save(_ phrases: [UserPhrase]) throws {
        var stored = phrases
        if readOnlySharedDefaults != nil {
            let shared = effectiveSharedPhrases()
            var sharedByID: [UUID: UserPhrase] = [:]
            for phrase in shared where sharedByID[phrase.id] == nil {
                sharedByID[phrase.id] = phrase
            }
            stored = phrases.filter { sharedByID[$0.id] != $0 }
            guard let data = try? encoder.encode(stored) else {
                throw UserPhraseStoreError.encodingFailed
            }
            defaults.set(data, forKey: Self.storageKey)
            return
        }
        guard let data = try? encoder.encode(stored) else {
            throw UserPhraseStoreError.encodingFailed
        }
        defaults.set(data, forKey: Self.storageKey)
    }

    private func decodedPhrases(from source: UserDefaults) -> [UserPhrase] {
        guard let data = source.data(forKey: Self.storageKey),
              let decoded = try? decoder.decode([UserPhrase].self, from: data) else { return [] }
        return decoded
    }

    private func effectiveSharedPhrases() -> [UserPhrase] {
        guard let sharedDefaults = readOnlySharedDefaults else { return [] }
        let stored = decodedPhrases(from: sharedDefaults)
        guard includeBundledDefaults,
              !sharedDefaults.bool(forKey: Self.defaultsSeededKey),
              let seeds = Self.defaultPhrases else { return stored }
        var ids = Set(stored.map(\.id))
        return stored + seeds.filter { ids.insert($0.id).inserted }
    }

    private func seedDefaultPhrasesIfNeeded() {
        // The extension reads the app group without writing it. Defaults remain a
        // transient fallback until the app seeds the group, so later app edits win.
        if readOnlySharedDefaults != nil { return }
        guard !defaults.bool(forKey: Self.defaultsSeededKey) else { return }
        if let data = defaults.data(forKey: Self.storageKey),
           (try? decoder.decode([UserPhrase].self, from: data)) == nil { return }
        guard let seeds = Self.defaultPhrases else { return }
        var current = phrases()
        var ids = Set(current.map(\.id))
        for phrase in seeds where current.count < Self.maximumPhraseCount {
            if ids.insert(phrase.id).inserted { current.append(phrase) }
        }
        guard (try? save(current)) != nil else { return }
        defaults.set(true, forKey: Self.defaultsSeededKey)
    }

    private func migrateLegacyPhrases(from legacyDefaults: UserDefaults) {
        guard !legacyDefaults.bool(forKey: Self.migrationKey) else { return }
        guard let data = legacyDefaults.data(forKey: Self.storageKey),
              let legacyPhrases = try? decoder.decode([UserPhrase].self, from: data) else {
            legacyDefaults.set(true, forKey: Self.migrationKey)
            return
        }

        // The app may already have newer group data. Copy only legacy IDs that
        // have not been written there; extension-private data stays local.
        let existing = phrases()
        var seen = Set<UUID>()
        let merged = (existing + legacyPhrases).filter { phrase in
            guard seen.insert(phrase.id).inserted else { return false }
            return Self.validationError(title: phrase.title, content: phrase.content) == nil
        }.prefix(Self.maximumPhraseCount)
        guard let encoded = try? encoder.encode(Array(merged)) else { return }
        defaults.set(encoded, forKey: Self.storageKey)
        legacyDefaults.set(true, forKey: Self.migrationKey)
    }
}
