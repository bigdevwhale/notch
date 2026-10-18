package app.notch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.notch.data.DemoSeed
import app.notch.data.NotchDatabase
import app.notch.data.SettingsStore
import app.notch.data.Vault
import app.notch.notify.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {
    val db = NotchDatabase.create(context)
    val settings = SettingsStore(context)
    val vault = Vault(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Where a tapped reminder wants to go ("shoot", "interview", …) and for which child. */
    val open = MutableStateFlow<Pair<String, Long>?>(null)

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch {
            block()
            changed()
        }
    }

    /** Something changed: the next reminder may be a different one now. */
    suspend fun changed() = Alarms.reschedule(context)
}

class NotchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alarms.ensureChannels(this)
        // Re-arm on every start: OEM task killers (HyperOS & co.) silently drop alarms.
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(this@NotchApp, container)
            container.changed()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as NotchApp).container

/** Resources in the in-app language, for receivers that AppCompat doesn't localise before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}
