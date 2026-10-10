import UIKit

/// Clipboard history panel. Parent sizes this view to full keyboard bounds.
/// Never reads UIPasteboard automatically; import happens only through
/// the explicit UIPasteControl button.
public final class ClipboardPanel: UIView {
    private let store: ClipboardHistoryStore
    private let language: () -> String
    private let onImportSelection: () -> String?
    private let onPaste: (String) -> Void
    private let onClose: () -> Void
    private let onDismissKeyboard: () -> Void

    private var generation = 0
    private var currentText: String?
    private var feedback: String?
    private var confirmingClearAll = false

    private let headerView = UIStackView()
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    public init(
        store: ClipboardHistoryStore,
        language: @escaping () -> String,
        onImportSelection: @escaping () -> String?,
        onPaste: @escaping (String) -> Void,
        onClose: @escaping () -> Void,
        onDismissKeyboard: @escaping () -> Void
    ) {
        self.store = store
        self.language = language
        self.onImportSelection = onImportSelection
        self.onPaste = onPaste
        self.onClose = onClose
        self.onDismissKeyboard = onDismissKeyboard
        super.init(frame: .zero)
        self.pasteConfiguration = UIPasteConfiguration(forAccepting: NSString.self)
        setup()
        isHidden = true
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setup() {
        backgroundColor = .systemBackground

        headerView.axis = .horizontal
        headerView.alignment = .center
        headerView.spacing = 8
        headerView.translatesAutoresizingMaskIntoConstraints = false

        let closeButton = makeButton(title: t("닫기", "閉じる", "Close")) { [weak self] in
            self?.onClose()
        }
        closeButton.tag = 1
        closeButton.setContentHuggingPriority(.required, for: .horizontal)

        let titleLabel = UILabel()
        titleLabel.text = t("클립보드", "クリップボード", "Clipboard")
        titleLabel.font = .preferredFont(forTextStyle: .headline)
        titleLabel.textColor = .label
        titleLabel.textAlignment = .center

        let hideButton = makeButton(title: t("키보드 숨기기", "キーボードを隠す", "Hide keyboard")) { [weak self] in
            self?.onDismissKeyboard()
        }
        hideButton.tag = 2
        hideButton.setContentHuggingPriority(.required, for: .horizontal)

        headerView.addArrangedSubview(closeButton)
        headerView.addArrangedSubview(titleLabel)
        headerView.addArrangedSubview(hideButton)
        addSubview(headerView)

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scrollView)

        contentStack.axis = .vertical
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            headerView.topAnchor.constraint(equalTo: topAnchor),
            headerView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            headerView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            headerView.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),

            scrollView.topAnchor.constraint(equalTo: headerView.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 8),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 8),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -8),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -8),
            contentStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -16),
        ])
        refresh()
    }

    // MARK: - Lifecycle

    /// Show panel with fresh ephemeral state. Never reads the pasteboard.
    public func open() {
        isHidden = false
        for view in headerView.arrangedSubviews {
            if let button = view as? UIButton {
                button.setTitle(button.tag == 1 ? t("닫기", "閉じる", "Close") : t("키보드 숨기기", "キーボードを隠す", "Hide keyboard"), for: .normal)
            } else if let label = view as? UILabel {
                label.text = t("클립보드", "クリップボード", "Clipboard")
            }
        }
        generation += 1
        currentText = nil
        feedback = nil
        confirmingClearAll = false
        refresh()
    }

    /// Hide path: clears ephemeral text and invalidates in-flight paste completions.
    public func close() {
        isHidden = true
        generation += 1
        currentText = nil
        feedback = nil
        confirmingClearAll = false
        refresh()
    }

    // MARK: - Explicit paste

    override public func paste(itemProviders: [NSItemProvider]) {
        guard !isHidden else { return }
        currentText = nil
        generation += 1
        let token = generation
        guard let provider = itemProviders.first else {
            feedback = t("가져올 내용이 없습니다.", "貼り付け内容がありません。", "Nothing to paste.")
            refresh()
            return
        }
        provider.loadObject(ofClass: NSString.self) { [weak self] object, _ in
            DispatchQueue.main.async {
                guard let self, self.generation == token else { return }
                guard let string = object as? String, !string.isEmpty else {
                    self.feedback = self.t("가져오기에 실패했습니다.", "取得に失敗しました。", "Import failed.")
                    self.refresh()
                    return
                }
                guard string.utf16.count <= ClipboardHistoryStore.maxUTF16Length else {
                    self.feedback = self.t(
                        "텍스트가 너무 깁니다(최대 10000자).",
                        "テキストが長すぎます（最大10000文字）。",
                        "Text too long (max 10,000 characters)."
                    )
                    self.refresh()
                    return
                }
                self.currentText = string
                self.feedback = nil
                self.refresh()
            }
        }
    }

    // MARK: - Contents

    private func refresh() {
        for view in contentStack.arrangedSubviews {
            contentStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        buildCurrentSection()
        buildSavedSection()
    }

    private func buildCurrentSection() {
        let section = UIStackView()
        section.axis = .vertical
        section.spacing = 8

        let header = UILabel()
        header.text = t("현재 클립보드", "現在のクリップボード", "Current clipboard")
        header.font = .preferredFont(forTextStyle: .headline)
        header.textColor = .label
        header.numberOfLines = 0
        section.addArrangedSubview(header)

        let explainer = UILabel()
        explainer.text = t(
            "자동으로 기록되지 않습니다. 아래 버튼으로 직접 가져오세요. 버튼을 사용할 수 없으면 입력창을 길게 눌러 표준 붙여넣기 메뉴를 사용하세요.",
            "自動では保存されません。下のボタンで取り込んでください。ボタンを利用できない場合は入力欄を長押しして標準のペーストメニューを使ってください。",
            "No automatic history. Import explicitly with the button below. If the button is unavailable, use the host input's standard Paste menu."
        )
        explainer.font = .preferredFont(forTextStyle: .footnote)
        explainer.textColor = .secondaryLabel
        explainer.numberOfLines = 0
        section.addArrangedSubview(explainer)

        if #available(iOS 16.0, *) {
            let configuration = UIPasteControl.Configuration()
            configuration.displayMode = .iconAndLabel
            let control = UIPasteControl(configuration: configuration)
            control.target = self
            control.translatesAutoresizingMaskIntoConstraints = false
            control.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
            section.addArrangedSubview(control)
        }

        let selectionButton = makeButton(title: t("선택한 글 가져오기", "選択した文字を取り込む", "Import selected text")) { [weak self] in
            guard let self else { return }
            self.generation += 1
            self.currentText = nil
            if let text = self.onImportSelection(), !text.isEmpty,
               text.utf16.count <= ClipboardHistoryStore.maxUTF16Length {
                self.currentText = text
                self.feedback = nil
            } else {
                self.feedback = self.t("입력창에 붙여넣고 저장할 글을 선택해 주세요. 긴 글은 나누어 선택하세요.",
                    "入力欄に貼り付けて保存する文字を選択してください。長い文章は分けて選択してください。",
                    "Paste into the input field and select the text to save. Select a smaller portion for long text.")
            }
            self.refresh()
        }
        section.addArrangedSubview(selectionButton)

        if let text = currentText {
            let preview = UILabel()
            preview.text = text
            preview.numberOfLines = 2
            preview.lineBreakMode = .byTruncatingTail
            preview.textColor = .label
            section.addArrangedSubview(preview)

            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 8
            row.distribution = .fillEqually
            let pasteButton = makeButton(title: t("붙여넣기", "ペースト", "Paste to host")) { [weak self] in
                guard let self else { return }
                self.store.use(text: text)
                self.onPaste(text)
                self.onClose()
            }
            let saveButton = makeButton(title: t("저장", "保存", "Save")) { [weak self] in
                guard let self else { return }
                if self.store.save(text: text) {
                    self.feedback = nil
                } else {
                    self.feedback = self.t(
                        "저장에 실패했습니다(빈 텍스트 또는 길이 제한).",
                        "保存に失敗しました（空または長さ制限）。",
                        "Save failed (empty text or length limit)."
                    )
                }
                self.refresh()
            }
            row.addArrangedSubview(pasteButton)
            row.addArrangedSubview(saveButton)
            section.addArrangedSubview(row)
        }

        if let feedback {
            let label = UILabel()
            label.text = feedback
            label.font = .preferredFont(forTextStyle: .footnote)
            label.textColor = .systemRed
            label.numberOfLines = 0
            section.addArrangedSubview(label)
        }

        contentStack.addArrangedSubview(section)
    }

    private func buildSavedSection() {
        let section = UIStackView()
        section.axis = .vertical
        section.spacing = 8

        let header = UILabel()
        header.text = t("저장된 항목", "保存済み", "Saved")
        header.font = .preferredFont(forTextStyle: .headline)
        header.textColor = .label
        header.numberOfLines = 0
        section.addArrangedSubview(header)

        let entries = store.list()
        if entries.isEmpty {
            let empty = UILabel()
            empty.text = t("저장된 항목이 없습니다.", "保存された項目はありません。", "No saved items.")
            empty.font = .preferredFont(forTextStyle: .footnote)
            empty.textColor = .secondaryLabel
            empty.numberOfLines = 0
            section.addArrangedSubview(empty)
        }

        for pinned in [true, false] {
            let group = entries.filter { $0.pinned == pinned }
            if group.isEmpty { continue }
            let heading = UILabel()
            heading.text = pinned ? t("고정됨", "ピン留め", "Pinned") : t("저장 기록", "保存履歴", "Saved history")
            heading.font = .preferredFont(forTextStyle: .subheadline)
            heading.accessibilityTraits = .header
            section.addArrangedSubview(heading)
            for entry in group { section.addArrangedSubview(makeRow(for: entry)) }
        }

        if !entries.isEmpty {
            if confirmingClearAll {
                let confirmLabel = UILabel()
                confirmLabel.text = t("고정 항목을 포함해 모두 지울까요?", "ピン留めを含めすべて削除しますか？", "Clear all, including pins?")
                confirmLabel.textColor = .label
                confirmLabel.numberOfLines = 0
                section.addArrangedSubview(confirmLabel)

                let row = UIStackView()
                row.axis = .horizontal
                row.spacing = 8
                row.distribution = .fillEqually
                let confirm = makeButton(title: t("모두 지우기", "すべて削除", "Clear all")) { [weak self] in
                    self?.store.clear()
                    self?.confirmingClearAll = false
                    self?.refresh()
                }
                let cancel = makeButton(title: t("취소", "キャンセル", "Cancel")) { [weak self] in
                    self?.confirmingClearAll = false
                    self?.refresh()
                }
                row.addArrangedSubview(confirm)
                row.addArrangedSubview(cancel)
                section.addArrangedSubview(row)
            } else {
                let clearButton = makeButton(title: t("모두 지우기", "すべて削除", "Clear all")) { [weak self] in
                    self?.confirmingClearAll = true
                    self?.refresh()
                }
                section.addArrangedSubview(clearButton)
            }
        }

        contentStack.addArrangedSubview(section)
    }

    private func makeRow(for entry: ClipboardEntry) -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 8
        row.alignment = .center

        let textButton = makeButton(title: entry.text) { [weak self] in
            guard let self else { return }
            self.store.use(text: entry.text)
            self.onPaste(entry.text)
            self.onClose()
        }
        var textConfig = textButton.configuration
        textConfig?.titleLineBreakMode = .byTruncatingTail
        textButton.configuration = textConfig
        textButton.titleLabel?.numberOfLines = 2
        textButton.titleLabel?.lineBreakMode = .byTruncatingTail
        textButton.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textButton.accessibilityLabel = entry.text
        if entry.pinned {
            textButton.setTitle("📌 " + entry.text, for: .normal)
        }

        let pinTitle = entry.pinned
            ? t("고정 해제", "ピン解除", "Unpin")
            : t("고정", "ピン留め", "Pin")
        let pinButton = makeButton(title: pinTitle) { [weak self] in
            guard let self else { return }
            if !self.store.togglePin(text: entry.text) {
                self.feedback = self.t(
                    "고정 제한(30개)을 초과했습니다.",
                    "ピンの上限（30件）を超えています。",
                    "Pin limit (30) exceeded."
                )
            }
            self.refresh()
        }
        pinButton.setContentHuggingPriority(.required, for: .horizontal)

        let deleteButton = makeButton(title: t("삭제", "削除", "Delete")) { [weak self] in
            self?.store.delete(text: entry.text)
            self?.refresh()
        }
        deleteButton.setContentHuggingPriority(.required, for: .horizontal)

        row.addArrangedSubview(textButton)
        row.addArrangedSubview(pinButton)
        row.addArrangedSubview(deleteButton)
        return row
    }

    // MARK: - Helpers

    private func makeButton(title: String, action: @escaping () -> Void) -> UIButton {
        var config = UIButton.Configuration.filled()
        config.titleLineBreakMode = .byWordWrapping
        let button = UIButton(configuration: config, primaryAction: UIAction { _ in action() })
        button.setTitle(title, for: .normal)
        button.titleLabel?.numberOfLines = 0
        button.titleLabel?.lineBreakMode = .byWordWrapping
        button.translatesAutoresizingMaskIntoConstraints = false
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        button.widthAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        return button
    }

    private func t(_ ko: String, _ ja: String, _ en: String) -> String {
        let lang = language().lowercased()
        if lang.hasPrefix("ko") { return ko }
        if lang.hasPrefix("ja") { return ja }
        return en
    }
}
