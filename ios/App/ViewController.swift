import UIKit
import SharedUI

final class ViewController: UIViewController {
    private let languagePreferences: AppUILanguagePreferences
    private var selectedLanguage: AppUILanguage
    private var localization: AppLocalization

    private let languageTitleLabel = UILabel()
    private let languageControl = UISegmentedControl(items: [])
    private let titleLabel = UILabel()
    private let instructionLabel = UILabel()
    private let settingsButton = UIButton(type: .system)
    private let testField = UITextField()
    private let inputOrderTitleLabel = UILabel()
    private let inputOrderTable = UITableView(frame: .zero, style: .insetGrouped)
    private var inputOrder = KeyboardInputModeSettings().languageOrder()
    private(set) var sharedCoreProbeOutput = ""

    init(
        defaults: UserDefaults = .standard,
        preferredLanguages: @escaping () -> [String] = { Locale.preferredLanguages }
    ) {
        let preferences = AppUILanguagePreferences(
            defaults: defaults,
            preferredLanguages: preferredLanguages
        )
        let language = preferences.selectedLanguage()
        languagePreferences = preferences
        selectedLanguage = language
        localization = AppLocalization(language: language)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        sharedCoreProbeOutput = HangulProbe().compose(initialIndex: 0, vowelIndex: 0, finalIndex: 0)
        view.backgroundColor = .systemBackground
        configureOnboarding()
    }

    private func configureOnboarding() {
        languageTitleLabel.font = .preferredFont(forTextStyle: .headline)
        languageTitleLabel.adjustsFontForContentSizeCategory = true

        for (index, _) in AppUILanguage.allCases.enumerated() {
            languageControl.insertSegment(withTitle: nil, at: index, animated: false)
        }
        languageControl.addTarget(self, action: #selector(changeLanguage(_:)), for: .valueChanged)

        inputOrderTitleLabel.font = .preferredFont(forTextStyle: .headline)
        inputOrderTitleLabel.numberOfLines = 0
        inputOrderTable.dataSource = self
        inputOrderTable.delegate = self
        inputOrderTable.isScrollEnabled = false
        inputOrderTable.dragDelegate = self
        inputOrderTable.dropDelegate = self
        inputOrderTable.dragInteractionEnabled = true
        inputOrderTable.register(UITableViewCell.self, forCellReuseIdentifier: "input-language")

        titleLabel.font = .preferredFont(forTextStyle: .title1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.accessibilityTraits = .header

        instructionLabel.numberOfLines = 0
        instructionLabel.font = .preferredFont(forTextStyle: .body)
        instructionLabel.adjustsFontForContentSizeCategory = true

        settingsButton.configuration = .filled()
        settingsButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)

        testField.borderStyle = .roundedRect
        testField.clearButtonMode = .whileEditing
        testField.autocorrectionType = .no
        testField.autocapitalizationType = .none
        testField.returnKeyType = .done
        testField.delegate = self

        let stack = UIStackView(arrangedSubviews: [
            languageTitleLabel,
            languageControl,
            inputOrderTitleLabel,
            inputOrderTable,
            titleLabel,
            instructionLabel,
            settingsButton,
            testField
        ])
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 20
        stack.setCustomSpacing(28, after: languageControl)
        stack.setCustomSpacing(28, after: instructionLabel)

        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = false
        scrollView.keyboardDismissMode = .interactive

        // A content container closes the scroll view's vertical constraint chain.
        // Its minimum viewport height keeps short onboarding content centered,
        // while the stack's intrinsic height expands it and enables scrolling
        // for landscape or large Dynamic Type sizes.
        let contentView = UIView()
        contentView.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(scrollView)
        scrollView.addSubview(contentView)
        contentView.addSubview(stack)

        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),

            contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
            contentView.heightAnchor.constraint(greaterThanOrEqualTo: scrollView.frameLayoutGuide.heightAnchor),

            stack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -24),
            stack.topAnchor.constraint(greaterThanOrEqualTo: contentView.topAnchor, constant: 24),
            stack.bottomAnchor.constraint(lessThanOrEqualTo: contentView.bottomAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            testField.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            inputOrderTable.heightAnchor.constraint(equalToConstant: 176)
        ])

        applyLocalizedText()
    }

    private func applyLocalizedText() {
        languageTitleLabel.text = localization.string("language.setting.title")
        inputOrderTitleLabel.text = "키보드 입력 언어 순서 · 드래그하여 변경"
        languageTitleLabel.accessibilityTraits = .header

        let languageTitleKeys = [
            "language.option.korean",
            "language.option.japanese",
            "language.option.english"
        ]
        for (index, key) in languageTitleKeys.enumerated() {
            languageControl.setTitle(localization.string(key), forSegmentAt: index)
        }
        languageControl.selectedSegmentIndex = AppUILanguage.allCases.firstIndex(of: selectedLanguage) ?? 0
        languageControl.accessibilityLabel = localization.string("language.setting.accessibility")

        titleLabel.text = localization.string("onboarding.title")
        instructionLabel.text = localization.string("onboarding.instructions")
        settingsButton.configuration?.title = localization.string("onboarding.settings_button")
        testField.placeholder = localization.string("onboarding.test_placeholder")
        testField.accessibilityLabel = localization.string("onboarding.test_accessibility")
    }

    @objc private func changeLanguage(_ sender: UISegmentedControl) {
        guard AppUILanguage.allCases.indices.contains(sender.selectedSegmentIndex) else {
            return
        }

        let language = AppUILanguage.allCases[sender.selectedSegmentIndex]
        languagePreferences.select(language)
        selectedLanguage = language
        localization = AppLocalization(language: language)
        applyLocalizedText()
    }

    @objc private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString),
              UIApplication.shared.canOpenURL(url) else {
            return
        }
        UIApplication.shared.open(url)
    }
}

extension ViewController: UITableViewDataSource, UITableViewDelegate, UITableViewDragDelegate, UITableViewDropDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { inputOrder.count }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "input-language", for: indexPath)
        let language = inputOrder[indexPath.row]
        cell.textLabel?.text = language == .korean ? "한국어" : language == .japanese ? "日本語" : "English"
        cell.accessoryType = .disclosureIndicator
        cell.showsReorderControl = true
        return cell
    }
    func tableView(_ tableView: UITableView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
        let provider = NSItemProvider(object: inputOrder[indexPath.row].rawValue as NSString)
        let item = UIDragItem(itemProvider: provider)
        item.localObject = inputOrder[indexPath.row]
        return [item]
    }
    func tableView(_ tableView: UITableView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UITableViewDropProposal {
        UITableViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
    }
    func tableView(_ tableView: UITableView, performDropWith coordinator: UITableViewDropCoordinator) {
        guard let destination = coordinator.destinationIndexPath,
              let item = coordinator.items.first,
              let source = item.sourceIndexPath,
              let language = item.dragItem.localObject as? KeyboardInputLanguage else { return }
        tableView.performBatchUpdates({
            self.inputOrder.remove(at: source.row)
            let target = min(destination.row, self.inputOrder.count)
            self.inputOrder.insert(language, at: target)
            tableView.moveRow(at: source, to: IndexPath(row: target, section: 0))
        }) { [weak self] _ in
            guard let self else { return }
            KeyboardInputModeSettings().selectLanguageOrder(self.inputOrder)
        }
        coordinator.drop(item.dragItem, toRowAt: destination)
    }
}

extension ViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}
