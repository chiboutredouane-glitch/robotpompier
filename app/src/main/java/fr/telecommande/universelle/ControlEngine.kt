package fr.telecommande.universelle

/** Tracks user requests, never claims to know physical robot state. Main thread only. */
class ControlEngine(val profile: RobotProfile, private val write: (Char) -> Unit) {
    private val held = linkedSetOf<String>()
    private val requested = mutableMapOf<String, Boolean>()
    var connected = false
        private set
    init { profile.validate() }

    fun connection(ready: Boolean) { connected = ready; held.clear(); requested.clear() }
    fun requestedState(id: String): Boolean? = requested[id]
    private fun config(id: String) = profile.buttons.first { it.id == id }
    private fun emit(value: String) { if (value.isNotEmpty()) write(value.single()) }
    fun down(id: String) {
        val b = config(id)
        if (!connected || b.mode != ButtonMode.HOLD || !held.add(id)) return
        requested[id] = true; emit(b.down)
    }
    fun up(id: String, actualTouch: Boolean = false) {
        val b = config(id)
        val active = held.remove(id)
        if (!connected || b.mode != ButtonMode.HOLD || (!active && !actualTouch)) return
        requested[id] = false; emit(b.up)
    }
    fun click(id: String) {
        if (!connected) return
        val b = config(id)
        if (b.mode == ButtonMode.HOLD) { down(id); up(id); return }
        val next = requested[id] != true
        requested[id] = next
        emit(if (next) b.down else b.up)
    }
    /** Stop first, then deactivate only controls actually requested ON. Never send activation bytes. */
    fun deactivate(): List<Char> {
        if (!connected) { connection(false); return emptyList() }
        val stop = linkedSetOf(profile.safety.single())
        profile.buttons.filter { requested[it.id] == true }.forEach { b -> b.up.firstOrNull()?.let(stop::add) }
        connection(false)
        return stop.toList()
    }
}
