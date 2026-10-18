package app.notch.ui.moment

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.notch.R
import app.notch.container
import app.notch.data.Kind
import app.notch.data.Moment
import app.notch.data.Series
import app.notch.domain.Fading
import app.notch.domain.Interview
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.Chip
import app.notch.ui.components.Clips
import app.notch.ui.components.ConfirmDialog
import app.notch.ui.components.DateTimeDialog
import app.notch.ui.components.SoftButton
import app.notch.ui.components.TopBar
import app.notch.ui.components.UTextField
import app.notch.ui.components.rememberPicture
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import java.io.File

/** One picture or clip, large: who is in it and how old they were, what was written under it. */
@Composable
fun MomentScreen(id: Long, house: House, go: Go) {
    val c = ctx()
    val container = c.container
    val m = house.moment(id) ?: return
    val kids = m.kidIds.mapNotNull { house.child(it) }
    var note by remember(m.id) { mutableStateOf(m.note) }
    var deleting by remember { mutableStateOf(false) }
    var redating by remember { mutableStateOf(false) }
    fun save(change: (Moment) -> Moment) = container.write { db.dao().moment(m.id)?.let { db.dao().update(change(it)) } }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(T.date(c, m.takenAt), {
            if (note != m.note) save { it.copy(note = note.trim()) }
            go.back()
        }) {
            IconButton(onClick = { save { it.copy(favorite = !it.favorite) } }) {
                Icon(if (m.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite), tint = if (m.favorite) U.c.accent else U.c.muted)
            }
            IconButton(onClick = { share(c, container.vault.file(m.file), m.video) }) { Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = U.c.muted) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = U.c.muted) }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (m.video) Clips(listOf(container.vault.file(m.file)), Modifier.fillMaxWidth().aspectRatio(0.75f), loop = true)
            else {
                val b = rememberPicture(m.file, full = true)
                Box(Modifier.fillMaxWidth().heightIn(min = 280.dp).background(U.c.night)) {
                    b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height), contentScale = ContentScale.Fit) }
                }
            }
            Column(Modifier.padding(20.dp)) {
                kids.forEach { k ->
                    Text(k.name + " · " + T.ageAt(c, k, m.takenAt), style = UType.Name.copy(color = crayon(k.crayon)))
                }
                Text(T.date(c, m.takenAt) + ", " + T.time(c, m.takenAt), style = UType.Muted, modifier = Modifier.clickable { redating = true }.padding(vertical = 4.dp))
                when (m.kindOf) {
                    Kind.SECOND -> Text(stringResource(R.string.moment_second), style = UType.Small)
                    Kind.INTERVIEW -> Text(stringResource(Interview.questions.getOrElse(m.question - 1) { R.string.interview_title }), style = UType.Hand.copy(fontSize = 22.sp))
                    Kind.HAND -> Text(stringResource(R.string.moment_hand, T.mm(c, m.measure / 10f)), style = UType.Hand.copy(fontSize = 22.sp))
                    else -> Unit
                }
                Fading.byKey(m.fading)?.let { Text(stringResource(it.title), style = UType.Hand.copy(fontSize = 22.sp, color = U.c.accent)) }

                Spacer(Modifier.height(14.dp))
                UTextField(note, { note = it }, placeholder = stringResource(R.string.moment_note_hint), minLines = 2)
                if (note != m.note) {
                    Spacer(Modifier.height(8.dp))
                    SoftButton(stringResource(R.string.save), { save { it.copy(note = note.trim()) } })
                }

                if (house.children.size > 1) {
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.moment_who).uppercase(), style = UType.Eyebrow)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        house.children.forEach { k ->
                            val on = m.of(k.id)
                            Chip(k.name, on, {
                                val ids = if (on) m.kidIds - k.id else m.kidIds + k.id
                                if (ids.isNotEmpty()) save { it.copy(kids = Moment.kidsOf(ids)) }
                            }, crayon(k.crayon))
                        }
                    }
                }

                if (m.kindOf == Kind.PHOTO) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (m.seriesId > 0) SoftButton(stringResource(R.string.moment_series_open), { go.series(m.seriesId) }, Modifier.weight(1f), icon = Icons.Rounded.Layers)
                        else SoftButton(stringResource(R.string.moment_series_start), {
                            container.write {
                                val s = db.dao().insert(Series(createdAt = System.currentTimeMillis()))
                                db.dao().moment(m.id)?.let { db.dao().update(it.copy(seriesId = s)) }
                            }
                        }, Modifier.weight(1f), icon = Icons.Rounded.Layers)
                        kids.singleOrNull()?.let { k ->
                            SoftButton(stringResource(R.string.moment_face), {
                                container.write { db.dao().update(k.copy(face = m.id)) }
                            }, color = U.c.woodDark, soft = U.c.woodLight.copy(alpha = 0.6f), icon = Icons.Rounded.AccountCircle)
                        }
                    }
                    if (m.seriesId == 0L) Text(stringResource(R.string.moment_series_sub), style = UType.Small, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
    if (deleting) ConfirmDialog(
        stringResource(R.string.moment_delete_title), stringResource(R.string.moment_delete_text), stringResource(R.string.delete),
        onConfirm = {
            container.write {
                db.dao().delete(m)
                vault.delete(m.file)
                children().filter { it.face == m.id }.forEach { db.dao().update(it.copy(face = 0)) }
            }
            go.back()
        },
        onDismiss = { deleting = false }, danger = true,
    )
    if (redating) DateTimeDialog(m.takenAt, onDismiss = { redating = false }) { t -> save { it.copy(takenAt = t) } }
}

private suspend fun app.notch.AppContainer.children() = db.dao().children()

/** Sends a picture or a film to another app; it is copied to the share folder first so the vault's names stay private. */
fun share(context: Context, f: File, video: Boolean, text: String = "") {
    if (!f.exists()) return
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val out = File(dir, "notch-" + System.currentTimeMillis() + "." + f.extension)
    runCatching { f.copyTo(out, overwrite = true) }.onFailure { return }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", out)
    val send = Intent(Intent.ACTION_SEND)
        .setType(if (video) "video/mp4" else "image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (text.isNotEmpty()) send.putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
