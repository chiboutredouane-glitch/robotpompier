package fr.telecommande.universelle

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import fr.telecommande.universelle.Ui.dp
import java.util.UUID

class SettingsActivity : Activity() {
    private lateinit var store: ProfileStore
    private lateinit var draft: RobotProfile
    private lateinit var name: EditText
    private lateinit var label: EditText
    private lateinit var down: EditText
    private lateinit var up: EditText
    private lateinit var safety: EditText
    private lateinit var service: EditText
    private lateinit var characteristic: EditText
    private lateinit var modes: Spinner
    private lateinit var radios: Spinner
    private lateinit var picker: Spinner
    private lateinit var downLabel: TextView
    private lateinit var upLabel: TextView
    private lateinit var bleFields: LinearLayout
    private var currentButton = 0
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore(this)
        draft = savedInstanceState?.getString("draft")?.let { text ->
            try { DraftCodec.decode(text) } catch (_: Exception) { null }
        } ?: store.selected
        currentButton = savedInstanceState?.getInt("button", 0)?.coerceIn(0, 7) ?: 0
        buildUi(); bindProfile()
    }

    private fun field(idValue: Int, max: Int, ascii: Boolean = false) = EditText(this).apply {
        id = idValue; isSingleLine = true
        filters = arrayOf(InputFilter.LengthFilter(max))
        inputType = if (ascii) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        textSize = 15f; setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED)
        if (ascii) typeface = Typeface.MONOSPACE
        background = Ui.shape(this@SettingsActivity, Ui.BG)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        minimumHeight = dp(48)
    }
    private fun labeled(parent: LinearLayout, caption: String, view: View, weight: Float = 1f): TextView {
        val column = Ui.column(this).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val title = Ui.text(this, caption, 12f, Ui.MUTED)
        column.addView(title, LinearLayout.LayoutParams(-1, dp(24)))
        column.addView(view, LinearLayout.LayoutParams(-1, dp(48)))
        parent.addView(column, Ui.weighted(height = -2, weight = weight))
        return title
    }
    private fun buildUi() {
        val root = Ui.root(this)
        val header = Ui.row(this)
        header.addView(Ui.button(this, "‹ Retour").apply { setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(80), dp(48)))
        header.addView(Ui.gap(this))
        header.addView(Ui.text(this, "CONFIGURATION DES BOUTONS", 18f).apply { typeface = Typeface.DEFAULT_BOLD }, Ui.weighted(height = dp(48)))
        header.addView(Ui.button(this, "Enregistrer", R.id.save).apply {
            setTextColor(Ui.CYAN); setOnClickListener { save() }
        }, LinearLayout.LayoutParams(dp(104), dp(48)))
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val form = Ui.column(this)
        val robot = Ui.panel(this)
        val robotRow = Ui.row(this)
        name = field(R.id.profile_name, 40)
        radios = Ui.spinner(this, listOf("Bluetooth Classic", "Bluetooth BLE"), R.id.profile_radio)
        safety = field(R.id.safety, 1, true)
        labeled(robotRow, "Nom du profil", name, 1.5f)
        labeled(robotRow, "Connexion", radios, 1.3f)
        labeled(robotRow, "Arrêt de sécurité", safety, .7f)
        robot.addView(robotRow)
        val actions = Ui.row(this)
        listOf(
            Ui.button(this, "+ Nouveau profil", R.id.new_profile).apply { setOnClickListener { newProfile() } },
            Ui.button(this, "Copier ce profil", R.id.duplicate_profile).apply { setOnClickListener { duplicate() } },
            Ui.button(this, "Supprimer", R.id.delete_profile).apply { setTextColor(Ui.RED); setOnClickListener { delete() } }
        ).forEach { actions.addView(it, Ui.weighted(height = dp(48)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
        robot.addView(actions)
        form.addView(robot)
        form.addView(Ui.gap(this, 1, 10))
        val controls = Ui.panel(this)
        controls.addView(Ui.text(this, "CHOISIR LE BOUTON À CONFIGURER", 12f, Ui.MUTED))
        picker = Ui.spinner(this, emptyList(), R.id.button_picker)
        controls.addView(picker, LinearLayout.LayoutParams(-1, dp(48)))
        val fields = Ui.row(this)
        label = field(R.id.button_label, 24)
        modes = Ui.spinner(this, listOf("Appui / relâchement", "Interrupteur ON / OFF"), R.id.button_mode)
        down = field(R.id.command_down, 1, true)
        up = field(R.id.command_up, 1, true)
        labeled(fields, "Nom affiché", label, 1.3f)
        labeled(fields, "Mode", modes, 1.7f)
        downLabel = labeled(fields, "À l’appui", down, .8f)
        upLabel = labeled(fields, "Au relâchement", up, .8f)
        controls.addView(fields)
        controls.addView(Ui.text(this, "Vide = aucun envoi. Un seul caractère ASCII visible. Respectez les majuscules et minuscules : S est différent de s.", 12f, Ui.MUTED).apply { setPadding(dp(5), dp(8), dp(5), dp(8)) })
        form.addView(controls)
        form.addView(Ui.gap(this, 1, 10))
        bleFields = Ui.panel(this)
        bleFields.addView(Ui.text(this, "PROFIL BLE · MLT-BT05 / MODULE SÉRIE", 12f, Ui.MUTED))
        val uuids = Ui.row(this)
        service = field(R.id.service, 38, true)
        characteristic = field(R.id.characteristic, 38, true)
        labeled(uuids, "UUID du service (ex. FFE0)", service)
        labeled(uuids, "UUID d’écriture (ex. FFE1)", characteristic)
        bleFields.addView(uuids)
        bleFields.addView(Ui.text(this, "Les services et la propriété WRITE sont vérifiés à la connexion. WRITE ou WRITE_NO_RESPONSE est choisi selon le module.", 12f, Ui.MUTED))
        form.addView(bleFields)
        form.addView(Ui.text(this, "Les changements prennent effet après Enregistrer. Renommer un bouton ne modifie pas ses lettres. Aucun ordre n’est envoyé depuis cet écran.", 12f, Ui.MUTED).apply { setPadding(dp(4), dp(12), dp(4), dp(12)) })
        scroll.addView(form)
        root.addView(scroll, Ui.weighted(width = -1, height = 0))
        setContentView(root); root.requestApplyInsets()
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding || position == currentButton) return
                if (!captureButton()) { binding = true; picker.setSelection(currentButton); binding = false; return }
                currentButton = position; bindButton()
            }
        }
        modes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updateEventLabels() }
        }
        radios.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { bleFields.visibility = if (position == 1) View.VISIBLE else View.GONE }
        }
    }

    private fun bindProfile() {
        binding = true
        name.setText(draft.name); safety.setText(draft.safety)
        radios.setSelection(if (draft.radio == RadioMode.BLE) 1 else 0)
        service.setText(draft.service); characteristic.setText(draft.characteristic)
        picker.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, draft.buttons.mapIndexed { i, b -> "${i + 1} · ${b.label}" }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        picker.setSelection(currentButton)
        findViewById<Button>(R.id.delete_profile).isEnabled = store.profiles.any { it.id == draft.id }
        bleFields.visibility = if (draft.radio == RadioMode.BLE) View.VISIBLE else View.GONE
        bindButton(); binding = false
    }
    private fun bindButton() {
        val b = draft.buttons[currentButton]
        label.setText(b.label); down.setText(b.down); up.setText(b.up)
        modes.setSelection(if (b.mode == ButtonMode.TOGGLE) 1 else 0)
        updateEventLabels()
    }
    private fun updateEventLabels() {
        val toggle = modes.selectedItemPosition == 1
        downLabel.text = if (toggle) "Activation · 1er clic" else "À l’appui"
        upLabel.text = if (toggle) "Arrêt · 2e clic" else "Au relâchement"
    }
    private fun captureButton(): Boolean {
        val b = draft.buttons[currentButton].copy(label = label.text.toString().trim(),
            mode = if (modes.selectedItemPosition == 1) ButtonMode.TOGGLE else ButtonMode.HOLD,
            down = down.text.toString(), up = up.text.toString())
        return try {
            b.validate()
            draft = draft.copy(buttons = draft.buttons.mapIndexed { i, old -> if (i == currentButton) b else old })
            true
        } catch (e: IllegalArgumentException) { toast(e.message ?: "Vérifiez les commandes."); false }
    }
    private fun captureProfile(): Boolean {
        if (!captureButton()) return false
        return try {
            val selectedRadio = if (radios.selectedItemPosition == 1) RadioMode.BLE else RadioMode.CLASSIC
            draft = draft.copy(name = name.text.toString().trim(), safety = safety.text.toString(), radio = selectedRadio,
                service = BleUuids.normalize(service.text.toString()), characteristic = BleUuids.normalize(characteristic.text.toString()),
                address = if (selectedRadio == draft.radio) draft.address else "", deviceName = if (selectedRadio == draft.radio) draft.deviceName else "")
            draft.validate(); true
        } catch (e: IllegalArgumentException) { toast(e.message ?: "Vérifiez le profil."); false }
    }
    private fun save() {
        if (!captureProfile()) return
        try { store.save(draft); setResult(RESULT_OK); finish() }
        catch (e: IllegalArgumentException) { toast(e.message ?: "Impossible d’enregistrer.") }
    }
    private fun newProfile() {
        val input = field(View.NO_ID, 40).apply { setText(getString(R.string.new_robot_name, store.profiles.size + 1)) }
        val choices = Ui.spinner(this, listOf("Base Hum-Bot", "Réservés sans commande"), View.NO_ID)
        val content = Ui.column(this).apply { setPadding(dp(16), dp(8), dp(16), dp(8)); addView(input); addView(choices) }
        AlertDialog.Builder(this).setTitle("Nouveau profil").setView(content)
            .setPositiveButton("Créer") { _, _ ->
                if (input.text.toString().isBlank()) { toast("Donnez un nom au profil."); return@setPositiveButton }
                draft = if (choices.selectedItemPosition == 0) RobotProfile.humBot(name = input.text.toString().trim()) else RobotProfile.blank(input.text.toString().trim())
                currentButton = 0; bindProfile()
            }.setNegativeButton("Annuler", null).show()
    }
    private fun duplicate() {
        if (!captureProfile()) return
        draft = draft.copy(id = UUID.randomUUID().toString(), name = (draft.name.take(33) + " copie"), address = "", deviceName = "")
        bindProfile(); toast("Copie prête : modifiez son nom puis enregistrez.")
    }
    private fun delete() {
        AlertDialog.Builder(this).setTitle("Supprimer ${draft.name} ?")
            .setMessage("Ses réglages seront supprimés. Si c’est le dernier profil, Hum-Bot sera rétabli.")
            .setPositiveButton("Supprimer") { _, _ -> store.delete(draft.id); finish() }
            .setNegativeButton("Annuler", null).show()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onSaveInstanceState(outState: Bundle) {
        if (captureProfile()) outState.putString("draft", DraftCodec.encode(draft))
        outState.putInt("button", currentButton)
        super.onSaveInstanceState(outState)
    }
}

/** Draft uses a private temporary preference-free encoding for activity recreation. */
internal object DraftCodec {
    fun encode(p: RobotProfile): String = org.json.JSONObject().apply {
        put("id", p.id); put("name", p.name); put("radio", p.radio.name); put("safety", p.safety)
        put("service", p.service); put("characteristic", p.characteristic); put("address", p.address); put("device", p.deviceName)
        put("buttons", org.json.JSONArray().apply { p.buttons.forEach { b -> put(org.json.JSONArray(listOf(b.id, b.label, b.mode.name, b.down, b.up))) } })
    }.toString()
    fun decode(s: String): RobotProfile {
        val o = org.json.JSONObject(s); val a = o.getJSONArray("buttons")
        return RobotProfile(o.getString("id"), o.getString("name"), RadioMode.valueOf(o.getString("radio")), o.getString("safety"),
            (0 until a.length()).map { i -> a.getJSONArray(i).let { ButtonConfig(it.getString(0), it.getString(1), ButtonMode.valueOf(it.getString(2)), it.getString(3), it.getString(4)) } },
            o.getString("service"), o.getString("characteristic"), o.optString("address"), o.optString("device")).also { it.validate() }
    }
}
