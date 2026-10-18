package app.notch.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.notch.MainActivity
import app.notch.R
import app.notch.container
import app.notch.domain.Growth
import app.notch.domain.Nudge
import app.notch.domain.Nudges
import app.notch.domain.Plan
import app.notch.localized
import app.notch.ui.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Gentle reminders: a week going by without a notch, the day's second, birthdays, the half-year hand
 * and the month's postcard. One alarm, set for the earliest of them; each reminder is given only once.
 */
object Alarms {
    private const val CHANNEL = "nudges_v1"
    private const val ACTION = "app.notch.notify.NUDGE"
    const val EXTRA_OPEN = "open"
    const val EXTRA_CHILD = "child"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val l = context.localized()
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, l.getString(R.string.channel_nudges), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = l.getString(R.string.channel_nudges_desc)
                }
            )
        }
    }

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun plans(context: Context, now: Long): List<Plan> {
        val c = context.container
        val s = c.settings.now()
        if (!s.onboarded) return emptyList()
        val dao = c.db.dao()
        return Nudges.plan(dao.children(), dao.moments(), s, now)
    }

    suspend fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        val now = System.currentTimeMillis()
        val next = plans(context, now).firstOrNull { it.at > now }?.at
        if (next == null) {
            am.cancel(p)
            return
        }
        // A reminder doesn't need to-the-second precision; this avoids the exact-alarm permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, p)
    }

    /** Gives every reminder whose time has come. */
    suspend fun fire(context: Context) {
        val c = context.container
        val now = System.currentTimeMillis()
        val due = plans(context, now).filter { it.at <= now + 60_000 }
        if (due.isEmpty()) return
        c.settings.update { it.copy(fired = Nudges.remember(it.fired, due.map { p -> p.key })) }
        if (!canNotify(context)) return
        val children = c.db.dao().children().associateBy { it.id }
        val l = context.localized()
        val today = LocalDate.now()
        // Several weeks' reminders at once (two children) become one.
        val weeks = due.filter { it.nudge == Nudge.WEEK }.mapNotNull { children[it.child] }
        if (weeks.isNotEmpty()) {
            val text = if (weeks.size == 1) {
                val k = weeks[0]
                l.getString(
                    when { k.girl -> R.string.nudge_week_f; k.boy -> R.string.nudge_week_m; else -> R.string.nudge_week },
                    k.name, T.age(l, Growth.age(k.birthday, today)),
                )
            } else l.getString(R.string.nudge_week_many, weeks.joinToString(", ") { it.name })
            post(context, 10, l.getString(R.string.nudge_week_title), text, "shoot", if (weeks.size == 1) weeks[0].id else 0)
        }
        due.filter { it.nudge != Nudge.WEEK }.forEach { p ->
            val k = children[p.child]
            when (p.nudge) {
                Nudge.SECOND -> post(context, 11, l.getString(R.string.nudge_second_title), l.getString(R.string.nudge_second), "second", 0)
                Nudge.BIRTHDAY -> if (k != null) post(
                    context, 12 + (k.id % 50).toInt(), l.getString(R.string.nudge_bday_title, k.name, Growth.age(k.birthday, today).years),
                    l.getString(R.string.nudge_bday), "interview", k.id,
                )
                Nudge.HALF -> if (k != null) post(context, 70 + (k.id % 50).toInt(), l.getString(R.string.nudge_half_title, k.name), l.getString(R.string.nudge_half), "hands", k.id)
                Nudge.CARD -> post(context, 13, l.getString(R.string.nudge_card_title), l.getString(R.string.nudge_card, T.month(l, today.minusMonths(1))), "card", 0)
                Nudge.WEEK -> Unit
            }
        }
    }

    private fun post(context: Context, id: Int, title: String, text: String, open: String, child: Long) {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 100 + id,
                    Intent(context, MainActivity::class.java)
                        .putExtra(EXTRA_OPEN, open).putExtra(EXTRA_CHILD, child)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    internal const val NUDGE = ACTION
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.NUDGE) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.fire(context)
                Alarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
