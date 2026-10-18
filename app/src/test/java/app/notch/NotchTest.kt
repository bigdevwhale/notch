package app.notch

import app.notch.data.Backup
import app.notch.data.Child
import app.notch.data.Everything
import app.notch.data.Kind
import app.notch.data.Mark
import app.notch.data.Moment
import app.notch.data.Series
import app.notch.data.Settings
import app.notch.data.Word
import app.notch.domain.FadeState
import app.notch.domain.Fading
import app.notch.domain.Growth
import app.notch.domain.Nudge
import app.notch.domain.Nudges
import app.notch.make.HandTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotchTest {
    private val zone = ZoneOffset.UTC
    private fun at(d: LocalDate, h: Int = 12, m: Int = 0) = d.atTime(h, m).toInstant(zone).toEpochMilli()
    private val born = LocalDate.of(2022, 3, 14)
    private val masha = Child(id = 1, name = "Masha", born = born.toEpochDay(), createdAt = at(LocalDate.of(2024, 1, 1)))

    @Test
    fun ageCountsLikeACalendar() {
        val a = Growth.age(born, LocalDate.of(2024, 6, 20))
        assertEquals(2, a.years)
        assertEquals(3, a.months)
        assertEquals(6, a.days)
        assertEquals(27, a.totalMonths)
        assertEquals(0, Growth.age(born, born.minusDays(3)).totalDays)
    }

    @Test
    fun weeksStartOnTheBirthdayWeekday() {
        assertEquals(0, Growth.week(born, born))
        assertEquals(0, Growth.week(born, born.plusDays(6)))
        assertEquals(1, Growth.week(born, born.plusDays(7)))
        assertEquals(born.plusDays(14), Growth.weekStart(born, 2))
    }

    @Test
    fun streakForgivesTheWeekNotOverYet() {
        val notched = setOf(3, 4, 5, 6, 9)
        assertEquals(0, Growth.streak(notched, 8))
        assertEquals(1, Growth.streak(notched, 9))
        assertEquals(1, Growth.streak(notched, 10))
        assertEquals(4, Growth.streak(notched, 7))
        assertEquals(4, Growth.bestStreak(notched))
    }

    @Test
    fun sameAgeFindsTheOlderSiblingThen() {
        val petya = Child(id = 2, name = "Petya", born = born.plusYears(2).toEpochDay())
        val today = born.plusYears(2).plusMonths(5)
        // Masha at Petya's age today: 5 months old.
        val then = Moment(id = 7, kids = ",1,", file = "a.jpg", takenAt = at(born.plusMonths(5).plusDays(3)))
        val other = Moment(id = 8, kids = ",1,", file = "b.jpg", takenAt = at(born.plusMonths(9)))
        val found = Growth.sameAge(petya, masha, listOf(then, other), today, zone = zone)
        assertEquals(listOf(7L), found.map { it.id })
        assertTrue(Growth.sameAge(masha, petya, listOf(then), today, zone = zone).isEmpty())
    }

    @Test
    fun fadingStates() {
        val grip = Fading.byKey("grip")!!
        assertEquals(FadeState.NOW, Fading.state(grip, 2, false))
        assertEquals(FadeState.PASSED, Fading.state(grip, 9, false))
        assertEquals(FadeState.KEPT, Fading.state(grip, 9, true))
        val gap = Fading.byKey("gap_smile")!!
        assertEquals(FadeState.LATER, Fading.state(gap, 30, false))
        assertEquals(Fading.all.size, Fading.all.map { it.key }.distinct().size)
    }

    @Test
    fun aWeekWithoutAPictureIsRemindedAtTheUsualHour() {
        val today = born.plusDays(7L * 100 + 1)
        // Pictures usually at about 19:00.
        val old = (1..10).map { Moment(id = it.toLong(), kids = ",1,", file = "x", takenAt = at(today.minusDays(14L + it * 7), 19, 5)) }
        val plans = Nudges.plan(listOf(masha), old, Settings(onboarded = true), at(today, 8), zone)
        val weeks = plans.filter { it.nudge == Nudge.WEEK }
        assertEquals(2, weeks.size)
        weeks.forEach {
            val m = Nudges.minuteOf(it.at, zone)
            assertTrue(m in Nudges.EARLIEST..Nudges.LATEST)
            assertEquals(19 * 60, m)
        }
        // Once there is a picture this week, nothing.
        val notched = old + Moment(id = 99, kids = ",1,", file = "y", takenAt = at(today, 7))
        assertTrue(Nudges.plan(listOf(masha), notched, Settings(onboarded = true), at(today, 8), zone).none { it.nudge == Nudge.WEEK })
        // And a reminder given is never given again.
        val fired = Settings(onboarded = true, fired = Nudges.remember("", weeks.map { it.key }))
        assertTrue(Nudges.plan(listOf(masha), old, fired, at(today, 8), zone).none { it.nudge == Nudge.WEEK })
    }

    @Test
    fun birthdaysAndPostcards() {
        val day = born.plusYears(3).minusDays(1)
        val moments = listOf(Moment(kids = ",1,", file = "x", takenAt = at(day.minusDays(20))))
        val plans = Nudges.plan(listOf(masha), moments, Settings(onboarded = true, nudges = false), at(day, 12), zone)
        assertTrue(plans.any { it.nudge == Nudge.BIRTHDAY && Growth.date(it.at, zone) == born.plusYears(3) })
        val first = LocalDate.of(2025, 6, 1)
        val may = listOf(Moment(kids = ",1,", file = "x", takenAt = at(LocalDate.of(2025, 5, 12))))
        val p = Nudges.plan(listOf(masha), may, Settings(onboarded = true, nudges = false), at(first, 10), zone)
        assertTrue(p.any { it.nudge == Nudge.CARD && it.key == "card:2025-5" })
    }

    @Test
    fun handOnASheetIsFoundAndMeasured() {
        // A white A4 sheet (210 x 297 mm at 2 px/mm) on a dark table, a "hand" on it from the bottom edge.
        val w = 600
        val h = 800
        val px = IntArray(w * h) { 0xFF303030.toInt() }
        val sx = 90
        val sy = 100
        val sw = 420
        val sh = 594
        for (y in sy until sy + sh) for (x in sx until sx + sw) px[y * w + x] = 0xFFF4F1EA.toInt()
        // Palm 70 x 90 px, rising from the sheet's bottom edge, and a finger 20 x 110 px above it.
        val cx = sx + sw / 2
        val bottom = sy + sh
        for (y in bottom - 90 until bottom + 60) for (x in cx - 35 until cx + 35) if (y < h) px[y * w + x] = 0xFFC08A6A.toInt()
        for (y in bottom - 200 until bottom - 90) for (x in cx - 10 until cx + 10) px[y * w + x] = 0xFFC08A6A.toInt()
        val f = HandTrace.find(px, w, h)
        assertNotNull(f)
        // 200 px of hand on the sheet at 2 px/mm: 100 mm.
        assertEquals(100f, f!!.outline.lengthMm, 4f)
        assertEquals(35f, f.outline.widthMm, 3f)
        val back = HandTrace.decode(f.outline.encode())
        assertTrue(back.size > 20)
        assertTrue(back.all { it.second <= 1f })
    }

    @Test
    fun noSheetNoHand() {
        val px = IntArray(200 * 200) { 0xFF505050.toInt() }
        assertEquals(null, HandTrace.find(px, 200, 200))
    }

    @Test
    fun backupRoundTrip() {
        val all = Everything(
            children = listOf(masha.copy(gender = "GIRL", crayon = 2, face = 4)),
            moments = listOf(
                Moment(id = 4, kids = ",1,", file = "a.jpg", takenAt = 5, note = "hi", fading = "grip", seriesId = 3, favorite = true),
                Moment(id = 5, kids = ",1,", file = "h.jpg", kind = Kind.HAND.name, takenAt = 6, shape = "1.0,-2.0;3.0,-4.0", measure = 1012),
            ),
            words = listOf(Word(id = 1, childId = 1, said = "ябуко", means = "яблоко", audio = "w.m4a", at = 7, fading = "first_words")),
            marks = listOf(Mark(id = 1, childId = 1, cm = 92.5f, at = 8)),
            series = listOf(Series(id = 3, name = "By the window", createdAt = 9)),
        )
        val back = Backup.decode(Backup.encode(all))
        assertEquals(all.children, back.children)
        assertEquals(all.moments, back.moments)
        assertEquals(all.words, back.words)
        assertEquals(all.marks, back.marks)
        assertEquals(all.series, back.series)
        assertFalse(runCatching { Backup.decode("{\"app\":\"silverbox\"}") }.isSuccess)
    }

    @Test
    fun kidsOfIsStable() {
        assertEquals(",1,3,", Moment.kidsOf(listOf(3, 1, 3)))
        val m = Moment(kids = ",1,13,", file = "x", takenAt = 0)
        assertTrue(m.of(13))
        assertFalse(m.of(3))
    }
}
