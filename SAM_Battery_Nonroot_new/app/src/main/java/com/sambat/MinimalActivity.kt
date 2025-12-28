package com.sambat

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MinimalActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this)
        textView.text = "ULTIMATE MINIMAL MODE"
        textView.textSize = 30f
        textView.setTextColor(Color.GREEN)
        textView.gravity = Gravity.CENTER
        textView.setBackgroundColor(Color.BLACK)
        
        setContentView(textView)
        
        android.util.Log.e("SAM_BATTERY_TEST", "MinimalActivity Created Success")
    }
}
