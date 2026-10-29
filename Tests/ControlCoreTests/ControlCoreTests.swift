import XCTest
@testable import ControlCore

final class ControlCoreTests: XCTestCase {
    func testDirectionsUseUppercaseSOnRelease() throws {
        var out: [UInt8] = []; let e = try ControlEngine(profile: .humBot()) { out.append($0) }; e.connection(true)
        for id in RobotProfile.ids.prefix(4) { e.down(id); e.up(id) }
        XCTAssertEqual(String(bytes: out, encoding: .ascii), "ASRSGSDS")
    }
    func testToggleAccessoriesPreserveCase() throws {
        var out: [UInt8] = []; let e = try ControlEngine(profile: .humBot()) { out.append($0) }; e.connection(true)
        for id in RobotProfile.ids.suffix(4) { e.click(id); e.click(id) }
        XCTAssertEqual(String(bytes: out, encoding: .ascii), "FfTtKkXx")
    }
    func testMovementAndAccessoryAreIndependent() throws {
        var out: [UInt8] = []; let e = try ControlEngine(profile: .humBot()) { out.append($0) }; e.connection(true)
        e.down("forward"); e.click("reserved1"); e.up("forward")
        XCTAssertEqual(String(bytes: out, encoding: .ascii), "AFS")
    }
    func testStopUsesOnlySafetyAndActiveOffCommands() throws {
        let e = try ControlEngine(profile: .humBot()) { _ in }; e.connection(true)
        e.down("forward"); e.click("reserved1")
        XCTAssertEqual(String(bytes: e.deactivate(), encoding: .ascii), "Sf")
        XCTAssertFalse(e.connected); XCTAssertTrue(e.deactivate().isEmpty)
    }
    func testDisconnectedControlsSendNothingAndDoNotResume() throws {
        var out: [UInt8] = []; let e = try ControlEngine(profile: .humBot()) { out.append($0) }
        e.down("forward"); e.click("reserved1"); XCTAssertTrue(out.isEmpty)
        e.connection(true); e.down("forward"); e.connection(false); e.up("forward"); e.connection(true)
        XCTAssertEqual(String(bytes: out, encoding: .ascii), "A"); XCTAssertNil(e.requestedState("reserved1"))
    }
    func testRepeatedDownAndCancelSendExactlyOneRelease() throws {
        var out: [UInt8] = []; let e = try ControlEngine(profile: .humBot()) { out.append($0) }; e.connection(true)
        e.down("forward"); e.down("forward"); e.up("forward"); e.up("forward")
        XCTAssertEqual(String(bytes: out, encoding: .ascii), "AS")
    }
    func testCustomCommandsAndEmptyEvents() throws {
        var p = RobotProfile.humBot(); p.buttons[0].down = "q"; p.buttons[0].up = ""; p.safety = "!"
        var out: [UInt8] = []; let e = try ControlEngine(profile: p) { out.append($0) }; e.connection(true)
        e.down("forward"); e.up("forward"); XCTAssertEqual(out, [113]); XCTAssertEqual(e.deactivate(), [33])
    }
    func testASCIIValidation() throws {
        for bad in [" ", "\n", "\r", "é", "AA", "😀", "A\n"] { XCTAssertThrowsError(try commandByte(bad)) }
        XCTAssertEqual(try commandByte("a"), 97); XCTAssertEqual(try commandByte("A"), 65)
        XCTAssertNil(try commandByte("")); XCTAssertThrowsError(try commandByte("", allowEmpty: false))
    }
    func testProfileValidationAndRenamingDoNotChangeWireBytes() throws {
        var p = RobotProfile.humBot(); p.buttons[4].label = "Éclairage"; try p.validate()
        XCTAssertEqual(p.buttons[4].down, "F"); XCTAssertEqual(p.buttons[4].up, "f")
        p.buttons.removeLast(); XCTAssertThrowsError(try p.validate())
    }
    func testUUIDNormalizationAndRejection() throws {
        XCTAssertEqual(try normalizedUUID(" FFE0 "), RobotProfile.humBot().service)
        XCTAssertEqual(try normalizedUUID("0xFFE1"), RobotProfile.humBot().characteristic)
        XCTAssertEqual(try normalizedUUID("12345678"), "12345678-0000-1000-8000-00805f9b34fb")
        for bad in ["", "GGGG", "FFF", "1-1-1-1-1"] { XCTAssertThrowsError(try normalizedUUID(bad)) }
    }
    func testProfilesRoundTripAllSettings() throws {
        var p = RobotProfile.humBot(name: "Robot marin"); p.buttons[4].mode = .hold; p.buttons[4].down = "E"; p.buttons[4].up = "e"
        let data = try JSONEncoder().encode(p)
        XCTAssertEqual(try JSONDecoder().decode(RobotProfile.self, from: data), p)
    }
    func testQueueSerializesAndKeepsDuplicateBytes() {
        var q = WriteQueue(); q.enqueue(83); q.enqueue(83)
        XCTAssertEqual(q.next(), 83); XCTAssertNil(q.next()); XCTAssertEqual(q.complete(), 83)
        XCTAssertEqual(q.next(), 83); q.complete(); XCTAssertNil(q.next())
    }
    func testStopDropsQueuedMovementButPreservesInFlight() {
        var q = WriteQueue(); q.enqueue(65); q.enqueue(82); XCTAssertEqual(q.next(), 65)
        q.beginStop([83, 102]); XCTAssertNil(q.next()); q.complete()
        XCTAssertEqual(q.next(), 83); q.complete(); XCTAssertEqual(q.next(), 102)
        q.clear(); XCTAssertNil(q.inFlight); XCTAssertFalse(q.hasPending)
    }
}
