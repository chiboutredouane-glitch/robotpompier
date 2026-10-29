import CoreBluetooth
import Foundation

enum LinkPhase { case disconnected, connecting, connected }
struct LinkState {
    var phase: LinkPhase
    var message: String
    var lastWritten: UInt8?
}
protocol RobotLink: AnyObject {
    var onState: ((LinkState) -> Void)? { get set }
    func send(_ byte: UInt8)
    func disconnect(stop: [UInt8], reason: String)
}
/// CoreBluetooth and all state mutations use the main queue. No background auto-reconnect.
final class BLELink: NSObject, RobotLink, CBCentralManagerDelegate, CBPeripheralDelegate {
    var onState: ((LinkState) -> Void)?
    var onDevices: (([(CBPeripheral, String)]) -> Void)?
    var onScanFinished: (() -> Void)?
    var onScanNotice: ((String) -> Void)?
    var onReadyDevice: ((UUID) -> Void)?
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var characteristic: CBCharacteristic?
    private var profile: RobotProfile?
    private var devices: [(CBPeripheral, String)] = []
    private var queue = WriteQueue()
    private var state = LinkState(phase: .disconnected, message: "Déconnecté")
    private var writeType: CBCharacteristicWriteType = .withResponse
    private var initializing = false
    private var closing = false
    private var session = UUID()
    private var connectDeadline: DispatchWorkItem?
    private var writeDeadline: DispatchWorkItem?
    private var closeDeadline: DispatchWorkItem?
    private var scanDeadline: DispatchWorkItem?
    private var scanning = false
    private var pendingScan = false
    private var retiring = Set<UUID>()
    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main, options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }
    private func report() { onState?(state) }
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn { if pendingScan { startScan() }; return }
        let message: String
        switch central.state {
        case .unauthorized: message = "Bluetooth non autorisé. Ouvrez Réglages > Confidentialité et sécurité > Bluetooth."
        case .poweredOff: message = "Activez le Bluetooth dans les réglages de l’iPhone."
        case .unsupported: message = "Bluetooth BLE non disponible."
        case .resetting: message = "Bluetooth en cours de réinitialisation."
        default: message = "Initialisation Bluetooth…"
        }
        onScanNotice?(message)
        if central.state == .unknown || central.state == .resetting { return }
        retiring.removeAll(); finishScan(); fail(message)
    }
    func startScan() {
        guard central.state == .poweredOn else { pendingScan = true; centralManagerDidUpdateState(central); return }
        guard peripheral == nil else { return }
        finishScan(); devices = []; onDevices?(devices); scanning = true; onScanNotice?("Recherche BLE pendant 12 secondes…")
        // Many UART modules do not advertise the service UUID. Verify services after connection.
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        let task = DispatchWorkItem { [weak self] in self?.finishScan() }
        scanDeadline = task; DispatchQueue.main.asyncAfter(deadline: .now() + 12, execute: task)
    }
    func finishScan() {
        pendingScan = false
        scanDeadline?.cancel(); scanDeadline = nil
        if scanning { scanning = false; central.stopScan(); onScanFinished?() }
    }
    func centralManager(_ central: CBCentralManager, didDiscover p: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard scanning, !devices.contains(where: { $0.0.identifier == p.identifier }) else { return }
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? p.name ?? "Appareil BLE sans nom"
        devices.append((p, name)); onDevices?(devices)
    }
    func remembered(_ id: UUID) -> CBPeripheral? { central.retrievePeripherals(withIdentifiers: [id]).first }
    func connect(_ p: CBPeripheral, profile: RobotProfile) {
        guard central.state == .poweredOn, peripheral == nil else { return }
        guard !retiring.contains(p.identifier) else { fail("Déconnexion précédente en cours. Réessayez dans un instant."); return }
        do { try profile.validate() } catch { fail(error.localizedDescription); return }
        finishScan(); session = UUID(); let token = session
        self.profile = profile; peripheral = p; p.delegate = self; closing = false
        state = LinkState(phase: .connecting, message: "Connexion BLE et vérification du service…"); report()
        let task = DispatchWorkItem { [weak self] in guard self?.session == token else { return }; self?.fail("Délai de connexion BLE dépassé.") }
        connectDeadline = task; DispatchQueue.main.asyncAfter(deadline: .now() + 12, execute: task)
        central.connect(p, options: nil)
    }
    func centralManager(_ central: CBCentralManager, didConnect p: CBPeripheral) {
        if retiring.contains(p.identifier) { central.cancelPeripheralConnection(p); return }
        guard p === peripheral, !closing, let profile else { central.cancelPeripheralConnection(p); return }
        p.discoverServices([CBUUID(string: try! normalizedUUID(profile.service))])
    }
    func centralManager(_ central: CBCentralManager, didFailToConnect p: CBPeripheral, error: Error?) {
        if retiring.remove(p.identifier) != nil { return }
        guard p === peripheral else { return }; fail("Connexion BLE impossible : \(error?.localizedDescription ?? "appareil indisponible").")
    }
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral p: CBPeripheral, error: Error?) {
        if retiring.remove(p.identifier) != nil { return }
        guard p === peripheral else { return }
        fail(closing ? state.message : "Liaison BLE perdue. \(error?.localizedDescription ?? "")")
    }
    func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) {
        guard p === peripheral, !closing, let profile else { return }
        guard error == nil, let service = p.services?.first(where: { $0.uuid == CBUUID(string: try! normalizedUUID(profile.service)) }) else {
            fail("Service BLE introuvable. Vérifiez l’UUID dans Réglages."); return
        }
        p.discoverCharacteristics([CBUUID(string: try! normalizedUUID(profile.characteristic))], for: service)
    }
    func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard p === peripheral, !closing, let profile else { return }
        guard error == nil, let c = service.characteristics?.first(where: { $0.uuid == CBUUID(string: try! normalizedUUID(profile.characteristic)) }),
              c.properties.contains(.write) || c.properties.contains(.writeWithoutResponse) else {
            fail("Caractéristique BLE absente ou non inscriptible."); return
        }
        characteristic = c; writeType = c.properties.contains(.write) ? .withResponse : .withoutResponse
        initializing = true; queue.enqueue(try! commandByte(profile.safety, allowEmpty: false)!); pump()
    }
    func send(_ byte: UInt8) {
        guard (33...126).contains(byte), state.phase == .connected, !closing else { return }
        queue.enqueue(byte); pump()
    }
    private func pump() {
        guard let p = peripheral, let c = characteristic, queue.inFlight == nil else { return }
        if queue.hasPending && writeType == .withoutResponse && !p.canSendWriteWithoutResponse { armWriteDeadline(); return }
        guard let byte = queue.next() else { if closing { release() }; return }
        if writeType == .withResponse { armWriteDeadline() }
        p.writeValue(Data([byte]), for: c, type: writeType)
        if writeType == .withoutResponse {
            // Successful submission to the local stack, not a robot acknowledgement.
            writeDeadline?.cancel(); let token = session
            let task = DispatchWorkItem { [weak self] in guard self?.session == token else { return }; self?.complete() }
            writeDeadline = task; DispatchQueue.main.asyncAfter(deadline: .now() + 0.06, execute: task)
        }
    }
    private func armWriteDeadline() {
        guard writeDeadline == nil else { return }
        let token = session
        let task = DispatchWorkItem { [weak self] in guard self?.session == token else { return }; self?.fail("Écriture BLE bloquée : connexion fermée.") }
        writeDeadline = task; DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: task)
    }
    func peripheralIsReady(toSendWriteWithoutResponse p: CBPeripheral) {
        guard p === peripheral, writeType == .withoutResponse, queue.inFlight == nil else { return }
        writeDeadline?.cancel(); writeDeadline = nil; pump()
    }
    func peripheral(_ p: CBPeripheral, didWriteValueFor c: CBCharacteristic, error: Error?) {
        guard p === peripheral, c === characteristic, writeType == .withResponse, queue.inFlight != nil else { return }
        if let error { fail("Erreur d’écriture BLE : \(error.localizedDescription)"); return }; complete()
    }
    private func complete() {
        writeDeadline?.cancel(); writeDeadline = nil
        guard let byte = queue.complete() else { return }
        if initializing && !closing {
            initializing = false; connectDeadline?.cancel(); connectDeadline = nil
            state = LinkState(phase: .connected, message: "Connecté · \(peripheral?.name ?? "Appareil BLE")", lastWritten: byte)
            if let id = peripheral?.identifier { onReadyDevice?(id) }
        } else { state.lastWritten = byte }
        report(); pump()
    }
    func disconnect(stop: [UInt8], reason: String) {
        finishScan(); let wasReady = state.phase == .connected
        state = LinkState(phase: .disconnected, message: reason); report()
        connectDeadline?.cancel(); connectDeadline = nil
        guard peripheral != nil else { return }
        guard !closing else { return }
        guard wasReady, characteristic != nil, !stop.isEmpty else { release(); return }
        closing = true; queue.beginStop(stop)
        let token = session
        let task = DispatchWorkItem { [weak self] in guard self?.session == token else { return }; self?.release() }
        closeDeadline = task; DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: task); pump()
    }
    private func fail(_ message: String) { state = LinkState(phase: .disconnected, message: message); release(); report() }
    private func release() {
        connectDeadline?.cancel(); writeDeadline?.cancel(); closeDeadline?.cancel()
        connectDeadline = nil; writeDeadline = nil; closeDeadline = nil
        let old = peripheral; peripheral = nil; characteristic = nil; profile = nil
        queue.clear(); initializing = false; closing = false; session = UUID()
        old?.delegate = nil
        if let old, old.state != .disconnected { retiring.insert(old.identifier); central.cancelPeripheralConnection(old) }
    }
}
