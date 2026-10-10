import SharedUI
import XCTest

final class SharedCoreInteropTests: XCTestCase {
    func testComposesHangulThroughKotlinFramework() {
        let composer = HangulProbe()
        XCTAssertEqual(composer.compose(initialIndex: 0, vowelIndex: 0, finalIndex: 0), "가")
        XCTAssertEqual(composer.compose(initialIndex: 0, vowelIndex: 0, finalIndex: 1), "각")
    }
}
