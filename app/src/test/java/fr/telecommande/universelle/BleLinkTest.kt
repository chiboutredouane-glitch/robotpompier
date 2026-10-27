package fr.telecommande.universelle

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID

/** Simulated Android GATT callbacks exercise the production transport; no radio is involved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class BleLinkTest {
    private val states = mutableListOf<LinkState>()
    private fun connect(properties: Int): Pair<BleLink, BluetoothGatt> {
        val link = BleLink(RuntimeEnvironment.getApplication(), RobotProfile.humBot().copy(radio = RadioMode.BLE)) { states.add(it) }
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:CC:DD:EE:FF")
        link.connect(device, 'S')
        val gatt = BleLink::class.java.getDeclaredField("gatt").apply { isAccessible = true }.get(link) as BluetoothGatt
        val service = BluetoothGattService(UUID.fromString(BleUuids.SERVICE), 0).apply {
            addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(BleUuids.WRITE), properties, BluetoothGattCharacteristic.PERMISSION_WRITE))
        }
        shadowOf(gatt).addDiscoverableService(service)
        shadowOf(gatt).notifyConnection(device.address)
        shadowOf(Looper.getMainLooper()).idle()
        return link to gatt
    }
    @Test fun writeResponseConnectionSendsInitialStopBeforeEnablingCommands() {
        val (link, gatt) = connect(BluetoothGattCharacteristic.PROPERTY_WRITE)
        assertEquals(LinkPhase.CONNECTED, states.last().phase)
        assertArrayEquals(byteArrayOf(83), shadowOf(gatt).latestWrittenBytes)
        link.send('A'); link.send('S'); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("S", "A", "S"), states.mapNotNull { it.lastWritten })
        assertArrayEquals(byteArrayOf(83), shadowOf(gatt).latestWrittenBytes)
        link.close()
    }
    @Test fun readOnlyCharacteristicNeverEnablesButtons() {
        val (link, gatt) = connect(BluetoothGattCharacteristic.PROPERTY_READ)
        assertEquals(LinkPhase.DISCONNECTED, states.last().phase)
        assertTrue(shadowOf(gatt).isClosed)
        assertFalse(states.any { it.phase == LinkPhase.CONNECTED })
        link.close()
    }
    @Test fun noResponseModeIsPacedAndLateCallbacksCannotSkipTheNextByte() {
        val (link, gatt) = connect(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        assertEquals(LinkPhase.CONNECTING, states.last().phase)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        assertEquals(LinkPhase.CONNECTED, states.last().phase)
        link.send('A'); link.send('S'); shadowOf(Looper.getMainLooper()).idle()
        assertArrayEquals(byteArrayOf(65), shadowOf(gatt).latestWrittenBytes)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        assertArrayEquals(byteArrayOf(83), shadowOf(gatt).latestWrittenBytes)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        assertEquals(listOf("S", "A", "S"), states.mapNotNull { it.lastWritten })
        link.close()
    }
    @Test fun disconnectFlushesStopThenAccessoryOffBeforeClosingGatt() {
        val (link, gatt) = connect(BluetoothGattCharacteristic.PROPERTY_WRITE)
        link.disconnect(listOf('S', 'f'), "Arrêt demandé")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(gatt).isClosed)
        assertArrayEquals(byteArrayOf(102), shadowOf(gatt).latestWrittenBytes)
        assertEquals(LinkPhase.DISCONNECTED, states.last().phase)
        link.close()
    }
    @Test fun cancellingConnectionIgnoresLateConnectedCallback() {
        val link = BleLink(RuntimeEnvironment.getApplication(), RobotProfile.humBot()) { states.add(it) }
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:CC:DD:EE:FF")
        link.connect(device, 'S')
        val gatt = BleLink::class.java.getDeclaredField("gatt").apply { isAccessible = true }.get(link) as BluetoothGatt
        link.disconnect(emptyList(), "Annulé")
        shadowOf(gatt).notifyConnection(device.address); shadowOf(Looper.getMainLooper()).idle()
        assertFalse(states.any { it.phase == LinkPhase.CONNECTED })
        assertEquals("Annulé", states.last().message)
        link.close()
    }
}
