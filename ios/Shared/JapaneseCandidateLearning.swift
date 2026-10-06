import Foundation

/// On-device preference learning for Japanese conversion candidates.
///
/// The keyboard extension stores only a bounded set of reading/candidate pairs and counters
/// in its local UserDefaults container. No learned text is logged or sent over the network.
final class JapaneseCandidateLearning {
    private struct CandidateScore: Codable {
        var count: Int
        var lastUsed: Int
    }

    private struct ReadingScore: Codable {
        var updated: Int
        var candidates: [String: CandidateScore]
    }

    private struct Snapshot: Codable {
        var version: Int
        var sequence: Int
        var readings: [String: ReadingScore]
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func rank(reading: String, candidates: [String]) -> [String] {
        guard candidates.count > 1, Self.validReading(reading) else { return candidates }
        let snapshot = load()
        guard let learned = snapshot.readings[reading] else { return candidates }

        let originalOrder = Dictionary(uniqueKeysWithValues: candidates.enumerated().map { ($0.element, $0.offset) })
        return candidates.sorted { lhs, rhs in
            let left = learned.candidates[lhs] ?? CandidateScore(count: 0, lastUsed: 0)
            let right = learned.candidates[rhs] ?? CandidateScore(count: 0, lastUsed: 0)
            if left.count != right.count { return left.count > right.count }
            if left.lastUsed != right.lastUsed { return left.lastUsed > right.lastUsed }
            return (originalOrder[lhs] ?? Int.max) < (originalOrder[rhs] ?? Int.max)
        }
    }

    func recordSelection(reading: String, candidate: String) {
        guard Self.validReading(reading), !candidate.isEmpty, candidate.count <= Self.maxCandidateLength else { return }

        var snapshot = load()
        snapshot.sequence = snapshot.sequence == Int.max ? 1 : snapshot.sequence + 1
        var readingScore = snapshot.readings[reading] ?? ReadingScore(updated: 0, candidates: [:])
        readingScore.updated = snapshot.sequence
        var score = readingScore.candidates[candidate] ?? CandidateScore(count: 0, lastUsed: 0)
        score.count = min(score.count + 1, Self.maxCount)
        score.lastUsed = snapshot.sequence
        readingScore.candidates[candidate] = score
        snapshot.readings[reading] = readingScore

        while snapshot.readings.count > Self.maxReadings {
            guard let oldest = snapshot.readings.min(by: { $0.value.updated < $1.value.updated })?.key else { break }
            snapshot.readings.removeValue(forKey: oldest)
        }
        save(snapshot)
    }

    private func load() -> Snapshot {
        guard let data = defaults.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode(Snapshot.self, from: data),
              decoded.version == Self.version else {
            return Snapshot(version: Self.version, sequence: 0, readings: [:])
        }
        return decoded
    }

    private func save(_ snapshot: Snapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }

    private static func validReading(_ reading: String) -> Bool {
        guard !reading.isEmpty,
              reading.count <= JapaneseTransliterator.maxInputLength,
              reading.first != " ",
              reading.last != " " else { return false }
        return reading.unicodeScalars.allSatisfy { scalar in
            scalar.value == 0x20 ||
                (0xAC00...0xD7A3).contains(scalar.value) ||
                (0x3131...0x314E).contains(scalar.value)
        }
    }

    private static let version = 1
    private static let storageKey = "japaneseCandidateLearning.v1"
    private static let maxReadings = 256
    private static let maxCandidateLength = 64
    private static let maxCount = 1_000_000
}
