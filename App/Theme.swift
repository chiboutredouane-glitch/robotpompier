import UIKit

enum Theme {
    static let bg = UIColor(red: 16/255, green: 23/255, blue: 34/255, alpha: 1)
    static let panel = UIColor(red: 25/255, green: 35/255, blue: 49/255, alpha: 1)
    static let border = UIColor(red: 47/255, green: 66/255, blue: 85/255, alpha: 1)
    static let muted = UIColor(red: 158/255, green: 177/255, blue: 198/255, alpha: 1)
    static let yellow = UIColor(red: 1, green: 199/255, blue: 32/255, alpha: 1)
    static let cyan = UIColor(red: 23/255, green: 217/255, blue: 236/255, alpha: 1)
    static let green = UIColor(red: 77/255, green: 224/255, blue: 139/255, alpha: 1)
    static let red = UIColor(red: 1, green: 103/255, blue: 93/255, alpha: 1)
    static func label(_ text: String, size: CGFloat = 14, color: UIColor = .white) -> UILabel {
        let v = UILabel(); v.text = text; v.font = .systemFont(ofSize: size, weight: .medium); v.textColor = color
        v.numberOfLines = 2; v.adjustsFontSizeToFitWidth = true; v.minimumScaleFactor = 0.8; return v
    }
    static func button(_ text: String, color: UIColor = .white) -> UIButton {
        let b = UIButton(type: .system); b.setTitle(text, for: .normal); b.setTitleColor(color, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold); b.titleLabel?.numberOfLines = 2
        b.backgroundColor = panel; b.layer.cornerRadius = 10; b.layer.borderWidth = 1; b.layer.borderColor = border.cgColor
        b.contentEdgeInsets = UIEdgeInsets(top: 4, left: 8, bottom: 4, right: 8)
        b.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true; return b
    }
    static func stack(_ views: [UIView], axis: NSLayoutConstraint.Axis = .horizontal, spacing: CGFloat = 6) -> UIStackView {
        let s = UIStackView(arrangedSubviews: views); s.axis = axis; s.spacing = spacing; s.isMultipleTouchEnabled = true; return s
    }
    static func panel(_ content: UIView, caption: String) -> UIView {
        let outer = UIView(); outer.backgroundColor = panel; outer.layer.cornerRadius = 12
        outer.layer.borderColor = border.cgColor; outer.layer.borderWidth = 1
        let title = label(caption, size: 10, color: muted); title.heightAnchor.constraint(equalToConstant: 18).isActive = true
        let stack = stack([title, content], axis: .vertical, spacing: 3)
        stack.translatesAutoresizingMaskIntoConstraints = false; outer.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: outer.leadingAnchor, constant: 7), stack.trailingAnchor.constraint(equalTo: outer.trailingAnchor, constant: -7), stack.topAnchor.constraint(equalTo: outer.topAnchor, constant: 5), stack.bottomAnchor.constraint(equalTo: outer.bottomAnchor, constant: -6)])
        return outer
    }
}

/// Each button owns its touch. Losing the touch or leaving its bounds releases exactly once.
final class RemoteButton: UIButton {
    var onDown: (() -> Void)?
    var onUp: (() -> Void)?
    var onClick: (() -> Void)?
    var mode: ButtonMode = .hold
    private var touchHeld = false
    override init(frame: CGRect) {
        super.init(frame: frame)
        isExclusiveTouch = false; isMultipleTouchEnabled = false
        layer.cornerRadius = 12; layer.borderWidth = 1; layer.borderColor = Theme.border.cgColor
        titleLabel?.numberOfLines = 3; titleLabel?.textAlignment = .center
        titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel?.adjustsFontSizeToFitWidth = true; titleLabel?.minimumScaleFactor = 0.8
        addTarget(self, action: #selector(down), for: .touchDown)
        addTarget(self, action: #selector(releaseInside), for: .touchUpInside)
        addTarget(self, action: #selector(cancel), for: [.touchCancel, .touchUpOutside, .touchDragExit])
    }
    required init?(coder: NSCoder) { fatalError("Programmatic UI") }
    @objc private func down() { guard isEnabled, !touchHeld else { return }; touchHeld = true; if mode == .hold { onDown?() } }
    @objc private func releaseInside() {
        guard touchHeld else { return }; touchHeld = false
        if mode == .hold { onUp?() } else if isEnabled { onClick?() }
    }
    @objc func cancel() { guard touchHeld else { return }; touchHeld = false; if mode == .hold { onUp?() }; isHighlighted = false }
    func clearTouch() { touchHeld = false; isHighlighted = false }
    override func accessibilityActivate() -> Bool { guard isEnabled else { return false }; onClick?(); return true }
}
