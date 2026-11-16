package com.example.modkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = 48
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(p, p, p, p)
        }
        layout.addView(TextView(this).apply {
            text = "Mod Keyboard\n\n1. Tekan \"Dayakan papan kekunci\" dan hidupkan Mod Keyboard.\n2. Tekan \"Pilih papan kekunci\" dan pilih Mod Keyboard."
            textSize = 16f
        })
        layout.addView(Button(this).apply {
            text = "Dayakan papan kekunci"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        layout.addView(Button(this).apply {
            text = "Pilih papan kekunci"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        setContentView(layout)
    }
}
