package io.github.aldrinadira.jalanz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.aldrinadira.jalanz.core.designsystem.JalanzTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Text or link another app shared with Jalanz, until the UI has handled it (FR-17). */
    private var sharedInput by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedInput = intent.sharedText()
        setContent {
            JalanzTheme {
                JalanzApp(sharedInput = sharedInput, onSharedInputHandled = { sharedInput = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedInput = intent.sharedText()
    }
}

/** A `geo:` link opened with Jalanz, or text shared to it. Never logged (NFR-13). */
private fun Intent.sharedText(): String? = when (action) {
    Intent.ACTION_VIEW -> dataString
    Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
    else -> null
}
