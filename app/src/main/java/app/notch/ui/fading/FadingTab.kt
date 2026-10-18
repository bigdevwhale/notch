package app.notch.ui.fading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notch.R
import app.notch.data.Child
import app.notch.domain.Fade
import app.notch.domain.FadeState
import app.notch.domain.Fading
import app.notch.domain.Growth
import app.notch.domain.Keep
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.ChildBar
import app.notch.ui.components.SectionTitle
import app.notch.ui.components.Thumb
import app.notch.ui.ctx
import app.notch.ui.shoot.ShootRequest
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import app.notch.ui.words.WordDialog

/** "It will soon be gone": the things of this age that leave without anyone noticing, and what was kept of them. */
@Composable
fun FadingTab(house: House, child: Child, go: Go, onPick: (Long) -> Unit) {
    val c = ctx()
    val months = Growth.age(child.birthday, house.today).totalMonths
    val moments = house.of(child.id)
    val words = house.wordsOf(child.id)
    var voice by remember { mutableStateOf<Fade?>(null) }
    var showPassed by rememberSaveable { mutableStateOf(false) }
    val states = Fading.all.map { f ->
        val kept = moments.any { it.fading == f.key } || words.any { it.fading == f.key }
        f to Fading.state(f, months, kept)
    }
    val now = states.filter { it.second == FadeState.NOW }.map { it.first }.sortedBy { it.to }
    val kept = states.filter { it.second == FadeState.KEPT }.map { it.first }
    val passed = states.filter { it.second == FadeState.PASSED }.map { it.first }
    val later = states.filter { it.second == FadeState.LATER }.map { it.first }.sortedBy { it.from }.take(3)
    val color = crayon(child.crayon)

    fun keep(f: Fade) {
        if (f.keep == Keep.VOICE) voice = f else go.shoot(ShootRequest(kids = listOf(child.id), fading = f.key))
    }

    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        ChildBar(house, child, go, onPick)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            item {
                Text(stringResource(R.string.fading_title), style = UType.Display, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.fading_sub, child.name), style = UType.Sub)
            }
            if (now.isNotEmpty()) item { SectionTitle(stringResource(R.string.fading_now)) }
            items(now, key = { "n" + it.key }) { f ->
                Item(f, onClick = { keep(f) }) {
                    val left = Fading.left(f, months)
                    Text(
                        if (left == 0) stringResource(R.string.fading_last_month) else stringResource(R.string.fading_left, T.plural(c, R.plurals.months_left, left)),
                        style = UType.Small.copy(color = if (left <= 2) U.c.accent else U.c.faint),
                    )
                }
            }
            if (later.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.fading_later)) }
                items(later, key = { "l" + it.key }) { f ->
                    Item(f, Modifier.alpha(0.6f), onClick = { keep(f) }) {
                        Text(stringResource(R.string.fading_from, T.plural(c, R.plurals.months, f.from)), style = UType.Small)
                    }
                }
            }
            if (kept.isNotEmpty()) item { SectionTitle(stringResource(R.string.fading_kept, kept.size)) }
            items(kept, key = { "k" + it.key }) { f ->
                val m = moments.firstOrNull { it.fading == f.key }
                val w = words.firstOrNull { it.fading == f.key }
                Item(f, onClick = { if (m != null) go.moment(m.id) else voice = f }, trailing = {
                    if (m != null) Thumb(m, Modifier.size(48.dp, 60.dp), showLength = false)
                    else if (w != null) Text("«${w.said}»", style = UType.Hand.copy(fontSize = 20.sp, color = color), maxLines = 1, modifier = Modifier.width(110.dp))
                }) {
                    (m?.takenAt ?: w?.at)?.let { Text(stringResource(R.string.fading_kept_at, T.ageShortAt(c, child, it)), style = UType.Small.copy(color = color)) }
                }
            }
            if (passed.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.fading_passed, passed.size), Modifier.clickable { showPassed = !showPassed })
                    if (!showPassed) Text(stringResource(R.string.fading_passed_sub), style = UType.Muted, modifier = Modifier.clickable { showPassed = true })
                }
                if (showPassed) items(passed, key = { "p" + it.key }) { f ->
                    Item(f, Modifier.alpha(0.55f), onClick = { go.shoot(ShootRequest(kids = listOf(child.id), fading = f.key)) }) {
                        Text(stringResource(R.string.fading_passed_hint), style = UType.Small)
                    }
                }
            }
        }
    }
    voice?.let { f ->
        WordDialog(child, words.firstOrNull { it.fading == f.key }, f.key, onDismiss = { voice = null })
    }
}

@Composable
private fun Item(f: Fade, modifier: Modifier = Modifier, onClick: () -> Unit, trailing: @Composable () -> Unit = {}, sub: @Composable () -> Unit) {
    val icon = when (f.keep) { Keep.PHOTO -> Icons.Rounded.CameraAlt; Keep.VIDEO -> Icons.Rounded.Videocam; Keep.VOICE -> Icons.Rounded.Mic }
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp).clip(U.RMd).background(U.c.surface).border(1.dp, U.c.line, U.RMd).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(U.c.woodLight.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = U.c.woodDark)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(f.title), style = UType.Body)
            sub()
        }
        trailing()
    }
}
