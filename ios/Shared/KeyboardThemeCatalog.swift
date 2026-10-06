import Foundation

struct KeyboardThemePalette: Decodable, Equatable {
    let keyboardSurface: String
    let keySurface: String
    let controlSurface: String
    let border: String
    let text: String
    let accent: String
    let onAccent: String
    let ripple: String
    let flickHint: String
    let flickSelected: String

    func components(for keyPath: KeyPath<KeyboardThemePalette, String>) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat)? {
        return Self.rgba(self[keyPath: keyPath])
    }

    static func rgba(_ hex: String) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat)? {
        guard hex.hasPrefix("#") else { return nil }
        let body = String(hex.dropFirst())
        let argb: String
        if body.count == 6 {
            argb = "FF" + body
        } else if body.count == 8 {
            argb = body
        } else {
            return nil
        }
        var number: UInt64 = 0
        guard Scanner(string: argb).scanHexInt64(&number) else { return nil }
        return (
            r: CGFloat((number >> 16) & 0xFF) / 255,
            g: CGFloat((number >> 8) & 0xFF) / 255,
            b: CGFloat(number & 0xFF) / 255,
            a: CGFloat((number >> 24) & 0xFF) / 255
        )
    }
}

struct KeyboardThemeEntry: Decodable {
    let id: String
    let titleKo: String
    let kind: String
    let unlock: String?
    let image: String?
    let scrimAlpha: Int?
    let palette: KeyboardThemePalette?
    let available: Bool?
    let previewAsset: String?

    var isAvailable: Bool { available ?? true }
}

final class KeyboardThemeCatalog {
    private struct Pack: Decodable {
        let version: Int
        let themes: [KeyboardThemeEntry]
    }

    enum InvalidPack: Error { case invalidData }

    static let shared: KeyboardThemeCatalog = {
        guard let url = Bundle(for: KeyboardThemeCatalog.self).url(forResource: "themes", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let catalog = try? KeyboardThemeCatalog(data: data) else {
            assertionFailure("Missing or invalid bundled theme catalog")
            return KeyboardThemeCatalog(entries: [])
        }
        return catalog
    }()

    let entries: [KeyboardThemeEntry]

    init(data: Data) throws {
        let pack = try JSONDecoder().decode(Pack.self, from: data)
        guard pack.version == 1,
              !pack.themes.isEmpty,
              Set(pack.themes.map(\.id)).count == pack.themes.count,
              pack.themes.allSatisfy({ Self.isValid($0) }) else { throw InvalidPack.invalidData }
        self.entries = pack.themes
    }

    private init(entries: [KeyboardThemeEntry]) {
        self.entries = entries
    }

    func resolvedEntry(id: String, systemDark: Bool) -> KeyboardThemeEntry? {
        if id == "system" {
            let fallback = systemDark ? "basic_dark" : "basic_light"
            return entries.first { $0.id == fallback }
        }
        return entries.first { $0.id == id && $0.isAvailable }
    }

    func imageURL(for entry: KeyboardThemeEntry) -> URL? {
        guard let image = entry.image, !image.isEmpty else { return nil }
        let name = (image as NSString).deletingPathExtension
        let ext = (image as NSString).pathExtension
        return Bundle(for: KeyboardThemeCatalog.self).url(
            forResource: name, withExtension: ext.isEmpty ? nil : ext
        )
    }

    private static func isValid(_ theme: KeyboardThemeEntry) -> Bool {
        guard !theme.id.isEmpty, !theme.titleKo.isEmpty,
              theme.kind == "system" || theme.kind == "palette" || theme.kind == "image" else { return false }
        if let unlock = theme.unlock, unlock != "free" && unlock != "launch_set" { return false }
        if theme.kind == "system" {
            return theme.palette == nil
        }
        guard let palette = theme.palette else { return false }
        let colors = Mirror(reflecting: palette).children.compactMap { $0.value as? String }
        guard colors.count == 10, colors.allSatisfy({ KeyboardThemePalette.rgba($0) != nil }) else { return false }
        if theme.kind == "image" {
            guard let image = theme.image, !image.isEmpty,
                  let scrim = theme.scrimAlpha, (0...100).contains(scrim) else { return false }
        }
        return true
    }
}
