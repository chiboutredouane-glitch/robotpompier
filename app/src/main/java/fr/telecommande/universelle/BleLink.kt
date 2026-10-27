package fr.telecommande.universelle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

object BleTarget {
    fun resolve(services: List<BluetoothGattService>, service: String, characteristic: String): BluetoothGattCharacteristic {
        val target = services.firstOrNull { it.uuid == UUID.fromString(BleUuids.normalize(service)) }
            ?: throw IllegalArgumentException("Service BLE introuvable. Vérifiez l’UUID dans Réglages.")
        val c = target.getCharacteristic(UUID.fromString(BleUuids.normalize(characteristic)))
            ?: throw IllegalArgumentException("Caractéristique BLE introuvable. Vérifiez son UUID.")
        require(c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            "La caractéristique BLE ne permet pas l’écriture."
        }
        return c
    }
}

/** All state changes run on the main looper; GATT operations are asynchronous and serialized. */
@SuppressLint("MissingPermission")
class BleLink(private val context: Context, private val profile: RobotProfile,
    private var listener: ((LinkState) -> Unit)?) : RobotLink {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var target: BluetoothGattCharacteristic? = null
    private val buffer = WriteBuffer()
    private var state = LinkState(LinkPhase.DISCONNECTED, "Déconnecté")
    private var initial = 'S'
    private var initializing = false
    private var closing = false
    private var disposed = false
    private var withResponse = true
    private var busyRetries = 0
    private var writeDeadline: Runnable? = null
    private var closeDeadline: Runnable? = null
    private val connectionDeadline = Runnable { fail("Délai de connexion BLE dépassé.") }
    private fun report() { listener?.invoke(state) }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g !== gatt || disposed && !closing) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) { fail("Erreur BLE ($status)."); return@post }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        try { if (!g.discoverServices()) fail("Impossible de découvrir les services BLE.") }
                        catch (_: SecurityException) { fail("Autorisation Bluetooth retirée.") }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> fail(if (closing) state.message else "Liaison BLE perdue.")
                }
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g !== gatt || closing || disposed) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) { fail("Échec de découverte des services BLE."); return@post }
                try {
                    target = BleTarget.resolve(g.services, profile.service, profile.characteristic)
                    withResponse = target!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                    initializing = true
                    buffer.enqueue(initial); pump()
                } catch (e: IllegalArgumentException) { fail(e.message ?: "Profil BLE incompatible.") }
                catch (_: SecurityException) { fail("Autorisation Bluetooth retirée.") }
            }
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (g !== gatt || c.uuid != target?.uuid) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) { fail("Échec d’écriture BLE ($status)."); return@post }
                // No-response writes are paced separately: late callbacks must not complete the next byte.
                if (withResponse && buffer.inFlight != null) complete()
            }
        }
    }

    override fun connect(device: BluetoothDevice, initialStop: Char) {
        require(isCommand(initialStop.toString(), false))
        if (disposed || gatt != null) return
        initial = initialStop; closing = false
        state = LinkState(LinkPhase.CONNECTING, "Connexion BLE et vérification du service…"); report()
        main.postDelayed(connectionDeadline, 12000)
        try { gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
        catch (_: SecurityException) { fail("Autorisation Bluetooth requise.") }
        if (gatt == null) fail("Impossible de créer la connexion BLE.")
    }
    override fun send(command: Char) {
        require(isCommand(command.toString(), false))
        if (disposed || closing || state.phase != LinkPhase.CONNECTED) return
        buffer.enqueue(command); pump()
    }

    private fun pump() {
        val g = gatt ?: return
        val c = target ?: return
        if (buffer.inFlight != null) return
        val command = buffer.next() ?: run { if (closing) releaseGatt(); return }
        busyRetries = 0
        submit(g, c, command)
    }

    @Suppress("DEPRECATION")
    private fun submit(g: BluetoothGatt, c: BluetoothGattCharacteristic, command: Char) {
        if (g !== gatt || buffer.inFlight != command) return
        val type = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted: Boolean
        try {
            accepted = if (Build.VERSION.SDK_INT >= 33) {
                val status = g.writeCharacteristic(c, byteArrayOf(command.code.toByte()), type)
                if (status == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY && busyRetries++ < 6) {
                    main.postDelayed({ submit(g, c, command) }, 60); return
                }
                status == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = type; c.value = byteArrayOf(command.code.toByte())
                val ok = g.writeCharacteristic(c)
                if (!ok && busyRetries++ < 6) { main.postDelayed({ submit(g, c, command) }, 60); return }
                ok
            }
        } catch (_: SecurityException) { fail("Autorisation Bluetooth retirée."); return }
        if (!accepted) { fail("Le module BLE a refusé l’écriture."); return }
        writeDeadline?.let(main::removeCallbacks)
        if (withResponse) {
            writeDeadline = Runnable { fail("Écriture BLE sans réponse : connexion fermée.") }
            main.postDelayed(writeDeadline!!, 1500)
        } else {
            // UART modules offering only WRITE_NO_RESPONSE have no ATT acknowledgement.
            writeDeadline = Runnable { if (g === gatt) complete() }
            main.postDelayed(writeDeadline!!, 60)
        }
    }
    private fun complete() {
        writeDeadline?.let(main::removeCallbacks); writeDeadline = null
        val done = buffer.complete() ?: return
        if (initializing && !closing) {
            initializing = false
            main.removeCallbacks(connectionDeadline)
            val name = try { gatt?.device?.name ?: "Appareil BLE" } catch (_: SecurityException) { "Appareil BLE" }
            state = LinkState(LinkPhase.CONNECTED, "Connecté · $name", done.toString())
        } else state = state.copy(lastWritten = done.toString())
        report(); pump()
    }
    override fun disconnect(stop: List<Char>, reason: String) {
        val wasReady = state.phase == LinkPhase.CONNECTED
        state = LinkState(LinkPhase.DISCONNECTED, reason); report()
        main.removeCallbacks(connectionDeadline)
        if (gatt == null || closing) return
        if (!wasReady || target == null || stop.isEmpty()) { releaseGatt(); return }
        closing = true
        buffer.beginStop(stop)
        closeDeadline = Runnable { releaseGatt() }.also { main.postDelayed(it, 2000) }
        pump()
    }
    private fun fail(reason: String) {
        state = LinkState(LinkPhase.DISCONNECTED, reason); report(); releaseGatt()
    }
    private fun releaseGatt() {
        main.removeCallbacks(connectionDeadline)
        writeDeadline?.let(main::removeCallbacks); closeDeadline?.let(main::removeCallbacks)
        writeDeadline = null; closeDeadline = null
        val old = gatt; gatt = null; target = null; buffer.clear(); closing = false; initializing = false
        try { old?.disconnect() } catch (_: SecurityException) { }
        try { old?.close() } catch (_: Exception) { }
    }
    override fun close() {
        listener = null; disposed = true
        if (!closing) releaseGatt()
    }
}
