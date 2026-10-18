package app.notch.domain

import app.notch.data.Child
import app.notch.data.Moment
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A child's age at some day: whole years, months and days, as a calendar counts them. */
data class Age(val years: Int, val months: Int, val days: Int, val totalDays: Long) {
    val totalMonths: Int get() = years * 12 + months
}

/**
 * The door frame counts in a child's own weeks: week 0 starts on the day they were born, week 1 a
 * week later, and so on. A week with at least one picture has its notch.
 */
object Growth {
    const val DAY = 86_400_000L

    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun age(born: LocalDate, on: LocalDate): Age {
        if (on.isBefore(born)) return Age(0, 0, 0, 0)
        val p = Period.between(born, on)
        return Age(p.years, p.months, p.days, ChronoUnit.DAYS.between(born, on))
    }

    fun week(born: LocalDate, on: LocalDate): Int = (ChronoUnit.DAYS.between(born, on).coerceAtLeast(0) / 7).toInt()

    fun weekStart(born: LocalDate, week: Int): LocalDate = born.plusDays(week * 7L)

    /** The weeks of [child]'s life that have a notch. */
    fun notched(child: Child, moments: List<Moment>, zone: ZoneId = ZoneId.systemDefault()): Set<Int> =
        moments.filter { it.of(child.id) }.map { week(child.birthday, date(it.takenAt, zone)) }.toSet()

    /**
     * Weeks in a row with a notch, up to this one. This week only breaks the run once it is over: a
     * Tuesday without a picture yet still counts last week's run.
     */
    fun streak(notched: Set<Int>, current: Int): Int {
        var w = if (current in notched) current else current - 1
        var n = 0
        while (w >= 0 && w in notched) { n++; w-- }
        return n
    }

    /** The longest run of notched weeks ever. */
    fun bestStreak(notched: Set<Int>): Int {
        var best = 0
        var run = 0
        var last = Int.MIN_VALUE
        notched.sorted().forEach { w ->
            run = if (w == last + 1) run + 1 else 1
            best = maxOf(best, run)
            last = w
        }
        return best
    }

    /**
     * The first week the door frame expects a notch: from when the child was added to the app, or the
     * first picture, whichever is earlier. Weeks before that are "before the door frame", not missed.
     */
    fun firstCounted(child: Child, moments: List<Moment>, zone: ZoneId = ZoneId.systemDefault()): Int {
        val added = week(child.birthday, date(child.createdAt, zone))
        val first = moments.filter { it.of(child.id) }.minOfOrNull { week(child.birthday, date(it.takenAt, zone)) } ?: added
        return minOf(added, first)
    }

    /** Pictures from this day in earlier years, any child, newest year first. */
    fun onThisDay(moments: List<Moment>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Moment> =
        moments.filter {
            val d = date(it.takenAt, zone)
            d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth
        }.sortedByDescending { it.takenAt }

    /**
     * Pictures of [other] when they were as old as [child] is today, give or take [slackDays]: the
     * brother at three next to the sister at three.
     */
    fun sameAge(child: Child, other: Child, moments: List<Moment>, today: LocalDate, slackDays: Long = 10, zone: ZoneId = ZoneId.systemDefault()): List<Moment> {
        val days = ChronoUnit.DAYS.between(child.birthday, today)
        if (other.id == child.id || ChronoUnit.DAYS.between(other.birthday, today) <= days) return emptyList()
        val target = other.birthday.plusDays(days)
        return moments.filter { it.of(other.id) && !it.video && kotlin.math.abs(ChronoUnit.DAYS.between(target, date(it.takenAt, zone))) <= slackDays }
            .sortedBy { kotlin.math.abs(ChronoUnit.DAYS.between(target, date(it.takenAt, zone))) }
    }

    /** The photo of [child] closest to a year before [today], within a month either way, for a ghost. */
    fun yearAgo(child: Child, moments: List<Moment>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Moment? {
        val target = today.minusYears(1)
        return moments.filter { it.of(child.id) && it.kindOf == app.notch.data.Kind.PHOTO }
            .map { it to kotlin.math.abs(ChronoUnit.DAYS.between(target, date(it.takenAt, zone))) }
            .filter { it.second <= 31 }
            .minByOrNull { it.second }?.first
    }

    /** Is today the birthday, or the half-birthday (six months on)? */
    fun birthday(child: Child, on: LocalDate): Boolean = on != child.birthday && on.monthValue == child.birthday.monthValue &&
        (on.dayOfMonth == child.birthday.dayOfMonth || (child.birthday.dayOfMonth == 29 && child.birthday.monthValue == 2 && !on.isLeapYear && on.dayOfMonth == 28))

    fun halfBirthday(child: Child, on: LocalDate): Boolean {
        val a = age(child.birthday, on)
        return a.months == 6 && a.days == 0
    }
}
