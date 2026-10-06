import XCTest

final class SharedUIAppLaunchTests: XCTestCase {
    func testComposeOnboardingOpensHome() {
        let app = XCUIApplication()
        app.launch()

        let start = app.buttons["연습 시작"]
        XCTAssertTrue(start.waitForExistence(timeout: 20), "Compose onboarding should render")
        start.tap()

        XCTAssertTrue(app.staticTexts["오늘도 한 글자씩"].waitForExistence(timeout: 10), "Compose home should render")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Shared UI home"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testRainSceneOpens() {
        let app = XCUIApplication()
        app.launch()

        let start = app.buttons["연습 시작"]
        XCTAssertTrue(start.waitForExistence(timeout: 20))
        start.tap()

        let rain = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Rain")).firstMatch
        XCTAssertTrue(rain.waitForExistence(timeout: 10))
        rain.tap()

        let play = app.buttons["시작"]
        XCTAssertTrue(play.waitForExistence(timeout: 10))
        play.tap()
        XCTAssertTrue(app.buttons["게임 끝내기"].waitForExistence(timeout: 10))

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Shared UI Rain scene"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testCafeSceneOpens() {
        let app = XCUIApplication()
        app.launch()

        let start = app.buttons["연습 시작"]
        XCTAssertTrue(start.waitForExistence(timeout: 20))
        start.tap()

        let cafe = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Cafe")).firstMatch
        XCTAssertTrue(cafe.waitForExistence(timeout: 10))
        cafe.tap()

        let play = app.buttons["시작"]
        XCTAssertTrue(play.waitForExistence(timeout: 10))
        play.tap()
        XCTAssertTrue(app.buttons["카페 나가기"].waitForExistence(timeout: 10))

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Shared UI Cafe scene"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testPhraseManagerOpensFromSharedSettings() {
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["연습 시작"]
        XCTAssertTrue(start.waitForExistence(timeout: 20))
        start.tap()

        let settings = app.buttons["설정"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        XCTAssertTrue(app.staticTexts["세로 화면"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["가로 화면"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Shared layout settings"
        attachment.lifetime = .keepAlways
        add(attachment)
        let phrases = app.buttons["상용구 관리"]
        XCTAssertTrue(phrases.waitForExistence(timeout: 10))
        for _ in 0..<3 {
            if phrases.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(phrases.isHittable)
        phrases.tap()
        let afterTap = XCTAttachment(screenshot: app.screenshot())
        afterTap.name = "After opening phrase manager"
        afterTap.lifetime = .keepAlways
        add(afterTap)
        print("Phrase manager navigation hierarchy: \(app.debugDescription)")
        XCTAssertTrue(app.buttons["새 상용구"].waitForExistence(timeout: 10))
    }
}
