package fr.telecommande.universelle

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.PipedOutputStream

/** Real production executors and simulated RFCOMM streams; no Bluetooth hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class ClassicLinkTest {
    private val states = mutableListOf<LinkState>()
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        fail("État non atteint : $states")
    }
    private fun connect(): Pair<ClassicLink, BluetoothSocket> {
        val link = ClassicLink { states.add(it) }
        link.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:CC:DD:EE:FF"), 'S')
        await { states.lastOrNull()?.phase == LinkPhase.CONNECTED }
        val session = ClassicLink::class.java.getDeclaredField("current").apply { isAccessible = true }.get(link)
        val socket = session.javaClass.getDeclaredField("socket").apply { isAccessible = true }.get(session) as BluetoothSocket
        return link to socket
    }
    private fun cleanup(link: ClassicLink, socket: BluetoothSocket) {
        link.close()
        // Robolectric's socket.close changes state but does not close its simulated pipes.
        shadowOf(socket).inputStreamFeeder.close()
        shadowOf(socket).outputStreamSink.close()
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun silentModuleAcceptsManyDirectionAndAccessoryCommandsWithoutRequiringReplies() {
        val (link, socket) = connect()
        try {
            val commands = "ASRSGSDSFfTtKkXxASRS"
            commands.forEach(link::send)
            val sink = shadowOf(socket).outputStreamSink
            await { sink.available() == commands.length + 1 }
            val bytes = ByteArray(commands.length + 1)
            assertEquals(bytes.size, sink.read(bytes))
            assertEquals("S$commands", bytes.toString(Charsets.US_ASCII))
            assertEquals(LinkPhase.CONNECTED, states.last().phase)
        } finally { cleanup(link, socket) }
    }
    @Test fun closedIncomingStreamDisablesConnectionWithoutWaitingForAnotherButton() {
        val (link, socket) = connect()
        try {
            shadowOf(socket).inputStreamFeeder.close()
            await { states.last().phase == LinkPhase.DISCONNECTED }
            assertTrue(states.last().message.contains("fin du flux SPP"))
            assertFalse(socket.isConnected)
        } finally { cleanup(link, socket) }
    }
    @Test fun androidWriteErrorIncludesOperationAndOriginalDetail() {
        val (link, socket) = connect()
        try {
            shadowOf(socket).setOutputStream(object : PipedOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) { throw IOException("socket closed\nread returned -1") }
            })
            link.send('F')
            await { states.last().phase == LinkPhase.DISCONNECTED }
            val reason = states.last().message
            assertTrue(reason.contains("envoi de F"))
            assertTrue(reason.contains("socket closed read returned -1"))
            assertFalse(reason.contains('\n'))
            assertFalse(socket.isConnected)
        } finally { cleanup(link, socket) }
    }
}
