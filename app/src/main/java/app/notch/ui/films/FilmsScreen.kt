package app.notch.ui.films

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notch.R
import app.notch.container
import app.notch.data.Kind
import app.notch.data.Moment
import app.notch.domain.Growth
import app.notch.make.Seconds
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.SectionTitle
import app.notch.ui.components.SwitchRow
import app.notch.ui.components.Thumb
import app.notch.ui.components.TopBar
import app.notch.ui.components.UCard
import app.notch.ui.ctx
import app.notch.ui.shoot.ShootRequest
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/** A second a day: the films a month or a year of them make. */
@Composable
fun FilmsScreen(childId: Long, house: House, go: Go) {
    val c = ctx()
    val container = c.container
    val child = house.child(childId) ?: return
    val seconds = house.of(child.id).filter { it.kindOf == Kind.SECOND }.sortedBy { it.takenAt }
    val byMonth = seconds.groupBy { YearMonth.from(Growth.date(it.takenAt, house.zone)) }.toSortedMap(compareByDescending { it })
    val byYear = seconds.groupBy { Growth.age(child.birthday, Growth.date(it.takenAt, house.zone)).years }.toSortedMap(compareByDescending { it })
    val today = seconds.any { Growth.date(it.takenAt, house.zone) == house.today }
    var film by remember { mutableStateOf<Film?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun make(list: List<Moment>, title: String, key: String) {
        film = Film.Making(0.1f)
        job = scope.launch {
            val out = File(c.cacheDir, "share").apply { mkdirs() }.let { File(it, "seconds-$key.mp4") }
            try {
                val clips = withContext(Dispatchers.IO) {
                    list.map { m ->
                        val f = container.vault.file(m.file)
                        Seconds.Clip(f, m.durationMs.takeIf { it > 0 } ?: container.vault.durationMs(f), T.day(c, m.takenAt) + " · " + T.ageShortAt(c, child, m.takenAt), container.vault.hasAudio(f))
                    }.filter { it.file.exists() }
                }
                film = Film.Making(0.4f)
                withContext(Dispatchers.Default) { Seconds.make(c, clips, out) }
                film = Film.Done(out, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                film = Film.Failed(e.message ?: "")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.films_title), go::back)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
            item {
                Text(stringResource(R.string.films_sub), style = UType.Sub)
                Spacer(Modifier.height(14.dp))
                UCard {
                    SwitchRow(
                        stringResource(R.string.films_remind), stringResource(R.string.films_remind_sub, T.minutes(c, house.settings.secondAt)),
                        house.settings.seconds, { on -> container.write { settings.update { it.copy(seconds = on) } } },
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        stringResource(if (today) R.string.films_today_done else R.string.films_today), { go.shoot(ShootRequest(kids = listOf(child.id), seconds = true)) },
                        Modifier.fillMaxWidth(), icon = Icons.Rounded.Timer,
                    )
                }
                if (seconds.isEmpty()) Text(stringResource(R.string.films_none), style = UType.Muted, modifier = Modifier.padding(vertical = 18.dp))
            }
            if (byYear.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.films_years)) }
                items(byYear.entries.toList(), key = { "y" + it.key }) { (y, list) ->
                    FilmRow(list, stringResource(R.string.films_year, y + 1), T.plural(c, R.plurals.seconds, list.size)) {
                        make(list, c.getString(R.string.films_year, y + 1) + " · " + child.name, "y$y-${child.id}")
                    }
                }
            }
            if (byMonth.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.films_months)) }
                items(byMonth.entries.toList(), key = { "m" + it.key }) { (ym, list) ->
                    val name = T.month(c, ym.atDay(1))
                    FilmRow(list, name, T.plural(c, R.plurals.seconds, list.size)) { make(list, name + " · " + child.name, "m$ym-${child.id}") }
                }
            }
        }
    }
    film?.let { f -> FilmDialog(f) { job?.cancel(); film = null } }
}

@Composable
private fun FilmRow(list: List<Moment>, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(U.c.surface, U.RMd).border(1.dp, U.c.line, U.RMd).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            list.takeLast(3).forEach { Thumb(it, Modifier.size(34.dp, 46.dp), corner = 6, showLength = false) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Strong)
            Text(sub, style = UType.Muted)
        }
        Icon(Icons.Rounded.Movie, null, tint = U.c.accent)
    }
}
