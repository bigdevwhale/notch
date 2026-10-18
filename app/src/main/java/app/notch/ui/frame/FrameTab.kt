package app.notch.ui.frame

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notch.R
import app.notch.container
import app.notch.data.Child
import app.notch.data.Mark
import app.notch.data.Moment
import app.notch.data.Pickup
import app.notch.domain.Growth
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.ChildBar
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.SoftButton
import app.notch.ui.components.Thumb
import app.notch.ui.components.jamb
import app.notch.ui.components.notch
import app.notch.ui.ctx
import app.notch.ui.shoot.ShootRequest
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A row on the door frame: one week of life, or a run of empty weeks folded into one. */
private sealed class Row_ {
    data class Week(val week: Int, val moments: List<Moment>, val marks: List<Mark>) : Row_()
    data class Gap(val from: Int, val to: Int, val before: Boolean) : Row_()
    data class Year(val years: Int) : Row_()
}

/**
 * The door frame. The newest week at the top, the way the highest pencil mark is the latest one;
 * scrolling down goes back to the day they were born. A week with pictures has its notch.
 */
@Composable
fun FrameTab(house: House, child: Child, go: Go, onPick: (Long) -> Unit) {
    val c = ctx()
    val moments = house.of(child.id)
    val marks = house.marksOf(child.id)
    val current = house.week(child)
    val first = remember(child, moments) { Growth.firstCounted(child, moments, house.zone) }
    val notched = remember(child, moments) { Growth.notched(child, moments, house.zone) }
    // Folded weeks the parent opened, to fill them from the gallery.
    var open by remember(child.id) { mutableStateOf(emptySet<Int>()) }
    val rows = remember(child, moments, marks, current, open) {
        val byWeek = moments.groupBy { house.weekOf(child, it) }
        val marksByWeek = marks.groupBy { Growth.week(child.birthday, Growth.date(it.at, house.zone)) }
        buildList {
            var w = current
            while (w >= 0) {
                if ((w + 1) % 52 == 0 && w != current) add(Row_.Year((w + 1) / 52))
                val l = byWeek[w].orEmpty()
                val mk = marksByWeek[w].orEmpty()
                if (l.isNotEmpty() || mk.isNotEmpty() || w == current || w in open) {
                    add(Row_.Week(w, l, mk)); w--; continue
                }
                // Fold empty weeks, but never across a birthday line.
                var a = w
                while (a - 1 >= 0 && byWeek[a - 1] == null && marksByWeek[a - 1] == null && a - 1 !in open && (a) % 52 != 0) a--
                add(Row_.Gap(a, w, before = w < first))
                w = a - 1
            }
        }
    }
    var measure by remember { mutableStateOf(false) }
    val color = crayon(child.crayon)

    // Pictures from the gallery into one chosen week, whenever they were taken.
    var into by rememberSaveable { mutableIntStateOf(-1) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(200)) { uris ->
        val week = into
        if (uris.isEmpty() || week < 0) return@rememberLauncherForActivityResult
        val container = c.container
        val from = Growth.weekStart(child.birthday, week).atStartOfDay(house.zone).toInstant().toEpochMilli()
        val to = Growth.weekStart(child.birthday, week + 1).atStartOfDay(house.zone).toInstant().toEpochMilli()
        container.scope.launch {
            val n = withContext(Dispatchers.IO) { Pickup.import(c, container.db, container.vault, uris, Moment.kidsOf(listOf(child.id)), from until to) }
            container.changed()
            withContext(Dispatchers.Main) {
                val text = if (n == 0) c.getString(R.string.import_none) else c.getString(R.string.import_week_done, T.plural(c, R.plurals.pictures, n), week + 1)
                android.widget.Toast.makeText(c, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val fill: (Int) -> Unit = { w -> into = w; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }

    LazyColumn(Modifier.fillMaxSize().background(U.c.bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ChildBar(house, child, go, onPick) }
        item {
            Header(house, child, notched, current, go) { measure = true }
        }
        items(rows, key = {
            when (it) {
                is Row_.Week -> "w${it.week}"
                is Row_.Gap -> "g${it.from}"
                is Row_.Year -> "y${it.years}"
            }
        }) { r ->
            when (r) {
                is Row_.Week -> WeekRow(child, r, r.week == current, color, go) { fill(r.week) }
                is Row_.Gap -> GapRow(child, r) { open = open + (r.from..r.to) }
                is Row_.Year -> YearRow(child, r.years)
            }
        }
        item {
            // The bottom of the frame: the day they were born.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(JAMB).height(26.dp).background(U.c.woodDark, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.frame_born, T.date(c, child.birthday)), style = UType.Hand)
            }
        }
    }
    if (measure) HeightDialog(child, house, onDismiss = { measure = false })
}

private val JAMB = 46.dp

@Composable
private fun Header(house: House, child: Child, notched: Set<Int>, current: Int, go: Go, onMeasure: () -> Unit) {
    val c = ctx()
    val age = Growth.age(child.birthday, house.today)
    val done = current in notched
    val streak = Growth.streak(notched, current)
    val left = 6 - (house.today.toEpochDay() - Growth.weekStart(child.birthday, current).toEpochDay()).toInt()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(T.age(c, age), style = UType.Display)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.frame_week, current + 1) + " · " + T.week(c, Growth.weekStart(child.birthday, current)),
            style = UType.Sub,
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(U.RMd).background(if (done) U.c.surface else U.c.accentSoft).padding(16.dp)) {
            Text(
                if (done) stringResource(R.string.frame_done) else stringResource(
                    when { child.girl -> R.string.frame_todo_f; child.boy -> R.string.frame_todo_m; else -> R.string.frame_todo },
                    child.name,
                ),
                style = UType.Name,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    done && streak > 1 -> stringResource(R.string.frame_streak, T.plural(c, R.plurals.weeks, streak))
                    done -> stringResource(R.string.frame_done_sub)
                    left <= 0 -> stringResource(R.string.frame_last_day)
                    else -> stringResource(R.string.frame_left, T.plural(c, R.plurals.days, left))
                },
                style = UType.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.frame_shoot), { go.shoot(ShootRequest(kids = listOf(child.id))) }, Modifier.weight(1f))
                SoftButton(stringResource(R.string.frame_height), onMeasure, color = U.c.woodDark, soft = U.c.woodLight.copy(alpha = 0.6f), icon = Icons.Rounded.Straighten)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun WeekRow(child: Child, r: Row_.Week, now: Boolean, color: androidx.compose.ui.graphics.Color, go: Go, onFill: () -> Unit) {
    val c = ctx()
    val p = U.c
    val start = Growth.weekStart(child.birthday, r.week)
    val has = r.moments.isNotEmpty()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 58.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(JAMB).fillMaxHeight()) {
            jamb(p, r.week * 31 + child.id.toInt(), size.width)
            if (has) notch(p, size.height / 2, size.width, p.pencil)
            r.marks.forEachIndexed { i, _ -> notch(p, size.height * (0.25f + 0.2f * i), size.width, color, long = true) }
            if (now) drawRect(p.accent, Offset(1.5f, 1.5f), androidx.compose.ui.geometry.Size(size.width - 3f, size.height - 3f), style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(T.ageShort(c, Growth.age(child.birthday, start)), style = UType.Hand.copy(fontSize = 21.sp, color = if (has) p.pencil else p.faint))
                Spacer(Modifier.width(8.dp))
                Text(T.week(c, start), style = UType.Small)
                r.marks.lastOrNull()?.let { m ->
                    Spacer(Modifier.width(8.dp))
                    Text(T.cm(c, m.cm), style = UType.Hand.copy(fontSize = 20.sp, color = color))
                }
            }
            if (has) {
                Spacer(Modifier.height(6.dp))
                // A plain scrolling row: the jamb beside it measures the row's height, which lazy lists can't tell.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.moments.sortedBy { it.takenAt }.forEach { m ->
                        Thumb(m, Modifier.size(74.dp, 92.dp).clickable { go.moment(m.id) }, showLength = false)
                    }
                }
            } else if (now) {
                Text(stringResource(R.string.frame_this_week_empty), style = UType.Muted)
            } else {
                Text(stringResource(R.string.frame_past_empty), style = UType.Muted)
            }
        }
        IconButton(onClick = onFill, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.frame_from_gallery), tint = if (has || now) p.faint else p.woodDark)
        }
    }
}

@Composable
private fun GapRow(child: Child, r: Row_.Gap, onOpen: () -> Unit) {
    val c = ctx()
    val p = U.c
    val n = r.to - r.from + 1
    Row(
        Modifier.fillMaxWidth().height((22 + minOf(n, 6) * 4).dp).clickable(onClickLabel = stringResource(R.string.frame_open), onClick = onOpen).padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(JAMB).fillMaxHeight()) {
            jamb(p, r.from * 17 + child.id.toInt(), size.width)
            // Faint pencil dots for the weeks that went by.
            val k = minOf(n, 6)
            for (i in 0 until k) drawCircle(p.woodDark.copy(alpha = 0.45f), 1.8f, Offset(size.width * 0.72f, size.height * (i + 1) / (k + 1)))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            if (r.before) stringResource(R.string.frame_before, T.plural(c, R.plurals.weeks, n)) else stringResource(R.string.frame_gap, T.plural(c, R.plurals.weeks, n)),
            style = UType.Small,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.UnfoldMore, stringResource(R.string.frame_open), tint = p.faint, modifier = Modifier.padding(end = 16.dp).size(18.dp))
    }
}

@Composable
private fun YearRow(child: Child, years: Int) {
    val c = ctx()
    val p = U.c
    Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(JAMB).fillMaxHeight()) {
            jamb(p, years * 7 + child.id.toInt(), size.width)
            drawLine(p.woodDark, Offset(0f, size.height / 2), Offset(size.width + 30f, size.height / 2), strokeWidth = 2f)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.frame_year, T.plural(c, R.plurals.years, years), T.date(c, child.birthday.plusYears(years.toLong()))),
            style = UType.Strong.copy(color = U.c.woodDark, fontSize = 14.sp, textAlign = TextAlign.Start),
        )
    }
}

@Composable
private fun HeightDialog(child: Child, house: House, onDismiss: () -> Unit) {
    val c = ctx()
    var text by remember { mutableStateOf(house.marksOf(child.id).lastOrNull()?.cm?.let { if (it % 1f == 0f) "%.0f".format(it) else "%.1f".format(it) } ?: "") }
    var at by remember { mutableStateOf(house.now) }
    var picking by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.surface,
        icon = { Icon(Icons.Rounded.Straighten, null, tint = U.c.woodDark) },
        title = { Text(stringResource(R.string.height_title, child.name), style = UType.Strong.copy(fontSize = 19.sp)) },
        text = {
            Column {
                app.notch.ui.components.UTextField(text, { text = it.replace(',', '.') }, placeholder = "92.5", number = true, suffix = stringResource(R.string.cm_unit))
                Spacer(Modifier.height(10.dp))
                Text(T.date(c, at), style = UType.Body.copy(color = U.c.accent), modifier = Modifier.clickable { picking = true }.padding(vertical = 6.dp))
                Spacer(Modifier.height(6.dp))
                val prev = house.marksOf(child.id)
                if (prev.isNotEmpty()) Text(prev.takeLast(3).joinToString("  ·  ") { T.cm(c, it.cm) + " " + T.date(c, it.at) }, style = UType.Small)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = text.toFloatOrNull()
                if (cm != null && cm in 30f..230f) {
                    c.container.write { db.dao().insert(Mark(childId = child.id, cm = cm, at = at)) }
                    onDismiss()
                }
            }) { Text(stringResource(R.string.height_mark), color = U.c.accent, style = UType.Button) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) } },
    )
    if (picking) app.notch.ui.components.DateTimeDialog(at, onDismiss = { picking = false }) { at = it }
}
