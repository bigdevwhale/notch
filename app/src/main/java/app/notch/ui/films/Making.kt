package app.notch.ui.films

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.notch.R
import app.notch.ui.components.Bar
import app.notch.ui.components.Clips
import app.notch.ui.ctx
import app.notch.ui.moment.share
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import java.io.File

/** A film being made, or made: progress while it is, then the film itself with share and save. */
sealed class Film {
    data class Making(val progress: Float) : Film()
    data class Done(val file: File, val title: String) : Film()
    data class Failed(val why: String) : Film()
}

@Composable
fun FilmDialog(film: Film, onDismiss: () -> Unit) {
    val c = ctx()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = film !is Film.Making)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (film) {
                is Film.Making -> Column(Modifier.align(Alignment.Center).padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.film_making), style = UType.Name.copy(color = Color.White))
                    Spacer(Modifier.height(16.dp))
                    Bar(film.progress, color = U.c.accent, track = Color(0x33FFFFFF))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.film_making_sub), style = UType.Muted.copy(color = Color(0xAAFFFFFF)))
                }
                is Film.Failed -> Column(Modifier.align(Alignment.Center).padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.film_failed), style = UType.Name.copy(color = Color.White))
                    Spacer(Modifier.height(8.dp))
                    Text(film.why, style = UType.Muted.copy(color = Color(0xAAFFFFFF)))
                }
                is Film.Done -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White) }
                        Text(film.title, style = UType.Strong.copy(color = Color.White), modifier = Modifier.weight(1f))
                        IconButton(onClick = { Gallery.save(c, film.file, video = true) }) { Icon(Icons.Rounded.Download, stringResource(R.string.save_gallery), tint = Color.White) }
                        IconButton(onClick = { share(c, film.file, video = true) }) { Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = Color.White) }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Clips(listOf(film.file), Modifier.fillMaxWidth().aspectRatio(0.75f), loop = true)
                    }
                }
            }
            if (film !is Film.Done) IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}

/** Copies a film or a card into the phone's gallery, under Notch. */
object Gallery {
    fun save(context: android.content.Context, src: File, video: Boolean): Boolean {
        val ok = runCatching {
            val resolver = context.contentResolver
            val name = "Notch_" + System.currentTimeMillis() + if (video) ".mp4" else ".jpg"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, (if (video) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_PICTURES) + "/Notch")
                }
            }
            val collection = if (video) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values) ?: error("no uri")
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            true
        }.getOrDefault(false)
        android.widget.Toast.makeText(context, if (ok) R.string.saved_gallery else R.string.save_failed, android.widget.Toast.LENGTH_SHORT).show()
        return ok
    }
}
