package app.notch.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.notch.BuildConfig
import app.notch.R
import app.notch.container
import app.notch.data.Backup
import app.notch.data.Everything
import app.notch.ui.Ghost
import app.notch.ui.Go
import app.notch.ui.House
import app.notch.ui.Language
import app.notch.ui.T
import app.notch.ui.components.ConfirmDialog
import app.notch.ui.components.InfoRow
import app.notch.ui.components.SectionTitle
import app.notch.ui.components.Segmented
import app.notch.ui.components.SwitchRow
import app.notch.ui.components.TimeDialog
import app.notch.ui.components.TopBar
import app.notch.ui.components.UCard
import app.notch.ui.ctx
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(house: House, go: Go) {
    val c = ctx()
    val container = c.container
    val s = house.settings
    var timePick by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Ghost.allowed(c)) }
    var alpha by remember { mutableFloatStateOf(s.ghostAlpha.toFloat()) }
    var restoring by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlay = Ghost.allowed(c) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val dao = container.db.dao()
            val ok = runCatching {
                withContext(Dispatchers.IO) { Backup.write(c, uri, Everything(dao.children(), dao.moments(), dao.words(), dao.marks(), dao.series()), container.vault) }
            }.isSuccess
            withContext(Dispatchers.Main) { message = c.getString(if (ok) R.string.backup_done else R.string.backup_failed) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.settings), go::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.set_reminders))
            UCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SwitchRow(stringResource(R.string.set_nudges), stringResource(R.string.set_nudges_sub), s.nudges, { on -> container.write { settings.update { it.copy(nudges = on) } } })
                SwitchRow(stringResource(R.string.set_seconds), stringResource(R.string.set_seconds_sub), s.seconds, { on -> container.write { settings.update { it.copy(seconds = on) } } })
                if (s.seconds) InfoRow(Icons.Rounded.Schedule, stringResource(R.string.set_second_at), T.minutes(c, s.secondAt), onClick = { timePick = true })
                SwitchRow(stringResource(R.string.set_occasions), stringResource(R.string.set_occasions_sub), s.occasions, { on -> container.write { settings.update { it.copy(occasions = on) } } })
            }

            SectionTitle(stringResource(R.string.set_ghost))
            UCard {
                Text(stringResource(R.string.set_ghost_sub), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                if (!overlay) InfoRow(Icons.Rounded.Layers, stringResource(R.string.set_ghost_overlay), stringResource(R.string.set_ghost_off), onClick = { runCatching { c.startActivity(Ghost.askIntent(c)) } })
                else Text(stringResource(R.string.set_ghost_on), style = UType.Body.copy(color = U.c.accent))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.set_ghost_alpha, alpha.toInt()), style = UType.Body)
                Slider(
                    alpha, { alpha = it }, valueRange = 15f..75f,
                    onValueChangeFinished = { val v = alpha.toInt(); container.write { settings.update { it.copy(ghostAlpha = v) } } },
                    colors = SliderDefaults.colors(thumbColor = U.c.accent, activeTrackColor = U.c.accent),
                )
            }

            SectionTitle(stringResource(R.string.set_language))
            var lang by remember { mutableStateOf(Language.current()) }
            Segmented(listOf(Language.SYSTEM, Language.EN, Language.RU), lang, {
                when (it) { Language.EN -> "English"; Language.RU -> "Русский"; else -> stringResource(R.string.set_lang_system) }
            }, { lang = it; Language.set(it) })

            SectionTitle(stringResource(R.string.set_data))
            UCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                InfoRow(Icons.Rounded.Download, stringResource(R.string.backup_export), stringResource(R.string.backup_export_sub), onClick = { export.launch("notch-" + java.time.LocalDate.now() + ".zip") })
                InfoRow(Icons.Rounded.Upload, stringResource(R.string.backup_import), stringResource(R.string.backup_import_sub), onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) })
            }
            message?.let { Text(it, style = UType.Body.copy(color = U.c.accent), modifier = Modifier.padding(top = 10.dp)) }

            SectionTitle(stringResource(R.string.set_about))
            Text(stringResource(R.string.set_privacy), style = UType.Muted)
            Spacer(Modifier.height(8.dp))
            Text("Notch " + BuildConfig.VERSION_NAME + " · Stillcraft", style = UType.Small)
            Spacer(Modifier.height(30.dp))
        }
    }
    if (timePick) TimeDialog(s.secondAt, { timePick = false }) { m -> container.write { settings.update { it.copy(secondAt = m) } } }
    restoring?.let { uri ->
        ConfirmDialog(
            stringResource(R.string.backup_import_title), stringResource(R.string.backup_import_text), stringResource(R.string.backup_import_go),
            onConfirm = {
                container.scope.launch {
                    val ok = runCatching {
                        val (all, files) = withContext(Dispatchers.IO) { Backup.read(c, uri) }
                        container.vault.wipe()
                        container.vault.adopt(files)
                        container.db.dao().replaceAll(all)
                        container.settings.update { it.copy(onboarded = true) }
                    }.isSuccess
                    container.changed()
                    withContext(Dispatchers.Main) { message = c.getString(if (ok) R.string.backup_restored else R.string.backup_bad) }
                }
            },
            onDismiss = { restoring = null }, danger = true,
        )
    }
}
