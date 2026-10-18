package app.notch.domain

import app.notch.data.Child
import app.notch.data.Kind
import app.notch.data.Moment
import app.notch.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What a reminder is about. */
enum class Nudge {
    /** A week of a child's life is going by without a notch. */
    WEEK,
    /** Today's second for the year's film. */
    SECOND,
    /** A birthday: time for the interview and the hand. */
    BIRTHDAY,
    /** Half a year on: the hand again. */
    HALF,
    /** The month's postcard is ready. */
    CARD,
}

/** One planned reminder; [key] makes sure it is only ever given once. */
data class Plan(val at: Long, val nudge: Nudge, val key: String, val child: Long = 0)

/**
 * When to remind, and about what. Nothing is sent at night or more than once: a week's reminder comes
 * at the hour this family usually takes pictures on that day of the week, learned from the pictures
 * themselves.
 */
object Nudges {
    /** No reminder before this or after [LATEST] (minutes after midnight). */
    const val EARLIEST = 9 * 60
    const val LATEST = 20 * 60 + 30

    /**
     * The usual minute for pictures on [day]: the busiest half hour on that weekday, else on any day,
     * else a guess (evening on weekdays, late morning at the weekend). Kept inside waking hours.
     */
    fun usualMinute(moments: List<Moment>, day: DayOfWeek, zone: ZoneId = ZoneId.systemDefault()): Int {
        val times = moments.takeLast(400).map { java.time.Instant.ofEpochMilli(it.takenAt).atZone(zone) }
        fun busiest(l: List<java.time.ZonedDateTime>): Int? {
            if (l.size < 4) return null
            val slots = l.groupingBy { (it.hour * 60 + it.minute) / 30 }.eachCount()
            return slots.maxByOrNull { it.value }?.key?.let { it * 30 }
        }
        val m = busiest(times.filter { it.dayOfWeek == day }) ?: busiest(times)
            ?: if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) 11 * 60 else 18 * 60 + 30
        return m.coerceIn(EARLIEST, LATEST)
    }

    private fun at(d: LocalDate, minute: Int, zone: ZoneId) = d.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    fun plan(children: List<Child>, moments: List<Moment>, s: Settings, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Plan> {
        val today = Growth.date(now, zone)
        val fired = s.fired.split('|').toSet()
        val out = mutableListOf<Plan>()
        if (s.nudges) children.forEach { c ->
            val week = Growth.week(c.birthday, today)
            val start = Growth.weekStart(c.birthday, week)
            if (today.isBefore(c.birthday)) return@forEach
            val has = moments.any { it.of(c.id) && Growth.week(c.birthday, Growth.date(it.takenAt, zone)) == week }
            if (has) return@forEach
            // Halfway through the week, and on its last day.
            listOf(1 to start.plusDays(3), 2 to start.plusDays(6)).forEach { (n, d) ->
                val key = "week:${c.id}:$week:$n"
                val t = at(d, usualMinute(moments, d.dayOfWeek, zone), zone)
                if (key !in fired) out += Plan(t, Nudge.WEEK, key, c.id)
            }
        }
        if (s.seconds && children.isNotEmpty()) {
            val done = moments.any { it.kindOf == Kind.SECOND && Growth.date(it.takenAt, zone) == today }
            val d = if (done) today.plusDays(1) else today
            out += Plan(at(d, s.secondAt, zone), Nudge.SECOND, "second:$d")
        }
        if (s.occasions) {
            children.forEach { c ->
                for (d in listOf(today, today.plusDays(1))) {
                    if (Growth.birthday(c, d)) out += Plan(at(d, 9 * 60 + 30, zone), Nudge.BIRTHDAY, "bday:${c.id}:$d", c.id)
                    if (Growth.halfBirthday(c, d)) out += Plan(at(d, 10 * 60 + 30, zone), Nudge.HALF, "half:${c.id}:$d", c.id)
                }
            }
            // The first of the month, for the month gone by, if it has pictures.
            val first = today.withDayOfMonth(1).let { if (now >= at(it, 11 * 60, zone)) it.plusMonths(1) else it }
            for (d in listOf(today.withDayOfMonth(1), first).distinct()) {
                val month = d.minusMonths(1)
                val has = moments.any { Growth.date(it.takenAt, zone).let { m -> m.year == month.year && m.month == month.month } }
                if (has) out += Plan(at(d, 11 * 60, zone), Nudge.CARD, "card:${month.year}-${month.monthValue}")
            }
        }
        // Plans for times already gone are only kept for the last hour, so a phone that was off still hears.
        return out.filter { it.key !in fired && it.at > now - 60 * 60_000L }.sortedBy { it.at }
    }

    /** The fired list, kept short: the newest 80 keys. */
    fun remember(fired: String, keys: List<String>): String = (fired.split('|').filter { it.isNotEmpty() } + keys).takeLast(80).joinToString("|")

    /** For the "within waking hours" promise in tests. */
    fun minuteOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Int = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone).let { it.hour * 60 + it.minute }
}
