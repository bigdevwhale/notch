package app.notch.ui.words

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.notch.R
import app.notch.container
import app.notch.data.Kind
import app.notch.data.Moment
import app.notch.domain.Growth
import app.notch.domain.Interview
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.Chip
import app.notch.ui.components.Clips
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.Thumb
import app.notch.ui.components.TopBar
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import java.io.File

/** A clip from the phone's camera, front-facing where the camera listens to the hint. */
class AnswerContract : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: android.net.Uri): Intent = super.createIntent(context, input)
        .putExtra("android.intent.extras.CAMERA_FACING", 1)
        .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
        .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        .putExtra(MediaStore.EXTRA_DURATION_LIMIT, 90)
}

/**
 * The birthday interview: the same twelve questions every year, answered on video. A year's answers
 * play one after another; a question's answers from every year sit side by side.
 */
@Composable
fun InterviewScreen(childId: Long, house: House, go: Go) {
    val c = ctx()
    val container = c.container
    val child = house.child(childId) ?: return
    val answers = house.of(child.id).filter { it.kindOf == Kind.INTERVIEW }
    val now = Interview.yearOf(child.birthday, house.today)
    val years = (answers.map { Interview.yearOf(child.birthday, Growth.date(it.takenAt)) } + now).distinct().sortedDescending()
    var year by rememberSaveable { mutableIntStateOf(now) }
    var asking by rememberSaveable { mutableIntStateOf(-1) }
    var target by rememberSaveable { mutableStateOf("") }
    var watching by remember { mutableStateOf<List<Moment>?>(null) }
    var across by remember { mutableIntStateOf(-1) }
    val color = crayon(child.crayon)
    fun answer(q: Int, y: Int) = answers.filter { it.question == q && Interview.yearOf(child.birthday, Growth.date(it.takenAt)) == y }.maxByOrNull { it.takenAt }

    val record = rememberLauncherForActivityResult(AnswerContract()) { ok ->
        val f = File(target)
        val q = asking
        if (ok && f.exists() && f.length() > 0 && q > 0) {
            container.write {
                val name = vault.adoptFile(f, "mp4")
                db.dao().insert(Moment(kids = Moment.kidsOf(listOf(child.id)), file = name, kind = Kind.INTERVIEW.name, takenAt = System.currentTimeMillis(), question = q, durationMs = vault.durationMs(vault.file(name))))
            }
            // On to the next question still unanswered this year.
            asking = (q + 1..Interview.questions.size).firstOrNull { answer(it, now) == null } ?: -1
        } else f.delete()
    }
    fun ask(q: Int) {
        asking = q
        val f = container.vault.capture("mp4")
        target = f.path
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        runCatching { record.launch(uri) }.onFailure { android.widget.Toast.makeText(c, R.string.no_camera, android.widget.Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.interview_title), go::back)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text(stringResource(R.string.interview_sub, child.name), style = UType.Sub)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(years) { y -> Chip(T.plural(c, R.plurals.years, y), y == year, { year = y }, color) }
                }
                Spacer(Modifier.height(12.dp))
                val done = Interview.questions.indices.mapNotNull { answer(it + 1, year) }
                if (done.isNotEmpty()) {
                    PrimaryButton(stringResource(R.string.interview_watch, done.size), { watching = done }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                    Spacer(Modifier.height(10.dp))
                }
                if (year == now) {
                    val next = (1..Interview.questions.size).firstOrNull { answer(it, now) == null }
                    if (next != null) {
                        Box(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.accentSoft).padding(16.dp)) {
                            Column {
                                Text(stringResource(R.string.interview_next), style = UType.Eyebrow)
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(Interview.questions[next - 1]), style = UType.Title)
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton(stringResource(R.string.interview_record), { ask(next) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Videocam)
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.interview_hint), style = UType.Small)
                            }
                        }
                    } else Text(stringResource(R.string.interview_all_done), style = UType.Name.copy(color = color))
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(Interview.questions) { i, q ->
                val a = answer(i + 1, year)
                val everyYear = answers.filter { it.question == i + 1 }
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(U.RMd).background(U.c.surface).border(1.dp, U.c.line, U.RMd)
                        .clickable { across = if (across == i) -1 else i }.padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (a != null) color else U.c.surface2), contentAlignment = Alignment.Center) {
                            if (a != null) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
                            else Text("${i + 1}", style = UType.Small)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(q), style = UType.Body, modifier = Modifier.weight(1f))
                        if (a != null) Thumb(a, Modifier.size(44.dp, 56.dp).clickable { watching = listOf(a) }, showLength = false)
                        else if (year == now) Icon(Icons.Rounded.Videocam, stringResource(R.string.interview_record), tint = U.c.accent, modifier = Modifier.clickable { ask(i + 1) }.padding(6.dp))
                    }
                    if (across == i && everyYear.size > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.interview_across), style = UType.Small)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(everyYear.sortedBy { it.takenAt }, key = { it.id }) { m ->
                                Column(Modifier.width(84.dp).clickable { watching = listOf(m) }) {
                                    Thumb(m, Modifier.fillMaxWidth().aspectRatio(0.75f))
                                    Text(T.ageShortAt(c, child, m.takenAt), style = UType.Hand.copy(fontSize = 19.sp))
                                }
                            }
                        }
                        if (everyYear.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.interview_play_across), style = UType.Button.copy(color = U.c.accent),
                                modifier = Modifier.clickable { watching = everyYear.sortedBy { it.takenAt } }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    watching?.let { list ->
        Dialog(onDismissRequest = { watching = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
                Clips(
                    list.map { container.vault.file(it.file) }, Modifier.fillMaxSize(),
                    captions = list.map { m -> c.getString(Interview.questions.getOrElse(m.question - 1) { R.string.interview_title }) + " · " + T.ageShortAt(c, child, m.takenAt) },
                    onEnd = { watching = null },
                )
            }
        }
    }
}
