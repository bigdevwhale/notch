package app.notch.ui.shoot

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.notch.R
import app.notch.container
import app.notch.data.Child
import app.notch.data.Moment
import app.notch.data.Pickup
import app.notch.domain.Fading
import app.notch.domain.Growth
import app.notch.ui.Ghost
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.Face
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.SoftButton
import app.notch.ui.components.Thumb
import app.notch.ui.components.rememberPicture
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the next trip to the camera is for. */
data class ShootRequest(
    val kids: List<Long> = emptyList(),
    val fading: String = "",
    val ghost: Long = 0,
    val seconds: Boolean = false,
)

/**
 * Before the camera opens: who is in the picture, and whether to stand them where they stood before
 * (the ghost). Then the phone's own camera does the rest; or pictures come in from the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootSheet(start: ShootRequest, house: House, child: Child?, onDismiss: () -> Unit, onShoot: (ShootRequest) -> Unit) {
    val c = ctx()
    val container = c.container
    var r by remember { mutableStateOf(if (start.kids.isEmpty() && child != null) start.copy(kids = listOf(child.id)) else start) }
    var imported by remember { mutableIntStateOf(-1) }
    var overlayOk by remember { mutableStateOf(Ghost.allowed(c)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlayOk = Ghost.allowed(c) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(200)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        container.scope.launch {
            val n = withContext(Dispatchers.IO) { Pickup.import(c, container.db, container.vault, uris, Moment.kidsOf(r.kids)) }
            imported = n
            container.changed()
        }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = U.c.bg) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(if (r.seconds) R.string.shoot_second_title else R.string.shoot_title), style = UType.Title)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.shoot_who), style = UType.Sub)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                house.children.forEach { k ->
                    val on = k.id in r.kids
                    Column(
                        Modifier.clip(U.RMd).clickable {
                            val next = if (on) r.kids - k.id else r.kids + k.id
                            if (next.isNotEmpty()) r = r.copy(kids = next, ghost = 0)
                        }.padding(6.dp).alpha(if (on) 1f else 0.45f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Face(k, house.faceOf(k), 54.dp, ring = on)
                        Spacer(Modifier.height(4.dp))
                        Text(k.name, style = UType.Small.copy(color = if (on) crayon(k.crayon) else U.c.faint))
                    }
                }
            }
            Fading.byKey(r.fading)?.let { f ->
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.shoot_for, c.getString(f.title)), style = UType.Hand.copy(fontSize = 24.sp), modifier = Modifier.clip(U.RSm).background(U.c.accentSoft).padding(horizontal = 12.dp, vertical = 6.dp))
            }

            // The ghost: the same spot as before.
            val one = r.kids.singleOrNull()?.let { house.child(it) }
            val ghosts = remember(house.moments, r.kids) {
                if (one == null || r.seconds) emptyList() else buildList {
                    Growth.yearAgo(one, house.moments, house.today)?.let { add(it to c.getString(R.string.ghost_year_ago)) }
                    house.series.forEach { s ->
                        val l = house.inSeries(s.id).filter { it.of(one.id) }
                        l.lastOrNull()?.let { m -> if (none { it.first.id == m.id }) add(m to (s.name.ifBlank { T.day(c, l.first().takenAt) })) }
                    }
                }
            }
            if (ghosts.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Layers, null, tint = U.c.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ghost_title).uppercase(), style = UType.Eyebrow)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ghost_sub), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ghosts.forEach { (m, label) ->
                        val on = r.ghost == m.id
                        Column(Modifier.width(92.dp).clickable { r = r.copy(ghost = if (on) 0 else m.id) }) {
                            Thumb(m, Modifier.fillMaxWidth().aspectRatio(0.75f).then(if (on) Modifier.border(3.dp, U.c.accent, RoundedCornerShape(10.dp)) else Modifier))
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = UType.Small.copy(color = if (on) U.c.accent else U.c.muted), maxLines = 1)
                        }
                    }
                }
                if (r.ghost > 0) {
                    Spacer(Modifier.height(12.dp))
                    if (overlayOk) Text(stringResource(R.string.ghost_on_camera), style = UType.Muted)
                    else {
                        // Without the overlay, a good look at it before the camera opens.
                        val b = rememberPicture(house.moment(r.ghost)?.file.orEmpty(), full = true, max = 900)
                        b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp).clip(U.RMd), contentScale = ContentScale.Fit) }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ghost_no_overlay), style = UType.Muted)
                        Spacer(Modifier.height(8.dp))
                        SoftButton(stringResource(R.string.ghost_allow), { runCatching { c.startActivity(Ghost.askIntent(c)) } }, Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                stringResource(if (r.seconds) R.string.shoot_second_open else R.string.shoot_open), { onShoot(r) }, Modifier.fillMaxWidth(),
                enabled = r.kids.isNotEmpty(), icon = if (r.seconds) Icons.Rounded.Timer else Icons.Rounded.CameraAlt,
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(if (r.seconds) R.string.shoot_second_hint else R.string.shoot_hint), style = UType.Small, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!r.seconds) SoftButton(stringResource(R.string.shoot_second), { r = r.copy(seconds = true, ghost = 0) }, Modifier.weight(1f), icon = Icons.Rounded.Timer)
                else SoftButton(stringResource(R.string.shoot_photos), { r = r.copy(seconds = false) }, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt)
                SoftButton(
                    stringResource(R.string.shoot_gallery), { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    Modifier.weight(1f), color = U.c.woodDark, soft = U.c.woodLight.copy(alpha = 0.6f), icon = Icons.Rounded.PhotoLibrary,
                )
            }
            if (imported >= 0) {
                LaunchedEffect(imported) { if (imported > 0) { kotlinx.coroutines.delay(1200); onDismiss() } }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (imported == 0) stringResource(R.string.import_none) else stringResource(R.string.import_done, T.plural(c, R.plurals.pictures, imported)),
                    style = UType.Body.copy(color = U.c.accent),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
