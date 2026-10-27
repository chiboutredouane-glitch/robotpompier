package fr.telecommande.universelle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("robot_profiles_v1", Context.MODE_PRIVATE)
    var warning: String? = null
        private set
    var profiles: List<RobotProfile> = emptyList()
        private set
    var selectedId = ""
        private set
    val selected: RobotProfile get() = profiles.first { it.id == selectedId }

    init { reload() }
    fun reload() {
        warning = null
        val raw = prefs.getString("profiles", null)
        try {
            profiles = if (raw == null) listOf(RobotProfile.humBot()) else {
                val array = JSONArray(raw)
                require(array.length() in 1..30)
                (0 until array.length()).map { decode(array.getJSONObject(it)).also { p -> p.validate() } }
                    .also { require(it.map { p -> p.id }.toSet().size == it.size) }
            }
            selectedId = prefs.getString("selected", null)?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        } catch (_: Exception) {
            profiles = listOf(RobotProfile.humBot()); selectedId = profiles.first().id
            warning = "Les réglages enregistrés sont illisibles. Le profil Hum-Bot a été rétabli ; vérifiez ses commandes avant de connecter un robot."
        }
        persist()
    }
    fun select(id: String) { require(profiles.any { it.id == id }); selectedId = id; persist() }
    fun save(profile: RobotProfile) {
        profile.validate()
        require(profiles.size < 30 || profiles.any { it.id == profile.id }) { "Maximum : 30 profils." }
        profiles = if (profiles.any { it.id == profile.id }) profiles.map { if (it.id == profile.id) profile else it }
        else profiles + profile
        selectedId = profile.id; persist()
    }
    fun delete(id: String) {
        profiles = profiles.filterNot { it.id == id }.ifEmpty { listOf(RobotProfile.humBot()) }
        if (profiles.none { it.id == selectedId }) selectedId = profiles.first().id
        persist()
    }
    private fun persist() {
        val array = JSONArray()
        profiles.forEach { array.put(encode(it)) }
        prefs.edit().putString("profiles", array.toString()).putString("selected", selectedId).apply()
    }
    private fun encode(p: RobotProfile) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("radio", p.radio.name); put("safety", p.safety)
        put("service", p.service); put("characteristic", p.characteristic)
        put("address", p.address); put("deviceName", p.deviceName)
        put("buttons", JSONArray().apply { p.buttons.forEach { b -> put(JSONObject().apply {
            put("id", b.id); put("label", b.label); put("mode", b.mode.name); put("down", b.down); put("up", b.up)
        }) } })
    }
    private fun decode(o: JSONObject): RobotProfile {
        val buttons = o.getJSONArray("buttons")
        return RobotProfile(o.getString("id"), o.getString("name"), RadioMode.valueOf(o.getString("radio")),
            o.getString("safety"), (0 until buttons.length()).map { i -> buttons.getJSONObject(i).let {
                ButtonConfig(it.getString("id"), it.getString("label"), ButtonMode.valueOf(it.getString("mode")), it.getString("down"), it.getString("up"))
            } }, o.getString("service"), o.getString("characteristic"), o.optString("address"), o.optString("deviceName"))
    }
}
