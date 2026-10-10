import UIKit
import GoogleMobileAds
import SharedUI

/// Game-end interstitial ads belong to the containing app. The keyboard extension never loads this SDK.
final class GameEndInterstitialAdController: NSObject, FullScreenContentDelegate {
    private static let testAdUnitID = "ca-app-pub-3940256099942544/4411468910"
    private static let roundsKey = "game_end_ads.completed_rounds"
    private static let lastFullscreenKey = "game_end_ads.last_fullscreen_at"
    private static let maxAdAgeSeconds: TimeInterval = 3_600

    var canRequestAds: () -> Bool = { false }
    private var generation: UInt = 0
    private var policy: SharedUI.GameEndAdPolicy
    private var interstitialAd: InterstitialAd?
    private var presentingAd: InterstitialAd?
    private var shown = false
    private var loadedAtUptime: TimeInterval = 0
    private var hasLoadedAd = false
    private var isLoading = false
    private var isPending = false
    private var disposed = false
    private var onFinished: (() -> Void)?

    override init() {
        let defaults = UserDefaults.standard
        let roundsInt = min(max(defaults.integer(forKey: Self.roundsKey), 0), 3)
        let now = Self.epochMillisNow()
        let last = (defaults.object(forKey: Self.lastFullscreenKey) as? NSNumber)?.int64Value ?? now
        policy = SharedUI.GameEndAdPolicy(completedRoundsSinceAd: Int32(roundsInt), lastFullscreenAtMillis: last)
        super.init()
        persist()
    }

    private func persist() {
        UserDefaults.standard.set(Int(policy.completedRoundsSinceAd), forKey: Self.roundsKey)
        UserDefaults.standard.set(policy.lastFullscreenAtMillis, forKey: Self.lastFullscreenKey)
    }

    func invalidate() {
        generation &+= 1
        interstitialAd = nil
        hasLoadedAd = false
        isLoading = false
    }

    func prepare() {
        guard Thread.isMainThread else { return }
        guard !disposed else { return }
        guard canRequestAds() else { invalidate(); return }
        guard !isLoading else { return }
        guard !isPending else { return }
        if hasLoadedAd, interstitialAd != nil {
            let age = ProcessInfo.processInfo.systemUptime - loadedAtUptime
            if age < 0 || age >= Self.maxAdAgeSeconds {
                interstitialAd = nil
                hasLoadedAd = false
            } else {
                return
            }
        }
        guard interstitialAd == nil, !hasLoadedAd else { return }
        guard Self.resolvedAdUnitID() != nil else { return }
        guard Self.hasValidApplicationIdentifier() else { return }
        isLoading = true
        let requestGeneration = generation
        MobileAds.shared.start()
        Task { @MainActor [weak self] in
            guard let self else { return }
            guard let adUnitID = Self.resolvedAdUnitID() else {
                self.isLoading = false
                return
            }
            do {
                let ad = try await InterstitialAd.load(with: adUnitID, request: Request())
                guard !self.disposed, self.generation == requestGeneration else { return }
                guard self.canRequestAds() else { self.isLoading = false; return }
                self.isLoading = false
                guard !self.isPending else { return }
                self.interstitialAd = ad
                self.loadedAtUptime = ProcessInfo.processInfo.systemUptime
                self.hasLoadedAd = true
            } catch {
                if !self.disposed, self.generation == requestGeneration { self.isLoading = false }
            }
        }
    }

    func onCompletedRound(onFinished: @escaping () -> Void) -> Bool {
        guard Thread.isMainThread else { return false }
        guard !disposed else { return false }
        guard canRequestAds() else { invalidate(); return false }
        guard !isPending else { return false }
        policy.onCompletedRound()
        persist()
        let nowMillis = Self.epochMillisNow()
        guard policy.canShow(nowMillis: nowMillis) else {
            prepare()
            return false
        }
        guard let ad = interstitialAd, hasLoadedAd else {
            prepare()
            return false
        }
        let age = ProcessInfo.processInfo.systemUptime - loadedAtUptime
        guard age >= 0, age < Self.maxAdAgeSeconds else {
            interstitialAd = nil
            hasLoadedAd = false
            prepare()
            return false
        }
        guard UIApplication.shared.applicationState == .active else {
            prepare()
            return false
        }
        guard let root = Self.keyWindowRoot(), root.presentedViewController == nil else {
            prepare()
            return false
        }
        interstitialAd = nil
        hasLoadedAd = false
        self.onFinished = onFinished
        isPending = true
        presentingAd = ad
        shown = false
        ad.fullScreenContentDelegate = self
        ad.present(from: root)
        return true
    }

    func noteFullscreenShown() {
        guard Thread.isMainThread else { return }
        guard !disposed else { return }
        policy.onFullscreenShown(nowMillis: Self.epochMillisNow())
        persist()
    }

    func dispose() {
        guard Thread.isMainThread else { return }
        disposed = true
        isLoading = false
        interstitialAd = nil
        hasLoadedAd = false
        isPending = false
        finishOnce()
    }

    func adWillPresentFullScreenContent(_ ad: FullScreenPresentingAd) {
        guard let current = presentingAd, (ad as AnyObject) === current, !shown else { return }
        shown = true
        noteFullscreenShown()
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        guard let current = presentingAd, (ad as AnyObject) === current else { return }
        interstitialAd = nil
        hasLoadedAd = false
        isPending = false
        finishOnce()
    }

    func ad(
        _ ad: FullScreenPresentingAd,
        didFailToPresentFullScreenContentWithError error: Error
    ) {
        guard let current = presentingAd, (ad as AnyObject) === current else { return }
        interstitialAd = nil
        hasLoadedAd = false
        isPending = false
        finishOnce()
    }

    private func finishOnce() {
        let callback = onFinished
        onFinished = nil
        presentingAd = nil
        shown = false
        callback?()
    }

    private static func epochMillisNow() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1_000)
    }

    private static func resolvedAdUnitID() -> String? {
#if DEBUG
        return testAdUnitID
#else
        if (Bundle.main.object(forInfoDictionaryKey: "KKUseTestAds") as? Bool) == true {
            return testAdUnitID
        }
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "KKInterstitialAdUnitID") as? String else {
            return nil
        }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard trimmed.range(of: "^ca-app-pub-[0-9]+/[0-9]+$", options: .regularExpression) != nil else {
            return nil
        }
        return trimmed
#endif
    }

    private static func hasValidApplicationIdentifier() -> Bool {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier") as? String else {
            return false
        }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        return trimmed.range(of: "^ca-app-pub-[0-9]+~[0-9]+$", options: .regularExpression) != nil
    }

    private static func keyWindowRoot() -> UIViewController? {
        return AppPresentationContext.rootViewController()
    }
}
