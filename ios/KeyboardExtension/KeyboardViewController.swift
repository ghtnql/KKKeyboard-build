import UIKit
import SharedUI

final class KeyboardViewController: UIInputViewController {
    private let composer = HangulComposer()
    private let candidateInput = CandidateInputBuffer()
    private let candidateLearning = JapaneseCandidateLearning()
    private let layoutSettings = KeyboardLayoutSettings()
    private let keyImpactFeedback = UIImpactFeedbackGenerator(style: .light)
    private var hapticFeedbackEnabled = true
    private let themeSettings = KeyboardThemeSettings()
    private let phraseStore = UserPhraseStore()
    private let phraseDraftComposer = HangulComposer()
    private let cheonjiinInput = CheonjiinInput()
    private var cheonjiinJamo: [Character] = []
    private var cheonjiinSyllableBreaks: Set<Int> = []
    private var cheonjiinRenderedText = ""
    private var cheonjiinPhraseRenderedText = ""
    private var cheonjiinCandidatePrefix = ""
    private var koreanLayout: KoreanLayout = .cheonjiin
    private enum KoreanLayout { case cheonjiin, cheonjiinPlus, qwerty, flick }
    private var inputLanguage: KeyboardInputLanguage = .korean
    private var usesCheonjiin: Bool { inputLanguage != .english && (koreanLayout == .cheonjiin || koreanLayout == .cheonjiinPlus) }
    private var usesFlick: Bool { inputLanguage != .english && koreanLayout == .flick }
    private var languageButtons: [UIButton] = []
    private var inputLanguageTitle: String {
        inputLanguage == .english ? ui("영어", "英語", "English") : inputLanguage == .japanese
            ? ui("日本語", "日本語", "Japanese") : ui("한국어", "韓国語", "Korean")
    }
    private var nextLanguageKeyTitle: String {
        inputLanguage == .english ? "한글" : inputLanguage == .japanese ? "ABC" : "한/日"
    }
    private var plusRows: [UIStackView] = []
    private var renderedComposition = ""
    private var japaneseCandidateMode = false
    private var displayedCandidates: [String] = []
    private var shiftEnabled = false
    private var symbolPage = false
    private var symbolBank = 0
    private var symbolMiddleButton: UIButton?
    private var qwertyMiddleSpacers: [UIView] = []
    private var toolbarCursorButtons: [UIButton] = []
    private var settingsVisible = false
    private var isMutatingDocument = false
    private var layoutOrientation: KeyboardOrientation = .portrait
    private var settingsTargetOrientation: KeyboardOrientation = .portrait
    private var shiftedCharacterButtons: [(button: UIButton, baseLabel: String)] = []
    private var characterButtons: [UIButton] = []
    private var settingsController: UIViewController?
    private let settingsPlatform = IOSKeyboardSettingsPlatform()
    private func ui(_ ko: String, _ ja: String, _ en: String) -> String {
        settingsPlatform.localized(ko, ja, en)
    }
    private var flickRows: [UIStackView] = []
    private var flickKeys: [SharedFlickKeyControl] = []
    private var qwertyRows: [UIStackView] = []
    private var cheonjiinRows: [UIStackView] = []
    private var candidateButtons: [UIButton] = []
    private let modeLabel = UILabel()
    private var phraseDraftID: UUID?
    private var phraseDraftTitle = ""
    private var phraseDraftContent = ""
    private var phraseDraftRenderedComposition = ""
    private var phraseDraftEditingTitle = true

    private let keyboardStack = UIStackView()
    private let themeBackground = UIImageView()
    private var themeButtons: [UIButton] = []
    private var originalButtonStyles: [ObjectIdentifier: (UIColor, UIColor, UIColor?)] = [:]
    private let candidateSuggestionsRow = UIStackView()
    private let candidateRow = UIStackView()
    private let candidateScroll = UIScrollView()
    private let numberRow = UIStackView()
    private let cursorRow = UIStackView()
    private let settingsRow = UIStackView()
    private let phraseEditorPanel = UIStackView()
    private let phraseEditorActions = UIStackView()
    private let phraseBrowser = UIView()
    private let phraseListStack = UIStackView()
    private let phraseEditorStatusLabel = UILabel()
    private let phraseBrowserHeading = UILabel()
    private var phraseStatusRefresh: (() -> Void)?
    private var phraseTitleButton: UIButton?
    private var phraseContentButton: UIButton?
    private var modeButton: UIButton?
    private var shiftButton: UIButton?
    private var pageButton: UIButton?
    private var orientationButton: UIButton?
    private var heightButton: UIButton?
    private var numberRowButton: UIButton?
    private var cursorRowButton: UIButton?
    private var keyboardHeightConstraint: NSLayoutConstraint?
    private var backspaceRepeatTimer: Timer?
    private var themeExpiryTimer: Timer?

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        for button in themeButtons {
            button.layer.sublayers?.first(where: { $0.name == "seoulGlass" })?.frame = button.bounds
        }
    }

    private var isEditingPhrase: Bool {
        phraseDraftID != nil || !phraseEditorPanel.isHidden
    }

    private var hangulLabels: [String] {
        TwoBeolsikLayout.characterRows.flatMap { $0 } + TwoBeolsikLayout.bottomRow
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        JapaneseTransliterator.prepare()
        inputLanguage = KeyboardInputModeSettings().language()
        japaneseCandidateMode = inputLanguage == .japanese
        configureKeyboard()
        refreshNativeChrome()
        reloadInputLayout()
        applyLayoutProfile(for: currentOrientation())
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        reloadInputLayout()
        applyKeyboardTheme()
        applyLayoutProfile(for: currentOrientation())
        refreshNativeChrome()
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        coordinator.animate(alongsideTransition: nil) { [weak self] _ in
            guard let self else { return }
            self.applyKeyboardTheme()
            self.applyLayoutProfile(for: self.currentOrientation())
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        stopCursorRepeat()
        closeSharedSettings()
        themeExpiryTimer?.invalidate()
        themeExpiryTimer = nil
        stopBackspaceRepeat()
        commitPhraseDraftComposition()
        commitPendingComposition()
        resetCheonjiinState()
        clearCandidateTracking()
        setShift(false)
        super.viewWillDisappear(animated)
    }

    override func textWillChange(_ textInput: UITextInput?) {
        super.textWillChange(textInput)
        discardPendingStateForExternalDocumentChange()
    }

    override func selectionWillChange(_ textInput: UITextInput?) {
        super.selectionWillChange(textInput)
        discardPendingStateForExternalDocumentChange()
    }

    private func configureKeyboard() {
        view.backgroundColor = UIColor(red: 0.92, green: 0.93, blue: 0.95, alpha: 1)
        themeBackground.contentMode = .scaleToFill
        themeBackground.isUserInteractionEnabled = false
        themeBackground.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(themeBackground)
        NSLayoutConstraint.activate([
            themeBackground.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            themeBackground.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            themeBackground.topAnchor.constraint(equalTo: view.topAnchor),
            themeBackground.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        keyboardStack.axis = .vertical
        keyboardStack.spacing = 6
        keyboardStack.distribution = .fillEqually
        keyboardStack.translatesAutoresizingMaskIntoConstraints = false

        configureCandidateRow()
        keyboardStack.addArrangedSubview(candidateSuggestionsRow)
        keyboardStack.addArrangedSubview(candidateRow)
        configurePhraseEditorPanel()
        keyboardStack.addArrangedSubview(phraseEditorPanel)
        configureNumberRow()
        keyboardStack.addArrangedSubview(numberRow)
        configureCursorRow()
        keyboardStack.addArrangedSubview(cursorRow)
        TwoBeolsikLayout.characterRows.forEach(addCharacterRow)
        addBottomCharacterRow()
        addControlRow()
        configureCheonjiinRows()
        configurePlusRows()
        configureFlickRows()
        configurePhraseEditorActions()
        keyboardStack.addArrangedSubview(phraseEditorActions)
        settingsRow.isHidden = true

        view.addSubview(keyboardStack)
        configurePhraseBrowser()
        for button in themeButtons {
            originalButtonStyles[ObjectIdentifier(button)] = (
                button.backgroundColor ?? .white,
                button.titleColor(for: .normal) ?? .black,
                button.layer.borderColor.map { UIColor(cgColor: $0) }
            )
        }
        applyKeyboardTheme()
        let heightConstraint = view.heightAnchor.constraint(equalToConstant: CGFloat(KeyboardLayoutSettings.defaultHeight))
        heightConstraint.priority = UILayoutPriority(999)
        keyboardHeightConstraint = heightConstraint
        NSLayoutConstraint.activate([
            keyboardStack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 6),
            keyboardStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -6),
            keyboardStack.topAnchor.constraint(equalTo: view.topAnchor, constant: 8),
            keyboardStack.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
            heightConstraint
        ])
        updateVisibleLayout()
    }

    private func configureCandidateRow() {
        candidateRow.axis = .horizontal
        candidateRow.spacing = 4
        candidateRow.distribution = .fill
        candidateRow.alpha = 1
        candidateRow.isUserInteractionEnabled = true

        let candidates = UIStackView()
        candidates.axis = .horizontal
        candidates.spacing = 4
        candidates.translatesAutoresizingMaskIntoConstraints = false
        candidateScroll.showsHorizontalScrollIndicator = false
        candidateScroll.addSubview(candidates)
        candidateSuggestionsRow.axis = .horizontal
        candidateSuggestionsRow.distribution = .fill
        candidateSuggestionsRow.isHidden = true
        candidateSuggestionsRow.addArrangedSubview(candidateScroll)
        NSLayoutConstraint.activate([
            candidates.leadingAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.leadingAnchor),
            candidates.trailingAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.trailingAnchor),
            candidates.topAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.topAnchor),
            candidates.bottomAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.bottomAnchor),
            candidates.heightAnchor.constraint(equalTo: candidateScroll.frameLayoutGuide.heightAnchor)
        ])
        candidateButtons = (0..<JapaneseDictionary.maxCandidates).map { _ in
            let button = makeButton(title: "", action: #selector(handleCandidate(_:)))
            button.isHidden = true
            button.contentEdgeInsets = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
            button.setContentCompressionResistancePriority(.required, for: .horizontal)
            candidates.addArrangedSubview(button)
            return button
        }
        modeLabel.text = inputLanguageTitle
        modeLabel.font = .systemFont(ofSize: 15, weight: .medium)
        modeLabel.textColor = .darkGray
        modeLabel.setContentHuggingPriority(.required, for: .horizontal)
        candidateRow.addArrangedSubview(modeLabel)
        for (title, selector) in [("←", #selector(handleCursorLeft)), ("→", #selector(handleCursorRight))] {
            let button = makeButton(title: title, action: selector)
            button.isHidden = true
            let width = button.widthAnchor.constraint(equalToConstant: 32)
            width.priority = .defaultHigh; width.isActive = true
            let minimum = button.widthAnchor.constraint(greaterThanOrEqualToConstant: 24)
            minimum.priority = .defaultHigh; minimum.isActive = true
            candidateRow.addArrangedSubview(button)
            toolbarCursorButtons.append(button)
        }
        let phrases = makeButton(title: "▤", action: #selector(handlePhraseBrowser))
        phrases.accessibilityLabel = ui("사용자 문구", "ユーザー定型文", "User phrases")
        let phrasesWidth = phrases.widthAnchor.constraint(equalToConstant: 40)
        phrasesWidth.priority = .defaultHigh; phrasesWidth.isActive = true
        phrases.widthAnchor.constraint(greaterThanOrEqualToConstant: 24).isActive = true
        candidateRow.addArrangedSubview(phrases)
        let layout = makeButton(title: "⌨", action: #selector(handleSettingsToggle))
        layout.accessibilityLabel = ui("입력 레이아웃 선택", "入力レイアウト選択", "Select input layout")
        let layoutWidth = layout.widthAnchor.constraint(equalToConstant: 40)
        layoutWidth.priority = .defaultHigh; layoutWidth.isActive = true
        layout.widthAnchor.constraint(greaterThanOrEqualToConstant: 24).isActive = true
        candidateRow.addArrangedSubview(layout)
        let next = makeButton(title: "🌐", action: #selector(handleNextKeyboard))
        next.accessibilityLabel = ui("다음 키보드", "次のキーボード", "Next keyboard")
        let nextWidth = next.widthAnchor.constraint(equalToConstant: 40)
        nextWidth.priority = .defaultHigh; nextWidth.isActive = true
        next.widthAnchor.constraint(greaterThanOrEqualToConstant: 24).isActive = true
        candidateRow.addArrangedSubview(next)
        let settings = makeButton(title: "⚙", action: #selector(handleSettingsToggle))
        settings.accessibilityLabel = ui("키보드 설정", "キーボード設定", "Keyboard settings")
        let settingsWidth = settings.widthAnchor.constraint(equalToConstant: 40)
        settingsWidth.priority = .defaultHigh; settingsWidth.isActive = true
        settings.widthAnchor.constraint(greaterThanOrEqualToConstant: 24).isActive = true
        candidateRow.addArrangedSubview(settings)
        let themes = makeButton(title: "◈", action: #selector(handleThemes))
        themes.accessibilityLabel = ui("키보드 테마", "キーボードテーマ", "Keyboard themes")
        let themesWidth = themes.widthAnchor.constraint(equalToConstant: 36)
        themesWidth.priority = .defaultHigh; themesWidth.isActive = true
        themes.widthAnchor.constraint(greaterThanOrEqualToConstant: 24).isActive = true
        candidateRow.addArrangedSubview(themes)
        let hide = makeButton(title: "⌄", action: #selector(handleDismiss))
        hide.accessibilityLabel = ui("키보드 내리기", "キーボードを閉じる", "Dismiss keyboard")
        let hideWidth = hide.widthAnchor.constraint(equalToConstant: 36)
        hideWidth.priority = .defaultHigh; hideWidth.isActive = true
        hide.widthAnchor.constraint(greaterThanOrEqualToConstant: 24).isActive = true
        candidateRow.addArrangedSubview(hide)
    }


    private func refreshNativeChrome() {
        modeLabel.text = inputLanguageTitle
        if !isEditingPhrase {
            languageButtons.forEach { $0.setTitle(nextLanguageKeyTitle, for: .normal) }
            if let controlRow = qwertyRows.last {
                for button in controlRow.arrangedSubviews.compactMap({ $0 as? UIButton }) where
                    button.actions(forTarget: self, forControlEvent: .touchDown)?.contains(NSStringFromSelector(#selector(handleSpace))) == true {
                    button.setTitle(inputLanguageTitle, for: .normal)
                }
            }
        }
        phraseBrowserHeading.text = ui("사용자 문구", "ユーザー定型文", "User phrases")
        let titles: [String: (String, String, String)] = [
            NSStringFromSelector(#selector(handlePhraseSave)): ("저장", "保存", "Save"),
            NSStringFromSelector(#selector(handlePhraseCancel)): ("취소", "キャンセル", "Cancel"),
            NSStringFromSelector(#selector(handlePhraseDelete)): ("삭제", "削除", "Delete"),
            NSStringFromSelector(#selector(handlePhraseBrowserClose)): ("닫기", "閉じる", "Close"),
            NSStringFromSelector(#selector(handlePhraseCreate)): ("+ 추가", "+ 追加", "+ Add")
        ]
        let labels: [String: (String, String, String)] = [
            NSStringFromSelector(#selector(handlePhraseBrowser)): ("사용자 문구", "ユーザー定型文", "User phrases"),
            NSStringFromSelector(#selector(handleNextKeyboard)): ("다음 키보드", "次のキーボード", "Next keyboard"),
            NSStringFromSelector(#selector(handleThemes)): ("키보드 테마", "キーボードテーマ", "Keyboard themes"),
            NSStringFromSelector(#selector(handleDismiss)): ("키보드 내리기", "キーボードを閉じる", "Dismiss keyboard"),
            NSStringFromSelector(#selector(handleCursorLeft)): ("커서 왼쪽", "カーソルを左へ", "Cursor left"),
            NSStringFromSelector(#selector(handleCursorRight)): ("커서 오른쪽", "カーソルを右へ", "Cursor right"),
            NSStringFromSelector(#selector(handleBackspace)): ("삭제", "削除", "Delete"),
            NSStringFromSelector(#selector(handleSpace)): ("공백", "スペース", "Space"),
            NSStringFromSelector(#selector(handleReturn)): ("줄바꿈", "改行", "Return")
        ]
        func update(_ container: UIView) {
            for child in container.subviews {
                if let button = child as? UIButton {
                    let actions = (button.actions(forTarget: self, forControlEvent: .touchUpInside) ?? []) +
                        (button.actions(forTarget: self, forControlEvent: .touchDown) ?? []) +
                        (button.actions(forTarget: self, forControlEvent: LongPressKeyButton.tapEvent) ?? [])
                    for action in actions {
                        if let value = titles[action] { button.setTitle(ui(value.0, value.1, value.2), for: .normal) }
                        if let value = labels[action] { button.accessibilityLabel = ui(value.0, value.1, value.2) }
                        if action == NSStringFromSelector(#selector(handleCursorRight)) && button.currentTitle == "→" {
                            button.accessibilityLabel = ui("다음 글자, 입력 중이 아니면 커서 오른쪽", "次の文字、未入力時はカーソルを右へ", "Next character, or cursor right when not composing")
                        }
                        if action == NSStringFromSelector(#selector(handlePageToggle)) {
                            button.accessibilityLabel = symbolPage ? ui("한글 자판", "ハングル配列", "Hangul layout") : ui("숫자 및 기호", "数字と記号", "Numbers and symbols")
                        }
                        if action == NSStringFromSelector(#selector(handleSettingsToggle)) {
                            button.accessibilityLabel = button.currentTitle == "⌨"
                                ? ui("입력 레이아웃 선택", "入力レイアウト選択", "Select input layout")
                                : ui("키보드 설정", "キーボード設定", "Keyboard settings")
                        }
                    }
                }
                update(child)
            }
        }
        update(keyboardStack)
        update(phraseBrowser)
        pageButton?.accessibilityLabel = symbolPage ? ui("한글 자판", "ハングル配列", "Hangul layout") : ui("숫자 및 기호", "数字と記号", "Numbers and symbols")
        pageButton?.accessibilityHint = ui("길게 누르면 레이아웃 설정", "長押しでレイアウト設定", "Hold for layout settings")
        refreshSettingsControls()
        if isEditingPhrase {
            let statusRefresh = phraseStatusRefresh
            refreshPhraseEditor()
            phraseStatusRefresh = statusRefresh
            statusRefresh?()
        }
        if !phraseBrowser.isHidden { refreshPhraseBrowser() }
    }

    private func configurePhraseEditorPanel() {
        phraseEditorPanel.axis = .vertical
        phraseEditorPanel.spacing = 2
        phraseEditorPanel.distribution = .fillEqually
        phraseEditorPanel.isHidden = true

        phraseEditorStatusLabel.font = .preferredFont(forTextStyle: .caption2)
        phraseEditorStatusLabel.textAlignment = .center
        phraseEditorStatusLabel.adjustsFontSizeToFitWidth = true
        phraseEditorStatusLabel.minimumScaleFactor = 0.75
        phraseEditorPanel.addArrangedSubview(phraseEditorStatusLabel)

        let fields = makeRow()
        let title = makeButton(title: ui("제목", "タイトル", "Title"), action: #selector(handlePhraseTitleField))
        title.titleLabel?.adjustsFontSizeToFitWidth = true
        title.titleLabel?.minimumScaleFactor = 0.7
        phraseTitleButton = title
        fields.addArrangedSubview(title)

        let content = makeButton(title: ui("내용", "内容", "Body"), action: #selector(handlePhraseContentField))
        content.titleLabel?.adjustsFontSizeToFitWidth = true
        content.titleLabel?.minimumScaleFactor = 0.7
        phraseContentButton = content
        fields.addArrangedSubview(content)
        phraseEditorPanel.addArrangedSubview(fields)
    }

    private func configurePhraseEditorActions() {
        phraseEditorActions.axis = .horizontal
        phraseEditorActions.spacing = 4
        phraseEditorActions.distribution = .fillEqually
        phraseEditorActions.isHidden = true
        phraseEditorActions.addArrangedSubview(makeButton(title: ui("저장", "保存", "Save"), action: #selector(handlePhraseSave)))
        phraseEditorActions.addArrangedSubview(makeButton(title: ui("취소", "キャンセル", "Cancel"), action: #selector(handlePhraseCancel)))
        phraseEditorActions.addArrangedSubview(makeButton(title: ui("삭제", "削除", "Delete"), action: #selector(handlePhraseDelete)))
    }

    private func configurePhraseBrowser() {
        phraseBrowser.translatesAutoresizingMaskIntoConstraints = false
        phraseBrowser.backgroundColor = .systemBackground
        phraseBrowser.isHidden = true

        let header = makeRow()
        header.addArrangedSubview(makeButton(title: ui("닫기", "閉じる", "Close"), action: #selector(handlePhraseBrowserClose)))
        let heading = phraseBrowserHeading
        heading.text = ui("사용자 문구", "ユーザー定型文", "User phrases")
        heading.font = .preferredFont(forTextStyle: .headline)
        heading.textAlignment = .center
        heading.accessibilityTraits = .header
        header.addArrangedSubview(heading)
        header.addArrangedSubview(makeButton(title: ui("+ 추가", "+ 追加", "+ Add"), action: #selector(handlePhraseCreate)))

        phraseListStack.axis = .vertical
        phraseListStack.spacing = 6
        phraseListStack.translatesAutoresizingMaskIntoConstraints = false

        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        scrollView.addSubview(phraseListStack)

        let browserStack = UIStackView(arrangedSubviews: [header, scrollView])
        browserStack.translatesAutoresizingMaskIntoConstraints = false
        browserStack.axis = .vertical
        browserStack.spacing = 8
        phraseBrowser.addSubview(browserStack)
        view.addSubview(phraseBrowser)

        NSLayoutConstraint.activate([
            phraseBrowser.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            phraseBrowser.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            phraseBrowser.topAnchor.constraint(equalTo: view.topAnchor),
            phraseBrowser.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            browserStack.leadingAnchor.constraint(equalTo: phraseBrowser.leadingAnchor, constant: 8),
            browserStack.trailingAnchor.constraint(equalTo: phraseBrowser.trailingAnchor, constant: -8),
            browserStack.topAnchor.constraint(equalTo: phraseBrowser.topAnchor, constant: 8),
            browserStack.bottomAnchor.constraint(equalTo: phraseBrowser.bottomAnchor, constant: -8),
            header.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            phraseListStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            phraseListStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            phraseListStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            phraseListStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            phraseListStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor)
        ])
    }

    private func configureNumberRow() {
        numberRow.axis = .horizontal
        numberRow.spacing = 4
        numberRow.distribution = .fillEqually
        numberRow.isHidden = true
        for digit in ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"] {
            numberRow.addArrangedSubview(makeButton(title: digit, action: #selector(handleDirectInput(_:))))
        }
    }

    private func configureCursorRow() {
        cursorRow.axis = .horizontal
        cursorRow.spacing = 4
        cursorRow.distribution = .fillEqually
        cursorRow.isHidden = true

        let left = makeButton(title: "◀", action: #selector(handleCursorLeft))
        left.accessibilityLabel = ui("커서 왼쪽", "カーソルを左へ", "Cursor left")
        cursorRow.addArrangedSubview(left)

        let right = makeButton(title: "▶", action: #selector(handleCursorRight))
        right.accessibilityLabel = ui("커서 오른쪽", "カーソルを右へ", "Cursor right")
        cursorRow.addArrangedSubview(right)
    }

    private func constrainWeightedCells(_ cells: [(UIView, CGFloat)], reference: UIView, in row: UIStackView) {
        row.distribution = .fill
        for (cell, weight) in cells where cell !== reference {
            let constraint = cell.widthAnchor.constraint(equalTo: reference.widthAnchor, multiplier: weight)
            if cell is UIButton { constraint.priority = cell === symbolMiddleButton ? .defaultHigh : .required }
            else { constraint.priority = .defaultHigh }
            constraint.isActive = true
        }
    }

    private func addCharacterRow(_ characters: [String]) {
        let row = makeRow()
        let buttons = characters.map { makeCharacterButton(baseLabel: $0) }
        var cells: [(UIView, CGFloat)] = []
        let inset = CGFloat(10 - characters.count) / 2
        if inset > 0 {
            let spacer = UIView(); qwertyMiddleSpacers.append(spacer)
            row.addArrangedSubview(spacer); cells.append((spacer, inset))
        }
        for button in buttons { row.addArrangedSubview(button); cells.append((button, 1)) }
        if characters.count == 9 {
            let extra = makeButton(title: "/", action: #selector(handleCharacter(_:)))
            extra.isHidden = true
            symbolMiddleButton = extra
            row.addArrangedSubview(extra); cells.append((extra, 1))
        }
        if inset > 0 {
            let spacer = UIView(); qwertyMiddleSpacers.append(spacer)
            row.addArrangedSubview(spacer); cells.append((spacer, inset))
        }
        if let first = buttons.first { constrainWeightedCells(cells, reference: first, in: row) }
        keyboardStack.addArrangedSubview(row)
        qwertyRows.append(row)
    }

    private func addBottomCharacterRow() {
        let row = makeRow()
        let shift = makeButton(title: "⇧", action: #selector(handleShift))
        shift.accessibilityLabel = "Shift"
        shiftButton = shift
        row.addArrangedSubview(shift)
        var cells: [(UIView, CGFloat)] = [(shift, 1.5)]
        let buttons = TwoBeolsikLayout.bottomRow.map { makeCharacterButton(baseLabel: $0) }
        for button in buttons { row.addArrangedSubview(button); cells.append((button, 1)) }
        let backspace = makeBackspaceButton()
        row.addArrangedSubview(backspace); cells.append((backspace, 1.5))
        if let first = buttons.first { constrainWeightedCells(cells, reference: first, in: row) }
        keyboardStack.addArrangedSubview(row)
        qwertyRows.append(row)
    }

    private func addControlRow() {
        let row = makeRow()
        let page = makeButton(title: "?123", action: #selector(handlePageToggle))
        page.accessibilityLabel = ui("숫자 및 기호", "数字と記号", "Numbers and symbols")
        page.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(handlePageLongPress(_:))))
        pageButton = page
        let mode = makeButton(title: nextLanguageKeyTitle, action: #selector(handleModeToggle))
        modeButton = mode
        let comma = makeButton(title: ",", action: #selector(handleDirectInput(_:)))
        let space = makeButton(title: inputLanguageTitle, action: #selector(handleSpace))
        space.accessibilityLabel = ui("공백", "スペース", "Space")
        let period = makeButton(title: ".", action: #selector(handleDirectInput(_:)))
        let enter = makeButton(title: "↵", action: #selector(handleReturn))
        enter.backgroundColor = UIColor(red: 0.12, green: 0.43, blue: 0.35, alpha: 1)
        enter.setTitleColor(.white, for: .normal)
        let cells: [(UIView, CGFloat)] = [(page, 1.4), (mode, 1.4), (comma, 0.8), (space, 4.1), (period, 0.8), (enter, 1.5)]
        cells.forEach { row.addArrangedSubview($0.0) }
        // Use the comma's 0.8 units as the reference; all ratios remain Android's.
        row.distribution = .fill
        for (cell, weight) in cells where cell !== comma {
            cell.widthAnchor.constraint(equalTo: comma.widthAnchor, multiplier: weight / 0.8).isActive = true
        }
        keyboardStack.addArrangedSubview(row)
        qwertyRows.append(row)
    }

    private func qwertyKeyId(_ label: String) -> String {
        let labels = Array("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ")
        let ids = Array("qwertyuiopasdfghjklzxcvbnm")
        guard let index = labels.firstIndex(of: label.first ?? " ") else { return "" }
        return String(ids[index])
    }

    private func configureLongPress(_ button: UIButton, layout: String, keyId: String) {
        guard let button = button as? LongPressKeyButton else { return }
        // UIControl emits touchDown outside beginTracking: move targets to an
        // explicit event that this button sends only after a short release.
        for target in button.allTargets {
            for name in button.actions(forTarget: target, forControlEvent: .touchDown) ?? [] {
                button.removeTarget(target, action: NSSelectorFromString(name), for: .touchDown)
                button.addTarget(target, action: NSSelectorFromString(name), for: LongPressKeyButton.tapEvent)
            }
        }
        button.shouldDeferTap = { [weak self] in self?.symbolPage == false }
        button.choicesProvider = { [weak self] in
            guard let self, !self.symbolPage else { return [] }
            let slots = LongPressCatalog.shared.slots(layoutId: layout, keyId: keyId,
                override: self.layoutSettings.longPressOverride(layout: layout, keyId: keyId))
            return LongPressCatalog.shared.choices(layoutId: layout, keyId: keyId, slots: slots)
        }
        button.onChoice = { [weak self] choice in
            guard let self else { return }
            self.playKeyHaptic()
            if let character = choice.first,
               LongPressCatalog.shared.key(layoutId: layout, keyId: keyId)?.fixedJamo == choice {
                if self.usesCheonjiin {
                    self.applyCheonjiinAction(self.cheonjiinInput.inputDirectConsonant(character))
                } else if self.isEditingPhrase {
                    self.inputPhraseDraft(choice)
                } else {
                    let edit = self.composer.input(character)
                    self.candidateInput.apply(edit)
                    self.apply(edit)
                    self.refreshCandidates()
                }
            } else {
                if self.isEditingPhrase { self.appendDirectlyToPhraseDraft(choice) }
                else {
                    self.commitPendingComposition()
                    self.clearCandidateTracking()
                    self.mutateDocument { self.textDocumentProxy.insertText(choice) }
                }
            }
        }
        button.refreshHint()
    }

    private func configurePlusRows() {
        addPlusRow([("←", 1), ("ㅣ", 2), ("·", 2), ("ㅡ", 2), ("⌫", 1)])
        addPlusRow([("→", 1), ("ㄱ", 1), ("ㅋ", 1), ("ㄴ", 1), ("ㄹ", 1), ("ㄷ", 1), ("ㅌ", 1), ("↵", 1)])
        addPlusRow([("!", 1), ("ㅂ", 1), ("ㅍ", 1), ("ㅅ", 1), ("ㅎ", 1), ("ㅈ", 1), ("ㅊ", 1), (",", 1)])
        addPlusRow([("?", 1), ("?123", 1), ("한/日", 1), ("ㅇ", 1), ("ㅁ", 1), ("␣", 2), (".", 1)])
    }

    private func addPlusRow(_ keys: [(String, CGFloat)]) {
        let row = makeRow()
        row.distribution = .fill
        var cells: [(UIButton, CGFloat)] = []
        for (title, weight) in keys {
            let action: Selector
            switch title {
            case "←": action = #selector(handleCursorLeft)
            case "→": action = #selector(handleCursorRight)
            case "⌫": action = #selector(handleBackspace)
            case "↵": action = #selector(handleReturn)
            case "?123": action = #selector(handlePageToggle)
            case "한/日": action = #selector(handleModeToggle)
            case "␣": action = #selector(handleSpace)
            case "!", "?", ",", ".": action = #selector(handleDirectInput(_:))
            default: action = #selector(handlePlusKey(_:))
            }
            let button = title == "⌫" ? makeBackspaceButton() : makeButton(title: title, action: action)
            if action == #selector(handlePlusKey(_:)) {
                configureLongPress(button, layout: "cheonjiin_plus", keyId: title)
                button.titleLabel?.font = .systemFont(ofSize: 22, weight: .semibold)
            }
            if ["←", "→", "⌫", "?123", "한/日", "␣"].contains(title) {
                button.backgroundColor = UIColor(red: 0.85, green: 0.87, blue: 0.90, alpha: 1)
            }
            if title == "↵" {
                button.backgroundColor = UIColor(red: 0.12, green: 0.43, blue: 0.35, alpha: 1)
                button.setTitleColor(.white, for: .normal)
                button.accessibilityLabel = ui("줄바꿈", "改行", "Return")
            }
            if title == "␣" { button.accessibilityLabel = ui("공백", "スペース", "Space") }
            if title == "?123" {
                button.accessibilityLabel = ui("숫자 및 기호", "数字と記号", "Numbers and symbols")
                button.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(handlePageLongPress(_:))))
            }
            row.addArrangedSubview(button)
            cells.append((button, weight))
        }
        if let first = cells.first {
            for (button, weight) in cells.dropFirst() {
                button.widthAnchor.constraint(equalTo: first.0.widthAnchor, multiplier: weight / first.1).isActive = true
            }
        }
        keyboardStack.addArrangedSubview(row)
        plusRows.append(row)
    }

    @objc private func handlePlusKey(_ sender: UIButton) {
        guard let label = sender.currentTitle, let character = label.first else { return }
        let vowel: CheonjiinKey? = label == "ㅣ" ? .i : label == "·" ? .dot : label == "ㅡ" ? .eu : nil
        let now = ProcessInfo.processInfo.systemUptime
        let timeout = Double(layoutSettings.cycleTimeout) / 1000
        let action = vowel.map {
            cheonjiinInput.input($0, at: now, cycleTimeout: timeout)
        } ?? cheonjiinInput.inputDirectConsonant(character, at: now, doubleTapTimeout: timeout)
        applyCheonjiinAction(action)
    }

    private func configureCheonjiinRows() {
        addCheonjiinRow([
            ("←", 1, #selector(handleCursorLeft), nil),
            ("ㅣ", 2, #selector(handleCheonjiinKey(_:)), .i),
            ("·", 2, #selector(handleCheonjiinKey(_:)), .dot),
            ("ㅡ", 2, #selector(handleCheonjiinKey(_:)), .eu),
            ("⌫", 1, #selector(handleBackspace), nil)
        ])
        addCheonjiinRow([
            ("→", 1, #selector(handleCursorRight), nil),
            ("ㄱㅋ", 2, #selector(handleCheonjiinKey(_:)), .giyeok),
            ("ㄴㄹ", 2, #selector(handleCheonjiinKey(_:)), .nieun),
            ("ㄷㅌ", 2, #selector(handleCheonjiinKey(_:)), .digeut),
            ("↵", 1, #selector(handleReturn), nil)
        ])
        addCheonjiinRow([
            ("!", 1, #selector(handleDirectInput(_:)), nil),
            ("ㅂㅍ", 2, #selector(handleCheonjiinKey(_:)), .bieup),
            ("ㅅㅎ", 2, #selector(handleCheonjiinKey(_:)), .siot),
            ("ㅈㅊ", 2, #selector(handleCheonjiinKey(_:)), .jieut),
            (",", 1, #selector(handleDirectInput(_:)), nil)
        ])
        addCheonjiinRow([
            ("?", 1, #selector(handleDirectInput(_:)), nil),
            ("?123", 1, #selector(handlePageToggle), nil),
            ("한/日", 1, #selector(handleModeToggle), nil),
            ("ㅇㅁ", 2, #selector(handleCheonjiinKey(_:)), .ieung),
            ("␣", 2, #selector(handleSpace), nil),
            (".", 1, #selector(handleDirectInput(_:)), nil)
        ])
    }

    private func addCheonjiinRow(_ keys: [(String, CGFloat, Selector, CheonjiinKey?)]) {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 4
        row.distribution = .fill
        var buttons: [(UIButton, CGFloat)] = []
        for (title, weight, action, key) in keys {
            let button = title == "⌫" ? makeBackspaceButton() : makeButton(title: title, action: action)
            if let key {
                configureLongPress(button, layout: "cheonjiin", keyId: key.label)
                button.tag = CheonjiinKey.allCases.firstIndex(of: key) ?? 0
                button.accessibilityLabel = key.consonants.isEmpty
                    ? key.label : key.consonants.map { String($0) }.joined(separator: ", ")
                button.titleLabel?.font = .systemFont(ofSize: 22, weight: .semibold)
            } else {
                button.accessibilityLabel = title == "␣" ? ui("공백", "スペース", "Space") :
                    title == "→" ? ui("다음 글자, 입력 중이 아니면 커서 오른쪽", "次の文字、未入力時はカーソルを右へ", "Next character, or cursor right when not composing") : title
                if ["←", "→", "⌫", "?123", "한/日", "␣"].contains(title) {
                    button.backgroundColor = UIColor(red: 0.85, green: 0.87, blue: 0.90, alpha: 1)
                }
                if title == "↵" {
                    button.backgroundColor = UIColor(red: 0.12, green: 0.43, blue: 0.35, alpha: 1)
                    button.setTitleColor(.white, for: .normal)
                    button.accessibilityLabel = ui("줄바꿈", "改行", "Return")
                }
            }
            row.addArrangedSubview(button)
            buttons.append((button, weight))
        }
        if let first = buttons.first?.0 {
            for (button, weight) in buttons.dropFirst() {
                button.widthAnchor.constraint(equalTo: first.widthAnchor, multiplier: weight).isActive = true
            }
        }
        keyboardStack.addArrangedSubview(row)
        cheonjiinRows.append(row)
    }

    private var cursorRepeatTimer: Timer?
    private weak var cursorRepeatRecognizer: UILongPressGestureRecognizer?

    private func configureCursorRepeat(_ button: UIButton, direction: Int) {
        button.tag = direction
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handleCursorLongPress(_:)))
        recognizer.minimumPressDuration = 0.35
        recognizer.cancelsTouchesInView = true
        button.addGestureRecognizer(recognizer)
    }

    @objc private func handleCursorLongPress(_ recognizer: UILongPressGestureRecognizer) {
        guard let button = recognizer.view as? UIButton else { return }
        let direction = button.tag
        guard direction == 1 || direction == -1 else { return }
        switch recognizer.state {
        case .began:
            stopCursorRepeat()
            cursorRepeatRecognizer = recognizer
            moveCursor(by: direction)
            let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self, weak button] timer in
                guard let self = self, let button = button else {
                    timer.invalidate()
                    return
                }
                guard button.isEnabled, !button.isHidden else {
                    self.stopCursorRepeat()
                    return
                }
                self.moveCursor(by: direction)
            }
            cursorRepeatTimer = timer
            RunLoop.main.add(timer, forMode: .common)
        case .changed:
            guard cursorRepeatRecognizer === recognizer else { return }
            let location = recognizer.location(in: button)
            let bounds = button.bounds.insetBy(dx: -12, dy: -12)
            if !bounds.contains(location) {
                // Out of bounds: cancel, no restart until a new gesture begins. No click on release.
                stopCursorRepeat()
            }
        case .ended, .cancelled, .failed:
            guard cursorRepeatRecognizer === recognizer else { return }
            stopCursorRepeat()
        default:
            break
        }
    }

    private func stopCursorRepeat() {
        cursorRepeatRecognizer = nil
        cursorRepeatTimer?.invalidate()
        cursorRepeatTimer = nil
    }

    private func makeBackspaceButton() -> UIButton {
        let backspace = makeButton(title: "⌫", action: #selector(handleBackspace))
        backspace.accessibilityLabel = ui("삭제", "削除", "Delete")
        let repeatGesture = UILongPressGestureRecognizer(
            target: self,
            action: #selector(handleBackspaceLongPress(_:))
        )
        repeatGesture.minimumPressDuration = 0.4
        backspace.addGestureRecognizer(repeatGesture)
        return backspace
    }

    private func makeRow() -> UIStackView {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 4
        row.distribution = .fillEqually
        return row
    }

    private func makeButton(title: String, action: Selector) -> UIButton {
        let isInputKey = action == #selector(handleCharacter(_:)) ||
            action == #selector(handleCheonjiinKey(_:)) ||
            action == #selector(handlePlusKey(_:)) ||
            action == #selector(handleDirectInput(_:)) ||
            action == #selector(handleSpace)
        let isLanguageKey = action == #selector(handleModeToggle)
        let button: UIButton = (isInputKey || isLanguageKey) ? LongPressKeyButton(type: .system) : UIButton(type: .system)
        button.isExclusiveTouch = !isInputKey
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 18, weight: .medium)
        button.setTitleColor(UIColor(white: 0.12, alpha: 1), for: .normal)
        button.backgroundColor = .white
        button.layer.cornerRadius = 7
        button.layer.borderColor = UIColor(white: 0.79, alpha: 1).cgColor
        button.layer.borderWidth = 0.7
        let activationEvent: UIControl.Event = isInputKey ? .touchDown : .touchUpInside
        button.addTarget(self, action: action, for: activationEvent)
        button.addTarget(self, action: #selector(playKeyHaptic), for: activationEvent)
        themeButtons.append(button)
        if action == #selector(handleCursorLeft) { configureCursorRepeat(button, direction: -1) }
        if action == #selector(handleCursorRight) { configureCursorRepeat(button, direction: 1) }
        if isLanguageKey, let languageButton = button as? LongPressKeyButton {
            languageButtons.append(languageButton)
            for target in languageButton.allTargets {
                for name in languageButton.actions(forTarget: target, forControlEvent: activationEvent) ?? [] {
                    languageButton.removeTarget(target, action: NSSelectorFromString(name), for: activationEvent)
                    languageButton.addTarget(target, action: NSSelectorFromString(name), for: LongPressKeyButton.tapEvent)
                }
            }
            languageButton.shouldDeferTap = { true }
            languageButton.choicesProvider = { [weak self] in
                self?.isEditingPhrase == true ? [] : ["한국어", "日本語", "English"]
            }
            languageButton.onChoice = { [weak self] choice in
                self?.selectInputLanguage(choice == "日本語" ? .japanese : choice == "English" ? .english : .korean)
            }
            languageButton.refreshHint()
        }
        return button
    }

    @objc private func playKeyHaptic() {
        if hapticFeedbackEnabled { keyImpactFeedback.impactOccurred() }
    }

    private func applyKeyboardTheme() {
        let theme = themeSettings.read()
        themeExpiryTimer?.invalidate()
        themeExpiryTimer = nil
        if theme == .seoulDay || theme == .seoulNight {
            let remainingSeconds = Double(themeSettings.remainingSeoulUnlockMillis()) / 1_000
            if remainingSeconds > 0 {
                themeExpiryTimer = Timer.scheduledTimer(withTimeInterval: remainingSeconds, repeats: false) { [weak self] _ in
                    self?.applyKeyboardTheme()
                }
            }
        }
        let imageName: String?
        switch theme {
        case .seoulDay: imageName = "seoul_day"
        case .seoulNight: imageName = "seoul_night"
        default: imageName = nil
        }
        // Portrait night loads the wide seoul_night.jpg and crops a deterministic
        // left 8:5 region (x=0..~768 of 1440x480) so Jamsil/Lotte World Tower
        // (x~330) shows near center and Namsan (x~1210) is excluded. Day and
        // all landscape selections are unchanged.
        if theme == .seoulNight && currentOrientation() == .portrait,
           let url = Bundle.main.url(forResource: "seoul_night", withExtension: "jpg"),
           let wide = UIImage(contentsOfFile: url.path) {
            themeBackground.image = croppedSeoulNightPortraitLeft(wide)
        } else {
            let orientedImageName = imageName.map {
                currentOrientation() == .portrait ? "\($0)_portrait" : $0
            }
            if let orientedImageName,
               let url = Bundle.main.url(forResource: orientedImageName, withExtension: "jpg") {
                themeBackground.image = UIImage(contentsOfFile: url.path)
            } else {
                themeBackground.image = nil
            }
        }
        if theme == .dark || theme == .seoulNight {
            view.backgroundColor = UIColor(red: 0.10, green: 0.15, blue: 0.24, alpha: 1)
        } else {
            view.backgroundColor = UIColor(red: 0.92, green: 0.93, blue: 0.95, alpha: 1)
        }
        let effectiveTheme: KeyboardTheme = theme == .system ? (traitCollection.userInterfaceStyle == .dark ? .dark : .light) : theme
        if let palette = KeyboardThemeCatalog.shared.entries.first(where: { $0.id == effectiveTheme.rawValue })?.palette {
            flickKeys.forEach { $0.applyPalette(palette) }
        }
        for button in themeButtons {
            guard let original = originalButtonStyles[ObjectIdentifier(button)] else { continue }
            button.layer.sublayers?.first(where: { $0.name == "seoulGlass" })?.removeFromSuperlayer()
            button.layer.shadowOpacity = 0
            button.layer.cornerRadius = 7
            button.layer.borderWidth = 0.7
            switch theme {
            case .seoulNight:
                styleSeoulKey(button, night: true)
            case .seoulDay:
                styleSeoulKey(button, night: false)
            case .dark:
                button.backgroundColor = UIColor(red: 0.18, green: 0.22, blue: 0.27, alpha: 1)
                button.setTitleColor(.white, for: .normal)
                button.layer.borderColor = UIColor(red: 0.38, green: 0.43, blue: 0.48, alpha: 1).cgColor
            default:
                button.backgroundColor = original.0
                button.setTitleColor(original.1, for: .normal)
                button.layer.borderColor = original.2?.cgColor
            }
        }
    }

    private func croppedSeoulNightPortraitLeft(_ source: UIImage) -> UIImage {
        guard let image = source.cgImage else { return source }
        let cropWidth = min(image.width, image.height * 8 / 5)
        guard cropWidth > 0, cropWidth < image.width,
              let cropped = image.cropping(to: CGRect(x: 0, y: 0, width: cropWidth, height: image.height)) else {
            return source
        }
        return UIImage(cgImage: cropped, scale: source.scale, orientation: source.imageOrientation)
    }

    private func styleSeoulKey(_ button: UIButton, night: Bool) {
        let title = button.currentTitle ?? ""
        let accent = title == "↵" || title == "return"
        let control = accent || ["←", "→", "⌫", "?123", "123", "한/日", "␣", "space", "Shift", "🌐"].contains(title)
        let gradient = CAGradientLayer()
        gradient.name = "seoulGlass"
        gradient.frame = button.bounds
        gradient.cornerRadius = 10
        gradient.masksToBounds = true
        gradient.colors = (night
            ? (accent
                ? [UIColor(red: 0.43, green: 0.53, blue: 0.78, alpha: 0.87), UIColor(red: 0.26, green: 0.32, blue: 0.59, alpha: 0.71)]
                : control
                    ? [UIColor(red: 0.33, green: 0.38, blue: 0.58, alpha: 0.72), UIColor(red: 0.18, green: 0.24, blue: 0.43, alpha: 0.61)]
                    : [UIColor(red: 0.61, green: 0.63, blue: 0.78, alpha: 0.69), UIColor(red: 0.27, green: 0.34, blue: 0.56, alpha: 0.56)])
            : (accent
                ? [UIColor(red: 0.59, green: 0.80, blue: 0.92, alpha: 0.93), UIColor(red: 0.29, green: 0.59, blue: 0.78, alpha: 0.80)]
                : control
                    ? [UIColor(red: 0.96, green: 0.99, blue: 1, alpha: 0.89), UIColor(red: 0.70, green: 0.85, blue: 0.94, alpha: 0.73)]
                    : [UIColor(red: 1, green: 1, blue: 0.99, alpha: 0.87), UIColor(red: 0.86, green: 0.94, blue: 0.98, alpha: 0.68)])
            ).map(\.cgColor)
        button.backgroundColor = .clear
        button.layer.insertSublayer(gradient, at: 0)
        button.layer.cornerRadius = 10
        button.layer.borderWidth = 1
        button.layer.borderColor = (night
            ? UIColor(red: 0.77, green: 0.82, blue: 1, alpha: 0.88)
            : UIColor(red: 0.49, green: 0.70, blue: 0.83, alpha: 0.88)).cgColor
        button.layer.shadowColor = (night ? UIColor(red: 0.04, green: 0.06, blue: 0.17, alpha: 1)
            : UIColor(red: 0.31, green: 0.45, blue: 0.56, alpha: 1)).cgColor
        button.layer.shadowOpacity = night ? 0.37 : 0.20
        button.layer.shadowRadius = 3
        button.layer.shadowOffset = CGSize(width: 0, height: 2)
        button.setTitleColor(night ? .white : UIColor(red: 0.10, green: 0.22, blue: 0.32, alpha: 1), for: .normal)
    }

    private func makeCharacterButton(baseLabel: String) -> UIButton {
        let button = makeButton(title: baseLabel, action: #selector(handleCharacter(_:)))
        button.accessibilityLabel = baseLabel
        configureLongPress(button, layout: "qwerty", keyId: qwertyKeyId(baseLabel))
        characterButtons.append(button)
        if TwoBeolsikLayout.hasShiftVariant(baseLabel) {
            shiftedCharacterButtons.append((button, baseLabel))
        }
        return button
    }

    @objc private func handleCharacter(_ sender: UIButton) {
        guard let title = sender.currentTitle, !title.isEmpty else { return }

        if inputLanguage == .english && !symbolPage {
            if isEditingPhrase { appendDirectlyToPhraseDraft(title) }
            else {
                commitPendingComposition()
                clearCandidateTracking()
                mutateDocument { textDocumentProxy.insertText(title) }
            }
            if shiftEnabled { setShift(false) }
            return
        }

        if isEditingPhrase {
            inputPhraseDraft(title)
            if shiftEnabled { setShift(false) }
            return
        }

        if symbolPage {
            commitPendingComposition()
            clearCandidateTracking()
            mutateDocument { textDocumentProxy.insertText(title) }
            return
        }

        guard let character = title.first else { return }
        let edit = composer.input(character)
        candidateInput.apply(edit)
        apply(edit)
        refreshCandidates()
        if shiftEnabled { setShift(false) }
    }

    @objc private func handleCheonjiinKey(_ sender: UIButton) {
        guard CheonjiinKey.allCases.indices.contains(sender.tag) else { return }
        let key = CheonjiinKey.allCases[sender.tag]
        let action = cheonjiinInput.input(key, at: ProcessInfo.processInfo.systemUptime, cycleTimeout: Double(layoutSettings.cycleTimeout) / 1000)
        applyCheonjiinAction(action)
    }

    @objc private func handleDirectInput(_ sender: UIButton) {
        guard let title = sender.currentTitle, !title.isEmpty else { return }
        if isEditingPhrase {
            appendDirectlyToPhraseDraft(title)
            return
        }
        commitPendingComposition()
        clearCandidateTracking()
        mutateDocument { textDocumentProxy.insertText(title) }
    }

    @objc private func handleShift() {
        if symbolPage {
            symbolBank = (symbolBank + 1) % SymbolLayout.pages.count
            refreshCharacterLabels()
            return
        }
        setShift(!shiftEnabled)
    }

    @objc private func handlePageToggle() {
        if isEditingPhrase {
            commitPhraseDraftComposition()
            setShift(false)
            setSymbolPage(!symbolPage)
            return
        }
        commitPendingComposition()
        clearCandidateTracking()
        setShift(false)
        setSymbolPage(!symbolPage)
    }

    @objc private func handlePageLongPress(_ recognizer: UILongPressGestureRecognizer) {
        guard recognizer.state == .began else { return }
        handleSettingsToggle()
    }

    @objc private func handleSettingsToggle() { showSharedSettings(themes: false) }
    @objc private func handleThemes() { showSharedSettings(themes: true) }
    @objc private func handleDismiss() { stopCursorRepeat(); commitPendingComposition(); dismissKeyboard() }

    private func reloadInputLayout() {
        inputLanguage = KeyboardInputModeSettings().language()
        japaneseCandidateMode = inputLanguage == .japanese
        hapticFeedbackEnabled = layoutSettings.hapticFeedbackEnabled
        switch layoutSettings.inputLayout {
        case "cheonjiin_plus": koreanLayout = .cheonjiinPlus
        case "qwerty": koreanLayout = .qwerty
        case "hangul_flick": koreanLayout = .flick
        default: koreanLayout = .cheonjiin
        }
        flickKeys.forEach { $0.threshold = CGFloat(layoutSettings.flickDistance) }
        refreshCharacterLabels()
        themeButtons.compactMap { $0 as? LongPressKeyButton }.forEach { $0.refreshHint() }
        refreshNativeChrome()
        updateVisibleLayout()
    }

    private func showSharedSettings(themes: Bool) {
        stopCursorRepeat()
        guard settingsController == nil, !isEditingPhrase else { return }
        commitPendingComposition()
        clearCandidateTracking()
        // When our containing app hosts the keyboard, route to its same full-screen settings.
        // Other host apps show the identical common settings embedded in the keyboard area.
        let name = themes ? "com.ghtnql.kkkeyboard.openThemes" : "com.ghtnql.kkkeyboard.openSettings"
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(name as CFString), nil, nil, true)
        settingsPlatform.onKeyboardPicker = { [weak self] in self?.advanceToNextInputMode() }
        settingsPlatform.onSettingsChanged = { [weak self] in
            self?.reloadInputLayout()
            self?.applyKeyboardTheme()
            self?.refreshNativeChrome()
        }
        let controller = MainViewControllerKt.KeyboardSettingsViewController(platform: settingsPlatform, themes: themes, onClose: { [weak self] in
            self?.closeSharedSettings()
            return ()
        })
        settingsController = controller
        addChild(controller)
        controller.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(controller.view)
        NSLayoutConstraint.activate([
            controller.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            controller.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            controller.view.topAnchor.constraint(equalTo: view.topAnchor),
            controller.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        controller.didMove(toParent: self)
        keyboardStack.isHidden = true
        keyboardHeightConstraint?.constant = CGFloat(max(layoutSettings.profile(for: layoutOrientation).height, 360))
    }

    func closeSharedSettings() {
        settingsController?.willMove(toParent: nil)
        settingsController?.view.removeFromSuperview()
        settingsController?.removeFromParent()
        settingsController = nil
        settingsPlatform.onKeyboardPicker = nil
        settingsPlatform.onSettingsChanged = nil
        refreshNativeChrome()
        keyboardStack.isHidden = false
        reloadInputLayout()
        applyKeyboardTheme()
        applyLayoutProfile(for: currentOrientation())
    }

    private func configureFlickRows() {
        let bridge = FlickLayoutBridge()
        let totalUnits: CGFloat = 1.1 + 1.9 * 3.0 + 1.1
        let sideMultiplier = 1.1 / totalUnits
        let centerMultiplier = 1.9 / totalUnits
        let leftTitles = ["←", "→", "?123", "한/日"]
        let leftActions = [NSSelectorFromString("handleCursorLeft"),
                           NSSelectorFromString("handleCursorRight"),
                           NSSelectorFromString("handlePageToggle"),
                           NSSelectorFromString("handleModeToggle")]
        for rowIndex in 0..<4 {
            let row = makeRow()
            row.distribution = .fill
            let left = makeButton(title: leftTitles[rowIndex], action: leftActions[rowIndex])
            styleFlickSideButton(left, rowIndex: rowIndex)
            row.addArrangedSubview(left)
            constrainFlickCell(left, in: row, multiplier: sideMultiplier)
            for column in 0..<3 {
                let key = bridge.key(row: Int32(rowIndex), column: Int32(column))
                let control = SharedFlickKeyControl(key: key) { [weak self] label in
                    guard let self else { return }
                    self.playKeyHaptic()
                    let button = UIButton(); button.setTitle(label, for: .normal)
                    self.handleCharacter(button)
                }
                control.translatesAutoresizingMaskIntoConstraints = false
                flickKeys.append(control)
                row.addArrangedSubview(control)
                constrainFlickCell(control, in: row, multiplier: centerMultiplier)
            }
            if rowIndex == 0 {
                let backspace = makeBackspaceButton()
                styleFlickSideButton(backspace, rowIndex: rowIndex)
                backspace.translatesAutoresizingMaskIntoConstraints = false
                row.addArrangedSubview(backspace)
                constrainFlickCell(backspace, in: row, multiplier: sideMultiplier)
            } else if rowIndex == 1 {
                let ret = makeButton(title: "↵", action: NSSelectorFromString("handleReturn"))
                ret.backgroundColor = UIColor(red: 0.12, green: 0.43, blue: 0.35, alpha: 1.0)
                ret.setTitleColor(.white, for: .normal)
                ret.translatesAutoresizingMaskIntoConstraints = false
                row.addArrangedSubview(ret)
                constrainFlickCell(ret, in: row, multiplier: sideMultiplier)
            } else if rowIndex == 2 {
                let pkey = bridge.punctuation()
                let punct = SharedFlickKeyControl(key: pkey) { [weak self] label in
                    guard let self else { return }
                    self.playKeyHaptic()
                    let button = UIButton(); button.setTitle(label, for: .normal)
                    self.handleDirectInput(button)
                }
                punct.translatesAutoresizingMaskIntoConstraints = false
                flickKeys.append(punct)
                row.addArrangedSubview(punct)
                constrainFlickCell(punct, in: row, multiplier: sideMultiplier)
            } else {
                let space = makeButton(title: "␣", action: NSSelectorFromString("handleSpace"))
                space.accessibilityLabel = ui("공백", "スペース", "Space")
                styleFlickSideButton(space, rowIndex: rowIndex)
                space.translatesAutoresizingMaskIntoConstraints = false
                row.addArrangedSubview(space)
                constrainFlickCell(space, in: row, multiplier: sideMultiplier)
            }
            // Mode key title stays fixed 한/日 in both modes to match Android;
            // the top modeLabel already reflects the selected language.
            keyboardStack.addArrangedSubview(row)
            flickRows.append(row)
        }
    }

    private func constrainFlickCell(_ cell: UIView, in row: UIStackView, multiplier: CGFloat) {
        cell.translatesAutoresizingMaskIntoConstraints = false
        // The five cell widths share only the space remaining after four gutters.
        cell.widthAnchor.constraint(equalTo: row.widthAnchor, multiplier: multiplier,
                                    constant: -4 * row.spacing * multiplier).isActive = true
    }

    private func styleFlickSideButton(_ button: UIButton, rowIndex: Int) {
        // Side grey family 0.85/0.87/0.90; mode key keeps the default grey.
        button.backgroundColor = UIColor(red: 0.85, green: 0.87, blue: 0.90, alpha: 1.0)
    }


    @objc private func handleSettingsOrientationToggle() {
        settingsTargetOrientation = settingsTargetOrientation == .portrait ? .landscape : .portrait
        refreshSettingsControls()
    }

    @objc private func handleHeightCycle() {
        let target = settingsTargetOrientation
        let nextHeight = layoutSettings.nextHeight(for: target)
        layoutSettings.setHeight(nextHeight, for: target)
        if target == layoutOrientation {
            applyLayoutProfile(for: layoutOrientation)
        } else {
            refreshSettingsControls()
        }
    }

    @objc private func handleNumberRowToggle() {
        let target = settingsTargetOrientation
        let profile = layoutSettings.profile(for: target)
        layoutSettings.setNumberRowEnabled(!profile.numberRowEnabled, for: target)
        if target == layoutOrientation {
            applyLayoutProfile(for: layoutOrientation)
        } else {
            refreshSettingsControls()
        }
    }

    @objc private func handleCursorRowToggle() {
        let target = settingsTargetOrientation
        let profile = layoutSettings.profile(for: target)
        layoutSettings.setCursorRowEnabled(!profile.cursorRowEnabled, for: target)
        if target == layoutOrientation {
            applyLayoutProfile(for: layoutOrientation)
        } else {
            refreshSettingsControls()
        }
    }

    @objc private func handleCursorLeft() {
        moveCursor(by: -1)
    }

    @objc private func handleCursorRight() {
        if japaneseCandidateMode && usesCheonjiin && !symbolPage &&
            !isEditingPhrase && !cheonjiinJamo.isEmpty {
            cheonjiinSyllableBreaks.insert(cheonjiinJamo.count)
            cheonjiinInput.reset()
            return
        }
        moveCursor(by: 1)
    }

    @objc private func handleSpace() {
        if isEditingPhrase {
            appendDirectlyToPhraseDraft(" ")
            return
        }
        let source = japaneseCandidateMode && !symbolPage ? currentCandidateSource() : ""
        let keepPhrase = source.count + 1 <= JapaneseTransliterator.maxInputLength &&
            !JapaneseTransliterator.candidatesExact(for: source).isEmpty
        commitPendingComposition()
        mutateDocument { textDocumentProxy.insertText(" ") }
        if keepPhrase {
            if usesCheonjiin {
                cheonjiinCandidatePrefix = source + " "
            } else {
                candidateInput.replaceCurrent(with: source + " ")
            }
            hideCandidateDisplay()
        } else {
            clearCandidateTracking()
        }
    }

    @objc private func handleReturn() {
        if isEditingPhrase {
            if phraseDraftEditingTitle {
                selectPhraseDraftField(title: false)
            } else {
                appendDirectlyToPhraseDraft("\n")
            }
            return
        }
        commitPendingComposition()
        clearCandidateTracking()
        mutateDocument { textDocumentProxy.insertText("\n") }
    }

    @objc private func handleBackspace() {
        if isEditingPhrase {
            if usesCheonjiin && !symbolPage {
                backspaceCheonjiinPhraseDraft()
                return
            }
            backspacePhraseDraft()
            return
        }
        if usesCheonjiin && !symbolPage {
            if let action = cheonjiinInput.backspace() {
                applyCheonjiinAction(action)
                return
            }
            if !cheonjiinJamo.isEmpty {
                applyCheonjiinAction(.removeLast)
                return
            }
            mutateDocument { textDocumentProxy.deleteBackward() }
            clearCandidateTracking()
            return
        }
        let edit = composer.backspace()
        if edit.consumed {
            apply(edit)
        } else {
            mutateDocument { textDocumentProxy.deleteBackward() }
            candidateInput.removeCommittedCharacter()
        }
        refreshCandidates()
    }

    @objc private func handleBackspaceLongPress(_ recognizer: UILongPressGestureRecognizer) {
        switch recognizer.state {
        case .began:
            playKeyHaptic()
            handleBackspace()
            stopBackspaceRepeat()
            backspaceRepeatTimer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) {
                [weak self] _ in self?.handleBackspace()
            }
        case .ended, .cancelled, .failed:
            stopBackspaceRepeat()
        default:
            break
        }
    }

    private func stopBackspaceRepeat() {
        backspaceRepeatTimer?.invalidate()
        backspaceRepeatTimer = nil
    }

    @objc private func handleNextKeyboard() {
        stopCursorRepeat()
        commitPhraseDraftComposition()
        commitPendingComposition()
        clearCandidateTracking()
        resetCheonjiinState()
        advanceToNextInputMode()
    }

    @objc private func handleModeToggle() {
        if isEditingPhrase {
            selectPhraseDraftField(title: !phraseDraftEditingTitle)
            return
        }
        selectInputLanguage(inputLanguage.next)
    }

    private func selectInputLanguage(_ language: KeyboardInputLanguage) {
        stopCursorRepeat()
        commitPhraseDraftComposition()
        commitPendingComposition()
        clearCandidateTracking()
        resetCheonjiinState()
        setShift(false)
        inputLanguage = language
        japaneseCandidateMode = language == .japanese
        KeyboardInputModeSettings().selectLanguage(language)
        setSymbolPage(false)
        refreshCharacterLabels()
        refreshNativeChrome()
        modeButton?.setTitle(language == .english ? "한글" : language == .japanese ? "ABC" : "한/日", for: .normal)
        applyLayoutProfile(for: layoutOrientation)
    }

    private func refreshCharacterLabels() {
        let bank = SymbolLayout.pages[symbolBank]
        let labels = symbolPage ? bank.top + Array(bank.middle.prefix(9)) + bank.bottom : inputLanguage == .english
            ? LatinQwertyLayout.characterRows.flatMap { $0 } + LatinQwertyLayout.bottomRow
            : hangulLabels
        for (button, base) in zip(characterButtons, labels) {
            let label = symbolPage ? base : inputLanguage == .english
                ? LatinQwertyLayout.label(for: base, shifted: shiftEnabled)
                : TwoBeolsikLayout.label(for: base, shifted: shiftEnabled)
            button.setTitle(label, for: .normal)
            button.accessibilityLabel = label
        }
        pageButton?.setTitle(symbolPage ? (inputLanguage == .english ? "ABC" : "가나다") : "?123", for: .normal)
        symbolMiddleButton?.isHidden = !symbolPage
        symbolMiddleButton?.setTitle(bank.middle.last, for: .normal)
        symbolMiddleButton?.accessibilityLabel = bank.middle.last
        qwertyMiddleSpacers.forEach { $0.isHidden = symbolPage }
        shiftButton?.setTitle(symbolPage ? "\(symbolBank + 1)/\(SymbolLayout.pages.count)" : "⇧", for: .normal)
        shiftButton?.accessibilityLabel = symbolPage ? ui("기호 페이지", "記号ページ", "Symbol page") : "Shift"
        if toolbarCursorButtons.count >= 2 {
            toolbarCursorButtons[0].setTitle(symbolPage ? "◀" : "←", for: .normal)
            toolbarCursorButtons[1].setTitle(symbolPage ? "▶" : "→", for: .normal)
        }
    }

    @objc private func handleCandidate(_ sender: UIButton) {
        guard let candidate = sender.currentTitle, !candidate.isEmpty else { return }
        selectCandidate(candidate)
    }

    @objc private func handlePhraseBrowser() {
        stopCursorRepeat()
        commitPendingComposition()
        clearCandidateTracking()
        settingsVisible = false
        settingsRow.isHidden = true
        refreshPhraseBrowser()
        phraseBrowser.isHidden = false
        view.bringSubviewToFront(phraseBrowser)
    }

    @objc private func handlePhraseBrowserClose() {
        phraseBrowser.isHidden = true
        applyLayoutProfile(for: currentOrientation())
    }

    @objc private func handlePhraseCreate() {
        guard phraseStore.phrases().count < UserPhraseStore.maximumPhraseCount else {
            showPhraseBrowserMessage(ui("문구는 최대 \(UserPhraseStore.maximumPhraseCount)개까지 저장할 수 있습니다.", "定型文は最大\(UserPhraseStore.maximumPhraseCount)件まで保存できます。", "Save up to \(UserPhraseStore.maximumPhraseCount) phrases."))
            return
        }
        beginPhraseEditing(nil)
    }

    @objc private func handlePhraseInsert(_ sender: UIButton) {
        let phrases = phraseStore.phrases()
        guard phrases.indices.contains(sender.tag) else { return }
        phraseBrowser.isHidden = true
        insertPhrase(phrases[sender.tag])
        applyLayoutProfile(for: currentOrientation())
    }

    @objc private func handlePhraseEdit(_ sender: UIButton) {
        let phrases = phraseStore.phrases()
        guard phrases.indices.contains(sender.tag) else { return }
        beginPhraseEditing(phrases[sender.tag])
    }

    @objc private func handlePhraseTitleField() {
        selectPhraseDraftField(title: true)
    }

    @objc private func handlePhraseContentField() {
        selectPhraseDraftField(title: false)
    }

    @objc private func handlePhraseSave() {
        commitPhraseDraftComposition()
        do {
            if let id = phraseDraftID {
                try phraseStore.update(id: id, title: phraseDraftTitle, content: phraseDraftContent)
            } else {
                try phraseStore.create(title: phraseDraftTitle, content: phraseDraftContent)
            }
            finishPhraseEditing(showBrowser: true)
        } catch let error as UserPhraseStoreError {
            phraseStatusRefresh = { [weak self] in
                guard let self else { return }
                self.phraseEditorStatusLabel.text = self.phraseErrorMessage(error)
                self.phraseEditorStatusLabel.textColor = .systemRed
            }
            phraseStatusRefresh?()
        } catch {
            showPhraseStatus("문구를 저장하지 못했습니다.", "定型文を保存できませんでした。", "Could not save the phrase.")
        }
    }

    @objc private func handlePhraseCancel() {
        finishPhraseEditing(showBrowser: true)
    }

    @objc private func handlePhraseDelete() {
        guard let id = phraseDraftID else {
            finishPhraseEditing(showBrowser: true)
            return
        }
        do {
            try phraseStore.delete(id: id)
            finishPhraseEditing(showBrowser: true)
        } catch {
            showPhraseStatus("문구를 삭제하지 못했습니다.", "定型文を削除できませんでした。", "Could not delete the phrase.")
        }
    }

    private func refreshPhraseBrowser() {
        for view in phraseListStack.arrangedSubviews {
            phraseListStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }

        let phrases = phraseStore.phrases()
        guard !phrases.isEmpty else {
            let empty = UILabel()
            empty.text = ui("+ 추가를 눌러 자주 쓰는 문구를 직접 등록하세요.", "+ 追加を押してよく使う定型文を登録してください。", "Tap + Add to save a frequently used phrase.")
            empty.textAlignment = .center
            empty.textColor = .secondaryLabel
            empty.numberOfLines = 0
            empty.heightAnchor.constraint(greaterThanOrEqualToConstant: 80).isActive = true
            phraseListStack.addArrangedSubview(empty)
            return
        }

        for (index, phrase) in phrases.enumerated() {
            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 4
            row.distribution = .fill
            let insert = makeButton(title: phrase.title, action: #selector(handlePhraseInsert(_:)))
            insert.tag = index
            insert.accessibilityLabel = ui("\(phrase.title) 문구 입력", "\(phrase.title)を入力", "Insert phrase: \(phrase.title)")
            insert.titleLabel?.numberOfLines = 1
            insert.contentHorizontalAlignment = .left
            insert.contentEdgeInsets = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 8)
            row.addArrangedSubview(insert)

            let edit = makeButton(title: ui("편집", "編集", "Edit"), action: #selector(handlePhraseEdit(_:)))
            edit.tag = index
            edit.accessibilityLabel = ui("\(phrase.title) 문구 편집", "\(phrase.title)を編集", "Edit phrase: \(phrase.title)")
            edit.widthAnchor.constraint(equalToConstant: 64).isActive = true
            row.addArrangedSubview(edit)
            row.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
            phraseListStack.addArrangedSubview(row)
        }
    }

    private func showPhraseBrowserMessage(_ message: String) {
        let label = UILabel()
        label.text = message
        label.textAlignment = .center
        label.textColor = .systemRed
        label.numberOfLines = 0
        label.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        phraseListStack.insertArrangedSubview(label, at: 0)
    }

    private func beginPhraseEditing(_ phrase: UserPhrase?) {
        stopCursorRepeat()
        phraseBrowser.isHidden = true
        commitPendingComposition()
        clearCandidateTracking()
        phraseDraftComposer.reset()
        resetCheonjiinState()
        phraseDraftRenderedComposition = ""
        phraseDraftID = phrase?.id
        phraseDraftTitle = phrase?.title ?? ""
        phraseDraftContent = phrase?.content ?? ""
        phraseDraftEditingTitle = phrase == nil
        phraseEditorPanel.isHidden = false
        phraseEditorActions.isHidden = false
        candidateRow.isHidden = true
        numberRow.isHidden = true
        cursorRow.isHidden = true
        settingsRow.isHidden = true
        settingsVisible = false
        setSymbolPage(false)
        refreshPhraseEditor()
        applyLayoutProfile(for: currentOrientation())
    }

    private func finishPhraseEditing(showBrowser: Bool) {
        commitPhraseDraftComposition()
        resetCheonjiinState()
        phraseDraftID = nil
        phraseDraftTitle = ""
        phraseDraftContent = ""
        phraseEditorPanel.isHidden = true
        phraseEditorActions.isHidden = true
        candidateSuggestionsRow.isHidden = !japaneseCandidateMode
        candidateRow.isHidden = false
        setShift(false)
        setSymbolPage(false)
        modeButton?.setTitle(nextLanguageKeyTitle, for: .normal)
        applyLayoutProfile(for: currentOrientation())

        if showBrowser {
            refreshPhraseBrowser()
            phraseBrowser.isHidden = false
            view.bringSubviewToFront(phraseBrowser)
        }
    }

    private func selectPhraseDraftField(title: Bool) {
        guard isEditingPhrase else { return }
        commitPhraseDraftComposition()
        phraseDraftEditingTitle = title
        refreshPhraseEditor()
    }

    private func inputPhraseDraft(_ text: String) {
        guard let character = text.first else { return }
        if symbolPage {
            appendDirectlyToPhraseDraft(text)
            return
        }

        let previousValue = currentPhraseDraftValue()
        let edit = phraseDraftComposer.input(character)
        var replacement = previousValue
        if !phraseDraftRenderedComposition.isEmpty, !replacement.isEmpty {
            replacement.removeLast()
        }
        replacement += edit.commit
        replacement += edit.composing ?? ""

        guard phraseDraftCanAccept(replacement) else {
            phraseDraftComposer.reset()
            phraseDraftRenderedComposition = ""
            showPhraseDraftLimit()
            return
        }
        setCurrentPhraseDraftValue(replacement)
        phraseDraftRenderedComposition = edit.composing ?? ""
        refreshPhraseEditor()
    }

    private func appendDirectlyToPhraseDraft(_ text: String) {
        commitPhraseDraftComposition()
        let replacement = currentPhraseDraftValue() + text
        guard phraseDraftCanAccept(replacement) else {
            showPhraseDraftLimit()
            return
        }
        setCurrentPhraseDraftValue(replacement)
        refreshPhraseEditor()
    }

    private func backspacePhraseDraft() {
        let edit = phraseDraftComposer.backspace()
        var replacement = currentPhraseDraftValue()
        if edit.consumed {
            if !phraseDraftRenderedComposition.isEmpty, !replacement.isEmpty {
                replacement.removeLast()
            }
            replacement += edit.composing ?? ""
            phraseDraftRenderedComposition = edit.composing ?? ""
        } else if !replacement.isEmpty {
            replacement.removeLast()
        }
        setCurrentPhraseDraftValue(replacement)
        refreshPhraseEditor()
    }

    private func commitPhraseDraftComposition() {
        if usesCheonjiin {
            resetCheonjiinState()
        }
        guard isEditingPhrase else {
            phraseDraftComposer.reset()
            phraseDraftRenderedComposition = ""
            return
        }
        _ = phraseDraftComposer.flush()
        phraseDraftRenderedComposition = ""
    }

    private func currentPhraseDraftValue() -> String {
        phraseDraftEditingTitle ? phraseDraftTitle : phraseDraftContent
    }

    private func setCurrentPhraseDraftValue(_ value: String) {
        if phraseDraftEditingTitle {
            phraseDraftTitle = value
        } else {
            phraseDraftContent = value
        }
    }

    private func phraseDraftCanAccept(_ value: String) -> Bool {
        let limit = phraseDraftEditingTitle
            ? UserPhraseStore.maximumTitleLength
            : UserPhraseStore.maximumContentLength
        return value.count <= limit
    }

    private func showPhraseDraftLimit() {
        let limit = phraseDraftEditingTitle
            ? UserPhraseStore.maximumTitleLength
            : UserPhraseStore.maximumContentLength
        showPhraseStatus("최대 \(limit)자까지 입력할 수 있습니다.", "最大\(limit)文字まで入力できます。", "Enter up to \(limit) characters.")
    }

    private func showPhraseStatus(_ ko: String, _ ja: String, _ en: String) {
        phraseStatusRefresh = { [weak self] in
            guard let self else { return }
            self.phraseEditorStatusLabel.text = self.ui(ko, ja, en)
            self.phraseEditorStatusLabel.textColor = .systemRed
        }
        phraseStatusRefresh?()
    }

    private func refreshPhraseEditor() {
        phraseStatusRefresh = nil
        let titlePreview = phraseDraftTitle.isEmpty ? ui("제목 입력", "タイトルを入力", "Enter title") : phraseDraftTitle
        let contentPreview = phraseDraftContent.isEmpty
            ? ui("내용 입력", "内容を入力", "Enter body")
            : phraseDraftContent.replacingOccurrences(of: "\n", with: " ↵ ")
        phraseTitleButton?.setTitle(ui("제목: \(titlePreview)", "タイトル: \(titlePreview)", "Title: \(titlePreview)"), for: .normal)
        phraseContentButton?.setTitle(ui("내용: \(contentPreview)", "内容: \(contentPreview)", "Body: \(contentPreview)"), for: .normal)
        phraseTitleButton?.isSelected = phraseDraftEditingTitle
        phraseContentButton?.isSelected = !phraseDraftEditingTitle
        phraseTitleButton?.backgroundColor = phraseDraftEditingTitle ? .systemBlue.withAlphaComponent(0.2) : .secondarySystemBackground
        phraseContentButton?.backgroundColor = phraseDraftEditingTitle ? .secondarySystemBackground : .systemBlue.withAlphaComponent(0.2)
        modeButton?.setTitle(phraseDraftEditingTitle ? ui("제목", "タイトル", "Title") : ui("내용", "内容", "Body"), for: .normal)
        phraseEditorStatusLabel.text = ui("제목 \(phraseDraftTitle.count)/\(UserPhraseStore.maximumTitleLength) · 내용 \(phraseDraftContent.count)/\(UserPhraseStore.maximumContentLength)", "タイトル \(phraseDraftTitle.count)/\(UserPhraseStore.maximumTitleLength) · 内容 \(phraseDraftContent.count)/\(UserPhraseStore.maximumContentLength)", "Title \(phraseDraftTitle.count)/\(UserPhraseStore.maximumTitleLength) · body \(phraseDraftContent.count)/\(UserPhraseStore.maximumContentLength)")
        phraseEditorStatusLabel.textColor = .secondaryLabel
    }

    private func phraseErrorMessage(_ error: UserPhraseStoreError) -> String {
        switch error {
        case .emptyTitle: return ui("제목을 입력하세요.", "タイトルを入力してください。", "Enter a title.")
        case .emptyContent: return ui("내용을 입력하세요.", "内容を入力してください。", "Enter the body.")
        case .titleTooLong: return ui("제목이 너무 깁니다.", "タイトルが長すぎます。", "The title is too long.")
        case .contentTooLong: return ui("내용이 너무 깁니다.", "内容が長すぎます。", "The body is too long.")
        case .phraseLimitReached: return ui("저장 가능한 문구 수를 초과했습니다.", "保存できる定型文の数を超えています。", "The phrase limit was exceeded.")
        case .phraseNotFound: return ui("문구를 찾지 못했습니다.", "定型文が見つかりませんでした。", "Could not find the phrase.")
        case .encodingFailed: return ui("문구를 저장하지 못했습니다.", "定型文を保存できませんでした。", "Could not save the phrase.")
        }
    }

    private func insertPhrase(_ phrase: UserPhrase) {
        commitPendingComposition()
        clearCandidateTracking()
        composer.reset()
        renderedComposition = ""
        mutateDocument { textDocumentProxy.insertText(phrase.content) }
    }

    private func currentOrientation() -> KeyboardOrientation {
        if let interfaceOrientation = view.window?.windowScene?.interfaceOrientation {
            return interfaceOrientation.isLandscape ? .landscape : .portrait
        }
        return UIScreen.main.bounds.width > UIScreen.main.bounds.height ? .landscape : .portrait
    }

    private func applyLayoutProfile(for orientation: KeyboardOrientation) {
        layoutOrientation = orientation
        if settingsController != nil { return }
        let profile = layoutSettings.profile(for: orientation)
        if isEditingPhrase {
            keyboardHeightConstraint?.constant = CGFloat(max(profile.height, usesCheonjiin ? 332 : 300))
            candidateSuggestionsRow.isHidden = true
            candidateRow.isHidden = true
            phraseEditorPanel.isHidden = false
            phraseEditorActions.isHidden = false
            numberRow.isHidden = true
            cursorRow.isHidden = true
            settingsRow.isHidden = true
            refreshPhraseEditor()
            updateVisibleLayout()
            return
        }

        let renderedHeight = KeyboardLayoutSettings.renderedHeight(
            requestedHeight: profile.height,
            numberRowEnabled: profile.numberRowEnabled,
            cursorRowEnabled: profile.cursorRowEnabled,
            settingsRowVisible: settingsVisible,
            candidateRowVisible: japaneseCandidateMode
        )
        let cheonjiinVisible = (usesCheonjiin || usesFlick) && !symbolPage
        keyboardHeightConstraint?.constant = CGFloat(cheonjiinVisible
            ? max(renderedHeight + 38, settingsVisible ? 364 : 318)
            : renderedHeight)
        candidateSuggestionsRow.isHidden = !japaneseCandidateMode
        candidateRow.isHidden = false
        phraseEditorPanel.isHidden = true
        phraseEditorActions.isHidden = true
        numberRow.isHidden = !profile.numberRowEnabled
        cursorRow.isHidden = true // Cursor actions now share the toolbar, as on Android.
        updateVisibleLayout()

        if !settingsVisible {
            settingsTargetOrientation = orientation
        }
        refreshSettingsControls()
    }

    private func refreshSettingsControls() {
        let profile = layoutSettings.profile(for: settingsTargetOrientation)
        let orientationLabel = settingsTargetOrientation == .portrait ? ui("세로 설정", "縦設定", "Portrait settings") : ui("가로 설정", "横設定", "Landscape settings")
        orientationButton?.setTitle(orientationLabel, for: .normal)
        orientationButton?.accessibilityValue = settingsTargetOrientation == .portrait ? ui("세로", "縦", "Portrait") : ui("가로", "横", "Landscape")
        heightButton?.setTitle(ui("높이 \(profile.height)", "高さ \(profile.height)", "Height \(profile.height)"), for: .normal)
        numberRowButton?.setTitle(profile.numberRowEnabled ? ui("숫자열 켬", "数字列オン", "Number row on") : ui("숫자열 끔", "数字列オフ", "Number row off"), for: .normal)
        cursorRowButton?.setTitle(profile.cursorRowEnabled ? ui("커서열 켬", "カーソル列オン", "Cursor row on") : ui("커서열 끔", "カーソル列オフ", "Cursor row off"), for: .normal)
    }

    private func moveCursor(by offset: Int) {
        commitPendingComposition()
        clearCandidateTracking()
        mutateDocument { textDocumentProxy.adjustTextPosition(byCharacterOffset: offset) }
    }

    private func updateVisibleLayout() {
        stopCursorRepeat()
        let cheonjiinVisible = usesCheonjiin && !symbolPage
        for row in cheonjiinRows { row.isHidden = !cheonjiinVisible || koreanLayout == .cheonjiinPlus }
        for row in plusRows { row.isHidden = inputLanguage == .english || koreanLayout != .cheonjiinPlus || symbolPage }
        for row in qwertyRows {
            row.isHidden = cheonjiinVisible || (usesFlick && !symbolPage)
        }
        for row in flickRows { row.isHidden = !usesFlick || symbolPage }
        let compactLayout = (usesCheonjiin || usesFlick) && !symbolPage
        toolbarCursorButtons.forEach { $0.isHidden = compactLayout }


    }

    private func applyCheonjiinAction(_ action: CheonjiinAction) {
        switch action {
        case .none: return
        case .append(let character): cheonjiinJamo.append(character)
        case .replaceLast(let character):
            if !cheonjiinJamo.isEmpty { cheonjiinJamo.removeLast() }
            cheonjiinJamo.append(character)
        case .removeLast:
            if !cheonjiinJamo.isEmpty { cheonjiinJamo.removeLast() }
        }
        cheonjiinSyllableBreaks = Set(cheonjiinSyllableBreaks.filter { $0 <= cheonjiinJamo.count })

        let composer = HangulComposer()
        var nextText = ""
        for (index, character) in cheonjiinJamo.enumerated() {
            if cheonjiinSyllableBreaks.contains(index) {
                nextText += composer.flush()
            }
            let edit = composer.input(character)
            nextText += edit.commit
        }
        nextText += composer.currentText()

        if isEditingPhrase {
            var replacement = currentPhraseDraftValue()
            if !cheonjiinPhraseRenderedText.isEmpty {
                replacement.removeLast(cheonjiinPhraseRenderedText.count)
            }
            replacement += nextText
            guard phraseDraftCanAccept(replacement) else {
                resetCheonjiinState()
                showPhraseDraftLimit()
                return
            }
            setCurrentPhraseDraftValue(replacement)
            cheonjiinPhraseRenderedText = nextText
            refreshPhraseEditor()
        } else {
            let oldCharacters = Array(cheonjiinRenderedText)
            let newCharacters = Array(nextText)
            var commonLength = 0
            while commonLength < min(oldCharacters.count, newCharacters.count),
                  oldCharacters[commonLength] == newCharacters[commonLength] {
                commonLength += 1
            }
            mutateDocument {
                for _ in commonLength..<oldCharacters.count { textDocumentProxy.deleteBackward() }
                let replacement = String(newCharacters.dropFirst(commonLength))
                if !replacement.isEmpty { textDocumentProxy.insertText(replacement) }
            }
            cheonjiinRenderedText = nextText
            refreshCandidates()
        }
    }

    private func backspaceCheonjiinPhraseDraft() {
        if let action = cheonjiinInput.backspace() {
            applyCheonjiinAction(action)
        } else if !cheonjiinJamo.isEmpty {
            applyCheonjiinAction(.removeLast)
        } else {
            backspacePhraseDraft()
        }
    }

    private func resetCheonjiinState() {
        cheonjiinInput.reset()
        cheonjiinJamo.removeAll(keepingCapacity: true)
        cheonjiinSyllableBreaks.removeAll(keepingCapacity: true)
        cheonjiinRenderedText = ""
        cheonjiinPhraseRenderedText = ""
        cheonjiinCandidatePrefix = ""
    }

    private func currentCandidateSource() -> String {
        if usesCheonjiin && !symbolPage {
            return cheonjiinCandidatePrefix + cheonjiinRenderedText
        }
        return candidateInput.current(composing: composer.currentText())
    }

    private func setShift(_ enabled: Bool) {
        guard shiftEnabled != enabled else { return }
        shiftEnabled = enabled
        guard !symbolPage else {
            shiftButton?.isSelected = false
            shiftButton?.accessibilityValue = "off"
            return
        }
        refreshCharacterLabels()
        shiftButton?.isSelected = enabled
        shiftButton?.accessibilityValue = enabled ? "on" : "off"
    }

    private func setSymbolPage(_ enabled: Bool) {
        guard symbolPage != enabled else { return }
        symbolPage = enabled
        symbolBank = 0
        refreshCharacterLabels()
        themeButtons.compactMap { $0 as? LongPressKeyButton }.forEach { $0.refreshHint() }
        refreshNativeChrome()
        applyLayoutProfile(for: layoutOrientation)
    }

    private func apply(_ edit: HangulEdit) {
        mutateDocument {
            if !renderedComposition.isEmpty {
                textDocumentProxy.deleteBackward()
                renderedComposition = ""
            }

            if !edit.commit.isEmpty {
                textDocumentProxy.insertText(edit.commit)
            }

            if let composing = edit.composing, !composing.isEmpty {
                textDocumentProxy.insertText(composing)
                renderedComposition = composing
            }
        }
    }

    private func refreshCandidates(force: Bool = false) {
        let candidates: [String]
        let source: String?
        if usesCheonjiin {
            let current = currentCandidateSource()
            source = !current.isEmpty && current.count <= JapaneseTransliterator.maxInputLength
                ? current : nil
        } else {
            source = candidateInput.currentForLookup(
                composing: composer.currentText(),
                maxLength: JapaneseTransliterator.maxInputLength
            )
        }
        if japaneseCandidateMode && !symbolPage, let source {
            candidates = candidateLearning.rank(
                reading: source,
                candidates: JapaneseTransliterator.suggestionsExact(for: source)
            )
        } else {
            candidates = []
        }

        if !force && candidates == displayedCandidates { return }
        displayedCandidates = candidates
        modeLabel.isHidden = false
        candidateScroll.setContentOffset(.zero, animated: false)

        for (index, button) in candidateButtons.enumerated() {
            if index < candidates.count {
                button.setTitle(candidates[index], for: .normal)
                button.isHidden = false
            } else {
                button.setTitle("", for: .normal)
                button.isHidden = true
            }
        }
    }

    private func selectCandidate(_ candidate: String) {
        let source = currentCandidateSource()
        guard !source.isEmpty,
              JapaneseTransliterator.suggestionsExact(for: source).contains(candidate) else { return }

        commitPendingComposition()
        guard textDocumentProxy.documentContextBeforeInput?.hasSuffix(source) == true else {
            clearCandidateTracking()
            return
        }
        mutateDocument {
            for _ in source {
                textDocumentProxy.deleteBackward()
            }
            textDocumentProxy.insertText(candidate)
        }
        candidateLearning.recordSelection(reading: source, candidate: candidate)
        clearCandidateTracking()
    }

    private func replaceRenderedComposition(with text: String) {
        mutateDocument {
            if !renderedComposition.isEmpty {
                textDocumentProxy.deleteBackward()
                renderedComposition = ""
            }
            if !text.isEmpty {
                textDocumentProxy.insertText(text)
            }
        }
    }

    private func commitPendingComposition() {
        if cheonjiinInput.hasPendingInput || !cheonjiinJamo.isEmpty || !cheonjiinRenderedText.isEmpty {
            resetCheonjiinState()
        }
        guard !renderedComposition.isEmpty else {
            composer.reset()
            return
        }
        replaceRenderedComposition(with: composer.flush())
    }

    private func mutateDocument(_ mutation: () -> Void) {
        isMutatingDocument = true
        defer { isMutatingDocument = false }
        mutation()
    }

    private func discardPendingStateForExternalDocumentChange() {
        guard !isMutatingDocument else { return }
        guard !renderedComposition.isEmpty || !displayedCandidates.isEmpty ||
            !cheonjiinRenderedText.isEmpty || cheonjiinInput.hasPendingInput else { return }
        composer.reset()
        renderedComposition = ""
        resetCheonjiinState()
        clearCandidateTracking()
    }

    private func clearCandidateTracking() {
        candidateInput.clear()
        cheonjiinCandidatePrefix = ""
        hideCandidateDisplay()
    }

    private func hideCandidateDisplay() {
        displayedCandidates = []
        modeLabel.isHidden = false
        for button in candidateButtons {
            button.setTitle("", for: .normal)
            button.isHidden = true
        }
    }
}
