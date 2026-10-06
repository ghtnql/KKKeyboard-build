import UIKit
import GoogleMobileAds

/// Rewarded ads belong to the containing app. The keyboard extension never loads this SDK.
final class SeoulThemeRewardedAdController: NSObject, FullScreenContentDelegate {
    private static let testAdUnitID = "ca-app-pub-3940256099942544/1712485313"
    var canRequestAds: () -> Bool = { false }
    private var generation: UInt = 0
    private var disposed = false
    private let settings = KeyboardThemeSettings()
    private var rewardedAd: RewardedAd?
    private var rewardGranted = false
    private var shown = false
    var onFullscreenShown: (() -> Void)?
    private(set) var isPending = false
    private(set) var message: String?

    func request(theme: KeyboardTheme) -> Bool {
        guard Thread.isMainThread, !disposed else { return false }
        guard canRequestAds() else {
            message = "광고 개인정보 선택이 완료되지 않았습니다."
            return false
        }
        guard theme == .seoulDay || theme == .seoulNight else { return false }
        guard !isPending else { return false }
        guard settings.canGrantSeoulUnlock else {
            message = "공유 저장소를 사용할 수 없어 테마를 잠금 해제할 수 없습니다."
            return false
        }
        guard let adUnitID = Self.resolvedAdUnitID() else {
            message = "서울 테마 광고가 아직 준비되지 않았습니다."
            return false
        }
        guard Self.hasValidApplicationIdentifier() else {
            message = "서울 테마 광고가 아직 준비되지 않았습니다."
            return false
        }
        guard Self.presentingViewController() != nil else {
            message = "광고를 표시할 수 있는 화면이 없습니다. 다시 시도해 주세요."
            return false
        }

        isPending = true
        rewardGranted = false
        shown = false
        message = "광고를 불러오는 중입니다."
        let requestGeneration = generation
        MobileAds.shared.start()
        Task { @MainActor [weak self, adUnitID] in
            guard let self else { return }
            do {
                let ad = try await RewardedAd.load(with: adUnitID, request: Request())
                guard self.isPending, !self.disposed, self.generation == requestGeneration else { return }
                guard self.canRequestAds() else { self.finish(message: "광고 개인정보 선택이 완료되지 않았습니다."); return }
                guard let presenter = Self.presentingViewController() else {
                    self.finish(message: "광고를 표시할 수 있는 화면이 없습니다. 다시 시도해 주세요.")
                    return
                }
                self.rewardedAd = ad
                ad.fullScreenContentDelegate = self
                self.message = nil
                ad.present(from: presenter) { [weak self, weak ad] in
                    guard let self, let ad, !self.disposed,
                          self.generation == requestGeneration, self.canRequestAds(),
                          self.rewardedAd === ad, !self.rewardGranted else { return }
                    self.rewardGranted = true
                    if self.settings.grantSeoulUnlockAndSelect(theme) {
                        self.message = "서울 테마가 24시간 잠금 해제되었습니다."
                    } else {
                        self.message = "테마를 저장할 수 없습니다. 다시 시도해 주세요."
                    }
                }
            } catch {
                guard !self.disposed, self.generation == requestGeneration else { return }
                self.finish(message: "광고를 불러오지 못했습니다. 다시 시도해 주세요.")
            }
        }
        return true
    }

    func adWillPresentFullScreenContent(_ ad: FullScreenPresentingAd) {
        guard isCurrent(ad), !shown else { return }
        shown = true
        onFullscreenShown?()
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        guard isCurrent(ad) else { return }
        if shown { onFullscreenShown?() }
        finish(message: rewardGranted ? message : "광고 보상을 받지 못했습니다. 다시 시도해 주세요.")
    }

    func ad(
        _ ad: FullScreenPresentingAd,
        didFailToPresentFullScreenContentWithError error: Error
    ) {
        guard isCurrent(ad) else { return }
        finish(message: "광고를 표시하지 못했습니다. 다시 시도해 주세요.")
    }

    private func isCurrent(_ ad: FullScreenPresentingAd) -> Bool {
        guard !disposed, let current = rewardedAd else { return false }
        return (ad as AnyObject) === current
    }

    func invalidate() {
        generation &+= 1
        if rewardedAd == nil { isPending = false }
    }

    func close() {
        disposed = true
        generation &+= 1
        rewardedAd = nil
        isPending = false
        onFullscreenShown = nil
    }

    private static func resolvedAdUnitID() -> String? {
#if DEBUG
        return testAdUnitID
#else
        if (Bundle.main.object(forInfoDictionaryKey: "KKUseTestAds") as? Bool) == true {
            return testAdUnitID
        }
        if let raw = Bundle.main.object(forInfoDictionaryKey: "KKRewardedAdUnitID") as? String {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                return trimmed
            }
        }
        return nil
#endif
    }

    private static func hasValidApplicationIdentifier() -> Bool {
        if let raw = Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier") as? String {
            return !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        return false
    }

    private func finish(message: String?) {
        rewardedAd = nil
        isPending = false
        self.message = message
    }

    private static func presentingViewController() -> UIViewController? {
        return AppPresentationContext.presenter()
    }
}
