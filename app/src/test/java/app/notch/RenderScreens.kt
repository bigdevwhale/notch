package app.notch

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.test.core.app.ApplicationProvider
import app.notch.data.DemoSeed
import app.notch.data.Settings
import app.notch.ui.NotchRoot
import app.notch.ui.theme.NotchTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens with the demo family to PNGs (run with `-Prender`): a look without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class RenderScreens {
    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out = File(System.getProperty("render.out") ?: "build/art", "screens").apply { mkdirs() }
    private val demo = File(System.getProperty("render.photos") ?: "src/demo/assets/demo")

    private fun settle(ms: Long = 3000) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) {
            Thread.sleep(60)
            rule.mainClock.advanceTimeBy(100)
            t += 100
        }
    }

    private fun shot(name: String) {
        // Draw the window by hand: capturing waits for a frame, which a paused clock never gives.
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.mainClock.advanceTimeByFrame()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val root = rule.activity.window.decorView
        val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String, ms: Long = 2500, exact: Boolean = false) {
        settle(600)
        val node = rule.onAllNodesWithText(text, substring = !exact, useUnmergedTree = exact)[0]
        node.performClick()
        settle(ms)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    /** Scrolls the screen by a swipe: scrolling to a node waits for an idle the paused clock never gives. */
    private fun swipe(times: Int = 1) {
        repeat(times) {
            rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.25f, durationMillis = 300) }
            settle(800)
        }
    }

    private fun back() {
        rule.onAllNodesWithContentDescription("Back")[0].performClick()
        settle(1500)
    }

    @Test
    fun screens() {
        val app = ApplicationProvider.getApplicationContext<NotchApp>()
        runBlocking {
            DemoSeed.seedIfEmpty(app, app.container, list = { demo.list()?.toList().orEmpty() }) { name -> File(demo, name.removePrefix("demo/")).inputStream() }
        }
        rule.setContent {
            val s: Settings? by app.container.settings.settings.collectAsState(initial = null)
            NotchTheme { s?.let { NotchRoot(it) } }
        }
        settle(5000)
        shot("1-frame")
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(14)
        settle(1500)
        shot("2-frame-past")
        tap("Rings", exact = true)
        shot("3-rings")
        tap("Words", exact = true)
        shot("4-words")
        tap("Fading", exact = true)
        shot("5-fading")
        tap("Door frame", exact = true)
        tap("Petya", exact = true)
        shot("6-petya")
        tap("Masha", exact = true)
        tap("Rings", exact = true)
        swipe(2)
        shot("3b-rings-made")
        tap("A second a day")
        shot("7-films")
        back()
        swipe(2)
        tap("Hands", exact = true)
        shot("8-hands")
        back()
        swipe(2)
        tap("Birthday interview")
        shot("9-interview")
        back()
        swipe(2)
        tap("The month's postcard")
        settle(4000)
        shot("10-postcard")
        back()
        tap("Door frame", exact = true)
        tap("Make a notch")
        shot("11-shoot")
    }
}
