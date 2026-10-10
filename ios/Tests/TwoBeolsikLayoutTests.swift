import XCTest

final class TwoBeolsikLayoutTests: XCTestCase {
    func testShiftVariantsMatchKoreanDoubleJamo() {
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅂ", shifted: true), "ㅃ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅈ", shifted: true), "ㅉ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㄷ", shifted: true), "ㄸ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㄱ", shifted: true), "ㄲ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅅ", shifted: true), "ㅆ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅐ", shifted: true), "ㅒ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅔ", shifted: true), "ㅖ")
    }

    func testShiftLeavesKeysWithoutVariantUnchanged() {
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅁ", shifted: true), "ㅁ")
        XCTAssertEqual(TwoBeolsikLayout.label(for: "ㅏ", shifted: true), "ㅏ")
        XCTAssertFalse(TwoBeolsikLayout.hasShiftVariant("ㅁ"))
        XCTAssertTrue(TwoBeolsikLayout.hasShiftVariant("ㅂ"))
    }

    func testUnshiftedLabelsRemainStable() {
        for row in TwoBeolsikLayout.characterRows + [TwoBeolsikLayout.bottomRow] {
            for label in row {
                XCTAssertEqual(TwoBeolsikLayout.label(for: label, shifted: false), label)
            }
        }
    }
}
