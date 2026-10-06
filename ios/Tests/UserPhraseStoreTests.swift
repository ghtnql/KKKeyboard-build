import XCTest

final class UserPhraseStoreTests: XCTestCase {
    private var suiteName: String!
    private var defaults: UserDefaults!
    private var store: UserPhraseStore!

    override func setUp() {
        super.setUp()
        suiteName = "UserPhraseStoreTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
        store = UserPhraseStore(defaults: defaults, seedDefaults: false)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        store = nil
        defaults = nil
        suiteName = nil
        super.tearDown()
    }

    func testCreateUpdateAndDeleteRoundTrip() throws {
        let created = try store.create(title: "  인사  ", content: "안녕하세요!")
        XCTAssertEqual(store.phrases(), [
            UserPhrase(id: created.id, title: "인사", content: "안녕하세요!")
        ])

        try store.update(id: created.id, title: "퇴근 인사", content: "내일 뵙겠습니다.")
        XCTAssertEqual(store.phrases().first?.title, "퇴근 인사")
        XCTAssertEqual(store.phrases().first?.content, "내일 뵙겠습니다.")

        try store.delete(id: created.id)
        XCTAssertTrue(store.phrases().isEmpty)
    }

    func testRejectsBlankAndOversizedValues() {
        XCTAssertThrowsError(try store.create(title: " ", content: "내용")) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .emptyTitle)
        }
        XCTAssertThrowsError(try store.create(title: "제목", content: "\n")) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .emptyContent)
        }
        XCTAssertThrowsError(try store.create(
            title: String(repeating: "가", count: UserPhraseStore.maximumTitleLength + 1),
            content: "내용"
        )) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .titleTooLong)
        }
        XCTAssertThrowsError(try store.create(
            title: "제목",
            content: String(repeating: "가", count: UserPhraseStore.maximumContentLength + 1)
        )) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .contentTooLong)
        }
    }

    func testEnforcesPhraseCountLimit() throws {
        for index in 0..<UserPhraseStore.maximumPhraseCount {
            try store.create(title: "문구 \(index)", content: "내용 \(index)")
        }

        XCTAssertThrowsError(try store.create(title: "초과", content: "저장 안 됨")) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .phraseLimitReached)
        }
        XCTAssertEqual(store.phrases().count, UserPhraseStore.maximumPhraseCount)
    }

    func testCorruptStorageIsIgnored() {
        defaults.set(Data("not-json".utf8), forKey: "userPhrases.v1")
        XCTAssertEqual(store.phrases(), [])
    }

    func testMissingIdentifiersCannotBeChanged() {
        XCTAssertThrowsError(try store.update(id: UUID(), title: "제목", content: "내용")) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .phraseNotFound)
        }
        XCTAssertThrowsError(try store.delete(id: UUID())) { error in
            XCTAssertEqual(error as? UserPhraseStoreError, .phraseNotFound)
        }
    }

    func testMigratesAppLegacyPhrasesIntoSharedStoreOnce() throws {
        let sharedName = "UserPhraseStoreSharedTests.\(UUID().uuidString)"
        let shared = UserDefaults(suiteName: sharedName)!
        defer { shared.removePersistentDomain(forName: sharedName) }

        let groupPhrase = try UserPhraseStore(defaults: shared, seedDefaults: false).create(title: "앱", content: "앱 문구")
        let legacyPhrase = try store.create(title: "이전 앱", content: "기존 문구")
        let migrated = UserPhraseStore(defaults: shared, legacyDefaults: defaults, migrateLegacy: true, seedDefaults: false)
        XCTAssertEqual(migrated.phrases().map(\.id), [groupPhrase.id, legacyPhrase.id])

        // A second launch must not replay stale private data over newer edits.
        try migrated.delete(id: legacyPhrase.id)
        XCTAssertEqual(
            UserPhraseStore(defaults: shared, legacyDefaults: defaults, migrateLegacy: true, seedDefaults: false).phrases().map(\.id),
            [groupPhrase.id]
        )
        XCTAssertEqual(store.phrases().map(\.id), [legacyPhrase.id])
    }

    func testSharedStoreIsVisibleAcrossIndependentInstances() throws {
        let sharedName = "UserPhraseStoreSharedTests.\(UUID().uuidString)"
        let shared = UserDefaults(suiteName: sharedName)!
        defer { shared.removePersistentDomain(forName: sharedName) }
        let created = try UserPhraseStore(defaults: shared, seedDefaults: false).create(title: "공유", content: "내용")
        XCTAssertEqual(UserPhraseStore(defaults: UserDefaults(suiteName: sharedName)!, seedDefaults: false).phrases().map(\.id), [created.id])
    }

    func testMigrationKeepsNewerGroupVersionOfSamePhrase() throws {
        let sharedName = "UserPhraseStoreSharedTests.\(UUID().uuidString)"
        let shared = UserDefaults(suiteName: sharedName)!
        defer { shared.removePersistentDomain(forName: sharedName) }
        let id = UUID()
        defaults.set(try JSONEncoder().encode([UserPhrase(id: id, title: "이전", content: "이전 내용")]), forKey: "userPhrases.v1")
        shared.set(try JSONEncoder().encode([UserPhrase(id: id, title: "새 버전", content: "새 내용")]), forKey: "userPhrases.v1")

        let migrated = UserPhraseStore(defaults: shared, legacyDefaults: defaults, migrateLegacy: true, seedDefaults: false)
        XCTAssertEqual(migrated.phrases(), [UserPhrase(id: id, title: "새 버전", content: "새 내용")])
    }

    func testReadOnlySharedPhrasesKeepExtensionEditsLocal() throws {
        let sharedName = "UserPhraseStoreSharedTests.\(UUID().uuidString)"
        let shared = UserDefaults(suiteName: sharedName)!
        defer { shared.removePersistentDomain(forName: sharedName) }
        let appPhrase = try UserPhraseStore(defaults: shared, seedDefaults: false).create(title: "앱", content: "앱 문구")
        let localPhrase = try store.create(title: "확장", content: "확장 문구")
        let extensionStore = UserPhraseStore(defaults: defaults, readOnlySharedDefaults: shared, seedDefaults: false)
        XCTAssertEqual(extensionStore.phrases().map(\.id), [localPhrase.id, appPhrase.id])

        try extensionStore.update(id: appPhrase.id, title: "수정", content: "로컬 수정")
        XCTAssertEqual(extensionStore.phrases().first { $0.id == appPhrase.id }?.content, "로컬 수정")
        XCTAssertEqual(UserPhraseStore(defaults: shared, seedDefaults: false).phrases().first?.content, "앱 문구")

        try extensionStore.delete(id: appPhrase.id)
        XCTAssertEqual(extensionStore.phrases().map(\.id), [localPhrase.id])
        XCTAssertEqual(UserPhraseStore(defaults: shared, seedDefaults: false).phrases().map(\.id), [appPhrase.id])
    }
    func testBundledDefaultsSeedOnceAndDeletedPhraseStaysDeleted() throws {
        let seeded = UserPhraseStore(defaults: defaults)
        let first = seeded.phrases()
        XCTAssertEqual(first.count, 10)
        XCTAssertEqual(first.first?.content, "こんにちは")
        XCTAssertEqual(first.last?.content, "また明日。")
        try seeded.delete(id: first[0].id)
        XCTAssertEqual(UserPhraseStore(defaults: defaults).phrases().count, 9)
    }

    func testDefaultsAppendAfterExistingCustomWithoutChangingIt() throws {
        let custom = try store.create(title: "내 문구", content: "自分の文")
        let seeded = UserPhraseStore(defaults: defaults)
        XCTAssertEqual(seeded.phrases().count, 11)
        XCTAssertEqual(seeded.phrases().first, custom)
    }

    func testExtensionFallbackDoesNotOverrideLaterAppEdits() throws {
        let sharedName = "UserPhraseStoreFirstExtension.\(UUID().uuidString)"
        let shared = UserDefaults(suiteName: sharedName)!
        defer { shared.removePersistentDomain(forName: sharedName) }
        let extensionStore = UserPhraseStore(defaults: defaults, readOnlySharedDefaults: shared)
        let first = try XCTUnwrap(extensionStore.phrases().first)
        XCTAssertEqual(extensionStore.phrases().count, 10)
        XCTAssertNil(defaults.data(forKey: "userPhrases.v1"))

        let appStore = UserPhraseStore(defaults: shared)
        try appStore.update(id: first.id, title: "앱 수정", content: "変更しました。")
        XCTAssertEqual(extensionStore.phrases().first?.content, "変更しました。")
        try appStore.delete(id: first.id)
        XCTAssertFalse(extensionStore.phrases().contains(where: { $0.id == first.id }))
    }

    func testExtensionHidesDeletedSharedDefaultAcrossReload() throws {
        let sharedName = "UserPhraseStoreSeedShared.\(UUID().uuidString)"
        let shared = UserDefaults(suiteName: sharedName)!
        defer { shared.removePersistentDomain(forName: sharedName) }
        let appStore = UserPhraseStore(defaults: shared)
        let firstDefault = try XCTUnwrap(appStore.phrases().first)
        let extensionStore = UserPhraseStore(defaults: defaults, readOnlySharedDefaults: shared)
        try extensionStore.delete(id: firstDefault.id)
        XCTAssertEqual(UserPhraseStore(defaults: defaults, readOnlySharedDefaults: shared).phrases().count, 9)
        XCTAssertEqual(appStore.phrases().count, 10)
    }

}
