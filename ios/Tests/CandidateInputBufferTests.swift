import XCTest

final class CandidateInputBufferTests: XCTestCase {
    func testTracksCommittedHangulAndCurrentComposition() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "아리", composing: "가"))
        XCTAssertEqual(buffer.current(composing: "가"), "아리가")
    }

    func testNonHangulCommitClearsToken() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "아리"))
        buffer.apply(HangulEdit(commit: " "))
        XCTAssertEqual(buffer.current(composing: ""), "")
    }

    func testBackspaceRemovesOnlyCommittedCharacter() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "아리"))
        buffer.removeCommittedCharacter()
        XCTAssertEqual(buffer.current(composing: ""), "아")
    }

    func testGeneratedJapaneseLookupLeavesTrackedHangulAvailableForContinuedInput() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "미등록", composing: "입"))

        let sourceBeforeLookup = buffer.current(composing: "입")
        XCTAssertFalse(JapaneseTransliterator.candidates(for: sourceBeforeLookup).isEmpty)
        XCTAssertEqual(buffer.current(composing: "입"), "미등록입")

        buffer.apply(HangulEdit(commit: "입", composing: "력"))
        XCTAssertEqual(buffer.current(composing: "력"), "미등록입력")
    }

    func testContinuesCandidateSourceAcrossOneCommittedSpace() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "오하요"))
        buffer.replaceCurrent(with: "오하요 ")

        XCTAssertEqual(buffer.current(composing: "고"), "오하요 고")
    }

    func testLookupSkipsStringConstructionAfterDictionaryMaximumWhilePreservingSource() {
        let buffer = CandidateInputBuffer()
        let longToken = String(repeating: "가", count: JapaneseTransliterator.maxInputLength + 32)
        buffer.apply(HangulEdit(commit: longToken))

        XCTAssertNil(
            buffer.currentForLookup(
                composing: "나",
                maxLength: JapaneseTransliterator.maxInputLength
            )
        )
        XCTAssertEqual(buffer.current(composing: "나"), longToken + "나")
    }

    func testLookupLengthTrackingStaysCorrectAfterBackspaceAndClear() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "가나다"))

        XCTAssertNil(buffer.currentForLookup(composing: "라", maxLength: 3))

        buffer.removeCommittedCharacter()
        XCTAssertEqual(buffer.currentForLookup(composing: "라", maxLength: 3), "가나라")

        buffer.apply(HangulEdit(commit: " "))
        XCTAssertEqual(buffer.currentForLookup(composing: "마", maxLength: 1), "마")
    }

    func testEmptyCompositionDiscardsCommittedCandidatePrefix() {
        let buffer = CandidateInputBuffer()
        buffer.apply(HangulEdit(commit: "아"))

        XCTAssertNil(buffer.currentForLookup(composing: "", maxLength: 8))
        XCTAssertEqual(buffer.current(composing: "리가토"), "리가토")
    }
}
