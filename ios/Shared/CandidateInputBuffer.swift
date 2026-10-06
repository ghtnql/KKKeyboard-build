import Foundation
#if canImport(SharedUI)
import SharedUI
#else
import SharedCore
#endif

/// iOS adapter for the shared candidate input token buffer.
final class CandidateInputBuffer {
    private let shared = CandidateInputTokenBuffer()

    func apply(_ edit: HangulEdit) {
        shared.applyCommitted(value: edit.commit)
    }

    func removeCommittedCharacter() {
        shared.removeLastCommittedCharacter()
    }

    func replaceCurrent(with value: String) {
        shared.replaceCurrent(value: value)
    }

    func current(composing: String) -> String {
        shared.current(composing: composing)
    }

    func currentForLookup(composing: String, maxLength: Int) -> String? {
        // After deleting the active composition, the prior prefix may refer to
        // text at a different caret position. Preserve the iOS safety behavior.
        guard !composing.isEmpty else {
            shared.clear()
            return nil
        }
        return shared.currentForLookup(composing: composing, maxLength: Int32(maxLength))
    }

    func clear() {
        shared.clear()
    }
}
