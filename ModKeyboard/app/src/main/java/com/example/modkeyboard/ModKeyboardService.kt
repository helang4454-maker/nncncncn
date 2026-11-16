package com.example.modkeyboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ModKeyboardService : InputMethodService() {

    private val cBg = Color.parseColor("#00000000")
    private val cKey = Color.parseColor("#4DFFFFFF")
    private val cDark = Color.parseColor("#8C1E2434")
    private val cActive = Color.parseColor("#FFFFFF")
    private val cBorder = Color.parseColor("#47FFFFFF")

    private var shift = false
    private var caps = false
    private var ctrl = false
    private var alt = false
    private var meta = false
    private var panel = 0 // 0 = Aa, 1 = F, 2 = #, 3 = numpad

    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#59142030"))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        applyBlur()
        render()
        return root
    }

    // Blur tetingkap sebenar (Android 12+). Bergantung pada telefon; jika tak disokong, warna lutsinar sahaja.
    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            window?.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setBackgroundBlurRadius(90)
            }
        } catch (e: Throwable) { }
    }

    // ---------- UI helpers ----------
    private fun bg(color: Int, stroke: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(9).toFloat()
        setColor(if (stroke) Color.TRANSPARENT else color)
        if (stroke) setStroke(dp(1), cBorder)
    }

    private fun key(
        label: String, weight: Float = 1f, color: Int = cKey,
        outline: Boolean = false, active: Boolean = false, bare: Boolean = false, onTap: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = if (label.length > 2) 16f else 22f
        gravity = Gravity.CENTER
        setTextColor(if (active) Color.BLACK else Color.WHITE)
        background = if (bare) null else bg(if (active) cActive else color, outline && !active)
        layoutParams = LinearLayout.LayoutParams(0, dp(if (bare) 40 else 50), weight).apply {
            setMargins(dp(3), dp(4), dp(3), dp(4))
        }
        setOnClickListener { onTap() }
    }

    private fun repeatKey(label: String, weight: Float, color: Int, action: () -> Unit): TextView {
        val v = key(label, weight, color) {}
        val runner = object : Runnable {
            override fun run() { action(); handler.postDelayed(this, 60) }
        }
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { view.isPressed = true; action(); handler.postDelayed(runner, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.isPressed = false; handler.removeCallbacks(runner) }
            }
            true
        }
        return v
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        views.forEach { addView(it) }
    }

    private fun spacer(w: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(1), w)
    }

    // ---------- Render ----------
    private fun render() {
        root.removeAllViews()
        root.addView(topRow())
        when (panel) {
            0 -> letters()
            1 -> functionPanel()
            2 -> symbols()
            3 -> numpad()
        }
        root.addView(bottomRow())
    }

    private fun topRow(): View {
        val mods: List<Triple<String, Boolean, () -> Unit>> = listOf(
            Triple("⇧", shift, { shift = !shift; caps = false }),
            Triple("⌃", ctrl, { ctrl = !ctrl }),
            Triple("⌥", alt, { alt = !alt }),
            Triple("⌘", meta, { meta = !meta })
        )
        val left = mods.map { (l, a, f) -> key(l, 1f, cBg, outline = true, active = a) { f(); render() } }
        val tabs = listOf("Aa", "F", "#", "▦")
        val right = tabs.mapIndexed { i, t ->
            key(t, 1f, if (panel == i) cKey else cDark, outline = false) { panel = i; render() }
        }
        return row(*left.toTypedArray(), spacer(0.6f), *right.toTypedArray())
    }

    private fun charKeys(chars: String) = chars.map { c ->
        val s = c.toString()
        key(if (shift && panel == 0) s.uppercase() else s) { typeText(s) }
    }

    private fun letters() {
        root.addView(row(*charKeys("qwertyuiop").toTypedArray()))
        root.addView(row(spacer(0.5f), *charKeys("asdfghjkl").toTypedArray(), spacer(0.5f)))
        root.addView(row(
            key("⇧", 1.5f, cDark, active = shift) {
                if (shift && !caps) { caps = true } else { shift = !shift; caps = false }
                render()
            },
            *charKeys("zxcvbnm").toTypedArray(),
            repeatKey("⌫", 1.5f, cDark) { sendKey(KeyEvent.KEYCODE_DEL) }
        ))
    }

    private fun symbols() {
        root.addView(row(*charKeys("1234567890").toTypedArray()))
        root.addView(row(*charKeys("-/:;()$&@\"").toTypedArray()))
        root.addView(row(*charKeys("[]{}#%^*+=").toTypedArray()))
        root.addView(row(
            *charKeys("_\\|~<>.,?!").toTypedArray().take(8).toTypedArray(),
            repeatKey("⌫", 2f, cDark) { sendKey(KeyEvent.KEYCODE_DEL) }
        ))
    }

    private fun functionPanel() {
        fun fk(from: Int, to: Int) = (from..to).map { n ->
            key("F$n", 1f) { sendKey(KeyEvent.KEYCODE_F1 + n - 1) }
        }
        root.addView(row(*fk(1, 6).toTypedArray()))
        root.addView(row(*fk(7, 12).toTypedArray()))
        root.addView(row(
            key("Esc") { sendKey(KeyEvent.KEYCODE_ESCAPE) },
            key("Tab") { sendKey(KeyEvent.KEYCODE_TAB) },
            key("←") { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) },
            key("↑") { sendKey(KeyEvent.KEYCODE_DPAD_UP) },
            key("↓") { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) },
            key("→") { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        ))
        root.addView(row(
            key("Home") { sendKey(KeyEvent.KEYCODE_MOVE_HOME) },
            key("End") { sendKey(KeyEvent.KEYCODE_MOVE_END) },
            key("PgUp") { sendKey(KeyEvent.KEYCODE_PAGE_UP) },
            key("PgDn") { sendKey(KeyEvent.KEYCODE_PAGE_DOWN) },
            key("Del") { sendKey(KeyEvent.KEYCODE_FORWARD_DEL) },
            repeatKey("⌫", 1f, cDark) { sendKey(KeyEvent.KEYCODE_DEL) }
        ))
    }

    private fun numpad() {
        val rows = listOf("789/", "456*", "123-", "0.=+")
        rows.forEachIndexed { i, r ->
            val extra = when (i) {
                0 -> repeatKey("⌫", 1f, cDark) { sendKey(KeyEvent.KEYCODE_DEL) }
                1 -> key("(") { typeText("(") }
                2 -> key(")") { typeText(")") }
                else -> key(",") { typeText(",") }
            }
            root.addView(row(*charKeys(r).toTypedArray(), extra))
        }
    }

    private fun bottomRow(): View {
        val first = if (panel == 2) key("ABC", 1.3f, cDark) { panel = 0; render() }
        else key("123", 1.3f, cDark) { panel = 2; render() }
        val main = row(
            first,
            key("🙂", 1.1f, cDark) {
                Toast.makeText(this, "Emoji belum disertakan", Toast.LENGTH_SHORT).show()
            },
            key("space", 5f) { typeText(" ") },
            key("return", 2.2f, cDark) { sendKey(KeyEvent.KEYCODE_ENTER) }
        )
        val extra = row(
            key("🌐", 1f, bare = true) { switchKeyboard() },
            spacer(6f),
            key("🎤", 1f, bare = true) {
                Toast.makeText(this, "Input suara belum disertakan", Toast.LENGTH_SHORT).show()
            }
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(main)
            addView(extra)
        }
    }

    // ---------- Input logic ----------
    private fun metaState(): Int {
        var m = 0
        if (shift) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (ctrl) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (meta) m = m or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        return m
    }

    private fun afterKey() {
        var changed = false
        if (shift && !caps) { shift = false; changed = true }
        if (ctrl || alt || meta) { ctrl = false; alt = false; meta = false; changed = true }
        if (changed) render()
    }

    private fun sendKey(code: Int) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val m = metaState()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, m))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, m))
        afterKey()
    }

    private fun typeText(s: String) {
        val ic = currentInputConnection ?: return
        if (ctrl || alt || meta) {
            val code = KeyEvent.keyCodeFromString("KEYCODE_" + s.uppercase())
            if (code != KeyEvent.KEYCODE_UNKNOWN) { sendKey(code); return }
        }
        ic.commitText(if (shift && panel == 0) s.uppercase() else s, 1)
        afterKey()
    }

    private fun switchKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}
