package app.notch.ui.words

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.notch.R
import app.notch.container
import app.notch.data.Child
import app.notch.data.Word
import app.notch.domain.Fading
import app.notch.domain.Interview
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.ChildBar
import app.notch.ui.components.ConfirmDialog
import app.notch.ui.components.SoftButton
import app.notch.ui.components.SwitchRow
import app.notch.ui.components.UTextField
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import kotlinx.coroutines.delay
import java.io.File

/** The child's own dictionary: words the way they say them, with their voice, and the birthday interview. */
@Composable
fun WordsTab(house: House, child: Child, go: Go, onPick: (Long) -> Unit) {
    val c = ctx()
    val words = house.wordsOf(child.id)
    var editing by remember { mutableStateOf<Word?>(null) }
    var adding by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) { onDispose { VoicePlayer.stop() } }
    val color = crayon(child.crayon)

    LazyColumn(Modifier.fillMaxSize().background(U.c.bg), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { ChildBar(house, child, go, onPick) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.words_title), style = UType.Display)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.words_sub), style = UType.Sub)
                Spacer(Modifier.height(14.dp))
                // The interview, nearest the birthday.
                val year = Interview.yearOf(child.birthday, house.today)
                Row(
                    Modifier.fillMaxWidth().clip(U.RMd).background(U.c.woodLight.copy(alpha = 0.55f)).clickable { go.interview(child.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.RecordVoiceOver, null, tint = U.c.woodDark)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.interview_title), style = UType.Strong)
                        Text(stringResource(R.string.interview_banner, T.plural(c, R.plurals.years, year)), style = UType.Muted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = U.c.muted)
                }
                Spacer(Modifier.height(12.dp))
                SoftButton(stringResource(R.string.word_add), { adding = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            }
        }
        if (words.isEmpty()) item {
            Text(stringResource(R.string.words_none, child.name), style = UType.Sub, modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))
        }
        items(words, key = { it.id }) { w ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(U.RMd).background(U.c.surface)
                    .border(1.dp, U.c.line, U.RMd).clickable { editing = w }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (w.phrase) "«${w.said}»" else w.said, style = UType.Hand.copy(fontSize = if (w.phrase) 24.sp else 32.sp, lineHeight = 30.sp, color = color))
                    if (w.means.isNotEmpty()) Text(stringResource(R.string.word_means, w.means), style = UType.Body.copy(color = U.c.muted))
                    Text(T.ageShortAt(c, child, w.at) + " · " + T.date(c, w.at), style = UType.Small)
                }
                if (w.audio.isNotEmpty()) {
                    val on = playing == w.id
                    IconButton(onClick = {
                        if (on) { VoicePlayer.stop(); playing = null } else {
                            playing = w.id
                            VoicePlayer.play(c.container.vault.file(w.audio), w.id.toString()) { playing = null }
                        }
                    }) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(if (on) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = color)
                        }
                    }
                }
            }
        }
    }
    if (adding) WordDialog(child, null, "", onDismiss = { adding = false })
    editing?.let { WordDialog(child, it, it.fading, onDismiss = { editing = null }) }
}

/** Adding a word, or changing one: how it's said, what it means, and the child's own voice. */
@Composable
fun WordDialog(child: Child, word: Word?, fading: String, onDismiss: () -> Unit) {
    val c = ctx()
    val container = c.container
    var said by remember { mutableStateOf(word?.said ?: "") }
    var means by remember { mutableStateOf(word?.means ?: "") }
    var phrase by remember { mutableStateOf(word?.phrase ?: Fading.byKey(fading)?.let { it.key != "first_words" && it.key != "mixed_words" } ?: false) }
    var audio by remember { mutableStateOf<File?>(null) }
    var removeAudio by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var since by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var confirmDelete by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(c) }
    DisposableEffect(Unit) { onDispose { recorder.stop(); VoicePlayer.stop() } }
    var mic by remember { mutableStateOf(ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    LaunchedEffect(recording) { while (recording) { elapsed = System.currentTimeMillis() - since; delay(100) } }
    val existing = word?.audio?.takeIf { it.isNotEmpty() && !removeAudio }

    AlertDialog(
        onDismissRequest = { if (!recording) onDismiss() },
        containerColor = U.c.surface,
        title = { Text(stringResource(if (word == null) R.string.word_add else R.string.word_edit), style = UType.Strong.copy(fontSize = 19.sp)) },
        text = {
            Column {
                Fading.byKey(fading)?.let { Text(c.getString(it.title), style = UType.Hand.copy(fontSize = 22.sp, color = U.c.accent)); Spacer(Modifier.height(8.dp)) }
                UTextField(said, { said = it }, placeholder = stringResource(if (phrase) R.string.word_said_phrase_hint else R.string.word_said_hint), minLines = if (phrase) 2 else 1)
                Spacer(Modifier.height(8.dp))
                if (!phrase) {
                    UTextField(means, { means = it }, placeholder = stringResource(R.string.word_means_hint))
                    Spacer(Modifier.height(4.dp))
                }
                SwitchRow(stringResource(R.string.word_phrase), null, phrase, { phrase = it })
                Spacer(Modifier.height(6.dp))
                // Hold to record.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(if (recording) U.c.accent else U.c.accentSoft)
                            .pointerInput(mic) {
                                detectTapGestures(onPress = {
                                    if (!mic) { askMic.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                                    VoicePlayer.stop()
                                    if (recorder.start()) { recording = true; since = System.currentTimeMillis() }
                                    tryAwaitRelease()
                                    recording = false
                                    recorder.stop()?.let { audio?.delete(); audio = it }
                                })
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Mic, stringResource(R.string.word_record), tint = if (recording) U.c.paper else U.c.accent) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                recording -> T.duration(elapsed)
                                audio != null || existing != null -> stringResource(R.string.word_recorded)
                                else -> stringResource(R.string.word_hold)
                            },
                            style = UType.Body,
                        )
                        if (!recording && (audio != null || existing != null)) Row {
                            TextButton(onClick = {
                                (audio ?: existing?.let { container.vault.file(it) })?.let { VoicePlayer.play(it, "preview") {} }
                            }) { Text(stringResource(R.string.play), color = U.c.accent) }
                            TextButton(onClick = { audio?.delete(); audio = null; removeAudio = true }) { Text(stringResource(R.string.delete), color = U.c.muted) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = said.isNotBlank() && !recording, onClick = {
                val a = audio
                container.write {
                    val name = when {
                        a != null -> vault.adoptFile(a, "m4a").also { word?.audio?.let(vault::delete) }
                        removeAudio -> "".also { word?.audio?.let(vault::delete) }
                        else -> word?.audio ?: ""
                    }
                    val w = (word ?: Word(childId = child.id, said = "", at = System.currentTimeMillis(), fading = fading))
                        .copy(said = said.trim(), means = if (phrase) "" else means.trim(), phrase = phrase, audio = name)
                    if (word == null) db.dao().insert(w) else db.dao().update(w)
                }
                audio = null
                onDismiss()
            }) { Text(stringResource(R.string.save), color = U.c.accent, style = UType.Button) }
        },
        dismissButton = {
            Row {
                if (word != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = U.c.danger, style = UType.Button) }
                TextButton(onClick = { audio?.delete(); onDismiss() }) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) }
            }
        },
    )
    if (confirmDelete && word != null) ConfirmDialog(
        stringResource(R.string.word_delete_title), stringResource(R.string.word_delete_text, word.said), stringResource(R.string.delete),
        onConfirm = {
            container.write { db.dao().delete(word); vault.delete(word.audio) }
            onDismiss()
        },
        onDismiss = { confirmDelete = false }, danger = true,
    )
}
