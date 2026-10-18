package app.notch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notch.R
import app.notch.data.Child
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon

/** The children along the top of every tab: tap to switch, + to add one, the gear for settings. */
@Composable
fun ChildBar(house: House, child: Child, go: Go, onPick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            house.children.forEach { k ->
                val on = k.id == child.id
                val color = crayon(k.crayon)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) color.copy(alpha = 0.14f) else Color.Transparent)
                        .border(1.dp, if (on) color.copy(alpha = 0.5f) else U.c.line, RoundedCornerShape(50))
                        .clickable { if (on) go.child(k.id) else onPick(k.id) }
                        .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Face(k, house.faceOf(k), 32.dp, ring = on)
                    Spacer(Modifier.width(8.dp))
                    Text(k.name, style = UType.Strong.copy(color = if (on) U.c.ink else U.c.muted), maxLines = 1)
                }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).border(1.dp, U.c.line, CircleShape).clickable { go.child(0) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_child), tint = U.c.muted) }
        }
        IconButton(onClick = { go.settings() }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = U.c.muted) }
    }
}
