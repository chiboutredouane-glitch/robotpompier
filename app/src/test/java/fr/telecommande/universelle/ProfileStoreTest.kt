package fr.telecommande.universelle

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPreferences("robot_profiles_v1", 0).edit().clear().commit() }
    @Test fun customProfileAndLastDeviceSurviveRestart() {
        val store = ProfileStore(context)
        val p = store.selected.copy(name = "Mon robot", radio = RadioMode.BLE, address = "AA:BB:CC:DD:EE:FF", deviceName = "MLT-BT05",
            buttons = store.selected.buttons.map { if (it.id == "reserved1") it.copy(label = "Pince", down = "q", up = "Q") else it })
        store.save(p)
        assertEquals(p, ProfileStore(context).selected)
    }
    @Test fun profilesAreIndependentAndSelectionPersists() {
        val store = ProfileStore(context); val first = store.selected
        val second = RobotProfile.blank("Autre robot"); store.save(second); store.select(first.id)
        val restored = ProfileStore(context)
        assertEquals(first, restored.selected); assertEquals(2, restored.profiles.size)
        assertEquals("", restored.profiles.first { it.id == second.id }.buttons[4].down)
    }
    @Test fun deletingLastProfileRestoresAValidDefault() {
        val store = ProfileStore(context); store.delete(store.selected.id)
        assertEquals(1, store.profiles.size); store.selected.validate()
    }
    @Test fun corruptStorageProducesAnExplicitWarning() {
        context.getSharedPreferences("robot_profiles_v1", 0).edit().putString("profiles", "broken").commit()
        val store = ProfileStore(context)
        assertNotNull(store.warning); assertEquals("Hum-Bot", store.selected.name)
    }
    @Test fun draftRoundTripPreservesCaseAndUnicodeLabels() {
        val p = RobotProfile.humBot(name = "École")
        assertEquals(p, DraftCodec.decode(DraftCodec.encode(p)))
    }
    @Test fun invalidEditDoesNotOverwriteSavedCommands() {
        val store = ProfileStore(context); val old = store.selected
        val invalid = old.copy(buttons = old.buttons.map { if (it.id == "reserved1") it.copy(down = "é") else it })
        assertThrows(IllegalArgumentException::class.java) { store.save(invalid) }
        assertEquals(old, ProfileStore(context).selected)
    }
}
