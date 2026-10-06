import Foundation
#if canImport(SharedUI)
import SharedUI
#else
import SharedCore
#endif

enum PracticeMode: String, Codable {
    case koreanTyping = "ko_same_hangul"
    case japaneseToHangul = "ja_to_hangul_pronunciation"
}

struct LearningItem: Codable, Equatable {
    let id: String
    let category: String
    let difficulty: Int
    let sourceLanguage: String
    let sourceText: String
    let targetLanguage: String
    let acceptedAnswers: [String]
    let meaningHint: String?
    let enabledModes: [String]
    let gameTypes: [String]
}

enum LearningContent {
    private static let pronunciationMatcher = JapanesePronunciationMatcher()

    static func matches(_ answer: String, item: LearningItem, mode: PracticeMode) -> Bool {
        item.acceptedAnswers.contains {
            let expected = normalize($0)
            return expected == answer || (mode == .japaneseToHangul && item.sourceLanguage == "ja" &&
                pronunciationMatcher.matches(input: answer, expected: expected))
        }
    }

    static func load(bundle: Bundle = .main) throws -> [LearningItem] {
        guard let url = bundle.url(forResource: "learning_items", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        return try parse(Data(contentsOf: url))
    }

    static func parse(_ data: Data) throws -> [LearningItem] {
        let items = try JSONDecoder().decode([LearningItem].self, from: data)
        guard items.allSatisfy({ !$0.acceptedAnswers.isEmpty }),
              Set(items.map(\.id)).count == items.count else {
            throw CocoaError(.fileReadCorruptFile)
        }
        return items
    }

    static func items(
        from items: [LearningItem],
        mode: PracticeMode,
        gameType: String
    ) -> [LearningItem] {
        items.filter { $0.enabledModes.contains(mode.rawValue) && $0.gameTypes.contains(gameType) }
    }

    static func normalize(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).precomposedStringWithCanonicalMapping
    }
}

struct TypingSessionResult: Equatable {
    let modeId: String
    let totalExpectedCharacters: Int
    let totalTypedCharacters: Int
    let errorCount: Int
    let accuracy: Int
    let durationMs: Int64
    let cpm: Int
    let maxCombo: Int
    let score: Int
    let completed: Bool
}

final class PracticeSession {
    let mode: PracticeMode
    let items: [LearningItem]
    private let startedAtMs: Int64
    private(set) var currentIndex = 0
    private(set) var errorCount = 0
    private(set) var totalTypedCharacters = 0
    private(set) var combo = 0
    private(set) var maxCombo = 0
    private(set) var score = 0

    var currentItem: LearningItem? { items.indices.contains(currentIndex) ? items[currentIndex] : nil }

    init(mode: PracticeMode, items: [LearningItem], startedAtMs: Int64) {
        precondition(!items.isEmpty)
        self.mode = mode
        self.items = items
        self.startedAtMs = startedAtMs
    }

    @discardableResult
    func submit(_ rawAnswer: String) -> (correct: Bool, completed: Bool) {
        guard let item = currentItem else { return (false, true) }
        let answer = LearningContent.normalize(rawAnswer)
        totalTypedCharacters += answer.count
        let correct = LearningContent.matches(answer, item: item, mode: mode)
        if correct {
            combo += 1
            maxCombo = max(maxCombo, combo)
            score += answer.count * 10 + (combo - 1) * 2
            currentIndex += 1
        } else {
            errorCount += Self.characterDistance(answer, item.acceptedAnswers[0])
            combo = 0
        }
        return (correct, currentIndex == items.count)
    }

    func result(finishedAtMs: Int64) -> TypingSessionResult {
        let duration = max(Int64(1), finishedAtMs - startedAtMs)
        let accurate = max(0, totalTypedCharacters - errorCount)
        return TypingSessionResult(
            modeId: mode.rawValue,
            totalExpectedCharacters: items.reduce(0) { $0 + $1.acceptedAnswers[0].count },
            totalTypedCharacters: totalTypedCharacters,
            errorCount: errorCount,
            accuracy: totalTypedCharacters == 0 ? 0 : accurate * 100 / totalTypedCharacters,
            durationMs: duration,
            cpm: Int(Int64(accurate) * 60_000 / duration),
            maxCombo: maxCombo,
            score: score,
            completed: currentIndex == items.count
        )
    }

    static func characterDistance(_ first: String, _ second: String) -> Int {
        let a = Array(first), b = Array(second)
        if a.isEmpty { return max(1, b.count) }
        if b.isEmpty { return max(1, a.count) }
        var previous = Array(0...b.count)
        for i in a.indices {
            var diagonal = previous[0]
            previous[0] = i + 1
            for j in b.indices {
                let above = previous[j + 1]
                previous[j + 1] = min(
                    above + 1,
                    previous[j] + 1,
                    diagonal + (a[i] == b[j] ? 0 : 1)
                )
                diagonal = above
            }
        }
        return max(1, previous[b.count])
    }
}

struct RainGameConfig {
    var durationMs: Int64 = 60_000
    var spawnIntervalMs: Int64 = 2_400
    var fallPerSecond: Float = 0.115
    var startingLives = 3
    var scorePerCharacter = 10
    var comboStep = 2
}

struct RainTarget: Equatable {
    let instanceId: Int64
    let item: LearningItem
    let x: Float
    var y: Float
}

final class RainGame {
    let mode: PracticeMode
    private let items: [LearningItem]
    private let startedAtMs: Int64
    private let config: RainGameConfig
    private let randomFloat: () -> Float
    private(set) var targets: [RainTarget] = []
    private var spawnAccumulatorMs: Int64
    private var nextItem = 0
    private var nextInstanceId: Int64 = 1
    private var elapsedMs: Int64 = 0
    private var correctCharacters = 0
    private(set) var lives: Int
    private(set) var score = 0
    private(set) var combo = 0
    private(set) var maxCombo = 0
    private(set) var errorCount = 0
    private(set) var totalTypedCharacters = 0
    private(set) var isFinished = false

    init(
        mode: PracticeMode,
        items: [LearningItem],
        startedAtMs: Int64,
        config: RainGameConfig = RainGameConfig(),
        randomFloat: @escaping () -> Float = { Float.random(in: 0...1) }
    ) {
        precondition(!items.isEmpty)
        self.mode = mode
        self.items = items
        self.startedAtMs = startedAtMs
        self.config = config
        self.randomFloat = randomFloat
        self.lives = config.startingLives
        self.spawnAccumulatorMs = config.spawnIntervalMs
    }

    func tick(deltaMs: Int64) {
        guard !isFinished else { return }
        let delta = min(max(0, deltaMs), 250)
        elapsedMs += delta
        let distance = config.fallPerSecond * Float(delta) / 1_000
        targets = targets.map { var target = $0; target.y += distance; return target }
        let missed = targets.filter { $0.y >= 1 }.count
        targets.removeAll { $0.y >= 1 }
        if missed > 0 { lives = max(0, lives - missed); combo = 0 }
        spawnAccumulatorMs += delta
        let maximum = min(3, 1 + Int(elapsedMs / 20_000))
        if spawnAccumulatorMs >= config.spawnIntervalMs && targets.count < maximum {
            spawnAccumulatorMs -= config.spawnIntervalMs
            spawn()
        }
        if lives == 0 || elapsedMs >= config.durationMs { isFinished = true }
    }

    @discardableResult
    func submit(_ rawAnswer: String) -> Bool {
        guard !isFinished else { return false }
        let answer = LearningContent.normalize(rawAnswer)
        guard !answer.isEmpty else { return false }
        totalTypedCharacters += answer.count
        let matches = targets.enumerated().filter { pair in
            LearningContent.matches(answer, item: pair.element.item, mode: mode)
        }
        guard let hit = matches.max(by: { $0.element.y < $1.element.y }) else {
            let expected = targets.max(by: { $0.y < $1.y })?.item.acceptedAnswers[0] ?? ""
            errorCount += PracticeSession.characterDistance(answer, expected)
            combo = 0
            return false
        }
        targets.remove(at: hit.offset)
        combo += 1
        maxCombo = max(maxCombo, combo)
        correctCharacters += answer.count
        score += answer.count * config.scorePerCharacter + (combo - 1) * config.comboStep
        return true
    }

    func finish() { isFinished = true }

    func result(finishedAtMs: Int64) -> TypingSessionResult {
        let duration = max(Int64(1), finishedAtMs - startedAtMs)
        return TypingSessionResult(
            modeId: "rain_\(mode.rawValue)",
            totalExpectedCharacters: correctCharacters,
            totalTypedCharacters: totalTypedCharacters,
            errorCount: errorCount,
            accuracy: totalTypedCharacters == 0 ? 0 : max(0, totalTypedCharacters - errorCount) * 100 / totalTypedCharacters,
            durationMs: duration,
            cpm: Int(Int64(correctCharacters) * 60_000 / duration),
            maxCombo: maxCombo,
            score: score,
            completed: true
        )
    }

    private func spawn() {
        targets.append(RainTarget(
            instanceId: nextInstanceId,
            item: items[nextItem % items.count],
            x: min(max(randomFloat(), 0.05), 0.85),
            y: 0
        ))
        nextInstanceId += 1
        nextItem += 1
    }
}
