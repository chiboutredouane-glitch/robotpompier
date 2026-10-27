package fr.telecommande.universelle

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.InputDevice
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w720dp-h360dp-land-mdpi")
class ActivityTest {
    @Before fun clear() {
        RuntimeEnvironment.getApplication().getSharedPreferences("robot_profiles_v1", 0).edit().clear().commit()
        RuntimeEnvironment.getApplication().getSharedPreferences("connection_diagnostics", 0).edit().clear().commit()
    }
    private fun render(activity: MainActivity, state: LinkState) {
        MainActivity::class.java.getDeclaredMethod("render", LinkState::class.java).apply { isAccessible = true }.invoke(activity, state)
    }
    @Test fun writeFailureAndTxRemainAvailableAfterBackgroundAndRecreation() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
        val a = c.get()
        render(a, LinkState(LinkPhase.CONNECTING, "Connexion"))
        render(a, LinkState(LinkPhase.CONNECTED, "Connecté", "S"))
        render(a, LinkState(LinkPhase.CONNECTED, "Connecté", "F"))
        render(a, LinkState(LinkPhase.DISCONNECTED, "Écriture BLE sans réponse : connexion fermée."))
        c.pause().stop().destroy()
        val next = Robolectric.buildActivity(MainActivity::class.java).setup()
        val diagnostic = next.get().getSharedPreferences("connection_diagnostics", 0).getString("last", "")!!
        assertTrue(diagnostic.contains("Écriture BLE sans réponse"))
        assertTrue(diagnostic.contains("S F"))
        next.get().findViewById<TextView>(R.id.status).performClick()
        assertEquals("Diagnostic Bluetooth", shadowOf(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()).title.toString())
        next.pause().stop().destroy()
    }
    @Test fun focusSafetyReasonIsRecordedWithoutBeingReplacedByOnPause() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
        render(c.get(), LinkState(LinkPhase.CONNECTING, "Connexion"))
        render(c.get(), LinkState(LinkPhase.CONNECTED, "Connecté", "S"))
        c.windowFocusChanged(false).pause()
        assertTrue(c.get().getSharedPreferences("connection_diagnostics", 0).getString("last", "")!!.contains("Commande interrompue par sécurité."))
        assertTrue(c.get().findViewById<TextView>(R.id.status).text.contains("Commande interrompue"))
        c.stop().destroy()
    }
    @Test fun touchingConnectedStatusDoesNotOpenDialogOrDisconnect() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
        render(c.get(), LinkState(LinkPhase.CONNECTING, "Connexion"))
        render(c.get(), LinkState(LinkPhase.CONNECTED, "Connecté", "S"))
        c.get().findViewById<TextView>(R.id.status).performClick()
        assertTrue(c.get().findViewById<Button>(R.id.forward).isEnabled)
        assertEquals("Connecté", c.get().findViewById<TextView>(R.id.status).text.toString().removePrefix("● "))
        c.pause().stop().destroy()
    }
    private fun touch(button: Button, action: Int, x: Float = 30f) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, 20f, 0)
        try { button.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun fakeEngine(activity: MainActivity, output: StringBuilder): ControlEngine {
        val e = ControlEngine(ProfileStore(activity).selected) { output.append(it) }.apply { connection(true) }
        MainActivity::class.java.getDeclaredField("engine").apply { isAccessible = true; set(activity, e) }
        Ui.controlIds.forEach { activity.findViewById<Button>(it).apply { isEnabled = true; layout(0, 0, 140, 140) } }
        return e
    }
    @Test fun allCommandsStartDisabled() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        Ui.controlIds.forEach { assertFalse(c.get().findViewById<Button>(it).isEnabled) }
        c.pause().stop().destroy()
    }
    @Test fun nativeDirectionTouchesSendSOnLift() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val out = StringBuilder()
        fakeEngine(c.get(), out)
        listOf(R.id.forward, R.id.backward, R.id.left, R.id.right).forEach {
            val b = c.get().findViewById<Button>(it); touch(b, MotionEvent.ACTION_DOWN); touch(b, MotionEvent.ACTION_UP)
        }
        assertEquals("ASRSGSDS", out.toString()); c.pause().stop().destroy()
    }
    @Test fun leavingButtonAndCancellingCannotRestartMotion() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val out = StringBuilder()
        fakeEngine(c.get(), out); val b = c.get().findViewById<Button>(R.id.forward)
        touch(b, MotionEvent.ACTION_DOWN); touch(b, MotionEvent.ACTION_MOVE, -20f)
        touch(b, MotionEvent.ACTION_CANCEL); touch(b, MotionEvent.ACTION_UP)
        assertEquals("AS", out.toString()); c.pause().stop().destroy()
    }
    @Test fun toggleTouchFiresOnReleaseAndCancelledToggleDoesNothing() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val out = StringBuilder()
        fakeEngine(c.get(), out); val b = c.get().findViewById<Button>(R.id.reserved1)
        touch(b, MotionEvent.ACTION_DOWN); assertEquals("", out.toString())
        touch(b, MotionEvent.ACTION_CANCEL); assertEquals("", out.toString())
        touch(b, MotionEvent.ACTION_DOWN); touch(b, MotionEvent.ACTION_UP)
        touch(b, MotionEvent.ACTION_DOWN); touch(b, MotionEvent.ACTION_UP)
        assertEquals("Ff", out.toString()); c.pause().stop().destroy()
    }
    @Test fun accessoryClickWhileDirectionHeldDoesNotPreventItsStop() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val out = StringBuilder()
        fakeEngine(c.get(), out)
        val direction = c.get().findViewById<Button>(R.id.forward)
        touch(direction, MotionEvent.ACTION_DOWN)
        c.get().findViewById<Button>(R.id.reserved1).performClick()
        touch(direction, MotionEvent.ACTION_UP)
        assertEquals("AFS", out.toString()); c.pause().stop().destroy()
    }
    @Test fun quittingSendsStopAndOnlyActiveAccessoryOff() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val out = StringBuilder()
        val e = fakeEngine(c.get(), out)
        val stopped = mutableListOf<Char>()
        val fake = object : RobotLink {
            override fun connect(device: BluetoothDevice, initialStop: Char) { }
            override fun send(command: Char) { }
            override fun disconnect(stop: List<Char>, reason: String) { stopped.addAll(stop) }
            override fun close() { }
        }
        MainActivity::class.java.getDeclaredField("link").apply { isAccessible = true; set(c.get(), fake) }
        e.down("forward"); e.click("reserved1")
        c.get().findViewById<Button>(R.id.close).performClick()
        assertEquals(listOf('S', 'f'), stopped)
        c.pause().stop().destroy()
    }
    @Test fun renamingReservedButtonPreservesItsCommands() {
        val c = Robolectric.buildActivity(SettingsActivity::class.java).setup(); val a = c.get()
        a.findViewById<Spinner>(R.id.button_picker).setSelection(4); shadowOf(android.os.Looper.getMainLooper()).idle()
        a.findViewById<EditText>(R.id.button_label).setText("Éclairage")
        a.findViewById<Button>(R.id.save).performClick()
        val b = ProfileStore(a).selected.buttons[4]
        assertEquals("Éclairage", b.label); assertEquals("F", b.down); assertEquals("f", b.up)
        c.pause().stop().destroy()
    }
    @Test fun settingsSaveCustomCaseModeAndShortBleUuids() {
        val c = Robolectric.buildActivity(SettingsActivity::class.java).setup(); val a = c.get()
        a.findViewById<Spinner>(R.id.button_picker).setSelection(4); shadowOf(android.os.Looper.getMainLooper()).idle()
        a.findViewById<EditText>(R.id.button_label).setText("Pince")
        a.findViewById<Spinner>(R.id.button_mode).setSelection(0)
        a.findViewById<EditText>(R.id.command_down).setText("q"); a.findViewById<EditText>(R.id.command_up).setText("Q")
        a.findViewById<Spinner>(R.id.profile_radio).setSelection(1)
        a.findViewById<EditText>(R.id.service).setText("FFE0"); a.findViewById<EditText>(R.id.characteristic).setText("FFE1")
        a.findViewById<Button>(R.id.save).performClick()
        val p = ProfileStore(a).selected
        assertEquals(RadioMode.BLE, p.radio); assertEquals(BleUuids.SERVICE, p.service)
        assertEquals(ButtonMode.HOLD, p.buttons[4].mode); assertEquals("q", p.buttons[4].down); assertEquals("Q", p.buttons[4].up)
        c.pause().stop().destroy()
    }
    @Test fun fullWindowSplitsTwoFingersAcrossDirectionAndReservedPanels() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
        val a = c.get(); val out = StringBuilder(); fakeEngine(a, out)
        val forward = a.findViewById<Button>(R.id.forward)
        val reserved = a.findViewById<Button>(R.id.reserved1)
        (forward.parent as View).requestLayout(); (reserved.parent as View).requestLayout()
        val decor = a.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 720, 360)
        fun point(v: View): MotionEvent.PointerCoords {
            val xy = IntArray(2); val origin = IntArray(2)
            v.getLocationOnScreen(xy); decor.getLocationOnScreen(origin)
            return MotionEvent.PointerCoords().apply { x = (xy[0] - origin[0] + v.width / 2).toFloat(); y = (xy[1] - origin[1] + v.height / 2).toFloat(); pressure = 1f; size = 1f }
        }
        val coords = arrayOf(point(forward), point(reserved))
        val pointers = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val time = SystemClock.uptimeMillis()
        fun dispatch(action: Int, count: Int) {
            val e = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, count, pointers.copyOf(count), coords.copyOf(count), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { a.dispatchTouchEvent(e) } finally { e.recycle() }
        }
        dispatch(MotionEvent.ACTION_DOWN, 1); assertEquals("A", out.toString())
        dispatch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
        assertEquals("A", out.toString())
        dispatch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
        assertEquals("AF", out.toString())
        dispatch(MotionEvent.ACTION_UP, 1); assertEquals("AFS", out.toString())
        c.pause().stop().destroy()
    }
}
