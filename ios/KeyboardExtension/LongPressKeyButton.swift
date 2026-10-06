import UIKit

/// Release commits exactly one short tap. A hold exclusively commits its selected choice.
final class LongPressKeyButton: UIButton {
    static let tapEvent = UIControl.Event(rawValue: 1 << 24)
    var choicesProvider: (() -> [String])?
    var shouldDeferTap: (() -> Bool)?
    private var deferredTap = true
    private var active = false
    private var touchOrigin = CGPoint.zero
    var onChoice: ((String) -> Void)?
    private var holdTimer: Timer?
    private var holdActivated = false
    private var choices: [String] = []
    private var selectedChoiceIndex = 0
    private var popup: UIStackView?
    private let hint = UILabel()

    func refreshHint() {
        let choices = choicesProvider?() ?? []
        if hint.superview == nil {
            hint.font = .systemFont(ofSize: 9)
            hint.textColor = .secondaryLabel
            hint.isUserInteractionEnabled = false
            addSubview(hint)
        }
        hint.text = choices.first
        hint.sizeToFit()
        accessibilityHint = choices.isEmpty ? nil : "Hold and slide to choose: " + choices.joined(separator: ", ")
        accessibilityCustomActions = choices.map { choice in
            UIAccessibilityCustomAction(name: choice) { [weak self] _ in
                guard let self, self.isEnabled, let handler = self.onChoice else { return false }
                self.cleanup()
                handler(choice)
                return true
            }
        }
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { cleanup() }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        hint.frame.origin = CGPoint(x: max(2, bounds.width - hint.frame.width - 4), y: 2)
    }
    override func sendActions(for controlEvents: UIControl.Event) {
        if controlEvents.contains(Self.tapEvent), !isEnabled { return }
        super.sendActions(for: controlEvents)
    }
    override func beginTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        guard choicesProvider != nil else { return super.beginTracking(touch, with: event) }
        cleanup()
        deferredTap = shouldDeferTap?() ?? true
        if !deferredTap {
            sendActions(for: Self.tapEvent)
            return true
        }
        active = true
        touchOrigin = touch.location(in: self)
        holdActivated = false
        isHighlighted = true
        choices = choicesProvider?() ?? []
        if !choices.isEmpty {
            holdTimer = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: false) { [weak self] _ in self?.showChoices() }
        }
        return true
    }
    private func showChoices() {
        guard active, isEnabled, !choices.isEmpty else { return }
        guard let host = superview?.window ?? superview else { return }
        holdActivated = true
        selectedChoiceIndex = 0
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.backgroundColor = .secondarySystemBackground
        stack.layer.cornerRadius = 8
        stack.layer.borderWidth = 1
        stack.layer.borderColor = UIColor.separator.cgColor
        stack.isUserInteractionEnabled = false
        let popupFont = UIFont.systemFont(ofSize: 17)
        let horizontalPadding: CGFloat = 16
        let widest = choices.map {
            ($0 as NSString).size(withAttributes: [.font: popupFont]).width
        }.max() ?? 0
        let desiredCell = max(48, widest + horizontalPadding)
        for choice in choices {
            let label = UILabel()
            label.text = choice
            label.font = popupFont
            label.textAlignment = .center
            label.textColor = .label
            label.numberOfLines = 1
            label.lineBreakMode = .byClipping
            label.adjustsFontSizeToFitWidth = true
            label.minimumScaleFactor = 0.6
            stack.addArrangedSubview(label)
        }
        let keyFrame = convert(bounds, to: host)
        let width = min(host.bounds.width - 8, desiredCell * CGFloat(choices.count))
        stack.frame = CGRect(x: min(max(4, keyFrame.midX - width / 2), host.bounds.width - width - 4),
                             y: max(4, keyFrame.minY - 48), width: width, height: 44)
        host.addSubview(stack)
        popup = stack
        markSelection()
    }
    private func markSelection() {
        popup?.arrangedSubviews.enumerated().forEach { index, label in
            label.backgroundColor = index == selectedChoiceIndex ? UIColor.systemBlue.withAlphaComponent(0.25) : .clear
        }
    }
    override func continueTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        guard choicesProvider != nil else { return super.continueTracking(touch, with: event) }
        if !deferredTap { return true }
        guard active else { return false }
        if holdActivated, let popup {
            let local = touch.location(in: self)
            guard hypot(local.x - touchOrigin.x, local.y - touchOrigin.y) > 12 else { return true }
            let point = touch.location(in: popup)
            selectedChoiceIndex = min(choices.count - 1, max(0, Int(point.x / (popup.bounds.width / CGFloat(choices.count)))))
            markSelection()
            return true
        }
        let location = touch.location(in: self)
        if !bounds.insetBy(dx: -12, dy: -12).contains(location) {
            cleanup()
            return false
        }
        return true
    }
    override func endTracking(_ touch: UITouch?, with event: UIEvent?) {
        guard choicesProvider != nil else { super.endTracking(touch, with: event); return }
        guard active, isEnabled else { cleanup(); return }
        guard deferredTap else { cleanup(); return }
        let wasHeld = holdActivated
        let result = wasHeld && choices.indices.contains(selectedChoiceIndex) ? choices[selectedChoiceIndex] : nil
        let inside = touch.map { bounds.contains($0.location(in: self)) } ?? false
        cleanup()
        if wasHeld {
            if let result { onChoice?(result) }
        } else if inside {
            sendActions(for: choicesProvider == nil ? .touchDown : LongPressKeyButton.tapEvent)
        }
    }
    override func cancelTracking(with event: UIEvent?) {
        cleanup()
        super.cancelTracking(with: event)
    }
    private func cleanup() {
        holdTimer?.invalidate()
        holdTimer = nil
        popup?.removeFromSuperview()
        popup = nil
        active = false
        holdActivated = false
        choices = []
        selectedChoiceIndex = 0
        isHighlighted = false
    }
    override func accessibilityActivate() -> Bool {
        guard isEnabled else { return false }
        cleanup()
        sendActions(for: choicesProvider == nil ? .touchDown : LongPressKeyButton.tapEvent)
        return true
    }
}
