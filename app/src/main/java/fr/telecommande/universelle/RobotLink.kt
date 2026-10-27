package fr.telecommande.universelle

import android.bluetooth.BluetoothDevice

enum class LinkPhase { DISCONNECTED, CONNECTING, CONNECTED }
data class LinkState(val phase: LinkPhase, val message: String, val lastWritten: String? = null)

interface RobotLink {
    fun connect(device: BluetoothDevice, initialStop: Char)
    fun send(command: Char)
    fun disconnect(stop: List<Char>, reason: String = "Déconnecté")
    fun close()
}

/** One in-flight GATT operation; a safety stop drops waiting motion, not an in-flight write. */
class WriteBuffer {
    private val waiting = ArrayDeque<Char>()
    var inFlight: Char? = null
        private set
    val idle: Boolean get() = inFlight == null && waiting.isEmpty()
    fun enqueue(command: Char) { waiting.addLast(command) }
    fun beginStop(commands: List<Char>) { waiting.clear(); waiting.addAll(commands) }
    fun next(): Char? {
        if (inFlight != null || waiting.isEmpty()) return null
        return waiting.removeFirst().also { inFlight = it }
    }
    fun complete(): Char? = inFlight.also { inFlight = null }
    fun clear() { waiting.clear(); inFlight = null }
}
