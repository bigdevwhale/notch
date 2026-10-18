package app.notch.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.notch.BuildConfig
import app.notch.R
import app.notch.container
import app.notch.data.Child
import app.notch.data.Kind
import app.notch.data.Mark
import app.notch.data.Moment
import app.notch.data.Pickup
import app.notch.data.Series
import app.notch.data.Session
import app.notch.data.Settings
import app.notch.data.Word
import app.notch.domain.Growth
import app.notch.notify.Alarms
import app.notch.ui.child.ChildScreen
import app.notch.ui.components.rememberNow
import app.notch.ui.fading.FadingTab
import app.notch.ui.frame.FrameTab
import app.notch.ui.films.FilmsScreen
import app.notch.ui.films.PostcardScreen
import app.notch.ui.hands.HandsScreen
import app.notch.ui.moment.MomentScreen
import app.notch.ui.onboarding.OnboardingScreen
import app.notch.ui.rings.RingsTab
import app.notch.ui.series.SeriesScreen
import app.notch.ui.settings.SettingsScreen
import app.notch.ui.shoot.ShootRequest
import app.notch.ui.shoot.ShootSheet
import app.notch.ui.theme.Nunito
import app.notch.ui.theme.U
import app.notch.ui.theme.UType
import app.notch.ui.words.InterviewScreen
import app.notch.ui.words.WordsTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class Tab { FRAME, RINGS, WORDS, FADING }

/** Everything stored, plus what is derived from it, recomputed every minute or on change. */
@Immutable
class House(
    val children: List<Child>,
    val moments: List<Moment>,
    val words: List<Word>,
    val marks: List<Mark>,
    val series: List<Series>,
    val settings: Settings,
    val now: Long,
) {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.now(zone)
    private val byChild = HashMap<Long, List<Moment>>()

    fun child(id: Long) = children.firstOrNull { it.id == id }
    fun moment(id: Long) = moments.firstOrNull { it.id == id }

    /** A child's moments, newest first. */
    fun of(id: Long): List<Moment> = byChild.getOrPut(id) { moments.filter { it.of(id) } }
    fun wordsOf(id: Long) = words.filter { it.childId == id }
    fun marksOf(id: Long) = marks.filter { it.childId == id }
    fun inSeries(id: Long) = moments.filter { it.seriesId == id && it.kindOf == Kind.PHOTO }.sortedBy { it.takenAt }

    /** The picture of a child's face: the one they chose, else the latest photo of them alone, else any. */
    fun faceOf(c: Child): String {
        if (c.face > 0) moment(c.face)?.let { return it.file }
        val photos = of(c.id).filter { it.kindOf == Kind.PHOTO }
        return (photos.firstOrNull { it.kidIds.size == 1 } ?: photos.firstOrNull())?.file.orEmpty()
    }

    fun week(c: Child) = Growth.week(c.birthday, today)
    fun weekOf(c: Child, m: Moment) = Growth.week(c.birthday, Growth.date(m.takenAt, zone))
    fun notchedThisWeek(c: Child) = of(c.id).any { weekOf(c, it) == week(c) }
}

/** Everything a screen can open. */
class Go(private val nav: NavHostController, val shoot: (ShootRequest?) -> Unit) {
    fun child(id: Long) = nav.navigate("child/$id") { launchSingleTop = true }
    fun moment(id: Long) = nav.navigate("moment/$id")
    fun series(id: Long) = nav.navigate("series/$id")
    fun hands(child: Long) = nav.navigate("hands/$child") { launchSingleTop = true }
    fun interview(child: Long) = nav.navigate("interview/$child") { launchSingleTop = true }
    fun films(child: Long) = nav.navigate("films/$child") { launchSingleTop = true }
    fun card(child: Long, month: LocalDate) = nav.navigate("card/$child/${month.withDayOfMonth(1).toEpochDay()}")
    fun settings() = nav.navigate("settings") { launchSingleTop = true }
    fun home() { nav.popBackStack("main", false) }
    fun back() { nav.popBackStack() }
}

@Composable
fun NotchRoot(settings: Settings) {
    val context = LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.FRAME) }
    val start = remember { if (settings.onboarded) "main" else "welcome" }
    val dao = container.db.dao()

    val children by dao.observeChildren().collectAsStateWithLifecycle(initialValue = null)
    val moments by dao.observeMoments().collectAsStateWithLifecycle(initialValue = null)
    val words by dao.observeWords().collectAsStateWithLifecycle(initialValue = null)
    val marks by dao.observeMarks().collectAsStateWithLifecycle(initialValue = null)
    val series by dao.observeSeries().collectAsStateWithLifecycle(initialValue = null)
    val tick = rememberNow(60_000)
    val house = remember(children, moments, words, marks, series, settings, tick) {
        House(children.orEmpty(), moments.orEmpty(), words.orEmpty(), marks.orEmpty(), series.orEmpty(), settings, System.currentTimeMillis())
    }
    var picked by rememberSaveable { mutableLongStateOf(0L) }
    val child = house.child(picked) ?: house.children.firstOrNull()
    var sheet by remember { mutableStateOf<ShootRequest?>(null) }
    var carved by remember { mutableStateOf<Pickup.Got?>(null) }

    // The camera needs to see the gallery to bring its pictures back.
    var pending by remember { mutableStateOf<ShootRequest?>(null) }
    val askRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val r = pending
        pending = null
        if (Pickup.canRead(context)) { if (r != null) startShooting(context, r) } else if (granted.isNotEmpty()) openAppSettings(context)
    }
    val go = remember(nav) {
        Go(nav, shoot = { r -> sheet = r ?: ShootRequest() })
    }
    val doShoot: (ShootRequest) -> Unit = { r ->
        sheet = null
        if (!Pickup.canRead(context)) { pending = r; askRead.launch(Pickup.permissions()) } else startShooting(context, r)
    }

    // Back from the camera: whatever it took goes on the door frame. Clips may land a moment later.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        Ghost.hide(context)
        scope.launch {
            val s = container.settings.now().session
            if (!s.active || !Pickup.canRead(context)) return@launch
            var photos = 0
            var clips = 0
            val ids = mutableListOf<Long>()
            for (wait in listOf(0L, 1500L)) {
                delay(wait)
                val got = withContext(Dispatchers.IO) { Pickup.load(context, container.db, container.vault, s) }
                photos += got.photos; clips += got.clips; ids += got.ids
            }
            if (photos + clips > 0) carved = Pickup.Got(photos, clips, ids)
            if (System.currentTimeMillis() - s.since > Pickup.SESSION) container.settings.update { it.copy(session = Session()) }
            container.changed()
        }
    }

    // Onboarded elsewhere (a restored backup, the demo seed): leave the welcome screens.
    LaunchedEffect(settings.onboarded) {
        if (settings.onboarded && nav.currentDestination?.route == "welcome") nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
    }
    // A tapped reminder.
    val open by container.open.collectAsStateWithLifecycle()
    LaunchedEffect(open, house.children.isNotEmpty()) {
        val (where, who) = open ?: return@LaunchedEffect
        if (house.children.isEmpty()) return@LaunchedEffect
        container.open.value = null
        if (who > 0) picked = who
        val k = house.child(who) ?: child ?: return@LaunchedEffect
        go.home()
        when (where) {
            "shoot" -> sheet = ShootRequest(kids = listOf(k.id))
            "second" -> sheet = ShootRequest(kids = listOf(k.id), seconds = true)
            "interview" -> go.interview(k.id)
            "hands" -> go.hands(k.id)
            "card" -> go.card(k.id, house.today.minusMonths(1))
        }
    }
    LaunchedEffect(carved) { if (carved != null) { delay(5000); carved = null } }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.scope.launch { container.changed() }
    }
    LaunchedEffect(house.moments.isNotEmpty()) {
        // Asked once there is something to be reminded about: after the first notch.
        if (house.moments.isNotEmpty() && settings.nudges && android.os.Build.VERSION.SDK_INT >= 33 && !Alarms.canNotify(context) && !BuildConfig.DEMO) {
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.background(U.c.bg),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable("welcome") {
                OnboardingScreen { k ->
                    container.write {
                        val id = db.dao().insert(k)
                        this.settings.update { it.copy(onboarded = true) }
                        picked = id
                    }
                    nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
                }
            }
            composable("main") {
                if (child == null) {
                    // Every child removed: back to adding one.
                    LaunchedEffect(house.children.isEmpty()) { if (children != null && house.children.isEmpty()) go.child(0) }
                    Box(Modifier.fillMaxSize().background(U.c.bg))
                } else MainTabs(tab, { tab = it }, go, house, child, { picked = it })
            }
            composable("child/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ChildScreen(e.arguments?.getLong("id") ?: 0L, house, go) { picked = it }
            }
            composable("moment/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                MomentScreen(e.arguments?.getLong("id") ?: 0L, house, go)
            }
            composable("series/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                SeriesScreen(e.arguments?.getLong("id") ?: 0L, house, go)
            }
            composable("hands/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                HandsScreen(e.arguments?.getLong("id") ?: 0L, house, go)
            }
            composable("interview/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                InterviewScreen(e.arguments?.getLong("id") ?: 0L, house, go)
            }
            composable("films/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                FilmsScreen(e.arguments?.getLong("id") ?: 0L, house, go)
            }
            composable(
                "card/{id}/{month}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("month") { type = NavType.LongType }),
            ) { e ->
                PostcardScreen(e.arguments?.getLong("id") ?: 0L, LocalDate.ofEpochDay(e.arguments?.getLong("month") ?: 0L), house, go)
            }
            composable("settings") { SettingsScreen(house, go) }
        }

        sheet?.let { r ->
            ShootSheet(r, house, child, onDismiss = { sheet = null }, onShoot = doShoot)
        }
        AnimatedVisibility(
            carved != null,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp, start = 16.dp, end = 16.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            carved?.let { g -> Carved(g, house) { carved = null } }
        }
    }
}

/** "Notched! 3 photos and a clip for Masha." */
@Composable
private fun Carved(g: Pickup.Got, house: House, onClick: () -> Unit) {
    val c = ctx()
    val names = g.ids.firstNotNullOfOrNull { house.moment(it) }?.kidIds?.mapNotNull { house.child(it)?.name }.orEmpty()
    val what = listOfNotNull(
        if (g.photos > 0) T.plural(c, R.plurals.photos, g.photos) else null,
        if (g.clips > 0) T.plural(c, R.plurals.clips, g.clips) else null,
    ).joinToString(c.getString(R.string.and_sep))
    Row(
        Modifier.fillMaxWidth().shadow(8.dp, U.RMd).clip(U.RMd).background(U.c.pencil).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.DoorFront, null, tint = U.c.woodLight)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.carved_title), style = UType.Strong.copy(color = Color.White))
            Text(if (names.isEmpty()) what else stringResource(R.string.carved_for, what, names.joinToString(", ")), style = UType.Muted.copy(color = Color(0xCCFFFFFF)))
        }
    }
}

/** Hands the shutter to the phone's own camera; what it takes from now on goes on the door frame. */
private fun startShooting(context: android.content.Context, r: ShootRequest) {
    if (!Pickup.hasCamera(context)) {
        Toast.makeText(context, R.string.no_camera, Toast.LENGTH_LONG).show()
        return
    }
    val c = context.container
    c.scope.launch {
        val s = c.settings.now()
        c.settings.update {
            it.copy(session = Session(since = System.currentTimeMillis(), kids = Moment.kidsOf(r.kids), fading = r.fading, ghost = r.ghost, seconds = r.seconds))
        }
        if (r.ghost > 0 && Ghost.allowed(context)) {
            val m = c.db.dao().moment(r.ghost)
            val b = m?.let { withContext(Dispatchers.IO) { c.vault.full(it.file, 1600) } }
            if (b != null) withContext(Dispatchers.Main) { Ghost.show(context, b, s.ghostAlpha / 100f) }
        }
        withContext(Dispatchers.Main) {
            val intent = if (r.seconds) Pickup.videoIntent().takeIf { it.resolveActivity(context.packageManager) != null } ?: Pickup.cameraIntent() else Pickup.cameraIntent()
            runCatching { context.startActivity(intent) }.onFailure { Ghost.hide(context) }
        }
    }
}

fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, go: Go, house: House, child: Child, onPick: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.FRAME -> FrameTab(house, child, go, onPick)
                Tab.RINGS -> RingsTab(house, child, go, onPick)
                Tab.WORDS -> WordsTab(house, child, go, onPick)
                Tab.FADING -> FadingTab(house, child, go, onPick)
            }
        }
        HorizontalDivider(color = U.c.line)
        Row(
            Modifier.fillMaxWidth().background(U.c.surface).navigationBarsPadding().height(66.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.DoorFront, stringResource(R.string.tab_frame), tab == Tab.FRAME) { onTab(Tab.FRAME) }
            NavItem(Icons.Rounded.Park, stringResource(R.string.tab_rings), tab == Tab.RINGS) { onTab(Tab.RINGS) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.offset(y = (-10).dp).size(60.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(U.c.accent)
                        .border(3.dp, U.c.surface, CircleShape).clickable { go.shoot(ShootRequest(kids = listOf(child.id))) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CameraAlt, stringResource(R.string.shoot), Modifier.size(28.dp), tint = Color.White) }
            }
            NavItem(Icons.Rounded.AutoStories, stringResource(R.string.tab_words), tab == Tab.WORDS) { onTab(Tab.WORDS) }
            NavItem(Icons.Rounded.HourglassBottom, stringResource(R.string.tab_fading), tab == Tab.FADING, badge = false) { onTab(Tab.FADING) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, badge: Boolean = false, onClick: () -> Unit) {
    val color = if (selected) U.c.accent else U.c.faint
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (selected) U.c.accentSoft else Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp),
            ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
            if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp).size(9.dp).clip(RoundedCornerShape(50)).background(U.c.accent))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, fontFamily = Nunito, fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}
