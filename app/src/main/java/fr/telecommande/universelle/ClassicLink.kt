package fr.telecommande.universelle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class ClassicLink(context: Context, private var listener: ((LinkState) -> Unit)?) : RobotLink {
    private class Session(val socket: BluetoothSocket, val name: String, val address: String) {
        val started = SystemClock.elapsedRealtime()
    }
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newCachedThreadPool()
    private val writer = Executors.newSingleThreadExecutor()
    private val timers = Executors.newSingleThreadScheduledExecutor()
    private var current: Session? = null
    private var generation = 0L
    private var closed = false
    private var state = LinkState(LinkPhase.DISCONNECTED, "Déconnecté")
    private val appContext = context.applicationContext
    private val disconnectReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
                intent.action != BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val address = device?.address ?: return
            val target = synchronized(lock) {
                current?.takeIf { !closed && state.phase == LinkPhase.CONNECTED && it.address == address }
            } ?: return
            fail(target, "Liaison Classic interrompue : déconnexion signalée par Android (ACL).")
        }
    }
    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED).apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        // Bluetooth stack broadcasts may originate outside the system UID. Both actions are protected.
        if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(disconnectReceiver, filter, Context.RECEIVER_EXPORTED)
        else appContext.registerReceiver(disconnectReceiver, filter)
    }
    private fun report() { main.post { listener?.invoke(synchronized(lock) { state }) } }
    private fun closeSocket(s: Session) { try { s.socket.close() } catch (_: IOException) { } }

    override fun connect(device: BluetoothDevice, initialStop: Char) {
        require(isCommand(initialStop.toString(), false))
        val token = synchronized(lock) {
            if (closed || state.phase != LinkPhase.DISCONNECTED) return
            generation += 1
            state = LinkState(LinkPhase.CONNECTING, "Connexion Classic SPP…")
            generation
        }
        report()
        val deadline = timers.schedule({ failConnecting(token, "Délai de connexion dépassé.") }, 12, TimeUnit.SECONDS)
        workers.execute {
            var candidate: Session? = null
            var stage = "création SPP"
            try {
                val name = device.name ?: device.address
                val socket = device.createRfcommSocketToServiceRecord(SPP)
                val target = Session(socket, name, device.address)
                candidate = target
                val accepted = synchronized(lock) {
                    if (closed || generation != token) false else { current = target; true }
                }
                if (!accepted) { closeSocket(target); return@execute }
                stage = "connexion SPP"
                socket.connect()
                stage = "arrêt initial"
                socket.outputStream.write(byteArrayOf(initialStop.code.toByte()))
                socket.outputStream.flush()
                val ready = synchronized(lock) {
                    if (closed || generation != token || current !== target) false else {
                        state = LinkState(LinkPhase.CONNECTED, "Connecté · ${target.name}", initialStop.toString()); true
                    }
                }
                if (!ready) { closeSocket(target); return@execute }
                deadline.cancel(false); report()
                // This controller has no RX protocol. Like the supplied MIT project, it does not
                // keep a blocking read pending on a silent module. Android ACL events and write
                // errors detect loss instead; no heartbeat or repeated command is introduced.
            } catch (e: IOException) {
                candidate?.let { fail(it, ioFailure(it, stage, e)) }
                    ?: failConnecting(token, "Impossible de créer la connexion SPP : ${errorDetail(e)}")
            } catch (_: SecurityException) {
                candidate?.let { fail(it, "Autorisation Bluetooth retirée.") }
                    ?: failConnecting(token, "Autorisation Bluetooth requise.")
            } finally { deadline.cancel(false) }
        }
    }

    override fun send(command: Char) {
        require(isCommand(command.toString(), false))
        val target = synchronized(lock) {
            if (closed || state.phase != LinkPhase.CONNECTED) return
            current ?: return
        }
        writer.execute {
            if (!synchronized(lock) { current === target && state.phase == LinkPhase.CONNECTED }) return@execute
            val deadline = timers.schedule({ fail(target, "Écriture bloquée : connexion fermée.") }, 1500, TimeUnit.MILLISECONDS)
            try {
                target.socket.outputStream.write(byteArrayOf(command.code.toByte()))
                target.socket.outputStream.flush()
                synchronized(lock) {
                    if (current === target) state = state.copy(lastWritten = command.toString())
                }
                report()
            } catch (e: IOException) { fail(target, ioFailure(target, "envoi de $command", e)) }
            catch (_: SecurityException) { fail(target, "Autorisation Bluetooth retirée.") }
            finally { deadline.cancel(false) }
        }
    }

    override fun disconnect(stop: List<Char>, reason: String) {
        val (target, connected) = synchronized(lock) {
            generation += 1
            val pair = current to (state.phase == LinkPhase.CONNECTED)
            current = null
            state = LinkState(LinkPhase.DISCONNECTED, reason)
            pair
        }
        report()
        if (target == null) return
        if (!connected) { workers.execute { closeSocket(target) }; return }
        val deadline = timers.schedule({ closeSocket(target) }, 2, TimeUnit.SECONDS)
        writer.execute {
            try {
                target.socket.outputStream.write(stop.map { it.code.toByte() }.toByteArray())
                target.socket.outputStream.flush()
            } catch (_: IOException) { } catch (_: SecurityException) { }
            finally { closeSocket(target); deadline.cancel(false) }
        }
    }
    private fun failConnecting(token: Long, reason: String) {
        val old = synchronized(lock) {
            if (generation != token || state.phase != LinkPhase.CONNECTING) return
            generation += 1
            current.also { current = null; state = LinkState(LinkPhase.DISCONNECTED, reason) }
        }
        report(); old?.let(::closeSocket)
    }
    private fun errorDetail(e: IOException) = e.message?.replace(Regex("[\\r\\n]+"), " ")?.take(180) ?: "IOException sans détail Android"
    private fun ioFailure(target: Session, stage: String, e: IOException): String {
        val seconds = (SystemClock.elapsedRealtime() - target.started) / 1000
        return "Erreur Classic · $stage · après ${seconds}s : ${errorDetail(e)}"
    }
    private fun fail(target: Session, reason: String) {
        synchronized(lock) {
            if (current !== target) return
            generation += 1; current = null; state = LinkState(LinkPhase.DISCONNECTED, reason)
        }
        report(); closeSocket(target)
    }
    override fun close() {
        if (synchronized(lock) { closed }) return
        disconnect(emptyList(), "Déconnecté")
        synchronized(lock) { closed = true }
        appContext.unregisterReceiver(disconnectReceiver)
        listener = null; writer.shutdown(); workers.shutdown(); timers.shutdown()
    }
    companion object { val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb") }
}
