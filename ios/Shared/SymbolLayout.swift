enum SymbolLayout {
    struct Page {
        let top: [String]
        let middle: [String]
        let bottom: [String]
        var flattened: [String] { top + middle + bottom }
    }
    // Match the Android symbol banks, including the tenth middle-row key.
    static let pages: [Page] = [
        Page(top: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
             middle: ["@", "#", "$", "%", "&", "-", "+", "(", ")", "/"],
             bottom: ["*", "\"", "'", ":", ";", "!", "?"]),
        Page(top: ["[", "]", "{", "}", "<", ">", "←", "↑", "↓", "→"],
             middle: ["_", "\\", "|", "=", "^", "~", "`", "¥", "×", "÷"],
             bottom: ["「", "」", "、", "。", "ー", "…", "♪"]),
        Page(top: ["♡", "♥", "☆", "★", "○", "●", "□", "■", "△", "▲"],
             middle: ["▽", "▼", "↔", "↕", "€", "£", "•", "°", "©", "®"],
             bottom: ["※", "・", "々", "〒", "✓", "『", "』"]),
    ]
    static var characterRows: [[String]] { [pages[0].top, pages[0].middle] }
    static var bottomRow: [String] { pages[0].bottom }
    static var flattened: [String] { pages[0].flattened }
}
