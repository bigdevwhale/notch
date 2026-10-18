package app.notch.make

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.FaceDetector

/**
 * Lines faces up across the years: the eyes of every picture in a series land on the same spot at the
 * same size, so playing them one after another looks like the child growing in place. Uses the
 * phone's own face finder; nothing leaves the phone.
 */
object Align {
    const val W = 720
    const val H = 960

    /** Where the eyes go, and how far apart. */
    private const val EYES_X = W / 2f
    private const val EYES_Y = H * 0.42f
    private const val EYES_GAP = W * 0.2f

    class Face(val mid: PointF, val gap: Float)

    /** The biggest face in the picture, or null when none is found (a back, a profile, a blur). */
    fun face(b: Bitmap): Face? = runCatching {
        // The finder wants an even width and 565 colour; it works fine on a small copy.
        val k = minOf(1f, 640f / maxOf(b.width, b.height))
        val w = ((b.width * k).toInt() / 2) * 2
        val h = (b.height * k).toInt()
        if (w < 32 || h < 32) return null
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(small).drawBitmap(b, Matrix().apply { setScale(w.toFloat() / b.width, h.toFloat() / b.height) }, Paint(Paint.FILTER_BITMAP_FLAG))
        val found = arrayOfNulls<FaceDetector.Face>(4)
        val n = FaceDetector(w, h, 4).findFaces(small, found)
        small.recycle()
        val best = found.take(n).filterNotNull().filter { it.confidence() >= 0.3f }.maxByOrNull { it.eyesDistance() } ?: return null
        val mid = PointF().also { best.getMidPoint(it) }
        val kx = b.width.toFloat() / w
        Face(PointF(mid.x * kx, mid.y * b.height.toFloat() / h), best.eyesDistance() * kx)
    }.getOrNull()

    /** The picture placed so its face sits where every other one's does; without a face, simply filled. */
    fun aligned(b: Bitmap, face: Face? = face(b)): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(24, 20, 17))
        val m = Matrix()
        if (face != null) {
            val s = (EYES_GAP / face.gap).coerceIn(0.05f, 8f)
            m.postTranslate(-face.mid.x, -face.mid.y)
            m.postScale(s, s)
            m.postTranslate(EYES_X, EYES_Y)
            // Never leave the frame half empty: zoom in a little more if the picture doesn't cover it.
            val cover = maxOf(W.toFloat() / (b.width * s), H.toFloat() / (b.height * s))
            if (cover > 1f) m.postScale(cover, cover, EYES_X, EYES_Y)
        } else {
            val s = maxOf(W.toFloat() / b.width, H.toFloat() / b.height)
            m.postScale(s, s)
            m.postTranslate((W - b.width * s) / 2, (H - b.height * s) / 2)
        }
        c.drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }
}
