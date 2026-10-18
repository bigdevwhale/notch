package app.notch.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notch.container
import app.notch.data.Moment
import app.notch.ui.T
import app.notch.ui.ctx
import app.notch.ui.theme.Nunito
import app.notch.ui.theme.U
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A vault picture, loaded off the main thread; [full] for the whole one rather than the list copy. */
@Composable
fun rememberPicture(name: String, full: Boolean = false, max: Int = 2000): Bitmap? {
    val vault = ctx().container.vault
    var b by remember(name, full) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name, full) {
        if (name.isNotEmpty()) b = withContext(Dispatchers.IO) { if (full) vault.full(name, max) else vault.small(name) }
    }
    return b
}

/** A moment's picture in a list: cropped to fill, with a play mark and length on clips. */
@Composable
fun Thumb(m: Moment, modifier: Modifier = Modifier, corner: Int = 10, showLength: Boolean = true) {
    val b = rememberPicture(m.file)
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(U.c.surface2)) {
        b?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        if (m.video) {
            Box(Modifier.align(Alignment.Center).size(26.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White)
            }
            if (showLength && m.durationMs > 0) Text(
                T.duration(m.durationMs), fontFamily = Nunito, fontSize = 10.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x77000000)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
