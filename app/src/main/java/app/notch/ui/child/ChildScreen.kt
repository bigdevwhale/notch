package app.notch.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notch.R
import app.notch.container
import app.notch.data.Child
import app.notch.domain.Growth
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.T
import app.notch.ui.components.ConfirmDialog
import app.notch.ui.components.InfoRow
import app.notch.ui.components.PrimaryButton
import app.notch.ui.components.Segmented
import app.notch.ui.components.TopBar
import app.notch.ui.components.UTextField
import app.notch.ui.ctx
import app.notch.ui.theme.Crayons
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import java.time.LocalDate

/** A child's name, birthday and crayon: adding one, or changing them. */
@Composable
fun ChildScreen(id: Long, house: House, go: Go, onPick: (Long) -> Unit) {
    val c = ctx()
    val container = c.container
    val child = house.child(id)
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(if (child == null) stringResource(R.string.child_new) else child.name, if (house.children.isEmpty()) null else go::back) {
            if (child != null) TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.delete), color = U.c.muted) }
        }
        ChildForm(child, house.children.size, house.today, Modifier.padding(horizontal = 20.dp)) { k ->
            container.write {
                if (child == null) {
                    val newId = db.dao().insert(k)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onPick(newId) }
                } else db.dao().update(k)
            }
            go.back()
        }
    }
    if (deleting && child != null) ConfirmDialog(
        stringResource(R.string.child_delete_title, child.name), stringResource(R.string.child_delete_text), stringResource(R.string.delete),
        onConfirm = {
            container.write {
                val dao = db.dao()
                dao.moments().filter { it.of(child.id) }.forEach { m ->
                    val rest = m.kidIds - child.id
                    if (rest.isEmpty()) { dao.delete(m); vault.delete(m.file) } else dao.update(m.copy(kids = app.notch.data.Moment.kidsOf(rest)))
                }
                dao.words().filter { it.childId == child.id }.forEach { vault.delete(it.audio) }
                dao.deleteWordsOf(child.id)
                dao.deleteMarksOf(child.id)
                dao.delete(child)
            }
            go.home()
        },
        onDismiss = { deleting = false }, danger = true,
    )
}

/** The form itself, also the onboarding's last page. */
@Composable
fun ChildForm(child: Child?, count: Int, today: LocalDate, modifier: Modifier = Modifier, button: String = "", onSave: (Child) -> Unit) {
    val c = ctx()
    var name by remember { mutableStateOf(child?.name ?: "") }
    var born by remember { mutableLongStateOf(child?.born ?: today.minusYears(1).toEpochDay()) }
    var gender by remember { mutableStateOf(child?.gender ?: "") }
    var crayon by remember { mutableIntStateOf(child?.crayon ?: (count % Crayons.size)) }
    var picking by remember { mutableStateOf(false) }
    var bornSet by remember { mutableStateOf(child != null) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.child_name).uppercase(), style = UType.Eyebrow, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        UTextField(name, { name = it }, placeholder = stringResource(R.string.child_name_hint))
        Spacer(Modifier.height(10.dp))
        InfoRow(
            Icons.Rounded.Cake, stringResource(R.string.child_born),
            if (bornSet) T.date(c, LocalDate.ofEpochDay(born)) + " · " + T.age(c, Growth.age(LocalDate.ofEpochDay(born), today)) else stringResource(R.string.child_born_pick),
            onClick = { picking = true },
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.child_gender_q).uppercase(), style = UType.Eyebrow, modifier = Modifier.padding(bottom = 8.dp))
        Segmented(listOf("GIRL", "BOY", ""), gender, {
            stringResource(when (it) { "GIRL" -> R.string.child_girl; "BOY" -> R.string.child_boy; else -> R.string.child_none })
        }, { gender = it })
        Text(stringResource(R.string.child_gender_why), style = UType.Small, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.child_crayon).uppercase(), style = UType.Eyebrow, modifier = Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Crayons.forEachIndexed { i, col ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(col).then(if (i == crayon) Modifier.border(3.dp, U.c.ink.copy(alpha = 0.6f), CircleShape) else Modifier)
                        .clickable { crayon = i },
                    contentAlignment = Alignment.Center,
                ) { if (i == crayon) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.height(26.dp))
        PrimaryButton(
            button.ifEmpty { stringResource(R.string.save) },
            { onSave((child ?: Child(name = "", born = 0)).copy(name = name.trim(), born = born, gender = gender, crayon = crayon)) },
            Modifier.fillMaxWidth(), enabled = name.isNotBlank() && bornSet,
        )
        Spacer(Modifier.height(24.dp))
    }
    if (picking) BirthdayDialog(LocalDate.ofEpochDay(born), today, { picking = false }) { born = it.toEpochDay(); bornSet = true }
}

/** A date picker for the birthday: nothing in the future. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayDialog(initial: LocalDate, today: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(today)
        },
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.save), color = U.c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = U.c.bg),
    ) { androidx.compose.material3.DatePicker(state, showModeToggle = true) }
}
