package app.notch.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notch.R
import app.notch.container
import app.notch.make.Morph
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.ConfirmDialog
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.SoftButton
import app.notch.ui.components.Thumb
import app.notch.ui.components.TopBar
import app.notch.ui.components.UTextField
import app.notch.ui.ctx
import app.notch.ui.films.Film
import app.notch.ui.films.FilmDialog
import app.notch.ui.shoot.ShootRequest
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The same spot over the years. Shoot it again with the last picture as a ghost over the camera, and
 * play them all as one film where the face stays put and the child grows.
 */
@Composable
fun SeriesScreen(id: Long, house: House, go: Go) {
    val c = ctx()
    val container = c.container
    val series = house.series.firstOrNull { it.id == id } ?: return
    val shots = house.inSeries(id)
    val kids = shots.flatMap { it.kidIds }.distinct().mapNotNull { house.child(it) }
    val child = kids.firstOrNull()
    var name by remember(id) { mutableStateOf(series.name) }
    var film by remember { mutableStateOf<Film?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun make() {
        if (shots.size < 2 || child == null) return
        film = Film.Making(0f)
        job = scope.launch {
            val out = File(c.cacheDir, "share").apply { mkdirs() }.let { File(it, "grow-$id.mp4") }
            val title = series.name.ifBlank { child.name }
            try {
                Morph.make(
                    c,
                    shots.map { m -> Morph.Shot({ container.vault.full(m.file, 1600) }, T.ageShortAt(c, child, m.takenAt)) },
                    title = title,
                    sub = T.ageShortAt(c, child, shots.first().takenAt) + " → " + T.ageShortAt(c, child, shots.last().takenAt),
                    out = out,
                ) { p -> scope.launch(Dispatchers.Main) { if (film is Film.Making) film = Film.Making(p) } }
                withContext(Dispatchers.Main) { film = Film.Done(out, title) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { film = Film.Failed(e.message ?: "") }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(name.ifBlank { stringResource(R.string.series_one) }, {
            if (name != series.name) container.write { db.dao().update(series.copy(name = name.trim())) }
            go.back()
        }) {
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = U.c.muted) }
        }
        LazyVerticalGrid(
            GridCells.Fixed(3), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    UTextField(name, { name = it }, placeholder = stringResource(R.string.series_name_hint))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.series_sub), style = UType.Sub)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(stringResource(R.string.series_again), {
                            go.shoot(ShootRequest(kids = kids.map { it.id }, ghost = shots.lastOrNull()?.id ?: 0))
                        }, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt)
                        if (shots.size >= 2) SoftButton(stringResource(R.string.series_film), ::make, icon = Icons.Rounded.Movie)
                    }
                    if (shots.size < 2) Text(stringResource(R.string.series_need_two), style = UType.Small, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(14.dp))
                }
            }
            items(shots, key = { it.id }) { m ->
                Column(Modifier.clickable { go.moment(m.id) }) {
                    Thumb(m, Modifier.fillMaxWidth().aspectRatio(0.75f))
                    child?.let { Text(T.ageShortAt(c, it, m.takenAt), style = UType.Hand.copy(fontSize = 18.sp)) }
                }
            }
        }
    }
    film?.let { f -> FilmDialog(f) { job?.cancel(); film = null } }
    if (deleting) ConfirmDialog(
        stringResource(R.string.series_delete_title), stringResource(R.string.series_delete_text), stringResource(R.string.delete),
        onConfirm = {
            container.write { db.dao().leaveSeries(series.id); db.dao().delete(series) }
            go.back()
        },
        onDismiss = { deleting = false }, danger = true,
    )
}
