package com.example.memory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.memory.nav.MemoryNavHost
import com.example.memory.ui.theme.MemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoryTheme {
                MemoryNavHost()
            }
        }
    }

    // Force a write on backgrounding so a pending debounced auto-backup isn't lost if the
    // process is killed while backgrounded (R2.6) - this is the app's only Activity, so this
    // is equivalent to a process-lifecycle observer without adding one.
    override fun onPause() {
        super.onPause()
        (application as MemoryApp).triggerImmediateBackup()
    }

    override fun onStop() {
        super.onStop()
        (application as MemoryApp).triggerImmediateBackup()
    }
}
