package com.munkberries.tikhayapolyanka

import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.munkberries.tikhayapolyanka.ui.*

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(russianContext(newBase)) }
    private val model: PolyankaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if(savedInstanceState==null) model.route(intent?.data)
        setContent { PolyankaTheme { PolyankaApp(model) } }
    }
    override fun onStart() { super.onStart(); model.visible(true) }
    override fun onStop() { model.visible(false); super.onStop() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Existing Activity, same persisted session; no start action.
        model.route(intent.data ?: if(intent.action!=Intent.ACTION_MAIN) android.net.Uri.parse("polyanka://focus") else null)
        model.refresh()
    }
}



