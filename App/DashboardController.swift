import UIKit
import CoreBluetooth

final class DashboardController: UIViewController {
    private let store: ProfileStore
    private var profile: RobotProfile
    private(set) var engine: ControlEngine!
    private var link: RobotLink?
    private var ble: BLELink?
    private var phase = LinkPhase.disconnected
    private var lastTX: [String] = []
    private let profileButton = Theme.button("Hum-Bot")
    private let connectButton = Theme.button("Connecter", color: Theme.cyan)
    private let status = Theme.button("● Déconnecté", color: Theme.red)
    private let tx = Theme.label("TX : —", size: 11)
    private var controls: [String: RemoteButton] = [:]
    private var center = Theme.label("Arrêt : S", size: 10, color: Theme.muted)
    private let footer = Theme.label("Noms et commandes personnalisables dans Réglages", size: 9, color: Theme.muted)
    private weak var picker: DevicePickerController?
    private let simulation: Bool
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    init(store: ProfileStore = ProfileStore(), link: RobotLink? = nil, simulation: Bool = false) {
        self.store = store; self.profile = store.selected; self.link = link; self.simulation = simulation
        super.init(nibName: nil, bundle: nil); makeEngine()
    }
    required init?(coder: NSCoder) { fatalError("Programmatic UI") }
    deinit {
        let task = backgroundTask
        if task != .invalid { DispatchQueue.main.async { UIApplication.shared.endBackgroundTask(task) } }
    }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .landscape }
    private func makeEngine() { engine = try! ControlEngine(profile: profile) { [weak self] byte in self?.link?.send(byte) } }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = Theme.bg
        // Simulator and tests do not request radio access. The actual device initializes on Connect.
        if ProcessInfo.processInfo.arguments.contains("--preview") { profile = .humBot(); makeEngine() }
        buildUI(); refresh()
        if let link { link.onState = { [weak self] state in self?.render(state) } }
        if let warning = store.warning { DispatchQueue.main.async { [weak self] in self?.message("Vérifiez les réglages", warning) } }
    }
    private func buildUI() {
        let title = Theme.label("TÉLÉCOMMANDE\nUNIVERSELLE", size: 15); title.font = .systemFont(ofSize: 15, weight: .heavy)
        title.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        profileButton.addTarget(self, action: #selector(profiles), for: .touchUpInside)
        let settings = Theme.button("⚙ Réglages"); settings.accessibilityIdentifier = "settings"
        settings.addTarget(self, action: #selector(settingsTapped), for: .touchUpInside)
        let end = Theme.button("Fin"); end.addTarget(self, action: #selector(endTapped), for: .touchUpInside)
        let header = Theme.stack([title, profileButton, settings, end]); header.heightAnchor.constraint(equalToConstant: 44).isActive = true
        profileButton.widthAnchor.constraint(equalTo: title.widthAnchor).isActive = true
        connectButton.addTarget(self, action: #selector(connectTapped), for: .touchUpInside); connectButton.accessibilityIdentifier = "connect"
        let badge = Theme.label("BLE", size: 12, color: Theme.green)
        status.addTarget(self, action: #selector(diagnostic), for: .touchUpInside); status.layer.borderWidth = 0
        status.titleLabel?.font = .systemFont(ofSize: 11); status.titleLabel?.textAlignment = .left
        status.contentHorizontalAlignment = .leading; status.accessibilityIdentifier = "status"
        status.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        tx.font = .monospacedSystemFont(ofSize: 11, weight: .regular); tx.widthAnchor.constraint(equalToConstant: 106).isActive = true
        let connection = Theme.stack([connectButton, badge, status, tx]); connection.heightAnchor.constraint(equalToConstant: 44).isActive = true
        for (i, config) in profile.buttons.enumerated() {
            let b = RemoteButton(frame: .zero); b.accessibilityIdentifier = config.id
            b.backgroundColor = i < 4 ? Theme.yellow : Theme.panel
            b.setTitleColor(i < 4 ? Theme.bg : .white, for: .normal); b.setTitleColor(Theme.muted, for: .disabled)
            b.onDown = { [weak self] in self?.engine.down(config.id); self?.refreshStates() }
            b.onUp = { [weak self] in self?.engine.up(config.id); self?.refreshStates() }
            b.onClick = { [weak self] in self?.engine.click(config.id); self?.refreshStates() }
            controls[config.id] = b
        }
        let pad = UIView(); pad.isMultipleTouchEnabled = true; center.textAlignment = .center
        let positions = [(1,0),(1,2),(0,1),(2,1)]
        for (i, pos) in positions.enumerated() {
            let b = controls[RobotProfile.ids[i]]!; b.translatesAutoresizingMaskIntoConstraints = false; pad.addSubview(b)
            NSLayoutConstraint.activate([b.widthAnchor.constraint(equalTo: pad.widthAnchor, multiplier: 1.0/3.0, constant: -5), b.heightAnchor.constraint(equalTo: pad.heightAnchor, multiplier: 1.0/3.0, constant: -3)])
            // Use layout guides so the cross scales without fixed screen dimensions.
            let guide = UILayoutGuide(); pad.addLayoutGuide(guide)
            NSLayoutConstraint.activate([guide.leadingAnchor.constraint(equalTo: pad.leadingAnchor), guide.topAnchor.constraint(equalTo: pad.topAnchor), guide.widthAnchor.constraint(equalTo: pad.widthAnchor, multiplier: CGFloat(pos.0 * 2 + 1)/6), guide.heightAnchor.constraint(equalTo: pad.heightAnchor, multiplier: CGFloat(pos.1 * 2 + 1)/6), b.centerXAnchor.constraint(equalTo: guide.trailingAnchor), b.centerYAnchor.constraint(equalTo: guide.bottomAnchor)])
        }
        center.translatesAutoresizingMaskIntoConstraints = false; pad.addSubview(center)
        NSLayoutConstraint.activate([center.centerXAnchor.constraint(equalTo: pad.centerXAnchor), center.centerYAnchor.constraint(equalTo: pad.centerYAnchor), center.widthAnchor.constraint(equalTo: pad.widthAnchor, multiplier: 1.0/3.0, constant: -5)])
        let rows = (0..<2).map { r -> UIStackView in
            let row = Theme.stack((0..<2).map { controls[RobotProfile.ids[4 + r*2 + $0]]! }); row.distribution = .fillEqually; return row
        }
        let functions = Theme.stack(rows, axis: .vertical); functions.distribution = .fillEqually
        let body = Theme.stack([Theme.panel(pad, caption: "DÉPLACEMENT"), Theme.panel(functions, caption: "FONCTIONS")], spacing: 9); body.distribution = .fillEqually
        footer.textAlignment = .center; footer.heightAnchor.constraint(equalToConstant: 16).isActive = true
        let root = Theme.stack([header, connection, body, footer], axis: .vertical, spacing: 5)
        root.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(root)
        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([root.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 8), root.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -8), root.topAnchor.constraint(equalTo: safe.topAnchor, constant: 5), root.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -5)])
    }
    private func refresh() {
        profileButton.setTitle(profile.name, for: .normal); center.text = "Arrêt : \(profile.safety)"
        let arrows = ["↑", "↓", "←", "→"]
        for (i, b) in profile.buttons.enumerated() {
            let button = controls[b.id]!; button.mode = b.mode; button.accessibilityLabel = b.label
            if i < 4 { button.setTitle("\(arrows[i])\n\(b.label)", for: .normal) }
        }
        refreshStates()
    }
    private func refreshStates() {
        for (i, b) in profile.buttons.enumerated() {
            let button = controls[b.id]!; button.isEnabled = engine.connected; button.alpha = engine.connected ? 1 : 0.6
            if !engine.connected { button.clearTouch() }
            if i >= 4 {
                let value = engine.requestedState(b.id).map { $0 ? "ON demandé" : "OFF demandé" } ?? "—"
                button.setTitle("\(b.label)\n\(value)", for: .normal)
                button.backgroundColor = engine.requestedState(b.id) == true ? UIColor(red: 0.09, green: 0.31, blue: 0.28, alpha: 1) : Theme.panel
            }
        }
    }
    func render(_ state: LinkState) {
        guard isViewLoaded else { return }
        if state.phase == .connected && !simulation && !ProcessInfo.processInfo.arguments.contains("--preview") && UIApplication.shared.applicationState != .active {
            endSession("Déconnecté : application inactive."); return
        }
        let old = phase
        if old != state.phase { engine.connection(state.phase == .connected) }
        if state.phase == .connecting && old != .connecting { lastTX = [] }
        if let byte = state.lastWritten { lastTX.append(String(UnicodeScalar(byte))); lastTX = Array(lastTX.suffix(20)) }
        if state.phase == .disconnected && old != .disconnected {
            let info = "Télécommande universelle iOS 1.0.3\niOS \(UIDevice.current.systemVersion) · BLE\nMotif : \(state.message)\nDerniers TX affichés : \(lastTX.joined(separator: " "))"
            UserDefaults.standard.set(info, forKey: "ios.lastDiagnostic")
        }
        phase = state.phase; status.setTitle("● \(state.message)", for: .normal)
        status.setTitleColor(state.phase == .connected ? Theme.green : Theme.red, for: .normal)
        tx.text = state.lastWritten.map { String(format: "TX : %@ · 0x%02X", String(UnicodeScalar($0)), $0) } ?? "TX : —"
        connectButton.setTitle(state.phase == .connected ? "Déconnecter" : state.phase == .connecting ? "Annuler" : "Connecter", for: .normal)
        UIApplication.shared.isIdleTimerDisabled = state.phase == .connected
        footer.text = state.phase == .connected ? "TX = écriture côté iPhone · ON/OFF = commande demandée" : "Toucher l’état rouge pour consulter le diagnostic"
        refreshStates()
    }
    func endSession(_ reason: String) {
        ble?.finishScan()
        let stop = engine.deactivate(); controls.values.forEach { $0.clearTouch() }
        if !stop.isEmpty && backgroundTask == .invalid {
            backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "RobotStop") { [weak self] in self?.finishBackgroundTask() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { [weak self] in self?.finishBackgroundTask() }
        }
        link?.disconnect(stop: stop, reason: reason)
        if phase != .disconnected { render(LinkState(phase: .disconnected, message: reason)) }
        UIApplication.shared.isIdleTimerDisabled = false
    }
    private func finishBackgroundTask() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask); backgroundTask = .invalid
    }
    @objc private func endTapped() { endSession("Session terminée."); message("Session terminée", "La liaison est fermée. Vous pouvez quitter l’application avec le geste d’accueil de l’iPhone.") }
    @objc private func diagnostic() {
        guard phase == .disconnected else { return }
        let text = UserDefaults.standard.string(forKey: "ios.lastDiagnostic") ?? "Aucune déconnexion enregistrée."
        let alert = UIAlertController(title: "Diagnostic Bluetooth", message: text, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Copier", style: .default) { _ in UIPasteboard.general.string = text })
        alert.addAction(UIAlertAction(title: "Fermer", style: .cancel)); present(alert, animated: true)
    }
    @objc private func profiles() {
        endSession("Profil changé.")
        let alert = UIAlertController(title: "Profil du robot", message: nil, preferredStyle: .actionSheet)
        for p in store.profiles { alert.addAction(UIAlertAction(title: p.name, style: .default) { [weak self] _ in
            guard let self else { return }; self.store.select(p.id); self.profile = p; self.makeEngine(); self.refresh()
        }) }
        alert.addAction(UIAlertAction(title: "Annuler", style: .cancel)); alert.popoverPresentationController?.sourceView = profileButton
        present(alert, animated: true)
    }
    @objc private func settingsTapped() {
        endSession("Déconnecté pour modifier les réglages.")
        let vc = SettingsController(profile: profile, store: store)
        vc.onSaved = { [weak self] in guard let self else { return }; self.profile = self.store.selected; self.makeEngine(); self.refresh() }
        let nav = UINavigationController(rootViewController: vc); nav.modalPresentationStyle = .fullScreen; present(nav, animated: true)
    }
    @objc private func connectTapped() {
        if phase != .disconnected { endSession("Déconnecté"); return }
        if ble == nil {
            let b = BLELink(); ble = b; link = b
            b.onState = { [weak self] s in self?.render(s) }
            b.onReadyDevice = { [weak self] id in guard let self else { return }; self.store.remember(id, profile: self.profile.id) }
        }
        guard let ble else { return }
        let vc = DevicePickerController(ble: ble, last: store.lastDevice(profile.id))
        vc.selected = { [weak self] p in guard let self else { return }; self.ble?.connect(p, profile: self.profile) }
        picker = vc; let nav = UINavigationController(rootViewController: vc); nav.modalPresentationStyle = .fullScreen
        present(nav, animated: true)
    }
    func message(_ title: String, _ text: String) {
        guard presentedViewController == nil else { return }
        let alert = UIAlertController(title: title, message: text, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
}

final class DevicePickerController: UITableViewController {
    private let ble: BLELink
    private let last: UUID?
    private var devices: [(CBPeripheral, String)] = []
    var selected: ((CBPeripheral) -> Void)?
    private let notice = UILabel()
    init(ble: BLELink, last: UUID?) { self.ble = ble; self.last = last; super.init(style: .insetGrouped) }
    required init?(coder: NSCoder) { fatalError("Programmatic UI") }
    override func viewDidLoad() {
        super.viewDidLoad(); title = "Appareils BLE · recherche 12 s"
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Annuler", style: .plain, target: self, action: #selector(cancel))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Rechercher", style: .plain, target: self, action: #selector(scan))
        ble.onDevices = { [weak self] items in self?.devices = items; self?.tableView.reloadData() }
        ble.onScanFinished = { [weak self] in self?.title = "Appareils BLE détectés" }
        ble.onScanNotice = { [weak self] text in self?.notice.text = text }
        notice.numberOfLines = 0; notice.font = .systemFont(ofSize: 14); notice.textColor = .secondaryLabel
        notice.frame = CGRect(x: 0, y: 0, width: 500, height: 60); tableView.tableHeaderView = notice
        NotificationCenter.default.addObserver(self, selector: #selector(resumeScan), name: UIApplication.didBecomeActiveNotification, object: nil)
        tableView.tableFooterView = {
            let label = Theme.label("HC-05/HC-06 Classic ne sont pas pris en charge sur iPhone. Utilisez un module BLE, par exemple MLT-BT05.", size: 13, color: .secondaryLabel)
            label.numberOfLines = 0; label.frame = CGRect(x: 16, y: 0, width: 500, height: 60); return label
        }()
        scan()
    }
    override func viewDidDisappear(_ animated: Bool) { super.viewDidDisappear(animated); ble.finishScan(); ble.onDevices = nil; ble.onScanFinished = nil; ble.onScanNotice = nil; NotificationCenter.default.removeObserver(self) }
    @objc private func resumeScan() { if view.window != nil { scan() } }
    @objc private func scan() { title = "Appareils BLE · recherche 12 s"; ble.startScan() }
    @objc private func cancel() { ble.finishScan(); dismiss(animated: true) }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { devices.count + (last == nil ? 0 : 1) }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let c = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        if last != nil && indexPath.row == 0 { c.textLabel?.text = "Dernier appareil"; c.detailTextLabel?.text = "Reprendre l’appareil enregistré pour ce profil" }
        else { let d = devices[indexPath.row - (last == nil ? 0 : 1)]; c.textLabel?.text = d.1; c.detailTextLabel?.text = String(d.0.identifier.uuidString.prefix(8)) }
        return c
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let p: CBPeripheral?
        if let last, indexPath.row == 0 { p = ble.remembered(last) }
        else { p = devices[indexPath.row - (last == nil ? 0 : 1)].0 }
        guard let p else { let a = UIAlertController(title: "Appareil non disponible", message: "Lancez une recherche BLE.", preferredStyle: .alert); a.addAction(UIAlertAction(title: "OK", style: .default)); present(a, animated: true); return }
        ble.finishScan(); let action = selected; dismiss(animated: true) { action?(p) }
    }
}
