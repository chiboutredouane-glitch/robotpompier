package fr.robotpompier.commande

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Classic RFCOMM SPP; connection, reads and writes never block the UI thread. */
@SuppressLint("MissingPermission") // MainActivity obtains CONNECT before calling Bluetooth APIs.
class BluetoothSerial(private var listener: ((State) -> Unit)?) {
    enum class Phase { DISCONNECTED, CONNECTING, CONNECTED }
    data class State(val phase: Phase, val message: String)
    private class Session(val socket: BluetoothSocket, val name: String)

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newCachedThreadPool()
    private val writer = Executors.newSingleThreadExecutor()
    private val timers = Executors.newSingleThreadScheduledExecutor()
    private var session: Session? = null
    private var generation = 0L
    private var closed = false
    private var state = State(Phase.DISCONNECTED, "Déconnecté")

    private fun notifyState() {
        main.post {
            val snapshot = synchronized(lock) { state }
            listener?.invoke(snapshot)
        }
    }

    fun connect(device: BluetoothDevice) {
        val token = synchronized(lock) {
            if (closed || state.phase != Phase.DISCONNECTED) return
            generation += 1
            state = State(Phase.CONNECTING, "Connexion en cours…")
            generation
        }
        notifyState()
        val timeout = timers.schedule({
            failConnection(token, "Connexion impossible : délai dépassé.")
        }, 12, TimeUnit.SECONDS)
        workers.execute {
            var candidate: Session? = null
            try {
                val name = device.name ?: device.address
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                candidate = Session(socket, name)
                val accepted = synchronized(lock) {
                    if (closed || generation != token) false
                    else { session = candidate; true }
                }
                if (!accepted) { socket.close(); return@execute }
                socket.connect()
                // Begin every connection with all actuators commanded off.
                socket.outputStream.write(CommandState.SAFE_STOP.toByteArray(Charsets.US_ASCII))
                socket.outputStream.flush()
                val connected = synchronized(lock) {
                    if (generation != token || session !== candidate || closed) false
                    else {
                        state = State(Phase.CONNECTED, "Connecté à ${candidate.name}")
                        true
                    }
                }
                if (!connected) { socket.close(); return@execute }
                timeout.cancel(false)
                notifyState()
                val buffer = ByteArray(256)
                // Blocking read detects EOF / broken RFCOMM even when no button is pressed.
                while (socket.inputStream.read(buffer) != -1) { /* no telemetry protocol required */ }
                fail(candidate, "Connexion perdue. Commandes désactivées.")
            } catch (_: IOException) {
                if (candidate == null) failConnection(token, "Impossible de créer la connexion Bluetooth.")
                else fail(candidate, "Connexion interrompue ou appareil indisponible.")
            } catch (_: SecurityException) {
                if (candidate == null) failConnection(token, "Autorisation Bluetooth manquante.")
                else fail(candidate, "Autorisation Bluetooth retirée.")
            } finally {
                timeout.cancel(false)
            }
        }
    }

    fun send(bytes: String) {
        require(bytes.length == 1 && bytes[0] in "AaRrDdGgEF")
        val target = synchronized(lock) {
            if (closed || state.phase != Phase.CONNECTED) return
            session ?: return
        }
        writer.execute {
            val usable = synchronized(lock) { session === target && state.phase == Phase.CONNECTED }
            if (!usable) return@execute
            val timeout = timers.schedule({
                fail(target, "Envoi bloqué. Connexion fermée par sécurité.")
            }, 750, TimeUnit.MILLISECONDS)
            try {
                target.socket.outputStream.write(bytes.toByteArray(Charsets.US_ASCII))
                target.socket.outputStream.flush()
            } catch (_: IOException) {
                fail(target, "Connexion perdue pendant la commande.")
            } catch (_: SecurityException) {
                fail(target, "Autorisation Bluetooth retirée.")
            } finally {
                timeout.cancel(false)
            }
        }
    }

    /** Disable immediately, discard queued motion, try stop, then close within 750 ms. */
    fun disconnect(message: String = "Déconnecté") {
        val (target, wasConnected) = synchronized(lock) {
            generation += 1
            val old = session
            val connected = state.phase == Phase.CONNECTED
            session = null
            state = State(Phase.DISCONNECTED, message)
            old to connected
        }
        notifyState()
        if (target == null) return
        if (!wasConnected) { workers.execute { closeSocket(target) }; return }
        val deadline = timers.schedule({ closeSocket(target) }, 750, TimeUnit.MILLISECONDS)
        writer.execute {
            try {
                target.socket.outputStream.write(CommandState.SAFE_STOP.toByteArray(Charsets.US_ASCII))
                target.socket.outputStream.flush()
            } catch (_: IOException) {
                // A stop cannot be delivered through an already broken radio link.
            } catch (_: SecurityException) {
                // The controller must also stop actuators when its STATE pin goes low.
            } finally {
                closeSocket(target)
                deadline.cancel(false)
            }
        }
    }

    private fun failConnection(token: Long, message: String) {
        val old = synchronized(lock) {
            if (generation != token || state.phase != Phase.CONNECTING) return
            generation += 1
            val old = session
            session = null
            state = State(Phase.DISCONNECTED, message)
            old
        }
        notifyState()
        old?.let { closeSocket(it) }
    }

    private fun fail(target: Session, message: String) {
        synchronized(lock) {
            if (session !== target) return
            generation += 1
            session = null
            state = State(Phase.DISCONNECTED, message)
        }
        notifyState()
        closeSocket(target)
    }

    private fun closeSocket(target: Session) {
        try { target.socket.close() } catch (_: IOException) { }
    }

    fun shutdown() {
        disconnect()
        synchronized(lock) { closed = true }
        listener = null
        writer.shutdown()
        workers.shutdown()
        // Keep the deadline executor alive until queued safety tasks have run.
        timers.shutdown()
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
