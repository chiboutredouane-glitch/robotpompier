import UIKit

final class SettingsController: UIViewController, UITextFieldDelegate {
    private let store: ProfileStore
    private var draft: RobotProfile
    private var index = 0
    var onSaved: (() -> Void)?
    private let name = UITextField()
    private let safety = UITextField()
    private let service = UITextField()
    private let characteristic = UITextField()
    private let buttonPicker = UIButton(type: .system)
    private let buttonName = UITextField()
    private let mode = UISegmentedControl(items: ["Appui / relâchement", "Interrupteur"])
    private let down = UITextField()
    private let up = UITextField()
    init(profile: RobotProfile, store: ProfileStore) { draft = profile; self.store = store; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("Programmatic UI") }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .landscape }
    override func viewDidLoad() {
        super.viewDidLoad(); title = "Réglages"; view.backgroundColor = Theme.bg
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Annuler", style: .plain, target: self, action: #selector(cancel))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Enregistrer", style: .done, target: self, action: #selector(save))
        let scroll = UIScrollView(); scroll.keyboardDismissMode = .interactive; scroll.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(scroll)
        NSLayoutConstraint.activate([scroll.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor), scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor), scroll.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)])
        let description = Theme.label("Chaque événement envoie un caractère ASCII, sans saut de ligne. Champ vide = aucun envoi. Les noms sont indépendants des lettres.", size: 13, color: Theme.muted); description.numberOfLines = 0
        let actions = Theme.stack([action("Nouveau", #selector(newProfile)), action("Copier", #selector(copyProfile)), action("Supprimer", #selector(deleteProfile))]); actions.distribution = .fillEqually
        let form = Theme.stack([description, actions, field("Nom du profil", name), field("Arrêt de sécurité", safety), Theme.label("Bluetooth BLE · MLT-BT05", size: 15), field("Service BLE", service), field("Caractéristique d’écriture", characteristic), Theme.label("Bouton à configurer", size: 14), buttonPicker, field("Nom affiché", buttonName), mode, field("À l’appui / Activation", down), field("Au relâchement / Désactivation", up)], axis: .vertical, spacing: 8)
        form.translatesAutoresizingMaskIntoConstraints = false; scroll.addSubview(form)
        NSLayoutConstraint.activate([form.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 14), form.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -14), form.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 10), form.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -20), form.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -28)])
        buttonPicker.heightAnchor.constraint(equalToConstant: 44).isActive = true; buttonPicker.backgroundColor = Theme.panel; buttonPicker.layer.cornerRadius = 10; buttonPicker.showsMenuAsPrimaryAction = true
        mode.heightAnchor.constraint(equalToConstant: 36).isActive = true
        name.accessibilityIdentifier = "profile_name"; buttonName.accessibilityIdentifier = "button_label"
        down.accessibilityIdentifier = "command_down"; up.accessibilityIdentifier = "command_up"
        load()
    }
    private func field(_ caption: String, _ input: UITextField) -> UIView {
        input.borderStyle = .roundedRect; input.backgroundColor = Theme.panel; input.textColor = .white
        input.autocapitalizationType = .none; input.autocorrectionType = .no; input.spellCheckingType = .no
        input.smartQuotesType = .no; input.smartDashesType = .no; input.delegate = self
        input.heightAnchor.constraint(equalToConstant: 40).isActive = true
        return Theme.stack([Theme.label(caption, size: 12, color: Theme.muted), input], axis: .vertical, spacing: 3)
    }
    private func action(_ title: String, _ selector: Selector) -> UIButton { let b = Theme.button(title); b.addTarget(self, action: selector, for: .touchUpInside); return b }
    private func captureButton() {
        draft.buttons[index].label = buttonName.text ?? ""; draft.buttons[index].mode = mode.selectedSegmentIndex == 1 ? .toggle : .hold
        draft.buttons[index].down = down.text ?? ""; draft.buttons[index].up = up.text ?? ""
    }
    private func capture() {
        captureButton(); draft.name = name.text ?? ""; draft.safety = safety.text ?? ""
        draft.service = service.text ?? ""; draft.characteristic = characteristic.text ?? ""
    }
    private func load() {
        name.text = draft.name; safety.text = draft.safety; service.text = draft.service; characteristic.text = draft.characteristic
        loadButton()
    }
    private func loadButton() {
        let b = draft.buttons[index]; buttonName.text = b.label; down.text = b.down; up.text = b.up
        mode.selectedSegmentIndex = b.mode == .toggle ? 1 : 0; buttonPicker.setTitle(b.label + " ▾", for: .normal)
        buttonPicker.menu = UIMenu(children: draft.buttons.enumerated().map { i, b in UIAction(title: b.label, state: i == index ? .on : .off) { [weak self] _ in
            guard let self else { return }; self.captureButton(); self.index = i; self.loadButton()
        } })
    }
    @objc private func cancel() { view.endEditing(true); dismiss(animated: true) }
    @objc private func save() {
        capture()
        do {
            draft.service = try normalizedUUID(draft.service); draft.characteristic = try normalizedUUID(draft.characteristic)
            try store.save(draft); onSaved?(); dismiss(animated: true)
        } catch { errorAlert(error.localizedDescription) }
    }
    @objc private func newProfile() {
        let a = UIAlertController(title: "Nouveau profil", message: "Il sera enregistré avec le bouton Enregistrer.", preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "Base Hum-Bot", style: .default) { [weak self] _ in self?.draft = .humBot(name: "Nouveau robot"); self?.index = 0; self?.load() })
        a.addAction(UIAlertAction(title: "Fonctions sans commande", style: .default) { [weak self] _ in self?.draft = .blank(name: "Nouveau robot"); self?.index = 0; self?.load() })
        a.addAction(UIAlertAction(title: "Annuler", style: .cancel)); present(a, animated: true)
    }
    @objc private func copyProfile() { capture(); draft.id = UUID(); draft.name = String((draft.name + " copie").prefix(40)); load() }
    @objc private func deleteProfile() {
        guard store.profiles.contains(where: { $0.id == draft.id }) else { errorAlert("Ce profil n’est pas encore enregistré."); return }
        let a = UIAlertController(title: "Supprimer ce profil ?", message: draft.name, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "Annuler", style: .cancel))
        a.addAction(UIAlertAction(title: "Supprimer", style: .destructive) { [weak self] _ in
            guard let self else { return }
            do { try self.store.delete(self.draft.id); self.draft = self.store.selected; self.index = 0; self.load(); self.onSaved?() }
            catch { self.errorAlert(error.localizedDescription) }
        }); present(a, animated: true)
    }
    private func errorAlert(_ text: String) {
        let a = UIAlertController(title: "Vérifiez les réglages", message: text, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "OK", style: .default)); present(a, animated: true)
    }
    func textFieldShouldReturn(_ textField: UITextField) -> Bool { textField.resignFirstResponder(); return true }
}
