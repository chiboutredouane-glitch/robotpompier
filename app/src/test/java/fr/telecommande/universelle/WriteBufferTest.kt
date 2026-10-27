package fr.telecommande.universelle

import org.junit.Assert.*
import org.junit.Test

class WriteBufferTest {
    @Test fun writesWaitForThePreviousCompletion() {
        val q = WriteBuffer(); q.enqueue('A'); q.enqueue('S')
        assertEquals('A', q.next()); assertNull(q.next())
        assertEquals('A', q.complete()); assertEquals('S', q.next())
        q.complete(); assertTrue(q.idle)
    }
    @Test fun safetyStopDropsQueuedMotionButFinishesTheInFlightByte() {
        val q = WriteBuffer(); q.enqueue('A'); q.enqueue('D'); q.next()
        q.beginStop(listOf('S', 'f'))
        assertNull(q.next()); assertEquals('A', q.complete())
        assertEquals('S', q.next()); q.complete(); assertEquals('f', q.next()); q.complete()
        assertTrue(q.idle)
    }
    @Test fun resetCannotReplayAnOldCommand() {
        val q = WriteBuffer(); q.enqueue('A'); q.next(); q.clear()
        assertNull(q.complete()); assertNull(q.next()); assertTrue(q.idle)
    }
    @Test fun identicalConsecutiveBytesRemainSeparateOperations() {
        val q = WriteBuffer(); q.enqueue('S'); q.enqueue('S')
        assertEquals('S', q.next()); q.complete(); assertEquals('S', q.next()); q.complete()
        assertTrue(q.idle)
    }
}
