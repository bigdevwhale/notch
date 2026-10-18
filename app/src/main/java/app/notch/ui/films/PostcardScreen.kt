package app.notch.ui.films

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notch.R
import app.notch.container
import app.notch.data.Kind
import app.notch.domain.Growth
import app.notch.make.Postcard
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.SoftButton
import app.notch.ui.components.TopBar
import app.notch.ui.ctx
import app.notch.ui.moment.share
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** The month's postcard for the family chat: made here, sent with one tap. */
@Composable
fun PostcardScreen(childId: Long, start: LocalDate, house: House, go: Go) {
    val c = ctx()
    val container = c.container
    var month by remember { mutableStateOf(start.withDayOfMonth(1)) }
    var shuffle by remember { mutableIntStateOf(0) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    val inMonth = house.moments.filter { Growth.date(it.takenAt, house.zone).let { d -> d.year == month.year && d.month == month.month } }
    val photos = inMonth.filter { it.kindOf == Kind.PHOTO }
    val kids = house.children.filter { k -> inMonth.any { it.of(k.id) } }.ifEmpty { listOfNotNull(house.child(childId)) }

    LaunchedEffect(month, shuffle, photos.size) {
        card = null
        if (photos.isEmpty()) return@LaunchedEffect
        val end = month.plusMonths(1).minusDays(1)
        // Favourites first, then spread through the month; a shuffle picks others.
        val pool = photos.sortedWith(compareByDescending<app.notch.data.Moment> { it.favorite }.thenBy { it.takenAt })
        val pick = if (shuffle == 0) (pool.filter { it.favorite } + pool.filter { !it.favorite }.let { l -> List(minOf(4, l.size)) { i -> l[i * l.size / minOf(4, l.size).coerceAtLeast(1)] } }).distinctBy { it.file }.take(4)
        else pool.shuffled(kotlin.random.Random(shuffle)).distinctBy { it.file }.take(4)
        val word = house.words.filter { w -> kids.any { it.id == w.childId } && Growth.date(w.at, house.zone).let { it.year == month.year && it.month == month.month } }
            .maxByOrNull { it.at }
        val lines = kids.map { k ->
            val h = house.marksOf(k.id).lastOrNull { !Growth.date(it.at, house.zone).isAfter(end) }
            k.name + " — " + T.age(c, Growth.age(k.birthday, end)) + (h?.let { ", " + T.cm(c, it.cm) } ?: "")
        }
        val bmp = withContext(Dispatchers.Default) {
            val pics = pick.mapNotNull { container.vault.full(it.file, 1200) }
            Postcard.draw(
                c,
                Postcard.Content(
                    title = T.month(c, month), lines = lines, photos = pics,
                    word = word?.let { it.said to it.means }, footer = c.getString(R.string.card_footer), seed = month.hashCode() + shuffle,
                ),
            ).also { pics.forEach(Bitmap::recycle) }
        }
        val f = File(c.cacheDir, "share").apply { mkdirs() }.let { File(it, "card-$month.jpg") }
        withContext(Dispatchers.IO) { FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
        card = bmp
        file = f
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.card_title), go::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1); shuffle = 0 }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = U.c.ink) }
                Text(T.month(c, month), style = UType.Name, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (month.isBefore(house.today.withDayOfMonth(1))) { month = month.plusMonths(1); shuffle = 0 } }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = if (month.isBefore(house.today.withDayOfMonth(1))) U.c.ink else U.c.line)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(Postcard.W.toFloat() / Postcard.H).shadow(6.dp, U.RSm).clip(U.RSm).background(U.c.paper), contentAlignment = Alignment.Center) {
                val b = card
                when {
                    photos.isEmpty() -> Text(stringResource(R.string.card_empty), style = UType.Sub, modifier = Modifier.padding(30.dp))
                    b == null -> Text(stringResource(R.string.card_making), style = UType.Sub)
                    else -> Image(b.asImageBitmap(), null, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(16.dp))
            if (card != null) {
                PrimaryButton(stringResource(R.string.card_send), { file?.let { share(c, it, video = false) } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Send)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(stringResource(R.string.card_other), { shuffle++ }, Modifier.weight(1f), icon = Icons.Rounded.Shuffle)
                    SoftButton(stringResource(R.string.save_gallery), { file?.let { Gallery.save(c, it, video = false) } }, Modifier.weight(1f), icon = Icons.Rounded.Download)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
