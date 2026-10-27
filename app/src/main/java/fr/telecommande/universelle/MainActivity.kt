package fr.telecommande.universelle

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import fr.telecommande.universelle.Ui.dp

class MainActivity : Activity() {
    private lateinit var store: ProfileStore
    private lateinit var profile: RobotProfile
    private lateinit var engine: ControlEngine
    private var link: RobotLink? = null
    private var linkGeneration = 0
    private var phase = LinkPhase.DISCONNECTED
    private var resumed = false
    private var destroyed = false
    private var editing = false
    private val recentTx = ArrayDeque<String>()
    private var lastLinkState: LinkState? = null
    private lateinit var status: TextView
    private lateinit var tx: TextView
    private lateinit var footer: TextView
    private lateinit var profiles: Spinner
    private lateinit var connectButton: Button
    private lateinit var classicButton: Button
    private lateinit var bleButton: Button
    private lateinit var center: TextView
    private val controls = linkedMapOf<String, Button>()
    private val main = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null
    private var scanning: ScanCallback? = null
    private var receiverRegistered = false
    private val radio: BluetoothAdapter? get() = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanDeadline = Runnable { stopScan(); dialog?.setTitle("Appareils BLE détectés") }
    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON) {
                stopScan(); dialog?.dismiss(); stopSession("Bluetooth désactivé.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore(this); profile = store.selected
        makeEngine(); buildUi(); refreshProfiles(); refreshButtons()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(radioReceiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(radioReceiver, filter)
        receiverRegistered = true
        store.warning?.let { AlertDialog.Builder(this).setTitle("Vérifiez les réglages").setMessage(it).setPositiveButton("Compris", null).show() }
    }
    private fun makeEngine() { engine = ControlEngine(profile) { link?.send(it) } }

    private fun buildUi() {
        val root = Ui.root(this)
        val header = Ui.row(this)
        val title = Ui.text(this, "TÉLÉCOMMANDE\nUNIVERSELLE", 17f, Ui.TEXT).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        header.addView(title, Ui.weighted(height = dp(48), weight = 1.3f))
        profiles = Ui.spinner(this, listOf(profile.name), R.id.profile)
        profiles.contentDescription = "Profil du robot"
        header.addView(profiles, Ui.weighted(height = dp(48), weight = 1f))
        header.addView(Ui.gap(this, 6))
        header.addView(Ui.button(this, "⚙ Réglages", R.id.settings).apply { setOnClickListener { openSettings() } }, LinearLayout.LayoutParams(dp(96), dp(48)))
        header.addView(Ui.gap(this, 6))
        header.addView(Ui.button(this, "Quitter", R.id.close).apply { setOnClickListener { stopSession("Fermeture…"); finishAndRemoveTask() } }, LinearLayout.LayoutParams(dp(66), dp(48)))
        root.addView(header)
        val connection = Ui.row(this)
        connectButton = Ui.button(this, "Connecter", R.id.connect).apply {
            setTextColor(Ui.CYAN)
            setOnClickListener { if (phase == LinkPhase.DISCONNECTED) chooseDevice() else stopSession("Déconnecté") }
        }
        connection.addView(connectButton, LinearLayout.LayoutParams(dp(95), dp(48)))
        connection.addView(Ui.gap(this, 6))
        classicButton = Ui.button(this, "Classic", R.id.classic).apply { setOnClickListener { changeRadio(RadioMode.CLASSIC) } }
        bleButton = Ui.button(this, "BLE", R.id.ble).apply { setOnClickListener { changeRadio(RadioMode.BLE) } }
        connection.addView(classicButton, LinearLayout.LayoutParams(dp(68), dp(48)))
        connection.addView(Ui.gap(this, 3))
        connection.addView(bleButton, LinearLayout.LayoutParams(dp(54), dp(48)))
        status = Ui.text(this, "● Déconnecté", 12f, Ui.RED).apply {
            id = R.id.status; maxLines = 2; setPadding(dp(8), 0, dp(4), 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener { if (phase == LinkPhase.DISCONNECTED) showDiagnostics() }
        }
        connection.addView(status, Ui.weighted(height = dp(48)))
        tx = Ui.text(this, "TX : —", 12f).apply { id = R.id.tx; typeface = Typeface.MONOSPACE }
        connection.addView(tx, LinearLayout.LayoutParams(dp(116), dp(48)))
        root.addView(connection)
        root.addView(Ui.gap(this, 1, 6))
        val body = Ui.row(this)
        val directions = Ui.panel(this)
        directions.addView(Ui.text(this, "DÉPLACEMENT", 12f, Ui.MUTED), LinearLayout.LayoutParams(-1, dp(22)))
        profile.buttons.forEachIndexed { index, b ->
            controls[b.id] = Ui.button(this, b.label, Ui.controlIds[index], index < 4).apply {
                textSize = if (index < 4) 14f else 16f
                maxLines = 3
            }
        }
        center = Ui.text(this, "Relâcher → ${profile.safety}", 11f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        directions.addView(DpadPanel(this, profile.buttons.take(4).map { controls.getValue(it.id) }, center), Ui.weighted(width = -1, height = 0))
        body.addView(directions, Ui.weighted())
        body.addView(Ui.gap(this, 10))
        val reserved = Ui.panel(this)
        reserved.addView(Ui.text(this, "BOUTONS RÉSERVÉS", 12f, Ui.MUTED), LinearLayout.LayoutParams(-1, dp(22)))
        repeat(2) { row ->
            val cells = Ui.row(this)
            repeat(2) { col ->
                val b = controls.getValue(profile.buttons[4 + row * 2 + col].id)
                cells.addView(b, Ui.weighted().apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            reserved.addView(cells, Ui.weighted(width = -1, height = 0))
        }
        body.addView(reserved, Ui.weighted())
        root.addView(body, Ui.weighted(width = -1, height = 0))
        footer = Ui.text(this, "Noms et commandes personnalisables dans Réglages", 10f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(23)))
        controls.forEach { (id, button) -> bindControl(id, button) }
        setContentView(root); root.requestApplyInsets()
        profiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = store.profiles.getOrNull(position) ?: return
                if (selected.id == profile.id) return
                stopSession("Profil changé."); disposeLink()
                store.select(selected.id); profile = selected; makeEngine(); refreshButtons()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindControl(id: String, button: Button) {
        var pointer = -1
        var eligible = false
        fun release(cancelled: Boolean) {
            val active = pointer != -1
            if (profile.buttons.first { it.id == id }.mode == ButtonMode.HOLD) engine.up(id, active)
            else if (active && eligible && !cancelled && button.isEnabled) button.performClick()
            pointer = -1; eligible = false; button.isPressed = false; refreshButtonStates()
        }
        button.setOnTouchListener { _, event ->
            if (!button.isEnabled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pointer = event.getPointerId(0); eligible = true; button.isPressed = true; engine.down(id); refreshButtonStates() }
                MotionEvent.ACTION_UP -> release(false)
                MotionEvent.ACTION_CANCEL -> release(true)
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) release(false)
                MotionEvent.ACTION_MOVE -> {
                    val i = event.findPointerIndex(pointer)
                    if (i < 0 || event.getX(i) !in 0f..button.width.toFloat() || event.getY(i) !in 0f..button.height.toFloat()) release(true)
                }
            }
            true
        }
        button.setOnClickListener { engine.click(id); refreshButtonStates() }
        button.setOnKeyListener { _, key, event ->
            if (key !in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER) ||
                profile.buttons.first { it.id == id }.mode != ButtonMode.HOLD) return@setOnKeyListener false
            if (button.isEnabled) { if (event.action == KeyEvent.ACTION_DOWN) engine.down(id) else if (event.action == KeyEvent.ACTION_UP) engine.up(id) }
            refreshButtonStates(); true
        }
        button.setOnFocusChangeListener { _, focused -> if (!focused && pointer == -1) { engine.up(id); refreshButtonStates() } }
    }

    private fun refreshProfiles() {
        profiles.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, store.profiles.map { it.name }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        profiles.setSelection(store.profiles.indexOfFirst { it.id == profile.id }.coerceAtLeast(0))
    }
    private fun refreshButtons() {
        val arrows = listOf("↑", "↓", "←", "→")
        profile.buttons.forEachIndexed { index, b ->
            val text = if (index < 4) "${arrows[index]}\n${b.label}" else b.label
            controls.getValue(b.id).apply {
                this.text = if (index < 4) SpannableString(text).apply { setSpan(RelativeSizeSpan(1.6f), 0, 1, 0) } else text
                contentDescription = b.label
            }
        }
        center.text = getString(R.string.safety_caption, profile.safety)
        Ui.paint(classicButton, selected = profile.radio == RadioMode.CLASSIC)
        Ui.paint(bleButton, selected = profile.radio == RadioMode.BLE)
        refreshButtonStates()
    }
    private fun refreshButtonStates() {
        profile.buttons.forEachIndexed { index, b ->
            controls.getValue(b.id).apply {
                isEnabled = engine.connected
                alpha = if (isEnabled) 1f else .75f
                if (index >= 4) {
                    val value = when (engine.requestedState(b.id)) { true -> "ON demandé"; false -> "OFF demandé"; null -> "—" }
                    text = getString(R.string.reserved_caption, b.label, index - 3, value)
                    Ui.paint(this, selected = engine.requestedState(b.id) == true)
                }
                if (!isEnabled) isPressed = false
            }
        }
    }
    private fun render(state: LinkState) {
        if (destroyed) return
        if (state.phase == LinkPhase.CONNECTED && (!resumed || !hasWindowFocus())) { stopSession("Commande interrompue par sécurité."); return }
        val previous = lastLinkState
        if (state.phase == LinkPhase.CONNECTING && previous?.phase != LinkPhase.CONNECTING) recentTx.clear()
        if (state.lastWritten != null && state != previous) {
            recentTx.addLast(state.lastWritten)
            while (recentTx.size > 20) recentTx.removeFirst()
        }
        if (state.phase == LinkPhase.DISCONNECTED && previous != null && previous.phase != LinkPhase.DISCONNECTED) {
            val diagnostic = "Télécommande universelle ${getString(R.string.app_version)}\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Mode : ${profile.radio}\nMotif : ${state.message}\nDerniers TX : ${recentTx.joinToString(" ").ifEmpty { "aucun" }}"
            getSharedPreferences("connection_diagnostics", MODE_PRIVATE).edit().putString("last", diagnostic).apply()
        }
        lastLinkState = state
        if (phase != state.phase) engine.connection(state.phase == LinkPhase.CONNECTED)
        phase = state.phase
        status.text = getString(R.string.connection_caption, state.message)
        status.setTextColor(if (phase == LinkPhase.CONNECTED) Ui.GREEN else Ui.RED)
        tx.text = state.lastWritten?.let { "TX : $it · 0x${it.single().code.toString(16).uppercase().padStart(2, '0')}" } ?: "TX : —"
        connectButton.text = when (phase) { LinkPhase.CONNECTED -> "Déconnecter"; LinkPhase.CONNECTING -> "Annuler"; else -> "Connecter" }
        if (phase == LinkPhase.CONNECTED) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        footer.text = if (phase == LinkPhase.CONNECTED) "TX = écriture côté téléphone · ON/OFF = commande demandée" else "Toucher l’état rouge pour consulter le diagnostic de connexion"
        refreshButtonStates()
    }
    private fun stopSession(reason: String) {
        val stop = engine.deactivate()
        link?.disconnect(stop, reason)
        render(LinkState(LinkPhase.DISCONNECTED, reason))
    }
    private fun showDiagnostics() {
        val diagnostic = getSharedPreferences("connection_diagnostics", MODE_PRIVATE).getString("last", null)
            ?: "Aucune déconnexion enregistrée. Connectez le module puis consultez cet écran après la coupure."
        val text = Ui.text(this, diagnostic, 15f).apply { setTextIsSelectable(true); setPadding(dp(20), dp(12), dp(20), dp(12)) }
        dialog = AlertDialog.Builder(this).setTitle("Diagnostic Bluetooth").setView(text).setPositiveButton("Fermer", null).show()
    }
    private fun disposeLink() { linkGeneration++; link?.close(); link = null }
    private fun changeRadio(mode: RadioMode) {
        if (mode == profile.radio) return
        stopSession("Mode Bluetooth changé."); disposeLink()
        profile = profile.copy(radio = mode, address = "", deviceName = "")
        store.save(profile); makeEngine(); refreshButtons()
    }
    private fun openSettings() {
        stopSession("Déconnecté pour modifier les réglages."); disposeLink(); editing = true
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun neededPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 31 && profile.radio == RadioMode.BLE -> arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        Build.VERSION.SDK_INT >= 31 -> arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        profile.radio == RadioMode.BLE -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> emptyArray()
    }
    @SuppressLint("MissingPermission")
    private fun chooseDevice() {
        if (!resumed) return
        val missing = neededPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 100); return }
        try {
            val adapter = radio ?: run { notice("Bluetooth non disponible sur ce téléphone."); return }
            if (!adapter.isEnabled) { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 101); return }
            if (profile.radio == RadioMode.CLASSIC) {
                val devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
                if (devices.isEmpty()) {
                    dialog = AlertDialog.Builder(this).setTitle("Aucun appareil appairé")
                        .setMessage("Appairez votre HC-05 ou HC-06 dans les paramètres Bluetooth, puis revenez ici.")
                        .setPositiveButton("Paramètres Bluetooth") { _, _ -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        .setNegativeButton("Annuler", null).show()
                } else dialog = AlertDialog.Builder(this).setTitle("Appareils appairés · Classic")
                    .setItems(devices.map { "${it.name ?: "Sans nom"}\n${it.address}" }.toTypedArray()) { _, i -> connect(devices[i]) }
                    .setNegativeButton("Annuler", null).show()
            } else scanBle(adapter)
        } catch (_: SecurityException) { notice("Autorisez les appareils à proximité dans les réglages Android.") }
        catch (_: android.content.ActivityNotFoundException) { notice("Ouvrez les paramètres Bluetooth du téléphone.") }
    }
    @SuppressLint("MissingPermission")
    private fun scanBle(adapter: BluetoothAdapter) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { notice("Ce téléphone ne prend pas en charge le BLE."); return }
        if (Build.VERSION.SDK_INT < 31) {
            val locations = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled = if (Build.VERSION.SDK_INT >= 28) locations.isLocationEnabled else
                locations.isProviderEnabled(LocationManager.GPS_PROVIDER) || locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!enabled) {
                dialog = AlertDialog.Builder(this).setTitle("Recherche BLE sur Android 6 à 11")
                    .setMessage("Android demande d’activer la localisation pour rechercher les appareils BLE. L’application ne collecte pas votre position.")
                    .setPositiveButton("Ouvrir les réglages") { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    .setNegativeButton("Annuler", null).show(); return
            }
        }
        val scanner = adapter.bluetoothLeScanner ?: run { notice("Recherche BLE non disponible."); return }
        val devices = mutableListOf<BluetoothDevice>()
        val names = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val scan = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                main.post {
                    if (scanning !== this || !resumed) return@post
                    try {
                        if (devices.none { it.address == result.device.address }) {
                            devices.add(result.device)
                            names.add("${result.scanRecord?.deviceName ?: result.device.name ?: "Appareil BLE sans nom"}\n${result.device.address}")
                        }
                    } catch (_: SecurityException) { stopScan(); notice("Autorisation Bluetooth retirée.") }
                }
            }
            override fun onScanFailed(errorCode: Int) { main.post { if (scanning === this) { stopScan(); dialog?.setTitle("Recherche BLE indisponible ($errorCode)") } } }
        }
        val builder = AlertDialog.Builder(this).setTitle("Recherche BLE · 12 secondes")
            .setAdapter(names) { _, i -> stopScan(); devices.getOrNull(i)?.let(::connect) }
            .setNegativeButton("Annuler", null)
        if (profile.address.isNotEmpty()) builder.setNeutralButton("Dernier appareil") { _, _ ->
            stopScan(); connect(adapter.getRemoteDevice(profile.address))
        }
        dialog = builder.create().apply { setOnDismissListener { stopScan() }; show() }
        scanning = scan
        try { scanner.startScan(scan); main.postDelayed(scanDeadline, 12000) }
        catch (_: SecurityException) { stopScan(); notice("Autorisation de recherche Bluetooth requise.") }
        catch (_: IllegalStateException) { stopScan(); notice("Activez le Bluetooth avant la recherche.") }
    }
    @SuppressLint("MissingPermission")
    private fun stopScan() {
        main.removeCallbacks(scanDeadline)
        val old = scanning; scanning = null
        if (old != null) try { radio?.bluetoothLeScanner?.stopScan(old) } catch (_: SecurityException) { } catch (_: IllegalStateException) { }
    }
    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!resumed) return
        try {
            disposeLink()
            profile = profile.copy(address = device.address, deviceName = device.name ?: "")
            store.save(profile)
            val token = linkGeneration
            val listener: (LinkState) -> Unit = { if (token == linkGeneration && !destroyed) render(it) }
            link = if (profile.radio == RadioMode.CLASSIC) ClassicLink(listener) else BleLink(this, profile, listener)
            render(LinkState(LinkPhase.CONNECTING, "Connexion en cours…"))
            link?.connect(device, profile.safety.single())
        } catch (_: SecurityException) { stopSession("Autorisation Bluetooth retirée.") }
    }
    private fun notice(text: String) { status.text = text; status.setTextColor(Ui.RED); Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 100) {
            if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) main.post { chooseDevice() }
            else notice("Autorisation refusée. Vérifiez les autorisations de l’application dans Android.")
        }
    }
    @Deprecated("Native activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) main.post { chooseDevice() }
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (editing) { editing = false; store.reload(); profile = store.selected; makeEngine(); refreshProfiles(); refreshButtons() }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::engine.isInitialized && phase != LinkPhase.DISCONNECTED) stopSession("Commande interrompue par sécurité.")
    }
    override fun onPause() {
        resumed = false; stopScan(); dialog?.dismiss(); dialog = null
        if (::engine.isInitialized && phase != LinkPhase.DISCONNECTED) stopSession("Déconnecté en arrière-plan.")
        super.onPause()
    }
    @Deprecated("Native back handler")
    override fun onBackPressed() { stopSession("Fermeture…"); finishAndRemoveTask() }
    override fun onDestroy() {
        destroyed = true; stopScan(); disposeLink()
        if (receiverRegistered) unregisterReceiver(radioReceiver)
        super.onDestroy()
    }
}
