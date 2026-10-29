import XCTest
import UIKit
@testable import TelecommandeUniverselle

final class FakeLink: RobotLink {
    var onState: ((LinkState) -> Void)?
    var bytes: [UInt8] = []
    var stops: [[UInt8]] = []
    func send(_ byte: UInt8) { bytes.append(byte) }
    func disconnect(stop: [UInt8], reason: String) { stops.append(stop); onState?(LinkState(phase: .disconnected, message: reason)) }
}
@MainActor final class AppTests: XCTestCase {
    private func make() -> (DashboardController, FakeLink) {
        let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!
        let link = FakeLink(); let vc = DashboardController(store: ProfileStore(defaults: defaults), link: link, simulation: true)
        vc.loadViewIfNeeded(); vc.view.frame = CGRect(x: 0, y: 0, width: 568, height: 320); vc.view.layoutIfNeeded()
        return (vc, link)
    }
    private func button(_ id: String, in view: UIView) -> RemoteButton {
        var found: RemoteButton?
        func walk(_ v: UIView) { if let b = v as? RemoteButton, b.accessibilityIdentifier == id { found = b }; v.subviews.forEach(walk) }
        walk(view); return found!
    }
    func testControlsStartDisabledAndTouchTargetsFitLandscape() {
        let (vc, _) = make()
        for id in RobotProfile.ids {
            let b = button(id, in: vc.view); XCTAssertFalse(b.isEnabled); XCTAssertGreaterThanOrEqual(b.bounds.width, 44); XCTAssertGreaterThanOrEqual(b.bounds.height, 44)
        }
        let image = UIGraphicsImageRenderer(size: vc.view.bounds.size).image { vc.view.layer.render(in: $0.cgContext) }
        let attachment = XCTAttachment(image: image); attachment.name = "Dashboard iPhone paysage — rendu simulé"; attachment.lifetime = .keepAlways; add(attachment)
    }
    func testNativeDirectionEventsAndCancellationSendSOnlyOnce() {
        let (vc, link) = make(); vc.render(LinkState(phase: .connected, message: "Simulation", lastWritten: 83))
        let forward = button("forward", in: vc.view)
        forward.sendActions(for: .touchDown); forward.sendActions(for: .touchDragExit); forward.sendActions(for: .touchUpInside)
        XCTAssertEqual(String(bytes: link.bytes, encoding: .ascii), "AS")
    }
    func testTwoIndependentControlsDoNotStopEachOther() {
        let (vc, link) = make(); vc.render(LinkState(phase: .connected, message: "Simulation"))
        let forward = button("forward", in: vc.view); let accessory = button("reserved1", in: vc.view)
        forward.sendActions(for: .touchDown); accessory.sendActions(for: .touchDown); accessory.sendActions(for: .touchUpInside)
        forward.sendActions(for: .touchUpInside)
        XCTAssertEqual(String(bytes: link.bytes, encoding: .ascii), "AFS")
    }
    func testCancelledToggleDoesNothingAndSessionEndDisablesEverything() {
        let (vc, link) = make(); vc.render(LinkState(phase: .connected, message: "Simulation"))
        let b = button("reserved1", in: vc.view); b.sendActions(for: .touchDown); b.sendActions(for: .touchCancel)
        XCTAssertTrue(link.bytes.isEmpty)
        b.sendActions(for: .touchDown); b.sendActions(for: .touchUpInside)
        vc.endSession("Arrière-plan")
        XCTAssertEqual(String(bytes: link.stops.last!, encoding: .ascii), "Sf")
        RobotProfile.ids.forEach { XCTAssertFalse(button($0, in: vc.view).isEnabled) }
    }
    func testProfilePersistenceDeletionAndCorruptionRecovery() throws {
        let defaults = UserDefaults(suiteName: "profiles.\(UUID().uuidString)")!
        let store = ProfileStore(defaults: defaults); var p = store.selected; p.buttons[4].label = "Lumière"
        try store.save(p); store.remember(UUID(), profile: p.id)
        XCTAssertEqual(ProfileStore(defaults: defaults).selected.buttons[4].label, "Lumière")
        try store.delete(p.id); XCTAssertEqual(store.profiles.count, 1); XCTAssertNotEqual(store.selected.id, p.id)
        defaults.set(Data("broken".utf8), forKey: "ios.profiles.v1")
        let recovered = ProfileStore(defaults: defaults); XCTAssertNotNil(recovered.warning); XCTAssertNoThrow(try recovered.selected.validate())
    }
}
