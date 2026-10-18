package app.notch.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/**
 * The phone's camera is open for the door frame: what it takes from [since] on goes to [kids], tagged
 * with [fading] or joined to the series of [ghost], and clips count as seconds when [seconds].
 */
data class Session(
    val since: Long = 0,
    val kids: String = "",
    val fading: String = "",
    /** The moment whose ghost is laid over the camera, or 0. */
    val ghost: Long = 0,
    val seconds: Boolean = false,
) {
    val active: Boolean get() = since > 0
}

data class Settings(
    val onboarded: Boolean = false,
    /** Gentle reminders when a week has no notch yet, at the hour pictures usually get taken. */
    val nudges: Boolean = true,
    /** A second of video a day, for the year's film. */
    val seconds: Boolean = false,
    /** When to remind about the day's second, minutes after midnight. */
    val secondAt: Int = 18 * 60 + 30,
    /** Birthdays, half-birthdays and the month's postcard. */
    val occasions: Boolean = true,
    /** How strong the ghost of the earlier picture is over the camera, 0..100. */
    val ghostAlpha: Int = 38,
    val session: Session = Session(),
    /** Reminders already given ("week:3:112:1", "card:2026-09"), so none comes twice. */
    val fired: String = "",
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        nudges = this[NUDGES] ?: true,
        seconds = this[SECONDS] ?: false,
        secondAt = this[SECOND_AT] ?: (18 * 60 + 30),
        occasions = this[OCCASIONS] ?: true,
        ghostAlpha = this[GHOST_ALPHA] ?: 38,
        session = Session(
            since = this[S_SINCE] ?: 0L,
            kids = this[S_KIDS] ?: "",
            fading = this[S_FADING] ?: "",
            ghost = this[S_GHOST] ?: 0L,
            seconds = this[S_SECONDS] ?: false,
        ),
        fired = this[FIRED] ?: "",
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[NUDGES] = s.nudges
            it[SECONDS] = s.seconds
            it[SECOND_AT] = s.secondAt
            it[OCCASIONS] = s.occasions
            it[GHOST_ALPHA] = s.ghostAlpha
            it[S_SINCE] = s.session.since
            it[S_KIDS] = s.session.kids
            it[S_FADING] = s.session.fading
            it[S_GHOST] = s.session.ghost
            it[S_SECONDS] = s.session.seconds
            it[FIRED] = s.fired
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NUDGES = booleanPreferencesKey("nudges")
        val SECONDS = booleanPreferencesKey("seconds")
        val SECOND_AT = intPreferencesKey("second_at")
        val OCCASIONS = booleanPreferencesKey("occasions")
        val GHOST_ALPHA = intPreferencesKey("ghost_alpha")
        val S_SINCE = longPreferencesKey("session_since")
        val S_KIDS = stringPreferencesKey("session_kids")
        val S_FADING = stringPreferencesKey("session_fading")
        val S_GHOST = longPreferencesKey("session_ghost")
        val S_SECONDS = booleanPreferencesKey("session_seconds")
        val FIRED = stringPreferencesKey("fired")
    }
}
