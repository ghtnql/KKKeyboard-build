import Foundation

/// Extension-local persistent clipboard history. Uses only
/// `UserDefaults.standard` in production; no app groups, no network,
/// and never reads the system clipboard automatically.
public struct ClipboardEntry: Codable, Equatable {
    public var text: String
    public var pinned: Bool

    public init(text: String, pinned: Bool = false) {
        self.text = text
        self.pinned = pinned
    }
}

public final class ClipboardHistoryStore {
    public static let maxPins = 30
    public static let maxUnpinned = 30
    public static let maxUTF16Length = 10_000

    private let defaults: UserDefaults
    private let storageKey: String
    /// Newest-first internal order. `list()` groups pinned first, stable within each group.
    private var entries: [ClipboardEntry]

    public convenience init(storageKey: String = "ClipboardHistoryStore.entries.v1") {
        self.init(defaults: .standard, storageKey: storageKey)
    }

    public init(defaults: UserDefaults, storageKey: String = "ClipboardHistoryStore.entries.v1") {
        self.defaults = defaults
        self.storageKey = storageKey
        self.entries = Self.load(defaults: defaults, key: storageKey)
    }

    /// Exact text equality by UTF-16 code units (NFC/NFD distinct).
    private static func sameText(_ a: String, _ b: String) -> Bool {
        a.utf16.elementsEqual(b.utf16)
    }

    // MARK: - Query

    /// Pinned entries first (newest first), then unpinned (newest first).
    public func list() -> [ClipboardEntry] {
        let pinned = entries.filter { $0.pinned }
        let unpinned = entries.filter { !$0.pinned }
        return pinned + unpinned
    }

    // MARK: - Mutations

    /// Saves text as the newest unpinned entry. Exact-content dedupe:
    /// an existing entry keeps its pin state and moves to newest.
    /// - Returns: false when text is empty or exceeds 10,000 UTF-16 units.
    @discardableResult
    public func save(text: String) -> Bool {
        guard !text.isEmpty, text.utf16.count <= Self.maxUTF16Length else { return false }
        if let idx = entries.firstIndex(where: { Self.sameText($0.text, text) }) {
            let existing = entries.remove(at: idx)
            // Preserve pin; move to newest.
            entries.insert(existing, at: 0)
        } else {
            evictOldestUnpinnedIfNeeded()
            entries.insert(ClipboardEntry(text: text, pinned: false), at: 0)
        }
        persist()
        return true
    }

    /// Marks an existing entry as most recently used (moves to newest).
    /// Does nothing when the text is not stored.
    /// - Returns: true if an existing entry was moved.
    @discardableResult
    public func use(text: String) -> Bool {
        guard let idx = entries.firstIndex(where: { Self.sameText($0.text, text) }) else { return false }
        let entry = entries.remove(at: idx)
        entries.insert(entry, at: 0)
        persist()
        return true
    }

    /// Toggles the pin state of an existing entry.
    /// - Returns: false when the entry is missing, or when pinning
    ///   would exceed 30 pinned entries. Unpinning trims the oldest
    ///   unpinned entry when unpinned storage exceeds 30.
    @discardableResult
    public func togglePin(text: String) -> Bool {
        guard let idx = entries.firstIndex(where: { Self.sameText($0.text, text) }) else { return false }
        var entry = entries.remove(at: idx)
        if !entry.pinned {
            let pinnedCount = entries.filter { $0.pinned }.count
            guard pinnedCount < Self.maxPins else {
                entries.insert(entry, at: idx)
                return false
            }
        }
        entry.pinned.toggle()
        entries.insert(entry, at: 0)
        while entries.filter({ !$0.pinned }).count > Self.maxUnpinned {
            evictOldestUnpinned()
        }
        persist()
        return true
    }

    public func delete(text: String) {
        let before = entries.count
        entries.removeAll(where: { Self.sameText($0.text, text) })
        if entries.count != before { persist() }
    }

    public func clear() {
        guard !entries.isEmpty else { return }
        entries.removeAll()
        persist()
    }

    // MARK: - Persistence

    private static func load(defaults: UserDefaults, key: String) -> [ClipboardEntry] {
        guard let data = defaults.data(forKey: key) else { return [] }
        let decoded: [ClipboardEntry]
        do {
            decoded = try JSONDecoder().decode([ClipboardEntry].self, from: data)
        } catch {
            // Corrupt-data safe: fall back to empty rather than crashing.
            return []
        }
        var seen: [String] = []
        var pinnedCount = 0
        var unpinnedCount = 0
        var out: [ClipboardEntry] = []
        out.reserveCapacity(decoded.count)
        for entry in decoded {
            guard !entry.text.isEmpty, entry.text.utf16.count <= maxUTF16Length else { continue }
            guard !seen.contains(where: { sameText($0, entry.text) }) else { continue }
            if entry.pinned {
                guard pinnedCount < maxPins else { continue }
                pinnedCount += 1
            } else {
                guard unpinnedCount < maxUnpinned else { continue }
                unpinnedCount += 1
            }
            seen.append(entry.text)
            out.append(entry)
        }
        return out
    }

    private func persist() {
        do {
            defaults.set(try JSONEncoder().encode(entries), forKey: storageKey)
        } catch {
            // Encoding ClipboardEntry cannot realistically fail; ignore to stay total.
        }
    }

    private func evictOldestUnpinnedIfNeeded() {
        if entries.filter({ !$0.pinned }).count >= Self.maxUnpinned {
            evictOldestUnpinned()
        }
    }

    private func evictOldestUnpinned() {
        if let oldest = entries.lastIndex(where: { !$0.pinned }) {
            entries.remove(at: oldest)
        }
    }
}
