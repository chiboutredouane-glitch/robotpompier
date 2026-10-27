package fr.robotpompier.commande

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var serial: BluetoothSerial
    private lateinit var commands: CommandState
    private lateinit var commandButtons: List<Button>
    private var phase = BluetoothSerial.Phase.DISCONNECTED
    private var resumed = false
    private var picker: AlertDialog? = null
    private var receiverRegistered = false
    private val adapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON) {
                stopSession("Bluetooth désactivé. Commandes désactivées.")
            }
        }
    }

    @Suppress("DEPRECATION") // Insets fallback is needed on Android 6–10.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContentView(R.layout.activity_main)
        // Preserve system bars/cutout insets when targeting Android 15 edge-to-edge.
        val root = findViewById<View>(R.id.root)
        val base = (12 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                Rect(bars.left, bars.top, bars.right, bars.bottom)
            } else Rect(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            view.setPadding(base + safe.left, base + safe.top, base + safe.right, base + safe.bottom)
            insets
        }
        root.requestApplyInsets()
        serial = BluetoothSerial { render(it) }
        commands = CommandState { serial.send(it) }
        commandButtons = listOf(R.id.forward, R.id.backward, R.id.right, R.id.left, R.id.pump_on, R.id.pump_off)
            .map { findViewById(it) }
        bindDirection(R.id.forward, CommandState.Direction.FORWARD)
        bindDirection(R.id.backward, CommandState.Direction.BACKWARD)
        bindDirection(R.id.right, CommandState.Direction.RIGHT)
        bindDirection(R.id.left, CommandState.Direction.LEFT)
        findViewById<Button>(R.id.pump_on).setOnClickListener { commands.pump(true); updatePump() }
        findViewById<Button>(R.id.pump_off).setOnClickListener { commands.pump(false); updatePump() }
        findViewById<Button>(R.id.bluetooth).setOnClickListener {
            if (phase == BluetoothSerial.Phase.DISCONNECTED) chooseDevice()
            else stopSession("Déconnecté")
        }
        findViewById<Button>(R.id.close).setOnClickListener { closeApp() }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(radioReceiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(radioReceiver, filter)
        receiverRegistered = true
        render(BluetoothSerial.State(BluetoothSerial.Phase.DISCONNECTED, "Déconnecté"))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindDirection(id: Int, direction: CommandState.Direction) {
        val button = findViewById<Button>(id)
        var pointer = -1
        fun release() {
            commands.release(direction, touchWasActive = pointer != -1)
            pointer = -1
            button.isPressed = false
        }
        button.setOnTouchListener { _, event ->
            if (!button.isEnabled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointer = event.getPointerId(0)
                    button.isPressed = true
                    commands.press(direction)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) release()
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointer)
                    if (index < 0 || event.getX(index) < 0 || event.getX(index) >= button.width ||
                        event.getY(index) < 0 || event.getY(index) >= button.height) release()
                }
            }
            true
        }
        // TalkBack / switch-access activation is a short press followed by release.
        button.setOnClickListener {
            commands.press(direction)
            commands.release(direction)
        }
        button.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER &&
                keyCode != KeyEvent.KEYCODE_SPACE) return@setOnKeyListener false
            if (button.isEnabled) {
                if (event.action == KeyEvent.ACTION_DOWN) commands.press(direction)
                else if (event.action == KeyEvent.ACTION_UP) commands.release(direction)
            }
            true
        }
        button.setOnFocusChangeListener { _, focused -> if (!focused) release() }
    }

    private fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun chooseDevice() {
        if (!resumed) return
        if (!hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION)
            return
        }
        try {
            val radio = adapter
            if (radio == null) { hint("Ce téléphone ne dispose pas du Bluetooth Classic."); return }
            if (!radio.isEnabled) {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH)
                return
            }
            val devices = radio.bondedDevices.sortedWith(compareBy({ it.name ?: "" }, { it.address }))
            if (devices.isEmpty()) {
                picker = AlertDialog.Builder(this).setTitle("Aucun appareil appairé")
                    .setMessage("Appairez le HC-05 ou HC-06 dans les paramètres Bluetooth, puis revenez ici. Le code est souvent 1234 ou 0000.")
                    .setPositiveButton("Paramètres Bluetooth") { _, _ -> openBluetoothSettings() }
                    .setNegativeButton("Annuler", null).show()
                return
            }
            picker = AlertDialog.Builder(this).setTitle("Appareils déjà appairés")
                .setItems(devices.map { "${it.name ?: "Sans nom"}\n${it.address}" }.toTypedArray()) { _, index ->
                    commands.setConnected(false)
                    serial.connect(devices[index])
                }.setNegativeButton("Annuler", null)
                .setNeutralButton("Paramètres") { _, _ -> openBluetoothSettings() }.show()
        } catch (_: SecurityException) {
            stopSession("Autorisez l’accès aux appareils à proximité.")
        } catch (_: ActivityNotFoundException) {
            hint("Ouvrez les paramètres du téléphone pour activer le Bluetooth.")
        }
    }

    private fun openBluetoothSettings() {
        try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        catch (_: ActivityNotFoundException) { hint("Les paramètres Bluetooth ne sont pas disponibles.") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == PERMISSION && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) chooseDevice()
        else if (requestCode == PERMISSION) {
            hint("Autorisation refusée. Activez « Appareils à proximité » dans les autorisations de l’application.")
        }
    }

    @Deprecated("Platform activity result API used to avoid extra dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) findViewById<View>(R.id.bluetooth).post { chooseDevice() }
            else hint("Bluetooth désactivé. Activez-le pour connecter le robot.")
        }
    }

    private fun render(state: BluetoothSerial.State) {
        if (state.phase == BluetoothSerial.Phase.CONNECTED && (!resumed || !hasWindowFocus())) {
            stopSession("Commande interrompue par sécurité.")
            return
        }
        val connected = state.phase == BluetoothSerial.Phase.CONNECTED
        if (phase != state.phase) commands.setConnected(connected)
        phase = state.phase
        val color = Color.parseColor(if (connected) "#54D997" else "#FF6257")
        findViewById<View>(R.id.status_dot).background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(color)
        }
        findViewById<TextView>(R.id.status).apply { text = state.message; setTextColor(color) }
        findViewById<TextView>(R.id.last_command).text = when (val written = state.lastWritten) {
            null -> getString(R.string.no_transmission)
            else -> {
                if (written.length == 1) getString(R.string.transmission_byte, written,
                    written[0].code.toString(16).uppercase().padStart(2, '0'))
                else getString(R.string.transmission_sequence, written)
            }
        }
        commandButtons.forEach { it.isEnabled = connected; it.alpha = if (connected) 1f else 0.5f; if (!connected) it.isPressed = false }
        findViewById<Button>(R.id.bluetooth).text = when (state.phase) {
            BluetoothSerial.Phase.CONNECTED -> "Déconnecter"
            BluetoothSerial.Phase.CONNECTING -> "Annuler"
            BluetoothSerial.Phase.DISCONNECTED -> "Bluetooth"
        }
        if (connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hint(when (state.phase) {
            BluetoothSerial.Phase.CONNECTED -> "Déplacement et pompe utilisables simultanément. Relâchez pour arrêter le déplacement."
            BluetoothSerial.Phase.CONNECTING -> "Connexion SPP au robot…"
            BluetoothSerial.Phase.DISCONNECTED -> state.message + " Sélectionnez un appareil avec Bluetooth."
        })
        updatePump()
    }

    private fun updatePump() {
        findViewById<TextView>(R.id.pump_status).text = when {
            !commands.enabled -> "Pompe : état inconnu"
            commands.pumpOn -> "Activation demandée"
            else -> "Arrêt demandé"
        }
    }

    private fun hint(message: String) { findViewById<TextView>(R.id.hint).text = message }

    private fun stopSession(message: String) {
        commands.setConnected(false)
        serial.disconnect(message)
        render(BluetoothSerial.State(BluetoothSerial.Phase.DISCONNECTED, message))
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!hasBluetoothPermission()) stopSession("Autorisation Bluetooth requise.")
    }

    // Notification shade, task switch and lock screen must never leave a held command active.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::serial.isInitialized && phase != BluetoothSerial.Phase.DISCONNECTED) {
            stopSession("Commande interrompue par sécurité.")
        }
    }

    override fun onPause() {
        resumed = false
        picker?.dismiss()
        picker = null
        if (::serial.isInitialized) stopSession("Déconnecté en arrière-plan.")
        super.onPause()
    }

    private fun closeApp() {
        stopSession("Fermeture…")
        finishAndRemoveTask()
    }

    @Deprecated("Platform back handler")
    override fun onBackPressed() { closeApp() }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(radioReceiver)
        serial.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION = 100
        private const val ENABLE_BLUETOOTH = 101
    }
}
