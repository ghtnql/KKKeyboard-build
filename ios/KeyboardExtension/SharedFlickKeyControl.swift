import UIKit
import SharedUI

/// Native touches only; key mapping and direction hysteresis come from sharedCore.
final class SharedFlickKeyControl: UIControl {
    private let key: FlickKey
    private let onInput: (String) -> Void
    private let bridge = FlickLayoutBridge()
    private var origin = CGPoint.zero
    private var direction = FlickDirection.center
    private var activeTouch: UITouch?
    var threshold: CGFloat = 20
    private let centerLabel = UILabel()
    private var hints: [(UILabel, FlickDirection)] = []

    init(key: FlickKey, onInput: @escaping (String) -> Void) {
        self.key = key
        self.onInput = onInput
        super.init(frame: .zero)
        backgroundColor = .secondarySystemBackground
        layer.cornerRadius = 6
        isAccessibilityElement = true
        accessibilityLabel = key.center
        accessibilityTraits = .keyboardKey
        centerLabel.text = key.center; centerLabel.textAlignment = .center
        centerLabel.font = .systemFont(ofSize: 23)
        addSubview(centerLabel)
        for direction in [FlickDirection.left, .up, .right, .down] {
            guard let text = key.label(direction: direction) else { continue }
            let label = UILabel(); label.text = text; label.textAlignment = .center
            label.font = .systemFont(ofSize: 11); label.textColor = .secondaryLabel
            addSubview(label); hints.append((label, direction))
        }
    }
    func applyPalette(_ palette: KeyboardThemePalette) {
        func color(_ key: KeyPath<KeyboardThemePalette, String>) -> UIColor {
            guard let c = palette.components(for: key) else { return .label }
            return UIColor(red: c.r, green: c.g, blue: c.b, alpha: c.a)
        }
        backgroundColor = color(\.keySurface)
        centerLabel.textColor = color(\.text)
        hints.forEach { $0.0.textColor = color(\.flickHint) }
        layer.borderColor = color(\.border).cgColor
        layer.borderWidth = 0.7
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) unavailable") }
    override func layoutSubviews() {
        super.layoutSubviews()
        centerLabel.frame = CGRect(x: bounds.width * 0.25, y: bounds.height * 0.2, width: bounds.width * 0.5, height: bounds.height * 0.6)
        for (label, direction) in hints {
            let point: CGPoint
            switch direction {
            case .left: point = CGPoint(x: 0.13, y: 0.5)
            case .right: point = CGPoint(x: 0.87, y: 0.5)
            case .up: point = CGPoint(x: 0.5, y: 0.12)
            default: point = CGPoint(x: 0.5, y: 0.88)
            }
            label.frame = CGRect(x: bounds.width * point.x - 12, y: bounds.height * point.y - 7, width: 24, height: 14)
        }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard activeTouch == nil, let touch = touches.first else { return }
        activeTouch = touch; origin = touch.location(in: self); direction = .center
        alpha = 0.65
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        let point = touch.location(in: self)
        direction = bridge.resolve(dx: Float(point.x - origin.x), dy: Float(point.y - origin.y), threshold: Float(threshold), previous: direction)
        centerLabel.text = key.label(direction: direction) ?? "×"
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        touchesMoved(touches, with: event)
        if let label = key.label(direction: direction) { onInput(label) }
        resetTouch()
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { resetTouch() }
    override func accessibilityActivate() -> Bool { onInput(key.center); return true }
    private func resetTouch() { activeTouch = nil; direction = .center; centerLabel.text = key.center; alpha = 1 }
}
