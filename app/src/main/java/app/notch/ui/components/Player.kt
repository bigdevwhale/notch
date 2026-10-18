package app.notch.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.notch.ui.ctx
import app.notch.ui.theme.Hand
import java.io.File

/**
 * Plays clips one after another (a single clip, or a year of interview answers), with an optional
 * caption over each. Tap to pause; [onEnd] when the last one finishes.
 */
@OptIn(UnstableApi::class)
@Composable
fun Clips(files: List<File>, modifier: Modifier = Modifier, captions: List<String> = emptyList(), loop: Boolean = false, fill: Boolean = false, onEnd: () -> Unit = {}) {
    val c = ctx()
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val player = remember(files) {
        ExoPlayer.Builder(c).build().apply {
            setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) })
            repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { index = player.currentMediaItemIndex }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onEnd() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { paused = !isPlaying && player.playbackState != Player.STATE_ENDED && !player.playWhenReady }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    Box(modifier.background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        player.playWhenReady = !player.playWhenReady
        if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0, 0); player.playWhenReady = true }
    }) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        captions.getOrNull(index)?.let {
            Text(
                it, fontFamily = Hand, fontSize = 26.sp, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(Color(0x88000000)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (paused) Box(Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(40.dp), tint = Color.White)
        }
    }
}
