import Foundation
import UIKit
import UserMessagingPlatform

/// App-only UMP consent controller. Never initializes or starts ads.
///
/// Contract: call `refresh()` once per app launch, then read `canRequestAds`
/// on every ad request. Privacy options UI only when
/// `privacyOptionsRequired`. No locally persisted consent boolean.
/// All entry points must run on the main UI thread.
final class AdConsentController {

    var onChanged: (() -> Void)?

    private var closed = false
    private var didRequestThisLaunch = false
    private var updatingInformation = false
    private var requiredFormPending = false
    private var formTask: Task<Void, Never>?

    var canRequestAds: Bool {
        ConsentInformation.shared.canRequestAds
    }

    var privacyOptionsRequired: Bool {
        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    /// Requests consent info update once per launch, then presents the
    /// required form from the top active window root. If no root is
    /// available yet, returns without marking the launch requested so a
    /// later `refresh()` can retry. Notifies `onChanged` on every
    /// completion path (failure reports the SDK prior status).
    func refresh() {
        guard Thread.isMainThread, !closed else { return }
        guard formTask == nil, !updatingInformation else { return }
        if didRequestThisLaunch {
            // SDK info already requested: retry only the form with a fresh root.
            presentIfRequired()
            return
        }
        guard AppPresentationContext.rootViewController() != nil else {
            // Defer: allow refresh retry once a window root exists.
            return
        }
        didRequestThisLaunch = true
        updatingInformation = true
        ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { [weak self] error in
            Task { @MainActor [weak self] in
                guard let self, !self.closed else { return }
                self.updatingInformation = false
                if error != nil { self.onChanged?(); return }
                self.requiredFormPending = true
                self.presentIfRequired()
            }
        }
    }

    /// Presents the privacy options form. No-op unless the requirement
    /// status is `.required`, no form task is already running, and a valid
    /// presenter exists.
    func showPrivacyOptions() {
        guard Thread.isMainThread, !closed else { return }
        guard formTask == nil, !updatingInformation else { return }
        guard privacyOptionsRequired else { return }
        guard let root = AppPresentationContext.rootViewController() else { return }
        formTask = Task { @MainActor [weak self] in
            guard let self, !self.closed else { return }
            do {
                try await ConsentForm.presentPrivacyOptionsForm(from: root)
            } catch {
                // Fall through: notify so host re-reads SDK prior status.
            }
            self.finishFormTaskAndNotify()
        }
    }

    /// Cancels any in-flight form task and stops callbacks.
    func close() {
        closed = true
        formTask?.cancel()
        formTask = nil
        onChanged = nil
    }

    // MARK: - Private

    private func presentIfRequired() {
        guard Thread.isMainThread, !closed, requiredFormPending else { return }
        guard formTask == nil else { return }
        guard let root = AppPresentationContext.rootViewController() else {
            // Presenter temporarily missing: keep formTask nil so a later
            // refresh() retries the form without repeating SDK consent info.
            // Notify so the host re-reads current SDK status.
            onChanged?()
            return
        }
        formTask = Task { @MainActor [weak self] in
            guard let self, !self.closed else { return }
            do {
                try await ConsentForm.loadAndPresentIfRequired(from: root)
            } catch {
                // Fall through: notify so host re-reads SDK prior status.
            }
            self.requiredFormPending = false
            self.finishFormTaskAndNotify()
        }
    }

    private func finishFormTaskAndNotify() {
        formTask = nil
        guard !closed else { return }
        onChanged?()
    }

}
