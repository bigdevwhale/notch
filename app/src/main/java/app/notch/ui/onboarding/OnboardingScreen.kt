package app.notch.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notch.R
import app.notch.data.Child
import app.notch.ui.child.ChildForm
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.jamb
import app.notch.ui.components.notch
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.theme.crayon
import java.time.LocalDate

/** Two pages of why, then the first child. */
@Composable
fun OnboardingScreen(onDone: (Child) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        if (page < 2) {
            Column(Modifier.weight(1f).padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
                Frame(page)
                Spacer(Modifier.height(30.dp))
                Text(stringResource(if (page == 0) R.string.welcome_1_title else R.string.welcome_2_title), style = UType.Display)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(if (page == 0) R.string.welcome_1_text else R.string.welcome_2_text), style = UType.Sub.copy(fontSize = 16.sp, lineHeight = 24.sp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i -> Box(Modifier.padding(4.dp).size(if (i == page) 9.dp else 7.dp).clip(CircleShape).background(if (i == page) U.c.accent else U.c.line)) }
            }
            PrimaryButton(stringResource(R.string.next), { page++ }, Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp))
        } else {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.welcome_3_title), style = UType.Display)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.welcome_3_text), style = UType.Sub)
            }
            ChildForm(null, 0, LocalDate.now(), Modifier.padding(horizontal = 28.dp), button = stringResource(R.string.welcome_start), onSave = onDone)
        }
    }
}

/** A door frame with its pencil marks; the second page adds the ones still to come, faint. */
@Composable
private fun Frame(page: Int) {
    val p = U.c
    val marks = listOf("1", "2", "3", "4", "5")
    Row(Modifier.fillMaxWidth().height(260.dp), verticalAlignment = Alignment.Bottom) {
        Canvas(Modifier.width(56.dp).height(260.dp)) {
            jamb(p, 3, size.width)
            marks.forEachIndexed { i, _ ->
                val y = size.height * (0.88f - i * 0.17f)
                val faint = page == 1 && i >= 3
                notch(p, y, size.width, if (faint) p.pencil.copy(alpha = 0.25f) else crayon(i % 2), long = true)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.height(260.dp)) {
            marks.indices.reversed().forEach { i ->
                val faint = page == 1 && i >= 3
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Text(
                        stringResource(R.string.welcome_mark, i + 1), style = UType.Hand.copy(fontSize = 24.sp, color = if (faint) p.faint else p.pencil, textAlign = TextAlign.Start),
                    )
                }
            }
        }
    }
}
