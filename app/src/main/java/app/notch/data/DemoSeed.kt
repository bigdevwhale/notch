package app.notch.data

import android.content.Context
import app.notch.AppContainer
import app.notch.domain.Growth
import app.notch.domain.Interview
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A made-up family for the demo build (screenshots): Masha, four and a bit, and her brother Petya,
 * one and a half. Years of weekly notches (fewer before the app, as in real life), heights on the door
 * frame, words, a series by the window, traced hands, an interview and a month of seconds. The
 * pictures come from the demo assets, which only the demo build carries.
 */
object DemoSeed {
    /** [list] names the files of a demo folder; [open] reads one (the screen renders read them from disk). */
    suspend fun seedIfEmpty(
        context: Context,
        c: AppContainer,
        list: (String) -> List<String> = { context.assets.list(it)?.toList().orEmpty() },
        open: (String) -> java.io.InputStream = { context.assets.open(it) },
    ) {
        val dao = c.db.dao()
        if (dao.children().isNotEmpty()) return
        val tags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val lang = if (!tags.isEmpty) tags[0]!!.language else context.resources.configuration.locales[0].language
        val ru = lang == "ru"
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val r = Random(7)
        fun at(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

        val files = list("demo").sorted()
        fun take(name: String, ext: String): String? {
            val tmp = File(context.cacheDir, "demo-" + name)
            val ok = runCatching { open("demo/$name").use { a -> tmp.outputStream().use { a.copyTo(it) } } }.isSuccess
            if (!ok) return null
            return tmp.inputStream().use { c.vault.take(it, ext) }.also { tmp.delete() }
        }
        // Each picture copied once, then shared by every moment that shows it.
        val cache = HashMap<String, String>()
        fun photo(name: String) = cache.getOrPut(name) { take(name, "jpg") ?: "" }.takeIf { it.isNotEmpty() }

        val added = today.minusYears(2).minusMonths(3)
        val masha = Child(name = if (ru) "Маша" else "Masha", born = today.minusYears(4).minusMonths(3).minusDays(12).toEpochDay(), gender = "GIRL", crayon = 0, createdAt = at(added, 20, 0))
        val petya = Child(name = if (ru) "Петя" else "Petya", born = today.minusYears(1).minusMonths(7).minusDays(3).toEpochDay(), gender = "BOY", crayon = 1, createdAt = at(added, 20, 0))
        val mId = dao.insert(masha)
        val pId = dao.insert(petya)

        // Pictures for each child, oldest to newest: m-*.jpg for Masha, p-*.jpg for Petya, b-*.jpg of both.
        val mPics = files.filter { it.startsWith("m-") && it.endsWith(".jpg") }
        val pPics = files.filter { it.startsWith("p-") && it.endsWith(".jpg") }
        val bPics = files.filter { it.startsWith("b-") && it.endsWith(".jpg") }
        val clips = files.filter { it.endsWith(".mp4") }

        suspend fun weeks(child: Child, id: Long, pics: List<String>) {
            if (pics.isEmpty()) return
            val current = Growth.week(child.birthday, today)
            val first = Growth.week(child.birthday, LocalDate.ofEpochDay(added.toEpochDay()))
            for (w in 0..current) {
                // Before the app a picture every few weeks; since, most weeks; this week not yet.
                val p = when {
                    w == current -> 0f
                    w < first -> 0.22f
                    w > current - 10 -> 0.95f
                    else -> 0.82f
                }
                if (r.nextFloat() >= p) continue
                val n = if (r.nextFloat() < 0.35f) 2 else 1
                repeat(n) { k ->
                    val day = Growth.weekStart(child.birthday, w).plusDays(r.nextLong(0, 7)).let { if (it.isAfter(today)) today else it }
                    // Pictures follow the age: the list is spread over the whole childhood.
                    val idx = ((w.toFloat() / (current + 1)) * pics.size).toInt().coerceIn(0, pics.size - 1)
                    val name = pics[(idx + k + r.nextInt(-1, 2)).coerceIn(0, pics.size - 1)]
                    val file = photo(name) ?: return@repeat
                    dao.insert(Moment(kids = Moment.kidsOf(listOf(id)), file = file, takenAt = at(day, 10 + r.nextInt(9), r.nextInt(60)), favorite = r.nextInt(9) == 0))
                }
            }
        }
        weeks(masha.copy(id = mId), mId, mPics)
        weeks(petya.copy(id = pId), pId, pPics)
        bPics.forEachIndexed { i, name ->
            val d = today.minusDays(6L + i * 23)
            photo(name)?.let { dao.insert(Moment(kids = Moment.kidsOf(listOf(mId, pId)), file = it, takenAt = at(d, 17, 10 + i), note = if (i == 0) (if (ru) "Первый раз вместе на качелях" else "On the swing together for the first time") else "")) }
        }

        // A series by the window: one a year on her birthday.
        val window = files.filter { it.startsWith("s-") && it.endsWith(".jpg") }
        if (window.isNotEmpty()) {
            val s = dao.insert(Series(name = if (ru) "У окна" else "By the window", createdAt = at(masha.birthday.plusYears(1), 12, 0)))
            window.forEachIndexed { i, name ->
                val d = masha.birthday.plusYears(i.toLong()).plusDays(if (i == 0) 20 else 0).let { if (it.isAfter(today)) today else it }
                photo(name)?.let { dao.insert(Moment(kids = Moment.kidsOf(listOf(mId)), file = it, takenAt = at(d, 12, 0), seriesId = s)) }
            }
        }

        // Heights on the door frame.
        listOf(76f to 1L, 86.5f to 2L, 95f to 3L, 103f to 4L).forEach { (cm, y) ->
            val d = masha.birthday.plusYears(y)
            if (!d.isAfter(today)) dao.insert(Mark(childId = mId, cm = cm, at = at(d, 18, 0)))
        }
        dao.insert(Mark(childId = mId, cm = 106.5f, at = at(today.minusDays(9), 19, 0)))
        dao.insert(Mark(childId = pId, cm = 74f, at = at(petya.birthday.plusYears(1), 18, 0)))
        dao.insert(Mark(childId = pId, cm = 82f, at = at(today.minusDays(9), 19, 5)))

        // Words.
        val words = if (ru) listOf(
            Triple(pId, "ябуко", "яблоко"), Triple(pId, "ко-ко", "курица"), Triple(pId, "бибика", "машина"),
            Triple(mId, "вертилёт", "вертолёт"), Triple(mId, "колидор", "коридор"), Triple(mId, "мокасины", "макароны"),
        ) else listOf(
            Triple(pId, "appo", "apple"), Triple(pId, "bup-bup", "bubbles"), Triple(pId, "nana", "banana"),
            Triple(mId, "hekalopter", "helicopter"), Triple(mId, "pasghetti", "spaghetti"), Triple(mId, "lellow", "yellow"),
        )
        words.forEachIndexed { i, (id, said, means) ->
            dao.insert(Word(childId = id, said = said, means = means, at = at(today.minusDays(4L + i * 19), 19, 30), fading = if (id == pId && i == 0) "first_words" else if (id == mId && i == 4) "mixed_words" else ""))
        }
        dao.insert(
            Word(
                childId = mId, phrase = true, at = at(today.minusDays(2), 20, 10), fading = "why",
                said = if (ru) "Почему луна идёт за нашей машиной?" else "Why does the moon follow our car?",
            )
        )

        // Hands: an outline for each half-year, growing.
        for (k in 0..4) {
            val d = masha.birthday.plusYears(2).plusMonths(6L * k)
            if (d.isAfter(today)) break
            val len = 92f + k * 8.5f
            val pic = mPics.getOrNull((mPics.size * (0.5f + k * 0.1f)).toInt().coerceAtMost(mPics.size - 1)) ?: continue
            photo(pic)?.let {
                dao.insert(Moment(kids = Moment.kidsOf(listOf(mId)), file = it, kind = Kind.HAND.name, takenAt = at(d, 11, 0), shape = hand(len), measure = (len * 10).toInt()))
            }
        }

        // An interview from her last birthday, and a month of seconds.
        if (clips.isNotEmpty()) {
            val names = clips.map { take(it, "mp4") }
            val bday = masha.birthday.plusYears(4)
            for (q in 1..minOf(8, Interview.questions.size)) {
                val f = names[(q - 1) % names.size] ?: continue
                dao.insert(Moment(kids = Moment.kidsOf(listOf(mId)), file = f, kind = Kind.INTERVIEW.name, takenAt = at(bday, 16, q), question = q, durationMs = c.vault.durationMs(c.vault.file(f))))
            }
            for (d in 1..26) {
                val f = names[d % names.size] ?: continue
                val kid = if (d % 3 == 0) pId else mId
                dao.insert(Moment(kids = Moment.kidsOf(listOf(kid)), file = f, kind = Kind.SECOND.name, takenAt = at(today.minusDays(d.toLong()), 18, 40), durationMs = c.vault.durationMs(c.vault.file(f))))
            }
        }
        c.settings.update { it.copy(onboarded = true, seconds = true) }
    }

    /** A child's hand, spread on the paper: [len] mm from the wrist to the tip of the middle finger. */
    fun hand(len: Float): String {
        val pts = mutableListOf<Pair<Float, Float>>()
        val w = len * 0.46f
        val palm = len * 0.56f
        // A finger from its base along [deg] (0 = straight up, positive leans right): left side up, round tip, right side down.
        fun finger(bx: Float, by: Float, deg: Float, l: Float, fw: Float) {
            val a = Math.toRadians(deg.toDouble())
            val dx = sin(a).toFloat()
            val dy = -cos(a).toFloat()
            val nx = -dy
            val ny = dx
            val h = fw / 2
            pts += bx - nx * h to by - ny * h
            val tx = bx + dx * (l - h)
            val ty = by + dy * (l - h)
            for (s in 0..8) {
                val t = Math.PI * s / 8
                val c = cos(t).toFloat()
                val si = sin(t).toFloat()
                pts += tx + (-nx * c + dx * si) * h to ty + (-ny * c + dy * si) * h
            }
            pts += bx + nx * h to by + ny * h
        }
        val fw = len * 0.11f
        pts += -w * 0.36f to 0f
        pts += -w * 0.5f to -palm * 0.55f
        finger(-w * 0.36f, -palm * 0.93f, -20f, len * 0.31f, fw * 0.9f)
        finger(-w * 0.13f, -palm, -7f, len * 0.39f, fw)
        finger(w * 0.11f, -palm * 1.01f, 2f, len * 0.44f, fw)
        finger(w * 0.34f, -palm * 0.97f, 12f, len * 0.39f, fw)
        pts += w * 0.5f to -palm * 0.62f
        finger(w * 0.52f, -palm * 0.32f, 58f, len * 0.3f, fw * 1.15f)
        pts += w * 0.38f to 0f
        return pts.joinToString(";") { "%.1f,%.1f".format(java.util.Locale.US, it.first, it.second) }
    }
}
