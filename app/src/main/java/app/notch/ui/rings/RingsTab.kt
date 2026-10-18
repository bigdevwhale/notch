package app.notch.ui.rings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notch.R
import app.notch.data.Child
import app.notch.data.Kind
import app.notch.domain.Growth
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.ChildBar
import app.notch.ui.components.InfoRow
import app.notch.ui.components.Rings
import app.notch.ui.components.SectionTitle
import app.notch.ui.components.Stat
import app.notch.ui.components.Thumb
import app.notch.ui.components.UCard
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon

/** The whole childhood at a glance: its tree rings, and the things made from it. */
@Composable
fun RingsTab(house: House, child: Child, go: Go, onPick: (Long) -> Unit) {
    val c = ctx()
    val moments = house.of(child.id)
    val current = house.week(child)
    val notched = remember(child, moments) { Growth.notched(child, moments, house.zone) }
    val first = remember(child, moments) { Growth.firstCounted(child, moments, house.zone) }
    val years = current / 52 + 1
    var year by remember(child.id) { mutableIntStateOf(current / 52) }
    val color = crayon(child.crayon)

    Column(Modifier.fillMaxSize().background(U.c.bg).verticalScroll(rememberScrollState())) {
        ChildBar(house, child, go, onPick)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(6.dp))
            Rings(
                years = years,
                state = { w -> when { w > current -> -1; w in notched -> 2; w >= first -> 1; else -> 0 } },
                current = current, color = color, selected = year, onYear = { year = it },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            val inYear = (0 until 52).map { year * 52 + it }.filter { it <= current }
            val got = inYear.count { it in notched }
            Text(
                stringResource(R.string.rings_year, year + 1, got, inYear.size),
                style = UType.Name, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                T.date(c, child.birthday.plusYears(year.toLong())) + " – " + T.date(c, child.birthday.plusYears(year + 1L).minusDays(1)),
                style = UType.Muted,
            )
            Spacer(Modifier.height(16.dp))
            UCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(moments.size.toString(), stringResource(R.string.stat_moments), Modifier.weight(1f))
                    val counted = (current - first + 1).coerceAtLeast(1)
                    Stat("${notched.count { it >= first } * 100 / counted}%", stringResource(R.string.stat_weeks), Modifier.weight(1f))
                    Stat(Growth.bestStreak(notched).toString(), stringResource(R.string.stat_best), Modifier.weight(1f))
                }
            }

            val marks = house.marksOf(child.id)
            if (marks.size >= 2) {
                SectionTitle(stringResource(R.string.rings_height))
                UCard { HeightChart(child, house) }
            }

            val today = Growth.onThisDay(moments, house.today, house.zone)
            if (today.isNotEmpty()) {
                SectionTitle(stringResource(R.string.rings_on_this_day))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(today, key = { it.id }) { m ->
                        Column(Modifier.width(110.dp).clickable { go.moment(m.id) }) {
                            Thumb(m, Modifier.fillMaxWidth().aspectRatio(0.8f))
                            Text(T.ageShortAt(c, child, m.takenAt) + " · " + Growth.date(m.takenAt).year, style = UType.Small)
                        }
                    }
                }
            }

            house.children.filter { it.id != child.id }.forEach { other ->
                val same = Growth.sameAge(child, other, house.moments, house.today, zone = house.zone)
                if (same.isEmpty()) return@forEach
                SectionTitle(stringResource(R.string.rings_same_age, other.name, T.age(c, Growth.age(child.birthday, house.today))))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(same.take(10), key = { it.id }) { m ->
                        Thumb(m, Modifier.size(100.dp, 124.dp).clickable { go.moment(m.id) })
                    }
                }
            }

            SectionTitle(stringResource(R.string.rings_made))
            UCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                val seconds = moments.count { it.kindOf == Kind.SECOND }
                InfoRow(Icons.Rounded.Movie, stringResource(R.string.films_title), stringResource(R.string.films_count, T.plural(c, R.plurals.seconds, seconds)), onClick = { go.films(child.id) })
                val hands = moments.count { it.kindOf == Kind.HAND }
                InfoRow(Icons.Rounded.BackHand, stringResource(R.string.hands_title), if (hands == 0) stringResource(R.string.hands_none) else T.plural(c, R.plurals.hands, hands), onClick = { go.hands(child.id) })
                val answers = moments.filter { it.kindOf == Kind.INTERVIEW }.map { app.notch.domain.Interview.yearOf(child.birthday, Growth.date(it.takenAt)) }.distinct().size
                InfoRow(Icons.Rounded.RecordVoiceOver, stringResource(R.string.interview_title), if (answers == 0) stringResource(R.string.interview_none) else T.plural(c, R.plurals.interviews, answers), onClick = { go.interview(child.id) })
                val last = house.today.minusMonths(1)
                InfoRow(Icons.Rounded.Mail, stringResource(R.string.card_title), T.month(c, last), onClick = { go.card(child.id, last) })
            }

            val series = house.series.filter { s -> house.inSeries(s.id).any { it.of(child.id) } }
            SectionTitle(stringResource(R.string.series_title))
            if (series.isEmpty()) Text(stringResource(R.string.series_none), style = UType.Muted)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(series, key = { it.id }) { s ->
                    val l = house.inSeries(s.id)
                    Column(Modifier.width(120.dp).clickable { go.series(s.id) }) {
                        Thumb(l.last(), Modifier.fillMaxWidth().aspectRatio(0.75f))
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Rounded.Layers, null, Modifier.size(14.dp), tint = U.c.muted)
                            Spacer(Modifier.width(4.dp))
                            Text(s.name.ifBlank { T.day(c, l.first().takenAt) } + " · " + l.size, style = UType.Small, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Height against age: pencil dots joined by a line. */
@Composable
private fun HeightChart(child: Child, house: House) {
    val c = ctx()
    val marks = house.marksOf(child.id)
    val p = U.c
    val color = crayon(child.crayon)
    val ages = marks.map { Growth.age(child.birthday, Growth.date(it.at)).totalDays.toFloat() }
    val lo = marks.minOf { it.cm } - 3
    val hi = marks.maxOf { it.cm } + 3
    val a0 = ages.min()
    val a1 = ages.max().coerceAtLeast(a0 + 1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            fun pt(i: Int) = Offset(16f + (ages[i] - a0) / (a1 - a0) * (size.width - 32f), size.height - 12f - (marks[i].cm - lo) / (hi - lo) * (size.height - 24f))
            for (k in 1..3) drawLine(p.line, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), strokeWidth = 1f)
            val path = Path().apply { marks.indices.forEach { i -> val o = pt(i); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
            marks.indices.forEach { drawCircle(p.pencil, 6f, pt(it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(T.cm(c, marks.first().cm) + " · " + T.ageShortAt(c, child, marks.first().at), style = UType.Small)
            Text(T.cm(c, marks.last().cm) + " · " + T.ageShortAt(c, child, marks.last().at), style = UType.Small.copy(color = color))
        }
    }
}
