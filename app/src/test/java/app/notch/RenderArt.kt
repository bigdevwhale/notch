package app.notch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.core.app.ApplicationProvider
import app.notch.data.DemoSeed
import app.notch.make.Align
import app.notch.make.HandTrace
import app.notch.make.Postcard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws a postcard, the nested demo hands and an aligned frame to app/build/art (run with `-Prender`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArt {
    private val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
    private val demo = File(System.getProperty("render.photos") ?: "src/demo/assets/demo")

    private fun save(b: Bitmap, name: String) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun photos(prefix: String) = demo.listFiles().orEmpty().filter { it.name.startsWith(prefix) && it.name.endsWith(".jpg") }.sortedBy { it.name }

    @Test
    fun postcard() {
        val pics = photos("m-").takeLast(4).mapNotNull { BitmapFactory.decodeFile(it.path) }
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        val b = Postcard.draw(c, Postcard.Content("September 2026", listOf("Masha — 4 years 3 months, 106.5 cm", "Petya — 1 year 7 months, 82 cm"), pics, "pasghetti" to "spaghetti", "made with Notch", 3))
        save(b, "postcard")
    }

    @Test
    fun hands() {
        val b = Bitmap.createBitmap(600, 760, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(255, 253, 248))
        val shapes = (0..4).map { HandTrace.decode(DemoSeed.hand(92f + it * 8.5f)) }
        val k = 4f
        shapes.forEachIndexed { i, s ->
            val p = Path()
            s.forEachIndexed { j, (x, y) -> if (j == 0) p.moveTo(300 + x * k, 740 + y * k) else p.lineTo(300 + x * k, 740 + y * k) }
            p.close()
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(90 + i * 40, 217, 87, 59) })
        }
        save(b, "hands")
    }

    @Test
    fun aligned() {
        photos("s-").forEachIndexed { i, f -> BitmapFactory.decodeFile(f.path)?.let { save(Align.aligned(it), "aligned-$i") } }
    }
}
