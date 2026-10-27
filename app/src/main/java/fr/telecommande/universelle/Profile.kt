package fr.telecommande.universelle

import java.util.UUID

enum class ButtonMode { HOLD, TOGGLE }
enum class RadioMode { CLASSIC, BLE }

fun isCommand(value: String, allowEmpty: Boolean = true): Boolean =
    (allowEmpty && value.isEmpty()) || (value.length == 1 && value[0].code in 33..126)

data class ButtonConfig(val id: String, val label: String, val mode: ButtonMode,
    val down: String, val up: String) {
    fun validate() {
        require(label.isNotBlank() && label.length <= 24) { "Le nom du bouton doit contenir de 1 à 24 caractères." }
        require(isCommand(down) && isCommand(up)) { "Une commande doit contenir un caractère ASCII visible, ou être vide." }
    }
}

object BleUuids {
    const val SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val WRITE = "0000ffe1-0000-1000-8000-00805f9b34fb"
    fun normalize(input: String): String {
        val text = input.trim().removePrefix("0x").removePrefix("0X")
        val full = when {
            text.matches(Regex("[0-9a-fA-F]{4}")) -> "0000$text-0000-1000-8000-00805f9b34fb"
            text.matches(Regex("[0-9a-fA-F]{8}")) -> "$text-0000-1000-8000-00805f9b34fb"
            text.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) -> text
            else -> throw IllegalArgumentException("UUID BLE invalide. Exemple : FFE0 ou un UUID complet.")
        }
        return UUID.fromString(full).toString()
    }
}

data class RobotProfile(val id: String, val name: String, val radio: RadioMode,
    val safety: String, val buttons: List<ButtonConfig>,
    val service: String = BleUuids.SERVICE, val characteristic: String = BleUuids.WRITE,
    val address: String = "", val deviceName: String = "") {
    fun validate() {
        require(id.isNotBlank()) { "Identifiant de profil manquant." }
        require(name.isNotBlank() && name.length <= 40) { "Le profil doit avoir un nom de 1 à 40 caractères." }
        require(isCommand(safety, false)) { "L’arrêt de sécurité nécessite un caractère ASCII visible." }
        require(buttons.size == 8 && buttons.map { it.id } == IDS) { "Le profil doit contenir les huit boutons attendus." }
        buttons.forEach { it.validate() }
        BleUuids.normalize(service); BleUuids.normalize(characteristic)
        require(address.isEmpty() || address.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))) { "Adresse Bluetooth invalide." }
    }

    companion object {
        val IDS = listOf("forward", "backward", "left", "right", "reserved1", "reserved2", "reserved3", "reserved4")
        fun humBot(id: String = UUID.randomUUID().toString(), name: String = "Hum-Bot") = RobotProfile(
            id, name, RadioMode.CLASSIC, "S", listOf(
                ButtonConfig("forward", "Avancer", ButtonMode.HOLD, "A", "S"),
                ButtonConfig("backward", "Reculer", ButtonMode.HOLD, "R", "S"),
                ButtonConfig("left", "Gauche", ButtonMode.HOLD, "G", "S"),
                ButtonConfig("right", "Droite", ButtonMode.HOLD, "D", "S"),
                ButtonConfig("reserved1", "Phares", ButtonMode.TOGGLE, "F", "f"),
                ButtonConfig("reserved2", "Feux stop", ButtonMode.TOGGLE, "T", "t"),
                ButtonConfig("reserved3", "Klaxon", ButtonMode.TOGGLE, "K", "k"),
                ButtonConfig("reserved4", "Détresse", ButtonMode.TOGGLE, "X", "x")
            ))
        fun blank(name: String) = humBot(name = name).let { p ->
            p.copy(buttons = p.buttons.mapIndexed { index, b ->
                if (index < 4) b else b.copy(label = "Réservé ${index - 3}", mode = ButtonMode.HOLD, down = "", up = "")
            })
        }
    }
}
