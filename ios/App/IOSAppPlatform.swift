import UIKit
import SharedUI

/// Native services and existing JSON/progress storage used by the shared Compose UI.
final class IOSAppPlatform: IOSKeyboardSettingsPlatform {
    override var keyboardStatus: KeyboardStatus { .unknown }
    private var cachedItems: [SharedUI.LearningItem] = []
    override var learningItems: [SharedUI.LearningItem] { cachedItems }
    private var cachedProgress = SharedUI.LearningProgress(totalSessions: 0, bestAccuracy: 0, rainHighScore: 0, totalXp: 0)
    override var learningProgress: SharedUI.LearningProgress { cachedProgress }
    private let seoulAd = SeoulThemeRewardedAdController()
    private let gameEndAd = GameEndInterstitialAdController()
    private let gameAudio = GameAudioController()
    private lazy var adConsent = AdConsentController()
    private var previousAdsAllowed = false
    private var mockSeoulAdMessage: String?

    override init() {
        let nativeItems = (try? LearningContent.load()) ?? []
        let sentenceRows: [String: [String: Any]] = {
            guard let url = Bundle.main.url(forResource: "learning_items", withExtension: "json"),
                  let data = try? Data(contentsOf: url),
                  let rows = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else { return [:] }
            return Dictionary(uniqueKeysWithValues: rows.compactMap { row in
                guard let id = row["id"] as? String,
                      row["japaneseText"] is String,
                      row["japaneseHangulPronunciation"] is String,
                      row["koreanText"] is String else { return nil }
                return (id, row)
            })
        }()
        cachedItems = nativeItems.map { item in
            let triad = sentenceRows[item.id]
            return SharedUI.LearningItem(
                id: item.id,
                category: item.category,
                difficulty: Int32(item.difficulty),
                sourceLanguage: item.sourceLanguage,
                sourceText: item.sourceText,
                targetLanguage: item.targetLanguage,
                acceptedAnswers: item.acceptedAnswers,
                meaningHint: item.meaningHint,
                enabledModes: item.enabledModes,
                gameTypes: item.gameTypes,
                japaneseText: triad?["japaneseText"] as? String,
                japaneseHangulPronunciation: triad?["japaneseHangulPronunciation"] as? String,
                koreanText: triad?["koreanText"] as? String
            )
        }
        let defaults = UserDefaults.standard
        cachedProgress = SharedUI.LearningProgress(
            totalSessions: Int32(defaults.integer(forKey: "learning_progress.total_sessions")),
            bestAccuracy: Int32(defaults.integer(forKey: "learning_progress.best_accuracy")),
            rainHighScore: Int32(defaults.integer(forKey: "learning_progress.rain_high_score")),
            totalXp: Int32(defaults.integer(forKey: "learning_progress.total_xp"))
        )
        super.init()
        gameEndAd.canRequestAds = { [weak self] in self?.canRequestAds ?? false }
        seoulAd.canRequestAds = { [weak self] in self?.canRequestAds ?? false }
        // Clear stale test entitlements in both app and keyboard-shared storage.
        UserDefaults.standard.set(false, forKey: "monetization.adRemoval.owned")
        KeyboardSharedDefaults.availableDefaults()?.set(false, forKey: "monetization.adRemoval.owned")
        if !AdExecutionMode.simulatesAds {
            adConsent.onChanged = { [weak self] in
                guard let self else { return }
                // Privacy choices may change personalized-ad eligibility while canRequestAds stays true.
                self.previousAdsAllowed = self.canRequestAds
                self.gameEndAd.invalidate()
                self.seoulAd.invalidate()
                if self.previousAdsAllowed { self.gameEndAd.prepare() }
                self.onSettingsChanged?()
            }
        }
        seoulAd.onFullscreenShown = { [weak self] in self?.gameEndAd.noteFullscreenShown() }
    }

    private func reconcileAdsPermission() {
        let allowed = canRequestAds
        guard allowed != previousAdsAllowed else { return }
        previousAdsAllowed = allowed
        gameEndAd.invalidate()
        seoulAd.invalidate()
        if allowed { gameEndAd.prepare() }
    }

    private var canRequestAds: Bool { !AdExecutionMode.simulatesAds && adConsent.canRequestAds }
    func refreshMonetization() {
        if AdExecutionMode.simulatesAds { return }
        adConsent.refresh()
        reconcileAdsPermission()
    }
    override func isAdPrivacyOptionsRequired() -> Bool {
        if AdExecutionMode.simulatesAds { return false }
        return adConsent.privacyOptionsRequired
    }
    override func showAdPrivacyOptions() {
        if AdExecutionMode.simulatesAds { return }
        adConsent.showPrivacyOptions()
    }
    override var adRemovalOwnedForThemes: Bool? { false }
    override func openPrivacyPolicy() { openPolicyPage("privacy-policy") }
    private func openPolicyPage(_ page: String) {
        let suffix = readUiLanguage() == "ja" ? "-ja" : ""
        guard let url = URL(string: "https://ghtnql.github.io/KKKeyboard-policy/\(page)\(suffix).html") else { return }
        UIApplication.shared.open(url)
    }

    override func prepareGameEndAd() {
        if AdExecutionMode.simulatesAds { return }
        gameEndAd.prepare()
    }

    override func requestGameEndAd(onFinished: @escaping () -> Void) -> Bool {
        if AdExecutionMode.simulatesAds { return false }
        return gameEndAd.onCompletedRound { onFinished() }
    }

    override func readGameAudioSettings() -> SharedUI.GameAudioSettings { gameAudio.readSettings() }
    override func setGameMusicEnabled(enabled: Bool) { gameAudio.setMusicEnabled(enabled: enabled) }
    override func setGameEffectsEnabled(enabled: Bool) { gameAudio.setEffectsEnabled(enabled: enabled) }
    override func startGameAudio(track: SharedUI.GameMusicTrack) { gameAudio.start(track: track) }
    override func stopGameAudio() { gameAudio.stop() }
    override func finishGameAudio() { gameAudio.finish() }
    override func playGameSound(effect: SharedUI.GameSoundEffect) { gameAudio.play(effect: effect) }

    deinit {
        if !AdExecutionMode.simulatesAds { adConsent.close() }
        seoulAd.close(); gameEndAd.dispose(); gameAudio.dispose()
    }

    override func refreshKeyboardStatus() {
        // iOS does not expose whether a third-party keyboard is enabled or selected to its app.
    }

    override func openKeyboardSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    override func showKeyboardPicker() {
        let alert = UIAlertController(
            title: localized("키보드 선택", "キーボードを選択", "Choose keyboard"),
            message: localized("아래 테스트 입력칸을 누른 뒤 키보드의 지구본 키로 ㅋㅋ키보드를 선택하세요.", "下のテスト入力欄をタップして、地球儀キーからㅋㅋキーボードを選択してください。", "Tap the test field, then use the globe key to choose ㅋㅋ키보드."),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: localized("확인", "確認", "OK"), style: .default))
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController?
            .present(alert, animated: true)
    }

    override func onKeyboardTestTextChanged(text: String) {
        keyboardTestText = text
    }

    override func saveLearningProgress(progress: SharedUI.LearningProgress) {
        cachedProgress = progress
        let defaults = UserDefaults.standard
        defaults.set(progress.totalSessions, forKey: "learning_progress.total_sessions")
        defaults.set(progress.bestAccuracy, forKey: "learning_progress.best_accuracy")
        defaults.set(progress.rainHighScore, forKey: "learning_progress.rain_high_score")
        defaults.set(progress.totalXp, forKey: "learning_progress.total_xp")
    }

    override func requestSeoulThemeAd(theme: SharedUI.KeyboardThemeChoice) -> Bool {
        let nativeTheme: KeyboardTheme
        if theme == .seoulDay { nativeTheme = .seoulDay }
        else if theme == .seoulNight { nativeTheme = .seoulNight }
        else { return false }
        if AdExecutionMode.simulatesAds {
            let granted = KeyboardThemeSettings().grantSeoulUnlockAndSelect(nativeTheme)
            mockSeoulAdMessage = granted ? "서울 테마가 24시간 열렸습니다." : "테마를 저장할 수 없습니다. 다시 시도해 주세요."
            return granted
        }
        return seoulAd.request(theme: nativeTheme)
    }

    override func isSeoulThemeAdPending() -> Bool {
        if AdExecutionMode.simulatesAds { return false }
        return seoulAd.isPending
    }

    override func seoulThemeAdMessage() -> String? {
        if AdExecutionMode.simulatesAds { return mockSeoulAdMessage }
        return seoulAd.message
    }
}
