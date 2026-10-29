import Foundation

final class ProfileStore {
    private let defaults: UserDefaults
    private(set) var profiles: [RobotProfile] = []
    private var selectedID: UUID?
    private(set) var warning: String?
    var selected: RobotProfile { profiles.first(where: { $0.id == selectedID }) ?? profiles[0] }
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: "ios.profiles.v1") {
            do {
                let loaded = try JSONDecoder().decode([RobotProfile].self, from: data)
                guard !loaded.isEmpty, loaded.count <= 30, Set(loaded.map(\.id)).count == loaded.count else { throw ConfigError("Liste de profils invalide.") }
                try loaded.forEach { try $0.validate() }; profiles = loaded
            } catch {
                warning = "Les profils enregistrés sont illisibles. Le profil Hum-Bot est chargé ; vérifiez les commandes avant de connecter le robot."
                defaults.set(data, forKey: "ios.profiles.corruptBackup")
            }
        }
        if profiles.isEmpty {
            profiles = [.humBot()]
            if warning == nil, let data = try? JSONEncoder().encode(profiles) { defaults.set(data, forKey: "ios.profiles.v1") }
        }
        selectedID = defaults.string(forKey: "ios.selected.v1").flatMap(UUID.init(uuidString:))
    }
    func select(_ id: UUID) { selectedID = id; defaults.set(id.uuidString, forKey: "ios.selected.v1") }
    func save(_ profile: RobotProfile) throws {
        try profile.validate()
        var next = profiles
        if let i = next.firstIndex(where: { $0.id == profile.id }) { next[i] = profile }
        else { guard next.count < 30 else { throw ConfigError("Maximum : 30 profils.") }; next.append(profile) }
        try persist(next); select(profile.id)
    }
    func delete(_ id: UUID) throws {
        var next = profiles.filter { $0.id != id }
        if next.isEmpty { next = [.humBot()] }
        try persist(next)
        if selectedID == id { select(next[0].id) }
        var devices = defaults.dictionary(forKey: "ios.devices.v1") as? [String: String] ?? [:]
        devices.removeValue(forKey: id.uuidString); defaults.set(devices, forKey: "ios.devices.v1")
    }
    private func persist(_ next: [RobotProfile]) throws {
        let data = try JSONEncoder().encode(next); defaults.set(data, forKey: "ios.profiles.v1"); profiles = next
    }
    func remember(_ device: UUID, profile: UUID) {
        var map = defaults.dictionary(forKey: "ios.devices.v1") as? [String: String] ?? [:]
        map[profile.uuidString] = device.uuidString; defaults.set(map, forKey: "ios.devices.v1")
    }
    func lastDevice(_ profile: UUID) -> UUID? {
        let map = defaults.dictionary(forKey: "ios.devices.v1") as? [String: String] ?? [:]
        return map[profile.uuidString].flatMap(UUID.init(uuidString:))
    }
}
