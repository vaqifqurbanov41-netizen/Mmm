package com.aistudio.texnonezaret

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aistudio.texnonezaret.ui.TexnonezaretRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        window.decorView.filterTouchesWhenObscured = true
        setContent {
            TexnonezaretRoot()
        }
        findViewById<View>(android.R.id.content)?.filterTouchesWhenObscured = true
    }
}
