package app.notch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notch.data.Child
import app.notch.ui.theme.Lora
import app.notch.ui.theme.Palette
import app.notch.ui.theme.U
import app.notch.ui.theme.crayon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** The honey-wood strip of a door frame, its grain wandering down, seeded so it never jumps on scroll. */
fun DrawScope.jamb(p: Palette, seed: Int, width: Float) {
    drawRect(p.wood, Offset.Zero, Size(width, size.height))
    // A lighter face and a shadowed edge, the way a moulding catches the light.
    drawRect(p.woodLight.copy(alpha = 0.55f), Offset(width * 0.12f, 0f), Size(width * 0.22f, size.height))
    drawRect(p.woodDark.copy(alpha = 0.35f), Offset(width * 0.86f, 0f), Size(width * 0.14f, size.height))
    val r = Random(seed)
    repeat(6) {
        val x = width * (0.08f + r.nextFloat() * 0.8f)
        val wobble = r.nextFloat() * 3f - 1.5f
        drawLine(p.woodGrain.copy(alpha = 0.35f + r.nextFloat() * 0.3f), Offset(x, 0f), Offset(x + wobble, size.height), strokeWidth = 1f + r.nextFloat() * 1.4f)
    }
    if (r.nextInt(9) == 0) {
        // A knot.
        val cx = width * (0.3f + r.nextFloat() * 0.4f)
        val cy = size.height * r.nextFloat()
        drawOval(p.woodDark.copy(alpha = 0.45f), Offset(cx - 5f, cy - 9f), Size(10f, 18f), style = Stroke(1.5f))
        drawOval(p.woodDark.copy(alpha = 0.6f), Offset(cx - 2f, cy - 4f), Size(4f, 8f))
    }
}

/**
 * A notch cut into the frame at height [y]: a pencil line from the edge and a small V carved where it
 * meets the wood. [long] for the line of a measured height.
 */
fun DrawScope.notch(p: Palette, y: Float, width: Float, color: Color, long: Boolean = false) {
    val len = if (long) width * 1.0f else width * 0.7f
    val x0 = width - len
    drawLine(color, Offset(x0, y), Offset(width + (if (long) 22f else 10f), y), strokeWidth = if (long) 5f else 4.2f, cap = StrokeCap.Round)
    val v = Path().apply {
        moveTo(x0 - 2f, y - 8f)
        lineTo(x0 + 10f, y)
        lineTo(x0 - 2f, y + 8f)
        close()
    }
    drawPath(v, p.woodDark.copy(alpha = 0.85f))
}

/** A child's face in a crayon ring; their initial when there is no picture yet. */
@Composable
fun Face(child: Child, picture: String, size: Dp = 44.dp, ring: Boolean = true) {
    val b = rememberPicture(picture)
    val color = crayon(child.crayon)
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f))
            .then(if (ring) Modifier.border(2.5.dp, color, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        else Text(child.name.take(1).uppercase(), fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp, color = color)
    }
}

/**
 * The tree rings of a childhood: one ring a year of age from the heart outwards, each cut into its
 * 52 weeks. A week with a notch is dark wood; a week without is pale; weeks before the door frame
 * was hung are barely there. Tapping a ring picks that year.
 */
@Composable
fun Rings(
    years: Int,
    /** For a week of life: 2 notched, 1 counted but empty, 0 before the app, -1 not yet lived. */
    state: (Int) -> Int,
    current: Int,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Int = -1,
    onYear: (Int) -> Unit = {},
) {
    val p = U.c
    val rings = years.coerceAtLeast(1)
    Canvas(
        modifier.pointerInput(rings) {
            detectTapGestures { o ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = hypot(o.x - cx, o.y - cy)
                val outer = minOf(cx, cy) * 0.96f
                val core = outer * 0.1f
                if (r in core..outer) onYear(((r - core) / ((outer - core) / rings)).toInt().coerceIn(0, rings - 1))
            }
        },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = minOf(cx, cy) * 0.96f
        val core = outer * 0.1f
        val band = (outer - core) / rings
        // Bark and sapwood.
        drawCircle(p.woodDark, outer + 6f, Offset(cx, cy))
        drawCircle(p.woodLight, outer, Offset(cx, cy))
        drawCircle(p.woodDark.copy(alpha = 0.7f), core, Offset(cx, cy))
        val gap = 0.9f
        for (y in 0 until rings) {
            val r0 = core + band * y
            val mid = r0 + band / 2
            val thick = band * 0.78f
            for (w in 0 until 52) {
                val week = y * 52 + w
                val s = state(week)
                if (s < 0) continue
                val c = when (s) {
                    2 -> if (y == selected || selected < 0) color.copy(alpha = 0.92f) else color.copy(alpha = 0.55f)
                    1 -> p.wood.copy(alpha = 0.9f)
                    else -> p.woodLight.copy(alpha = 0.5f)
                }
                val start = -90f + w * (360f / 52) + gap / 2
                drawArc(
                    c, start, 360f / 52 - gap, false,
                    topLeft = Offset(cx - mid, cy - mid), size = Size(mid * 2, mid * 2), style = Stroke(thick),
                )
            }
            // The year's line between rings, darker like latewood.
            drawCircle(p.woodDark.copy(alpha = if (y == selected) 0.9f else 0.45f), r0 + band, Offset(cx, cy), style = Stroke(if (y == selected) 3f else 1.4f))
        }
        // This week: a pencil tick on the outside.
        if (current >= 0) {
            val y = current / 52
            val w = current % 52
            val a = ((-90f + (w + 0.5f) * (360f / 52)) / 180f * PI).toFloat()
            val r = core + band * (y + 1) + 4f
            drawLine(p.pencil, Offset(cx + cos(a) * r, cy + sin(a) * r), Offset(cx + cos(a) * (r + 16f), cy + sin(a) * (r + 16f)), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}
