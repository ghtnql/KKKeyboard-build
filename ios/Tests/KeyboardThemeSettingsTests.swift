import XCTest

final class KeyboardThemeSettingsTests: XCTestCase {
    private var suiteName: String!
    private var defaults: UserDefaults!
    private var settings: KeyboardThemeSettings!

    override func setUp() {
        super.setUp()
        suiteName = "KeyboardThemeSettingsTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
        settings = KeyboardThemeSettings(defaults: defaults)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        settings = nil
        defaults = nil
        suiteName = nil
        super.tearDown()
    }

    func testDefaultThemeIsSystem() {
        XCTAssertEqual(settings.read(), .system)
    }

    func testStalePaidEntitlementCannotBypassRewardedThemeExpiry() {
        defaults.set(true, forKey: "monetization.adRemoval.owned")
        let now = Date(timeIntervalSince1970: 1_000)
        let settings = KeyboardThemeSettings(defaults: defaults, verifiedAdRemovalOwned: true)
        XCTAssertEqual(settings.remainingSeoulUnlockMillis(now: now), 0)
        settings.set(.seoulDay)
        XCTAssertEqual(settings.read(), .system)
        XCTAssertTrue(settings.grantSeoulUnlockAndSelect(.seoulDay, now: now))
        XCTAssertEqual(settings.remainingSeoulUnlockMillis(now: now), 24 * 60 * 60 * 1_000)
        XCTAssertEqual(settings.remainingSeoulUnlockMillis(now: now.addingTimeInterval(24 * 60 * 60)), 0)
    }

    func testAvailableThemeRoundTripAndSeoulLock() {
        settings.set(.dark)
        XCTAssertEqual(settings.read(), .dark)
        settings.set(.seoulDay)
        XCTAssertEqual(settings.read(), .dark)
        XCTAssertTrue(settings.grantSeoulUnlockAndSelect(.seoulDay))
        XCTAssertEqual(settings.read(), .seoulDay)
    }
}

final class KeyboardThemeCatalogTests: XCTestCase {
    private let validPack = """
        {"version":1,"themes":[
        {"id":"system","titleKo":"시스템","kind":"system"},
        {"id":"basic_light","titleKo":"라이트","kind":"palette","palette":{"keyboardSurface":"#ECEEF1","keySurface":"#FFFFFF","controlSurface":"#DCE1E7","border":"#C7CDD4","text":"#20242A","accent":"#216B57","onAccent":"#FFFFFF","ripple":"#30216B57","flickHint":"#52606A","flickSelected":"#D7EBE3"}},
        {"id":"basic_dark","titleKo":"다크","kind":"palette","palette":{"keyboardSurface":"#161A1D","keySurface":"#2B3136","controlSurface":"#3A4248","border":"#56616A","text":"#F5F7F8","accent":"#5AC8A8","onAccent":"#0B211A","ripple":"#405AC8A8","flickHint":"#B4BFC7","flickSelected":"#375B51"}},
        {"id":"jeju_default","titleKo":"제주","kind":"image","image":"jeju_default.jpg","scrimAlpha":45,"palette":{"keyboardSurface":"#1A2E1F","keySurface":"#2A422F","controlSurface":"#3A5A41","border":"#6B8F71","text":"#F4F7F4","accent":"#FDB022","onAccent":"#231303","ripple":"#30FDB022","flickHint":"#B7C9B7","flickSelected":"#3F5A36"}}
        ]}
        """

    func testParsesValidPackAndResolvesSystem() throws {
        let catalog = try KeyboardThemeCatalog(data: Data(validPack.utf8))
        XCTAssertEqual(catalog.entries.map(\.id), ["system", "basic_light", "basic_dark", "jeju_default"])
        XCTAssertEqual(catalog.resolvedEntry(id: "system", systemDark: false)?.id, "basic_light")
        XCTAssertEqual(catalog.resolvedEntry(id: "system", systemDark: true)?.id, "basic_dark")
        XCTAssertEqual(catalog.resolvedEntry(id: "jeju_default", systemDark: false)?.titleKo, "제주")
        XCTAssertNil(catalog.resolvedEntry(id: "bogus", systemDark: false))
        let palette = try XCTUnwrap(catalog.resolvedEntry(id: "jeju_default", systemDark: false)?.palette)
        XCTAssertNotNil(palette.components(for: \.accent))
    }

    func testRejectsInvalidPack() {
        XCTAssertThrowsError(try KeyboardThemeCatalog(data: Data("{\"version\":2,\"themes\":[]}".utf8)))
        XCTAssertThrowsError(try KeyboardThemeCatalog(data: Data("{\"version\":1,\"themes\":[]}".utf8)))
        XCTAssertThrowsError(try KeyboardThemeCatalog(data: Data("{\"version\":1,\"themes\":[{\"id\":\"x\",\"titleKo\":\"x\",\"kind\":\"image\"}]}".utf8)))
    }
}
