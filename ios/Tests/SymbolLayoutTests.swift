import XCTest

final class SymbolLayoutTests: XCTestCase {
    func testSymbolLayoutMatchesHangulCharacterButtonCount() {
        let hangulCount = TwoBeolsikLayout.characterRows.flatMap { $0 }.count + TwoBeolsikLayout.bottomRow.count
        XCTAssertEqual(SymbolLayout.flattened.count, hangulCount + 1)
        XCTAssertEqual(SymbolLayout.pages.count, 3)
        for page in SymbolLayout.pages {
            XCTAssertEqual(page.top.count, 10)
            XCTAssertEqual(page.middle.count, 10)
            XCTAssertEqual(page.bottom.count, 7)
        }
    }

    func testSymbolLayoutContainsDigitsAndCommonPunctuation() {
        XCTAssertEqual(SymbolLayout.characterRows[0], ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"])
        XCTAssertTrue(SymbolLayout.flattened.contains("@"))
        XCTAssertTrue(SymbolLayout.flattened.contains("?"))
        XCTAssertTrue(SymbolLayout.flattened.contains("!"))
    }

    func testExpandedSymbolsKeepFrequentArrowsOnSecondPageAndDecorationsOnThird() {
        let symbols = SymbolLayout.pages.flatMap { $0.flattened }
        for symbol in ["←", "→", "↑", "↓"] {
            XCTAssertTrue(SymbolLayout.pages[1].flattened.contains(symbol), "Frequent arrow must be on page 2: \(symbol)")
        }
        for symbol in ["♡", "♥", "☆", "★", "※", "・", "々", "〒", "✓"] {
            XCTAssertTrue(SymbolLayout.pages[2].flattened.contains(symbol), "Missing page 3 symbol \(symbol)")
        }
        for symbol in ["⇐", "⇒", "⇑", "⇓"] {
            XCTAssertFalse(symbols.contains(symbol), "Redundant arrow should be removed: \(symbol)")
        }
    }
}
