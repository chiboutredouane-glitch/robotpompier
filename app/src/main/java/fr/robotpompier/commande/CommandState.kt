package fr.robotpompier.commande

/** Protocol state independent of Android. No terminators, spaces or heartbeat bytes. */
class CommandState(private val send: (String) -> Unit) {
    enum class Direction(val press: String, val release: String) {
        FORWARD("A", "a"), BACKWARD("R", "r"), RIGHT("D", "d"), LEFT("G", "g")
    }

    private val held = linkedSetOf<Direction>()
    var enabled = false
        private set
    var pumpOn = false
        private set

    fun setConnected(connected: Boolean) {
        enabled = connected
        held.clear()
        pumpOn = false
    }

    fun press(direction: Direction) {
        if (enabled && held.add(direction)) send(direction.press)
    }

    fun release(direction: Direction, touchWasActive: Boolean = false) {
        val wasHeld = held.remove(direction)
        // A real touch release is an explicit stop, even if local state was reset.
        if (enabled && (wasHeld || touchWasActive)) send(direction.release)
    }

    fun pump(on: Boolean) {
        if (!enabled) return
        pumpOn = on
        send(if (on) "E" else "F")
    }

    fun clear() {
        held.clear()
        pumpOn = false
    }

    companion object {
        // Every movement release plus pump off. Written once, without a line ending.
        const val SAFE_STOP = "ardgF"
    }
}
