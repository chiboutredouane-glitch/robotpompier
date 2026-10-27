package fr.robotpompier.commande

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Exercises the real Android touch listeners, not only the protocol state machine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "land")
class DirectionTouchTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var state: CommandState
    private val written = StringBuilder()

    @Before fun createActivity() {
        controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        activity = controller.get()
        state = CommandState { written.append(it) }.apply { setConnected(true) }
        MainActivity::class.java.getDeclaredField("commands").apply {
            isAccessible = true
            set(activity, state)
        }
        listOf(R.id.forward, R.id.backward, R.id.right, R.id.left, R.id.pump_on, R.id.pump_off)
            .forEach { activity.findViewById<Button>(it).apply { isEnabled = true; layout(0, 0, 150, 150) } }
    }

    @After fun destroyActivity() { controller.pause().stop().destroy() }

    private fun touch(button: View, action: Int, x: Float = 50f, y: Float = 50f) {
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, action, x, y, 0)
        try { button.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun liftingEachDirectionWritesItsLowercaseStop() {
        listOf(R.id.forward, R.id.backward, R.id.right, R.id.left).forEach { id ->
            val button = activity.findViewById<Button>(id)
            touch(button, MotionEvent.ACTION_DOWN)
            touch(button, MotionEvent.ACTION_UP)
        }
        assertEquals("AaRrDdGg", written.toString())
    }

    @Test fun cancellationWritesStopOnce() {
        val button = activity.findViewById<Button>(R.id.forward)
        touch(button, MotionEvent.ACTION_DOWN)
        touch(button, MotionEvent.ACTION_CANCEL)
        touch(button, MotionEvent.ACTION_UP)
        assertEquals("Aa", written.toString())
    }

    @Test fun realReleaseStillWritesStopIfLocalHeldStateWasCleared() {
        val button = activity.findViewById<Button>(R.id.forward)
        touch(button, MotionEvent.ACTION_DOWN)
        state.clear()
        touch(button, MotionEvent.ACTION_UP)
        assertEquals("Aa", written.toString())
    }

    @Test fun slidingOutsideStopsAndDoesNotRestartOnReentry() {
        val button = activity.findViewById<Button>(R.id.left)
        touch(button, MotionEvent.ACTION_DOWN)
        touch(button, MotionEvent.ACTION_MOVE, -10f)
        touch(button, MotionEvent.ACTION_MOVE)
        touch(button, MotionEvent.ACTION_UP)
        assertEquals("Gg", written.toString())
    }

    @Test fun pumpDoesNotSwallowDirectionRelease() {
        val button = activity.findViewById<Button>(R.id.forward)
        touch(button, MotionEvent.ACTION_DOWN)
        activity.findViewById<Button>(R.id.pump_on).performClick()
        touch(button, MotionEvent.ACTION_UP)
        activity.findViewById<Button>(R.id.pump_off).performClick()
        assertEquals("AEaF", written.toString())
    }

    @Test fun trackedFingerPointerUpStopsWhileAnotherFingerRemains() {
        val button = activity.findViewById<Button>(R.id.forward)
        touch(button, MotionEvent.ACTION_DOWN)
        val time = SystemClock.uptimeMillis()
        val pointers = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        val coordinates = Array(2) { MotionEvent.PointerCoords().apply { x = 50f; y = 50f } }
        val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_POINTER_UP, 2, pointers,
            coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try { button.dispatchTouchEvent(event) } finally { event.recycle() }
        touch(button, MotionEvent.ACTION_UP)
        assertEquals("Aa", written.toString())
    }
}
