package fr.telecommande.universelle

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BleTargetTest {
    private fun service(properties: Int): BluetoothGattService = BluetoothGattService(UUID.fromString(BleUuids.SERVICE), 0).apply {
        addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(BleUuids.WRITE), properties, BluetoothGattCharacteristic.PERMISSION_WRITE))
    }
    @Test fun findsFfe1WithWriteResponse() {
        val s = service(BluetoothGattCharacteristic.PROPERTY_WRITE)
        assertEquals(UUID.fromString(BleUuids.WRITE), BleTarget.resolve(listOf(s), "FFE0", "FFE1").uuid)
    }
    @Test fun acceptsModulesOfferingOnlyWriteWithoutResponse() {
        val s = service(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        assertNotNull(BleTarget.resolve(listOf(s), BleUuids.SERVICE, BleUuids.WRITE))
    }
    @Test fun readAndNotifyWithoutWriteAreRejected() {
        val s = service(BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY)
        assertThrows(IllegalArgumentException::class.java) { BleTarget.resolve(listOf(s), "FFE0", "FFE1") }
    }
    @Test fun missingServiceOrCharacteristicCannotEnableCommands() {
        assertThrows(IllegalArgumentException::class.java) { BleTarget.resolve(emptyList(), "FFE0", "FFE1") }
        val s = service(BluetoothGattCharacteristic.PROPERTY_WRITE)
        assertThrows(IllegalArgumentException::class.java) { BleTarget.resolve(listOf(s), "FFE0", "FFE2") }
    }
}
