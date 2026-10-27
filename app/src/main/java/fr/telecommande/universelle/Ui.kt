package fr.telecommande.universelle

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

object Ui {
    val BG = Color.rgb(16, 23, 34)
    val PANEL = Color.rgb(25, 35, 49)
    val BORDER = Color.rgb(47, 66, 85)
    val TEXT = Color.WHITE
    val MUTED = Color.rgb(158, 177, 198)
    val CYAN = Color.rgb(23, 217, 236)
    val YELLOW = Color.rgb(255, 199, 32)
    val GREEN = Color.rgb(77, 224, 139)
    val RED = Color.rgb(255, 103, 93)
    val controlIds = listOf(R.id.forward, R.id.backward, R.id.left, R.id.right, R.id.reserved1, R.id.reserved2, R.id.reserved3, R.id.reserved4)
    fun Context.dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    fun shape(context: Context, color: Int, border: Int = BORDER) = GradientDrawable().apply {
        setColor(color); cornerRadius = context.dp(12).toFloat(); setStroke(context.dp(1), border)
    }
    fun text(context: Context, value: String, size: Float = 14f, color: Int = TEXT) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    fun row(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isMotionEventSplittingEnabled = true }
    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; isMotionEventSplittingEnabled = true }
    fun panel(context: Context) = column(context).apply {
        background = shape(context, PANEL); setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
    }
    fun button(context: Context, label: String, idValue: Int = View.NO_ID, primary: Boolean = false) = Button(context).apply {
        id = idValue; text = label; isAllCaps = false; textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minimumWidth = context.dp(48); minimumHeight = context.dp(48)
        setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
        backgroundTintList = null
        paint(this, primary)
    }
    fun paint(button: Button, primary: Boolean = false, selected: Boolean = false) {
        val c = button.context
        val fill = if (primary) YELLOW else if (selected) Color.rgb(24, 79, 72) else PANEL
        val edge = if (selected) GREEN else if (primary) YELLOW else BORDER
        button.background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(c, Color.rgb(40, 48, 59)))
            addState(intArrayOf(android.R.attr.state_pressed), shape(c, if (primary) Color.rgb(215, 156, 15) else Color.rgb(45, 77, 91), CYAN))
            addState(intArrayOf(), shape(c, fill, edge))
        }
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(MUTED, if (primary) BG else TEXT)))
    }
    fun spinner(context: Context, values: List<String>, idValue: Int) = Spinner(context).apply { id = idValue; choices(values) }
    fun Spinner.choices(values: List<String>) {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, values).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }
    fun weighted(width: Int = 0, height: Int = ViewGroup.LayoutParams.MATCH_PARENT, weight: Float = 1f) = LinearLayout.LayoutParams(width, height, weight)
    fun gap(context: Context, width: Int = 8, height: Int = 1) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(width), context.dp(height))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Suppress("DEPRECATION")
    fun root(activity: Activity): LinearLayout {
        if (Build.VERSION.SDK_INT >= 28) activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return column(activity).apply {
            setBackgroundColor(BG)
            val pad = activity.dp(10)
            setPadding(pad, pad, pad, pad)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = if (Build.VERSION.SDK_INT >= 30) {
                    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    Rect(b.left, b.top, b.right, b.bottom)
                } else Rect(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                view.setPadding(pad + safe.left, pad + safe.top, pad + safe.right, pad + safe.bottom)
                insets
            }
        }
    }
}

/** A four-button directional pad with independent touch targets and a noninteractive center. */
@SuppressLint("ViewConstructor") // Built programmatically with its eight-slot profile, never inflated from XML.
class DpadPanel(context: Context, val controls: List<Button>, val center: TextView) : ViewGroup(context) {
    init { isMotionEventSplittingEnabled = true; controls.forEach(::addView); addView(center) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        val margin = (resources.displayMetrics.density * 3).toInt()
        val cw = (w / 3 - 2 * margin).coerceAtLeast(1)
        val minimum = (48 * resources.displayMetrics.density).toInt()
        val ch = (h / 3 - 2 * margin).coerceAtLeast(minimum).coerceAtMost((h / 2).coerceAtLeast(1))
        (controls + center).forEach { it.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)) }
        setMeasuredDimension(w, h)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cells = listOf(1 to 0, 1 to 2, 0 to 1, 2 to 1, 1 to 1)
        (controls + center).forEachIndexed { index, view ->
            val (x, y) = cells[index]
            val left = x * width / 3 + (width / 3 - view.measuredWidth) / 2
            val top = when (y) { 0 -> 0; 2 -> height - view.measuredHeight; else -> (height - view.measuredHeight) / 2 }
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
    }
}
