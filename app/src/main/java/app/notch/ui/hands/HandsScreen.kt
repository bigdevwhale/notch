package app.notch.ui.hands

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.notch.R
import app.notch.container
import app.notch.data.Kind
import app.notch.data.Moment
import app.notch.data.Vault
import app.notch.make.HandTrace
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.SoftButton
import app.notch.ui.components.TopBar
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands, traced on a sheet of paper twice a year and laid one inside the other: the small one from
 * when they were two, the bigger ones round it. The scale comes from the A4 sheet.
 */
@Composable
fun HandsScreen(childId: Long, house: House, go: Go) {
    val c = ctx()
    val container = c.container
    val child = house.child(childId) ?: return
    val hands = house.of(child.id).filter { it.kindOf == Kind.HAND && it.shape.isNotEmpty() }.sortedBy { it.takenAt }
    var target by rememberSaveable { mutableStateOf("") }
    var shot by rememberSaveable { mutableStateOf("") }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = File(target)
        if (ok && f.exists() && f.length() > 0) shot = f.path else f.delete()
    }
    fun open() {
        val f = container.vault.capture("jpg")
        target = f.path
        runCatching { take.launch(FileProvider.getUriForFile(c, c.packageName + ".files", f)) }
            .onFailure { android.widget.Toast.makeText(c, R.string.no_camera, android.widget.Toast.LENGTH_LONG).show() }
    }

    if (shot.isNotEmpty()) {
        TraceScreen(File(shot), child.name, onCancel = { File(shot).delete(); shot = "" }) { outline ->
            val f = File(shot)
            container.write {
                val name = vault.adoptFile(f, "jpg")
                db.dao().insert(
                    Moment(
                        kids = Moment.kidsOf(listOf(child.id)), file = name, kind = Kind.HAND.name, takenAt = System.currentTimeMillis(),
                        shape = outline.encode(), measure = (outline.lengthMm * 10).toInt(),
                    )
                )
            }
            shot = ""
        }
        return
    }

    val color = crayon(child.crayon)
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.hands_title), go::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.hands_sub, child.name), style = UType.Sub)
            Spacer(Modifier.height(14.dp))
            if (hands.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(U.RMd).background(U.c.paper)) {
                    Nested(hands.map { HandTrace.decode(it.shape) }, color, Modifier.fillMaxSize().padding(16.dp))
                }
                Spacer(Modifier.height(12.dp))
                hands.asReversed().forEachIndexed { i, m ->
                    val k = hands.size - 1 - i
                    Row(Modifier.fillMaxWidth().clickable { go.moment(m.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(shade(color, k, hands.size)))
                        Spacer(Modifier.width(12.dp))
                        Text(T.ageShortAt(c, child, m.takenAt), style = UType.Hand.copy(fontSize = 22.sp), modifier = Modifier.width(110.dp))
                        Text(T.mm(c, m.measure / 10f), style = UType.Strong, modifier = Modifier.weight(1f))
                        Text(T.date(c, m.takenAt), style = UType.Small)
                    }
                }
                if (hands.size >= 2) {
                    val grew = (hands.last().measure - hands.first().measure) / 10f
                    if (grew > 0) Text(stringResource(R.string.hands_grew, T.mm(c, grew)), style = UType.Name.copy(color = color), modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                Text(stringResource(R.string.hands_none_long), style = UType.Body)
            }
            Spacer(Modifier.height(16.dp))
            HowTo()
            Spacer(Modifier.height(14.dp))
            PrimaryButton(stringResource(R.string.hands_take), ::open, Modifier.fillMaxWidth(), icon = Icons.Rounded.CameraAlt)
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun shade(c: Color, i: Int, n: Int): Color = lerp(c.copy(alpha = 0.35f), c, if (n <= 1) 1f else i / (n - 1f))

@Composable
private fun HowTo() {
    Column(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.woodLight.copy(alpha = 0.45f)).padding(14.dp)) {
        listOf(R.string.hands_how_1, R.string.hands_how_2, R.string.hands_how_3).forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}.", style = UType.Strong.copy(color = U.c.woodDark), modifier = Modifier.width(22.dp))
                Text(stringResource(s), style = UType.Body)
            }
        }
    }
}

/** Outlines one inside the other, all to the same scale, wrists together at the bottom. */
@Composable
fun Nested(shapes: List<List<Pair<Float, Float>>>, color: Color, modifier: Modifier = Modifier) {
    val pencil = U.c.pencil
    Canvas(modifier) {
        val all = shapes.flatten()
        if (all.isEmpty()) return@Canvas
        val w = (all.maxOf { kotlin.math.abs(it.first) } * 2).coerceAtLeast(40f)
        val h = (-all.minOf { it.second }).coerceAtLeast(40f)
        val k = minOf(size.width / w, size.height / h) * 0.94f
        val ox = size.width / 2
        val oy = size.height - 4f
        shapes.forEachIndexed { i, s ->
            if (s.size < 3) return@forEachIndexed
            val path = Path().apply {
                s.forEachIndexed { j, (x, y) -> if (j == 0) moveTo(ox + x * k, oy + y * k) else lineTo(ox + x * k, oy + y * k) }
            }
            val last = i == shapes.size - 1
            drawPath(path, shade(color, i, shapes.size).copy(alpha = if (last) 0.12f else 0.05f))
            drawPath(path, if (last) pencil else shade(color, i, shapes.size), style = Stroke(if (last) 4f else 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * The photo with the found outline over it. If the sheet or the hand couldn't be found, the parent
 * draws round the hand with a finger instead.
 */
@Composable
private fun TraceScreen(file: File, name: String, onCancel: () -> Unit, onSave: (HandTrace.Outline) -> Unit) {
    val c = ctx()
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var found by remember { mutableStateOf<HandTrace.Found?>(null) }
    var looked by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    val drawn = remember { mutableStateListOf<Offset>() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(file) {
        val b = withContext(Dispatchers.IO) { Vault.decodeUpright(file, 900) }
        picture = b
        if (b != null) found = withContext(Dispatchers.Default) {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            HandTrace.find(px, b.width, b.height)
        }
        looked = true
        if (found == null) drawing = true
    }
    val pencil = U.c.accent
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.hands_trace_title, name), onCancel)
        val b = picture
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            if (b != null) {
                Box(Modifier.aspectRatio(b.width.toFloat() / b.height).onSizeChanged { box = it }) {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Canvas(Modifier.fillMaxSize().then(if (drawing) Modifier.pointerInput(Unit) {
                        detectDragGestures(onDragStart = { drawn.clear(); drawn += it }) { ch, _ -> drawn += ch.position }
                    } else Modifier)) {
                        val k = size.width / b.width
                        val pts = if (drawing) drawn.toList() else found?.pixels?.map { Offset(it.first * k, it.second * k) }.orEmpty()
                        if (pts.size > 1) {
                            val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                            drawPath(path, pencil, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            } else Text(stringResource(R.string.hands_looking), style = UType.Sub)
        }
        Column(Modifier.padding(20.dp)) {
            val f = found
            when {
                !looked -> Text(stringResource(R.string.hands_looking), style = UType.Sub)
                drawing -> Text(stringResource(if (f == null) R.string.hands_not_found else R.string.hands_draw), style = UType.Sub)
                f != null -> Text(stringResource(R.string.hands_found, T.mm(c, f.outline.lengthMm)), style = UType.Name)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!drawing) SoftButton(stringResource(R.string.hands_redraw), { drawing = true; drawn.clear() }, icon = Icons.Rounded.Gesture)
                PrimaryButton(stringResource(R.string.save), {
                    val outline = if (drawing) {
                        val k = if (box.width > 0 && b != null) b.width.toFloat() / box.width else 1f
                        HandTrace.fromDrawing(drawn.map { it.x * k to it.y * k }, b?.width ?: 1, found?.mmPerPx)
                    } else f?.outline
                    if (outline != null) onSave(outline)
                }, Modifier.weight(1f), enabled = looked && (if (drawing) drawn.size > 8 else f != null))
            }
        }
    }
}
