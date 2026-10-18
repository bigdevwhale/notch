package app.notch

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.notch.data.Settings
import app.notch.notify.Alarms
import app.notch.ui.NotchRoot
import app.notch.ui.theme.NotchTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A cream wall: dark icons in the bars.
        val style = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (savedInstanceState == null) opened(intent)
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            NotchTheme {
                // Wait for settings so the welcome screen doesn't flash for a family already here.
                settings?.let { NotchRoot(it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)
    }

    /** A tapped reminder: remember where it wants to go. */
    private fun opened(intent: Intent?) {
        val open = intent?.getStringExtra(Alarms.EXTRA_OPEN) ?: return
        container.open.value = open to intent.getLongExtra(Alarms.EXTRA_CHILD, 0)
    }
}
