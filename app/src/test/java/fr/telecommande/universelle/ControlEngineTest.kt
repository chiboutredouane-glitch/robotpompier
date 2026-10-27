package fr.telecommande.universelle

import org.junit.Assert.*
import org.junit.Test

class ControlEngineTest {
    private val output = StringBuilder()
    private fun engine(p: RobotProfile = RobotProfile.humBot()) = ControlEngine(p) { output.append(it) }.apply { connection(true) }

    @Test fun defaultDirectionsSendGlobalSWithoutTerminators() {
        val e = engine()
        listOf("forward", "backward", "left", "right").forEach { e.down(it); e.up(it) }
        assertEquals("ASRSGSDS", output.toString())
        assertTrue(output.all { it.code in 33..126 })
    }
    @Test fun reservedControlsUseIndependentToggles() {
        val e = engine()
        (1..4).forEach { e.click("reserved$it"); e.click("reserved$it") }
        assertEquals("FfTtKkXx", output.toString())
    }
    @Test fun accessoryDoesNotSwallowMovementRelease() {
        val e = engine()
        e.down("forward"); e.click("reserved1"); e.up("forward")
        assertEquals("AFS", output.toString())
        assertEquals(true, e.requestedState("reserved1"))
        e.click("reserved1"); assertEquals("AFSf", output.toString())
    }
    @Test fun customCommandsRemainCaseSensitive() {
        val p = RobotProfile.humBot().let { p -> p.copy(buttons = p.buttons.map { if (it.id == "forward") it.copy(down = "q", up = "Q") else it }) }
        val e = engine(p); e.down("forward"); e.up("forward")
        assertEquals("qQ", output.toString())
    }
    @Test fun noCommandMeansNoByte() {
        val e = engine(RobotProfile.blank("Vide"))
        e.down("reserved1"); e.up("reserved1")
        assertEquals("", output.toString())
    }
    @Test fun keyRepeatAndLateCancelDoNotRepeatCommands() {
        val e = engine(); e.down("forward"); e.down("forward"); e.up("forward"); e.up("forward")
        assertEquals("AS", output.toString())
    }
    @Test fun explicitTouchUpSurvivesBookkeepingReset() {
        val e = engine(); e.down("forward"); e.connection(true); e.up("forward", actualTouch = true)
        assertEquals("AS", output.toString())
    }
    @Test fun disconnectedEngineCannotWriteEvenOnLateTouchUp() {
        val e = engine(); e.connection(false); e.down("forward"); e.up("forward", true); e.click("reserved1")
        assertEquals("", output.toString())
    }
    @Test fun stopDeactivatesOnlyRequestedAccessories() {
        val e = engine(); e.down("forward"); e.click("reserved1")
        assertEquals(listOf('S', 'f'), e.deactivate())
        assertFalse(e.connected); assertNull(e.requestedState("reserved1"))
        e.down("forward"); assertEquals("AF", output.toString())
    }
    @Test fun inactiveProfileNeverInventsAccessoryShutdownCommands() {
        val e = engine(); assertEquals(listOf('S'), e.deactivate())
    }
    @Test fun momentaryCustomAuxiliaryHasItsOwnReleaseOnShutdown() {
        val p = RobotProfile.blank("Pince").let { p -> p.copy(buttons = p.buttons.map { if (it.id == "reserved1") it.copy(down = "E", up = "e") else it }) }
        val e = engine(p); e.down("reserved1")
        assertEquals(listOf('S', 'e'), e.deactivate())
    }
    @Test fun reconnectDoesNotRestorePreviousOnState() {
        val e = engine(); e.click("reserved1"); e.connection(false); e.connection(true)
        assertNull(e.requestedState("reserved1")); assertEquals("F", output.toString())
    }
    @Test fun whitespaceUnicodeAndMultiByteCommandsAreRejected() {
        listOf(" ", "\n", "\r", "é", "AB", "\t").forEach { assertFalse(it, isCommand(it)) }
        assertTrue(isCommand("")); assertFalse(isCommand("", false)); assertTrue(isCommand("S"))
    }
    @Test fun safetyCommandMustExistAndCannotContainLineEnding() {
        listOf("", "\n", "SS").forEach { stop ->
            assertThrows(IllegalArgumentException::class.java) { RobotProfile.humBot().copy(safety = stop).validate() }
        }
    }
    @Test fun uuidInputAcceptsShortFormsAndCanonicalizesCase() {
        assertEquals(BleUuids.SERVICE, BleUuids.normalize("0xFFE0"))
        assertEquals(BleUuids.WRITE, BleUuids.normalize("0000FFE1"))
        assertEquals(BleUuids.WRITE, BleUuids.normalize(BleUuids.WRITE.uppercase()))
        assertThrows(IllegalArgumentException::class.java) { BleUuids.normalize("1-1-1-1-1") }
    }
}
