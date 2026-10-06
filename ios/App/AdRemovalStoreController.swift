import Foundation
import SharedUI
import StoreKit

/// One-time non-consumable ad removal. App target only; never compiled into the keyboard extension.
@available(iOS 16.0, *)
public final class AdRemovalStoreController: NSObject {
    private static let productID = "com.ghtnql.kkkeyboard.remove_ads"
    private static let ownedKey = "monetization.adRemoval.owned"

    public private(set) var state: SharedUI.AdRemovalState {
        didSet { onStateChanged?(state) }
    }
    public var onStateChanged: ((SharedUI.AdRemovalState) -> Void)?

    private var operationTask: Task<Void, Never>?
    private var updatesTask: Task<Void, Never>?
    private var operating = false
    private var closed = false
    private var entitlementRevision = 0

    public override init() {
        self.state = SharedUI.AdRemovalState(
            owned: false, purchaseAvailable: false, price: nil, busy: false, messageCode: nil)
        super.init()
        startUpdatesObserver()
        startLifecycleObserver()
        refresh()
    }

    // MARK: - Public (main UI thread)

    public func refresh() {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in self?.refresh() }
            return
        }
        guard !closed, !operating else { return }
        operating = true
        publish(owned: state.owned, purchaseAvailable: state.purchaseAvailable, price: state.price, busy: true, messageCode: state.messageCode)
        operationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer { self.finishOperation() }
            guard !self.closed else { return }
            await self.recompute(fetchPrice: true, successCode: nil)
            guard !self.closed else { return }
        }
    }

    public func purchase() {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in self?.purchase() }
            return
        }
        guard !closed, !operating, !state.owned else { return }
        operating = true
        publish(owned: state.owned, purchaseAvailable: state.purchaseAvailable, price: state.price, busy: true, messageCode: state.messageCode)
        operationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer { self.finishOperation() }
            guard !self.closed else { return }
            let products = try? await Product.products(for: [Self.productID])
            guard !Task.isCancelled, !self.closed else { return }
            guard let product = products?.first(where: { $0.id == Self.productID && $0.type == .nonConsumable }) else {
                self.publish(owned: self.state.owned, purchaseAvailable: false, price: nil, busy: false, messageCode: "unavailable")
                await self.recompute(fetchPrice: false, successCode: nil)
                guard !self.closed, !Task.isCancelled else { return }
                self.publish(owned: self.state.owned, purchaseAvailable: false, price: nil, busy: false, messageCode: "unavailable")
                return
            }
            self.publish(owned: self.state.owned, purchaseAvailable: true, price: product.displayPrice, busy: true, messageCode: nil)
            guard !self.closed, !Task.isCancelled else { return }
            let result: Product.PurchaseResult
            do {
                result = try await product.purchase()
            } catch {
                guard !self.closed else { return }
                self.publish(owned: self.state.owned, purchaseAvailable: true, price: self.productPrice(), busy: false, messageCode: "purchase_failed")
                return
            }
            guard !self.closed, !Task.isCancelled else { return }
            switch result {

            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    guard transaction.productID == Self.productID,
                          transaction.productType == .nonConsumable,
                          transaction.revocationDate == nil else {
                        self.publish(owned: self.state.owned, purchaseAvailable: true, price: self.productPrice(), busy: false, messageCode: "verification_failed")
                        return
                    }
                    self.entitlementRevision += 1
                    await transaction.finish()
                    guard !self.closed, !Task.isCancelled else { return }
                    await self.recompute(fetchPrice: true, successCode: "purchased")
                case .unverified:
                    self.publish(owned: self.state.owned, purchaseAvailable: true, price: self.productPrice(), busy: false, messageCode: "verification_failed")
                }
            case .userCancelled:
                self.publish(owned: self.state.owned, purchaseAvailable: true, price: self.productPrice(), busy: false, messageCode: "cancelled")
            case .pending:
                self.publish(owned: self.state.owned, purchaseAvailable: true, price: self.productPrice(), busy: false, messageCode: "pending")
            @unknown default:
                self.publish(owned: self.state.owned, purchaseAvailable: true, price: self.productPrice(), busy: false, messageCode: "purchase_failed")
            }
        }
    }

    public func restore() {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in self?.restore() }
            return
        }
        guard !closed, !operating else { return }
        operating = true
        publish(owned: state.owned, purchaseAvailable: state.purchaseAvailable, price: state.price, busy: true, messageCode: state.messageCode)
        operationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer { self.finishOperation() }
            guard !self.closed, !Task.isCancelled else { return }
            do {
                try await AppStore.sync()
            } catch {
                guard !self.closed else { return }
                self.publish(owned: self.state.owned, purchaseAvailable: self.state.purchaseAvailable, price: self.state.price, busy: false, messageCode: "connection_failed")
                return
            }
            guard !self.closed, !Task.isCancelled else { return }
            await self.recompute(fetchPrice: true, successCode: "restored")
        }
    }

    public func close() {
        closed = true
        operationTask?.cancel()
        updatesTask?.cancel()
        operationTask = nil
        updatesTask = nil
        operating = false
        onStateChanged = nil
        NotificationCenter.default.removeObserver(self)
    }

    deinit {
        operationTask?.cancel()
        updatesTask?.cancel()
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Private

    private func startUpdatesObserver() {
        updatesTask = Task { @MainActor [weak self] in
            for await update in Transaction.updates {
                guard let self, !self.closed, !Task.isCancelled else { break }
                self.entitlementRevision += 1
                if case .verified(let transaction) = update,
                   transaction.productID == Self.productID,
                   transaction.productType == .nonConsumable {
                    await transaction.finish()
                }
                guard !self.closed, !Task.isCancelled else { break }
                // Updates never grant directly; only the verified entitlement scan authorizes.
                await self.recompute(fetchPrice: false, successCode: nil)
                guard !self.closed else { break }
            }
        }
    }

    private func startLifecycleObserver() {
        // App target only. String-based name avoids UIKit/StoreKit imports elsewhere;
        // this file is never compiled into the keyboard extension.
        let name = Notification.Name("UIApplicationWillEnterForegroundNotification")
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleForeground),
            name: name,
            object: nil)
    }

    @objc private func handleForeground() {
        refresh()
    }

    @MainActor
    private func recompute(fetchPrice: Bool, successCode: String?) async {
        var price = state.price
        var available = state.purchaseAvailable
        if fetchPrice {
            do {
                let products = try await Product.products(for: [Self.productID])
                if Task.isCancelled || closed { return }
                if let product = products.first(where: { $0.id == Self.productID && $0.type == .nonConsumable }) {
                    price = product.displayPrice
                    available = true
                } else {
                    available = false
                    price = nil
                }
            } catch {
                // Product fetch failure must not block the entitlement scan below.
                available = false
                price = nil
            }
            if Task.isCancelled || closed { return }
        }
        // Scan again if a verified update arrives while this asynchronous snapshot is read.
        var owned = false
        var scannedRevision: Int
        repeat {
            scannedRevision = entitlementRevision
            owned = false
            for await entitlement in Transaction.currentEntitlements {
                if Task.isCancelled || closed { return }
                if case .verified(let transaction) = entitlement,
                   transaction.productID == Self.productID,
                   transaction.productType == .nonConsumable,
                   transaction.revocationDate == nil {
                    owned = true
                }
            }
            if Task.isCancelled || closed { return }
        } while scannedRevision != entitlementRevision
        writeSharedFlag(owned: owned)
        if let successCode, owned {
            publish(owned: owned, purchaseAvailable: available, price: price, busy: false, messageCode: successCode)
        } else if !owned, successCode != nil {
            publish(owned: false, purchaseAvailable: available, price: price, busy: false, messageCode: successCode == "restored" ? "no_purchase" : "verification_failed")
        } else if fetchPrice, !available {
            publish(owned: owned, purchaseAvailable: false, price: price, busy: false, messageCode: owned ? nil : "unavailable")
        } else {
            publish(owned: owned, purchaseAvailable: available, price: price, busy: false, messageCode: nil)
        }
    }

    private func writeSharedFlag(owned: Bool) {
        guard !closed, !Task.isCancelled,
              let defaults = KeyboardSharedDefaults.availableDefaults() else { return }
        defaults.set(owned, forKey: Self.ownedKey)
    }

    private func finishOperation() {
        operating = false
        operationTask = nil
        if !closed, state.busy {
            publish(owned: state.owned, purchaseAvailable: state.purchaseAvailable, price: state.price, busy: false, messageCode: state.messageCode)
        }
    }

    private func productPrice() -> String? {
        state.price
    }

    private func publish(owned: Bool, purchaseAvailable: Bool, price: String?, busy: Bool, messageCode: String?) {
        guard !closed else { return }
        state = SharedUI.AdRemovalState(
            owned: owned,
            purchaseAvailable: purchaseAvailable,
            price: price,
            busy: busy || operating,
            messageCode: messageCode)
    }
}
