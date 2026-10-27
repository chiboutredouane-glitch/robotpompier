package fr.telecommande.universelle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native Android rendering preview; no Bluetooth hardware or real connected robot is implied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w720dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewTest {
    @Before fun clear() { RuntimeEnvironment.getApplication().getSharedPreferences("robot_profiles_v1", 0).edit().clear().commit() }
    private fun draw(view: View, name: String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 720, 360)
        val bitmap = Bitmap.createBitmap(720, 360, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val out = File("build/reports/previews").apply { mkdirs() }
        File(out, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun dashboardHasEightSeparateTouchTargets() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        draw(c.get().window.decorView, "dashboard.png")
        Ui.controlIds.forEach { id ->
            val button = c.get().findViewById<Button>(id)
            assertTrue("Button $id is too narrow", button.width >= 48)
            assertTrue("Button $id is too short", button.height >= 48)
        }
        c.pause().stop().destroy()
    }
    @Test fun settingsRenderInLandscape() {
        val c = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        draw(c.get().window.decorView, "settings.png")
        assertNotNull(c.get().findViewById<View>(R.id.save))
        c.pause().stop().destroy()
    }
    @Test fun connectedDashboardPreviewUsesTheRealRenderer() {
        val store = ProfileStore(RuntimeEnvironment.getApplication())
        store.save(store.selected.copy(radio = RadioMode.BLE))
        val c = Robolectric.buildActivity(MainActivity::class.java).setup().windowFocusChanged(true)
        MainActivity::class.java.getDeclaredMethod("render", LinkState::class.java).apply {
            isAccessible = true
            invoke(c.get(), LinkState(LinkPhase.CONNECTED, "Connecté · MLT-BT05 (simulation)", "S"))
        }
        draw(c.get().window.decorView, "dashboard-connected.png")
        assertTrue(c.get().findViewById<Button>(R.id.forward).isEnabled)
        c.pause().stop().destroy()
    }
}
