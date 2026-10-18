package app.notch.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.notch.R
import app.notch.data.Child
import app.notch.domain.Age
import app.notch.domain.Growth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Words and dates, in the app's language. Plain functions over a [Context] so receivers can use them too. */
object T {
    private fun locale(c: Context): Locale = c.resources.configuration.locales[0]

    fun plural(c: Context, id: Int, n: Int): String = c.resources.getQuantityString(id, n, n)

    /** "2 years 3 months", "5 months 12 days", "9 days". */
    fun age(c: Context, a: Age): String = when {
        a.totalDays < 31 && a.months == 0 && a.years == 0 -> if (a.totalDays == 0L) c.getString(R.string.age_born) else plural(c, R.plurals.days, a.totalDays.toInt())
        a.years == 0 -> plural(c, R.plurals.months, a.months) + if (a.days > 0) " " + plural(c, R.plurals.days, a.days) else ""
        else -> plural(c, R.plurals.years, a.years) + if (a.months > 0) " " + plural(c, R.plurals.months, a.months) else ""
    }

    /** "2 y 3 m", "5 m", "9 d": for captions and the door frame. */
    fun ageShort(c: Context, a: Age): String = when {
        a.years == 0 && a.months == 0 -> c.getString(R.string.age_short_d, a.totalDays.toInt())
        a.years == 0 -> c.getString(R.string.age_short_m, a.months)
        a.months == 0 -> c.getString(R.string.age_short_y, a.years)
        else -> c.getString(R.string.age_short_ym, a.years, a.months)
    }

    fun ageAt(c: Context, child: Child, ms: Long): String = age(c, Growth.age(child.birthday, Growth.date(ms)))
    fun ageShortAt(c: Context, child: Child, ms: Long): String = ageShort(c, Growth.age(child.birthday, Growth.date(ms)))

    fun local(ms: Long, zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(ms).atZone(zone)

    /** "17 Oct 2026". */
    fun date(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(c)))
    fun date(c: Context, d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(c)))

    /** "17 Oct". */
    fun day(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofPattern("d MMM", locale(c)))
    fun day(c: Context, d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", locale(c)))

    /** "September 2026". */
    fun month(c: Context, d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale(c))).replaceFirstChar { it.titlecase(locale(c)) }

    fun time(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(c)))

    fun minutes(c: Context, m: Int): String = java.time.LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(c)))

    /** "12–18 Oct", a week of life. */
    fun week(c: Context, start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) "${start.dayOfMonth}–" + day(c, end) else day(c, start) + " – " + day(c, end)
    }

    /** "1.5 s", "0:42". */
    fun duration(ms: Long): String {
        val s = (ms + 500) / 1000
        return if (s < 60) "0:%02d".format(s) else "%d:%02d".format(s / 60, s % 60)
    }

    fun cm(c: Context, cm: Float): String = c.getString(R.string.cm, if (cm % 1f == 0f) "%.0f".format(locale(c), cm) else "%.1f".format(locale(c), cm))
    fun mm(c: Context, mm: Float): String = c.getString(R.string.cm, "%.1f".format(locale(c), mm / 10f))
}

@Composable
fun ctx(): Context = LocalContext.current
