package app.notch.make

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Finds a hand laid on a sheet of A4 in a photo and traces round it, the way a pencil would. The sheet
 * is the big pale shape; the hand is the dark one on it, wrist at the bottom edge. The sheet's size
 * gives the scale, so a hand traced at three and again at six can be laid one over the other.
 *
 * Works on plain ARGB pixels so it can be tried without a phone.
 */
object HandTrace {
    /** An outline in millimetres: wrist centre at the origin, fingers up (negative y). */
    class Outline(val points: List<Pair<Float, Float>>, val lengthMm: Float, val widthMm: Float) {
        fun encode(): String = points.joinToString(";") { "%.1f,%.1f".format(java.util.Locale.US, it.first, it.second) }
    }

    /** Where it was found in the picture, for drawing the trace over the photo. */
    class Found(val outline: Outline, val pixels: List<Pair<Int, Int>>, val mmPerPx: Float, val wristX: Float, val wristY: Float)

    fun decode(s: String): List<Pair<Float, Float>> = s.split(';').mapNotNull { p ->
        val xy = p.split(',')
        if (xy.size != 2) null else xy[0].toFloatOrNull()?.let { x -> xy[1].toFloatOrNull()?.let { y -> x to y } }
    }

    const val A4_SHORT = 210f
    const val A4_LONG = 297f

    fun find(px: IntArray, w: Int, h: Int): Found? {
        val n = w * h
        val lum = IntArray(n) { val c = px[it]; ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 }
        // First the light things from the dark table; the hand may still be among the light ones.
        val t1 = otsu(lum)
        val rough = largest(BooleanArray(n) { lum[it] > t1 }, w, h) ?: return null
        if (rough.size < n * 0.12) return null
        // Then, among those, the paper from the hand: the paper is the lightest.
        val light = IntArray(rough.size) { lum[rough[it]] }
        val t2 = otsu(light)
        val lo = light.filter { it <= t2 }
        val hi = light.filter { it > t2 }
        // A sheet with no hand on it splits into nothing worth the name: keep the first cut then.
        val split = lo.size > light.size / 50 && hi.isNotEmpty() && hi.average() - lo.average() > 30
        val t = if (split) maxOf(t1, t2) else t1
        val bright = BooleanArray(n) { lum[it] > t }
        val sheet = largest(bright, w, h) ?: return null
        if (sheet.size < n * 0.12) return null
        val hull = hull(sheet.map { (it % w) to (it / w) })
        if (hull.size < 3) return null
        // The sheet's corners and so its size in pixels.
        val tl = hull.minBy { it.first + it.second }
        val br = hull.maxBy { it.first + it.second }
        val tr = hull.maxBy { it.first - it.second }
        val bl = hull.minBy { it.first - it.second }
        fun d(a: Pair<Int, Int>, b: Pair<Int, Int>) = hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble()).toFloat()
        val wide = (d(tl, tr) + d(bl, br)) / 2
        val tall = (d(tl, bl) + d(tr, br)) / 2
        val short = min(wide, tall)
        val long = max(wide, tall)
        if (short < 20) return null
        val mmPerPx = (A4_SHORT / short + A4_LONG / long) / 2

        // Not-bright pixels inside the sheet: the hand, cut off where the wrist leaves the paper.
        val inside = BooleanArray(n)
        val minY = hull.minOf { it.second }
        val maxY = hull.maxOf { it.second }
        for (y in minY..maxY) {
            val (a, b) = span(hull, y) ?: continue
            for (x in max(0, a)..min(w - 1, b)) {
                val i = y * w + x
                inside[i] = !bright[i]
            }
        }
        // A pixel's margin round the sheet's edge, where its shadow and border live.
        val hand = largest(erodeEdge(inside, bright, w, h, keepBelow = maxY - (maxY - minY) * 0.1f), w, h) ?: return null
        if (hand.size < sheet.size * 0.02) return null
        val mask = BooleanArray(n).also { m -> hand.forEach { m[it] = true } }
        fillHoles(mask, w, h)
        val contour = trace(mask, w, h)
        if (contour.size < 30) return null

        val hy = contour.maxOf { it.second }
        val bottom = contour.filter { it.second >= hy - 3 }
        val wx = bottom.map { it.first }.average().toFloat()
        val wy = hy.toFloat()
        val top = contour.minOf { it.second }
        val left = contour.minOf { it.first }
        val right = contour.maxOf { it.first }
        val step = max(1, contour.size / 260)
        val pts = smooth(contour.filterIndexed { i, _ -> i % step == 0 }).map { (x, y) -> (x - wx) * mmPerPx to (y - wy) * mmPerPx }
        return Found(Outline(pts, (wy - top) * mmPerPx, (right - left) * mmPerPx), contour, mmPerPx, wx, wy)
    }

    /** Otsu's threshold: the split that best separates light from dark. */
    fun otsu(lum: IntArray): Int {
        val hist = IntArray(256)
        lum.forEach { hist[it.coerceIn(0, 255)]++ }
        val total = lum.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0.0
        var best = 0.0
        var t = 127
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i }
        }
        return t
    }

    /** The biggest 4-connected blob of true pixels, as indices. */
    fun largest(m: BooleanArray, w: Int, h: Int): IntArray? {
        val seen = BooleanArray(m.size)
        var best: IntArray? = null
        val stack = IntArray(m.size)
        val buf = IntArray(m.size)
        for (s in m.indices) {
            if (!m[s] || seen[s]) continue
            var sp = 0
            var count = 0
            stack[sp++] = s
            seen[s] = true
            while (sp > 0) {
                val i = stack[--sp]
                buf[count++] = i
                val x = i % w
                val y = i / w
                if (x > 0 && m[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && m[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && m[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && m[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (count > (best?.size ?: 0)) best = buf.copyOf(count)
        }
        return best
    }

    /** Drops dark pixels that touch the outside of the sheet within a couple of pixels: its rim and shadow. */
    private fun erodeEdge(inside: BooleanArray, bright: BooleanArray, w: Int, h: Int, keepBelow: Float): BooleanArray {
        val out = inside.copyOf()
        val r = 2
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!inside[i]) continue
            var outside = false
            loop@ for (dy in -r..r) for (dx in -r..r) {
                val xx = x + dx
                val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) { outside = true; break@loop }
                val j = yy * w + xx
                if (!inside[j] && !bright[j]) { outside = true; break@loop }
            }
            // The wrist leaves through the bottom edge: keep it there so the hand stays whole.
            if (outside && y < keepBelow) out[i] = false
        }
        return out
    }

    /** Fills what the hand surrounds (a ring, a gap between pressed fingers) so only its outer edge is traced. */
    private fun fillHoles(m: BooleanArray, w: Int, h: Int) {
        val outside = BooleanArray(m.size)
        val stack = ArrayDeque<Int>()
        for (x in 0 until w) { stack += x; stack += (h - 1) * w + x }
        for (y in 0 until h) { stack += y * w; stack += y * w + w - 1 }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (i < 0 || i >= m.size || m[i] || outside[i]) continue
            outside[i] = true
            val x = i % w
            if (x > 0) stack += i - 1
            if (x < w - 1) stack += i + 1
            stack += i - w
            stack += i + w
        }
        for (i in m.indices) if (!m[i] && !outside[i]) m[i] = true
    }

    /** Walks round the blob's edge (Moore neighbourhood), clockwise, from its top-left pixel. */
    fun trace(m: BooleanArray, w: Int, h: Int): List<Pair<Int, Int>> {
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        val start = m.indices.firstOrNull { m[it] } ?: return emptyList()
        val sx = start % w
        val sy = start / w
        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val out = mutableListOf(sx to sy)
        var x = sx
        var y = sy
        var dir = 7
        var guard = 0
        while (guard++ < m.size) {
            var found = false
            for (k in 0 until 8) {
                val nd = (dir + 6 + k) % 8
                val nx = x + dx[nd]
                val ny = y + dy[nd]
                if (on(nx, ny)) {
                    x = nx; y = ny; dir = nd; found = true
                    break
                }
            }
            if (!found || (x == sx && y == sy)) break
            out += x to y
        }
        return out
    }

    private fun smooth(p: List<Pair<Int, Int>>): List<Pair<Float, Float>> {
        if (p.size < 5) return p.map { it.first.toFloat() to it.second.toFloat() }
        return p.indices.map { i ->
            var sx = 0f
            var sy = 0f
            for (k in -2..2) {
                val q = p[(i + k + p.size) % p.size]
                sx += q.first; sy += q.second
            }
            sx / 5 to sy / 5
        }
    }

    /** Convex hull (monotone chain). */
    fun hull(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        // Only each row's ends can be on the hull: far fewer points to sort.
        val rows = points.groupBy { it.second }.flatMap { (_, l) -> listOf(l.minBy { it.first }, l.maxBy { it.first }) }.distinct()
        val p = rows.sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Int, Int>, a: Pair<Int, Int>, b: Pair<Int, Int>) =
            (a.first - o.first).toLong() * (b.second - o.second) - (a.second - o.second).toLong() * (b.first - o.first)
        val lower = mutableListOf<Pair<Int, Int>>()
        for (q in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), q) <= 0) lower.removeAt(lower.size - 1)
            lower += q
        }
        val upper = mutableListOf<Pair<Int, Int>>()
        for (q in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), q) <= 0) upper.removeAt(upper.size - 1)
            upper += q
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Where a convex polygon crosses row [y]: its left and right x. */
    private fun span(poly: List<Pair<Int, Int>>, y: Int): Pair<Int, Int>? {
        var a = Int.MAX_VALUE
        var b = Int.MIN_VALUE
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            if ((p.second <= y && q.second >= y) || (q.second <= y && p.second >= y)) {
                val x = if (p.second == q.second) min(p.first, q.first).toFloat()
                else p.first + (y - p.second).toFloat() / (q.second - p.second) * (q.first - p.first)
                val x2 = if (p.second == q.second) max(p.first, q.first).toFloat() else x
                a = min(a, x.toInt()); b = max(b, x2.toInt())
            }
        }
        return if (a <= b) a to b else null
    }

    /**
     * An outline drawn by hand over the photo instead (when the sheet can't be found): [pixels] in the
     * picture, with the scale from the sheet if one was found, else as if the picture were a sheet wide.
     */
    fun fromDrawing(pixels: List<Pair<Float, Float>>, pictureWidth: Int, mmPerPx: Float?): Outline? {
        if (pixels.size < 8) return null
        val k = mmPerPx ?: (A4_SHORT / pictureWidth)
        val wy = pixels.maxOf { it.second }
        val bottom = pixels.filter { wy - it.second < 8 }
        val wx = bottom.map { it.first }.average().toFloat()
        val pts = pixels.map { (x, y) -> (x - wx) * k to (y - wy) * k }
        val top = pixels.minOf { it.second }
        return Outline(pts, (wy - top) * k, (pixels.maxOf { it.first } - pixels.minOf { it.first }) * k)
    }
}
