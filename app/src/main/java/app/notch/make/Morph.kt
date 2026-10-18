package app.notch.make

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.notch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * The growing-face film: the pictures of a series, faces lined up, each melting into the next with the
 * age written beneath. A title first, the last picture held at the end.
 */
object Morph {
    const val FPS = 30
    private const val HOLD = 16
    private const val BLEND = 18

    /** [load] gives the picture when its turn comes, so only two are ever in memory. */
    class Shot(val load: () -> Bitmap?, val caption: String)

    suspend fun make(context: Context, shots: List<Shot>, title: String, sub: String, out: File, progress: (Float) -> Unit = {}) = withContext(Dispatchers.Default) {
        require(shots.isNotEmpty())
        val w = Align.W
        val h = Align.H
        val hand = runCatching { ResourcesCompat.getFont(context, R.font.caveat) }.getOrNull() ?: Typeface.DEFAULT
        val serif = runCatching { ResourcesCompat.getFont(context, R.font.lora_italic) }.getOrNull() ?: Typeface.SERIF
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(frame)
        val faded = Paint(Paint.FILTER_BITMAP_FLAG)
        val shade = Paint().apply { shader = LinearGradient(0f, h * 0.72f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(170, 0, 0, 0), Shader.TileMode.CLAMP) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hand; textSize = 64f; color = Color.WHITE; textAlign = Paint.Align.CENTER }
        fun align(i: Int): Bitmap = shots[i].load()?.let { b -> Align.aligned(b).also { b.recycle() } }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(24, 20, 17)) }
        var cur: Bitmap? = null
        var nxt: Bitmap? = null
        out.delete()
        try {
            Encoder(out, w, h, FPS).use { enc ->
                // The title: a pencil line on paper.
                c.drawColor(Color.rgb(245, 239, 228))
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 66f; color = Color.rgb(58, 46, 38); textAlign = Paint.Align.CENTER }
                c.drawText(title, w / 2f, h * 0.46f, t)
                t.textSize = 40f; t.color = Color.rgb(120, 100, 84); t.typeface = hand; t.textSize = 50f
                c.drawText(sub, w / 2f, h * 0.46f + 76f, t)
                enc.hold(frame, FPS * 2)

                val total = shots.size
                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    val a = nxt ?: align(i)
                    cur?.recycle()
                    cur = a
                    nxt = null
                    fun caption(alpha: Int, s: String) {
                        c.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), shade)
                        text.alpha = alpha
                        c.drawText(s, w / 2f, h - 70f, text)
                    }
                    c.drawBitmap(a, 0f, 0f, null)
                    caption(255, shots[i].caption)
                    enc.hold(frame, if (i == total - 1) HOLD * 4 else HOLD)
                    if (i < total - 1) {
                        val next = align(i + 1).also { nxt = it }
                        for (k in 1..BLEND) {
                            val f = k / (BLEND + 1f)
                            // Ease in and out, so the change is felt more than seen.
                            val e = f * f * (3 - 2 * f)
                            c.drawBitmap(a, 0f, 0f, null)
                            faded.alpha = (e * 255).toInt()
                            c.drawBitmap(next, 0f, 0f, faded)
                            c.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), shade)
                            text.alpha = ((1 - e) * 255).toInt()
                            c.drawText(shots[i].caption, w / 2f, h - 70f, text)
                            text.alpha = (e * 255).toInt()
                            c.drawText(shots[i + 1].caption, w / 2f, h - 70f, text)
                            enc.add(frame)
                        }
                    }
                    progress((i + 1f) / total)
                }
                enc.finish()
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        } finally {
            cur?.recycle()
            nxt?.recycle()
            frame.recycle()
        }
    }
}
