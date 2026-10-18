package app.notch.make

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.notch.R
import kotlin.random.Random

/**
 * The month on one card, for the family chat: a few pictures pinned like prints, the ages, a word the
 * child said that month, how tall they were. Made on the phone; sent only when the parent sends it.
 */
object Postcard {
    const val W = 1080
    const val H = 1350

    class Content(
        val title: String,
        val lines: List<String>,
        val photos: List<Bitmap>,
        /** A word in the child's own spelling and what it means, or null. */
        val word: Pair<String, String>?,
        val footer: String,
        val seed: Int,
    )

    fun draw(context: Context, k: Content): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val rnd = Random(k.seed)
        val serif = runCatching { ResourcesCompat.getFont(context, R.font.lora_regular) }.getOrNull() ?: Typeface.SERIF
        val hand = runCatching { ResourcesCompat.getFont(context, R.font.caveat) }.getOrNull() ?: Typeface.DEFAULT
        val sans = runCatching { ResourcesCompat.getFont(context, R.font.nunito_semibold) }.getOrNull() ?: Typeface.DEFAULT

        // Paper with a little tooth.
        c.drawColor(Color.rgb(246, 239, 226))
        val tooth = Paint()
        repeat(9000) {
            tooth.color = if (rnd.nextBoolean()) Color.argb(10, 120, 90, 60) else Color.argb(14, 255, 255, 255)
            c.drawPoint(rnd.nextFloat() * W, rnd.nextFloat() * H, tooth)
        }
        // A strip of door frame down the left edge, with its notches.
        val wood = Paint().apply { color = Color.rgb(214, 178, 132) }
        c.drawRect(0f, 0f, 46f, H.toFloat(), wood)
        val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 120, 80, 40); strokeWidth = 2f }
        for (i in 0 until 14) c.drawLine(8f + i * 3, 0f, 6f + i * 3 + rnd.nextFloat() * 6, H.toFloat(), grain)
        val pencil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(72, 70, 76); strokeWidth = 4f }
        for (y in 120 until H step 74) c.drawLine(18f, y.toFloat(), 46f + (if (y % 3 == 0) 20 else 8), y.toFloat(), pencil)

        val ink = Color.rgb(54, 44, 37)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 76f; color = ink }
        c.drawText(k.title, 100f, 140f, title)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 36f; color = Color.rgb(118, 100, 86) }
        k.lines.take(3).forEachIndexed { i, s -> c.drawText(s, 102f, 200f + i * 48f, line) }

        // The prints: one large, the rest smaller, each a little askew.
        val top0 = 200f + k.lines.take(3).size * 48f + 30f
        // The prints are laid out in an 860-tall box, then fitted between the lines and the word.
        val room = H - top0 - (if (k.word != null) 250f else 110f)
        val fit = (room / 860f).coerceAtMost(1f)
        val top = 0f
        val raw = when (k.photos.size) {
            0 -> emptyList()
            1 -> listOf(RectF(150f, top, 930f, top + 780f))
            2 -> listOf(RectF(100f, top, 600f, top + 560f), RectF(500f, top + 260f, 1000f, top + 820f))
            3 -> listOf(RectF(100f, top, 620f, top + 560f), RectF(560f, top + 40f, 1000f, top + 440f), RectF(520f, top + 450f, 980f, top + 840f))
            else -> listOf(
                RectF(100f, top, 560f, top + 470f), RectF(560f, top + 20f, 1000f, top + 400f),
                RectF(110f, top + 450f, 520f, top + 830f), RectF(540f, top + 400f, 1000f, top + 860f),
            )
        }
        val slots = raw.map { r -> RectF(W / 2f + (r.left - W / 2f) * fit, top0 + r.top * fit, W / 2f + (r.right - W / 2f) * fit, top0 + r.bottom * fit) }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 40, 25, 10) }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 250, 245) }
        slots.forEachIndexed { i, r ->
            val photo = k.photos[i]
            c.save()
            c.rotate(rnd.nextFloat() * 6f - 3f, r.centerX(), r.centerY())
            c.drawRect(r.left + 8, r.top + 12, r.right + 8, r.bottom + 12, shadow)
            c.drawRect(r, white)
            val inner = RectF(r.left + 18, r.top + 18, r.right - 18, r.bottom - 18)
            val s = maxOf(inner.width() / photo.width, inner.height() / photo.height)
            c.save()
            c.clipRect(inner)
            c.drawBitmap(photo, Matrix().apply {
                postScale(s, s)
                postTranslate(inner.centerX() - photo.width * s / 2, inner.centerY() - photo.height * s / 2)
            }, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
            // A strip of tape.
            val tape = Paint().apply { color = Color.argb(120, 236, 220, 170) }
            c.drawRect(r.centerX() - 70 * fit, r.top - 22 * fit, r.centerX() + 70 * fit, r.top + 26 * fit, tape)
            c.restore()
        }

        // A word the child said, in pencil.
        k.word?.let { (said, means) ->
            val y = H - 190f
            val w = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hand; textSize = 78f; color = Color.rgb(44, 58, 104) }
            c.drawText("«$said»", 110f, y, w)
            if (means.isNotEmpty()) {
                w.textSize = 46f; w.color = Color.rgb(110, 100, 120)
                c.drawText("— $means", 140f + w.measureText(" "), y + 58f, w)
            }
        }
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 28f; color = Color.rgb(160, 140, 120); textAlign = Paint.Align.RIGHT }
        c.drawText(k.footer, W - 50f, H - 44f, foot)
        return b
    }
}
