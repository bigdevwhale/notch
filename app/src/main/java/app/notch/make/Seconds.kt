package app.notch.make

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import app.notch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A second a day, end to end: a month or a year of a child's life in a minute or six. Each clip gives
 * one second from its middle, with the date and age written in the corner in pencil. The sound stays
 * when every clip has some; otherwise the film is silent rather than lopsided.
 */
@OptIn(UnstableApi::class)
object Seconds {
    const val W = 720
    const val H = 960
    const val TAKE_MS = 1000L

    class Clip(val file: File, val durationMs: Long, val caption: String, val sound: Boolean)

    fun window(durationMs: Long): Pair<Long, Long> {
        if (durationMs <= TAKE_MS) return 0L to durationMs.coerceAtLeast(1)
        val start = (durationMs - TAKE_MS) / 2
        return start to start + TAKE_MS
    }

    /** Must not be called on the main thread; it hops there for the transformer. */
    suspend fun make(context: Context, clips: List<Clip>, out: File) {
        require(clips.isNotEmpty())
        val hand = runCatching { ResourcesCompat.getFont(context, R.font.caveat) }.getOrNull() ?: Typeface.DEFAULT
        val silent = clips.any { !it.sound }
        // Bottom left, a little in from the edge.
        val corner = StaticOverlaySettings.Builder().setOverlayFrameAnchor(-1f, -1f).setBackgroundFrameAnchor(-0.92f, -0.93f).build()
        val fit = Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        val items = clips.map { c ->
            val (a, b) = window(c.durationMs)
            val item = MediaItem.Builder()
                .setUri(Uri.fromFile(c.file))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(a).setEndPositionMs(b).build())
                .build()
            EditedMediaItem.Builder(item)
                .setRemoveAudio(silent)
                .setEffects(Effects(listOf(), listOf<Effect>(fit, OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(caption(c.caption, hand), corner))))))
                .build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build()
        out.delete()
        try {
            export(context, composition, out)
        } catch (e: Exception) {
            out.delete()
            throw e
        }
    }

    /** The pencil note for one second, just big enough for its words. */
    private fun caption(text: String, hand: Typeface): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hand; textSize = 46f; color = Color.WHITE; setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0)) }
        val b = Bitmap.createBitmap((p.measureText(text) + 24).toInt().coerceAtLeast(8), 70, Bitmap.Config.ARGB_8888)
        Canvas(b).drawText(text, 10f, 50f, p)
        return b
    }

    private suspend fun export(context: Context, composition: Composition, out: File) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                try {
                    transformer.start(composition, out.path)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }
    }
}
