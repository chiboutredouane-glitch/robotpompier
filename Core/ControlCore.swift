import Foundation

public enum ButtonMode: String, Codable, CaseIterable { case hold, toggle }
public struct ConfigError: LocalizedError {
    public let text: String
    public init(_ text: String) { self.text = text }
    public var errorDescription: String? { text }
}
public func commandByte(_ value: String, allowEmpty: Bool = true) throws -> UInt8? {
    if value.isEmpty && allowEmpty { return nil }
    guard value.utf8.count == 1, let byte = value.utf8.first, (33...126).contains(byte) else {
        throw ConfigError("Une commande doit contenir un seul caractère ASCII visible, ou être vide.")
    }
    return byte
}
public func normalizedUUID(_ input: String) throws -> String {
    var s = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    if s.hasPrefix("0x") { s.removeFirst(2) }
    let hex = CharacterSet(charactersIn: "0123456789abcdef")
    if (s.count == 4 || s.count == 8) && s.unicodeScalars.allSatisfy({ hex.contains($0) }) {
        if s.count == 4 { s = "0000" + s }
        s += "-0000-1000-8000-00805f9b34fb"
    }
    guard s.range(of: "^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$", options: .regularExpression) != nil,
          let uuid = UUID(uuidString: s) else { throw ConfigError("UUID BLE invalide. Exemple : FFE0 ou un UUID complet.") }
    return uuid.uuidString.lowercased()
}
public struct ButtonConfig: Codable, Equatable {
    public var id: String
    public var label: String
    public var mode: ButtonMode
    public var down: String
    public var up: String
    public func validate() throws {
        guard !label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, label.count <= 24 else {
            throw ConfigError("Le nom du bouton doit contenir de 1 à 24 caractères.")
        }
        _ = try commandByte(down); _ = try commandByte(up)
    }
}
public struct RobotProfile: Codable, Equatable {
    public static let ids = ["forward", "backward", "left", "right", "reserved1", "reserved2", "reserved3", "reserved4"]
    public var id: UUID
    public var name: String
    public var safety: String
    public var service: String
    public var characteristic: String
    public var buttons: [ButtonConfig]
    public func validate() throws {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, name.count <= 40 else {
            throw ConfigError("Le profil doit avoir un nom de 1 à 40 caractères.")
        }
        _ = try commandByte(safety, allowEmpty: false)
        _ = try normalizedUUID(service); _ = try normalizedUUID(characteristic)
        guard buttons.map(\.id) == Self.ids else { throw ConfigError("Le profil doit contenir les huit boutons attendus.") }
        try buttons.forEach { try $0.validate() }
    }
    public static func humBot(name: String = "Hum-Bot") -> Self {
        let labels = ["Avancer", "Reculer", "Gauche", "Droite", "Phares", "Feux stop", "Klaxon", "Détresse"]
        let down = ["A", "R", "G", "D", "F", "T", "K", "X"]
        let up = ["S", "S", "S", "S", "f", "t", "k", "x"]
        return Self(id: UUID(), name: name, safety: "S", service: "0000ffe0-0000-1000-8000-00805f9b34fb",
                    characteristic: "0000ffe1-0000-1000-8000-00805f9b34fb",
                    buttons: ids.enumerated().map { i, id in ButtonConfig(id: id, label: labels[i], mode: i < 4 ? .hold : .toggle, down: down[i], up: up[i]) })
    }
    public static func blank(name: String) -> Self {
        var p = humBot(name: name)
        for i in 4..<8 { p.buttons[i].label = "Bouton \(i - 3)"; p.buttons[i].mode = .hold; p.buttons[i].down = ""; p.buttons[i].up = "" }
        return p
    }
}
public final class ControlEngine {
    public let profile: RobotProfile
    private let write: (UInt8) -> Void
    private var held = Set<String>()
    private var requested: [String: Bool] = [:]
    public private(set) var connected = false
    public init(profile: RobotProfile, write: @escaping (UInt8) -> Void) throws {
        try profile.validate(); self.profile = profile; self.write = write
    }
    public func connection(_ ready: Bool) { connected = ready; held.removeAll(); requested.removeAll() }
    public func requestedState(_ id: String) -> Bool? { requested[id] }
    private func emit(_ value: String) { if let byte = try? commandByte(value) { write(byte) } }
    public func down(_ id: String) {
        guard connected, let b = profile.buttons.first(where: { $0.id == id }), b.mode == .hold,
              held.insert(id).inserted else { return }
        requested[id] = true; emit(b.down)
    }
    public func up(_ id: String) {
        guard connected, held.remove(id) != nil, let b = profile.buttons.first(where: { $0.id == id }) else { return }
        requested[id] = false; emit(b.up)
    }
    public func click(_ id: String) {
        guard connected, let b = profile.buttons.first(where: { $0.id == id }) else { return }
        if b.mode == .hold { down(id); up(id); return }
        let next = requested[id] != true; requested[id] = next; emit(next ? b.down : b.up)
    }
    public func deactivate() -> [UInt8] {
        guard connected else { connection(false); return [] }
        var bytes: [UInt8] = [try! commandByte(profile.safety, allowEmpty: false)!]
        for b in profile.buttons where requested[b.id] == true {
            if let byte = try? commandByte(b.up), !bytes.contains(byte) { bytes.append(byte) }
        }
        connection(false); return bytes
    }
}
/// At most one write in flight. Stop replaces queued commands, preserving the in-flight byte.
public struct WriteQueue {
    private var pending: [UInt8] = []
    public private(set) var inFlight: UInt8?
    public init() {}
    public var hasPending: Bool { !pending.isEmpty }
    public mutating func enqueue(_ byte: UInt8) { pending.append(byte) }
    public mutating func beginStop(_ bytes: [UInt8]) { pending = bytes }
    public mutating func next() -> UInt8? {
        guard inFlight == nil, !pending.isEmpty else { return nil }
        let byte = pending.removeFirst(); inFlight = byte; return byte
    }
    @discardableResult public mutating func complete() -> UInt8? { let byte = inFlight; inFlight = nil; return byte }
    public mutating func clear() { pending.removeAll(); inFlight = nil }
}
