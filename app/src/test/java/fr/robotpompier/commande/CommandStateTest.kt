package fr.robotpompier.commande

import org.junit.Assert.*
import org.junit.Test

class CommandStateTest {
    private val bytes = StringBuilder()
    private val state = CommandState { bytes.append(it) }

    @Test fun disconnectedCommandsAreIgnored() {
        CommandState.Direction.entries.forEach { state.press(it); state.release(it) }
        state.pump(true)
        state.pump(false)
        assertEquals("", bytes.toString())
    }

    @Test fun everyDirectionUsesExactAsciiPressAndRelease() {
        state.setConnected(true)
        CommandState.Direction.entries.forEach { state.press(it); state.release(it) }
        assertEquals("AaRrDdGg", bytes.toString())
        assertTrue(bytes.all { it.code < 128 && !it.isWhitespace() })
    }

    @Test fun pumpRemainsIndependentWhileMovementIsHeld() {
        state.setConnected(true)
        state.press(CommandState.Direction.FORWARD)
        state.pump(true)
        state.release(CommandState.Direction.FORWARD)
        assertTrue(state.pumpOn)
        state.pump(false)
        assertEquals("AEaF", bytes.toString())
    }

    @Test fun twoMovementTouchesReleaseIndependently() {
        state.setConnected(true)
        state.press(CommandState.Direction.FORWARD)
        state.press(CommandState.Direction.RIGHT)
        state.release(CommandState.Direction.FORWARD)
        state.release(CommandState.Direction.RIGHT)
        assertEquals("ADad", bytes.toString())
    }

    @Test fun repeatedKeyDownAndRepeatedCancelDoNotDuplicateBytes() {
        state.setConnected(true)
        state.press(CommandState.Direction.LEFT)
        state.press(CommandState.Direction.LEFT)
        state.release(CommandState.Direction.LEFT)
        state.release(CommandState.Direction.LEFT)
        assertEquals("Gg", bytes.toString())
    }

    @Test fun connectionLossDropsHeldStateAndBlocksLateEvents() {
        state.setConnected(true)
        state.press(CommandState.Direction.BACKWARD)
        state.pump(true)
        state.setConnected(false)
        state.release(CommandState.Direction.BACKWARD)
        state.press(CommandState.Direction.FORWARD)
        state.pump(false)
        assertEquals("RE", bytes.toString())
        assertFalse(state.enabled)
        assertFalse(state.pumpOn)
    }

    @Test fun reconnectionNeverRestartsOldMovement() {
        state.setConnected(true)
        state.press(CommandState.Direction.FORWARD)
        state.setConnected(false)
        state.setConnected(true)
        state.release(CommandState.Direction.FORWARD)
        assertEquals("A", bytes.toString())
        state.press(CommandState.Direction.FORWARD)
        state.release(CommandState.Direction.FORWARD)
        assertEquals("AAa", bytes.toString())
    }

    @Test fun safetySequenceReleasesAllDirectionsAndPumpWithoutLineEnding() {
        assertEquals("ardgF", CommandState.SAFE_STOP)
        assertArrayEquals(byteArrayOf(97, 114, 100, 103, 70), CommandState.SAFE_STOP.toByteArray(Charsets.US_ASCII))
    }
}
