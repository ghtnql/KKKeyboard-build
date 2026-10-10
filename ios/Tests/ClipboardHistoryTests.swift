import XCTest

final class ClipboardHistoryTests: XCTestCase {
    private var defaults: UserDefaults!
    private var suite: String!
    private var store: ClipboardHistoryStore!
    override func setUp() {
        super.setUp()
        suite = "ClipboardTests." + UUID().uuidString
        defaults = UserDefaults(suiteName: suite)!
        store = ClipboardHistoryStore(defaults: defaults)
    }
    override func tearDown() {
        defaults.removePersistentDomain(forName: suite)
        super.tearDown()
    }
    func testDedupeRecencyPersistenceAndExactUnicode() {
        store.save(text: "a"); store.save(text: "b"); store.save(text: "a")
        XCTAssertEqual(store.list().map(\.text), ["a", "b"])
        store.save(text: "\u{00E9}"); store.save(text: "e\u{0301}")
        XCTAssertEqual(store.list().count, 4)
        XCTAssertEqual(ClipboardHistoryStore(defaults: defaults).list(), store.list())
        XCTAssertFalse(store.use(text: "missing"))
    }
    func testCapsAndUnpinWhenTargetFollowsEvictedEntry() {
        store.save(text: "old-unpinned")
        store.save(text: "pin"); store.togglePin(text: "pin")
        for i in 0..<30 { store.save(text: "recent\(i)") }
        XCTAssertEqual(store.list().count, 31)
        XCTAssertTrue(store.togglePin(text: "pin"))
        XCTAssertEqual(store.list().count, 30)
        XCTAssertEqual(store.list().first?.text, "pin")
        XCTAssertFalse(store.list().contains { $0.text == "recent0" })
    }
    func testPinnedCapacityAndClearDelete() {
        for i in 0..<30 {
            store.save(text: "pin\(i)"); XCTAssertTrue(store.togglePin(text: "pin\(i)"))
        }
        store.save(text: "extra"); XCTAssertFalse(store.togglePin(text: "extra"))
        store.delete(text: "extra"); XCTAssertEqual(store.list().count, 30)
        store.clear(); XCTAssertTrue(ClipboardHistoryStore(defaults: defaults).list().isEmpty)
    }
    func testValidationAndCorruptStorage() {
        XCTAssertFalse(store.save(text: ""))
        XCTAssertFalse(store.save(text: String(repeating: "😀", count: 5001)))
        XCTAssertTrue(store.save(text: String(repeating: "😀", count: 5000)))
        defaults.set(Data("broken".utf8), forKey: "ClipboardHistoryStore.entries.v1")
        XCTAssertTrue(ClipboardHistoryStore(defaults: defaults).list().isEmpty)
    }
}
