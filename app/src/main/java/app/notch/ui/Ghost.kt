package app.notch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * The ghost: an earlier picture, faint, laid over the phone's own camera so the child can be stood in
 * the same spot. It is a window that lets every touch through, shown while the camera is open and
 * taken away when the parent comes back. Needs "display over other apps"; without it the ghost is
 * shown in the app before the camera opens instead.
 */
object Ghost {
    private var view: FrameLayout? = null

    fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun askIntent(context: Context) = android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

    /** [alpha] 0..1; above 0.8 Android would block touches to the camera underneath. */
    fun show(context: Context, picture: Bitmap, alpha: Float) {
        hide(context)
        if (!allowed(context)) return
        val wm = context.applicationContext.getSystemService(WindowManager::class.java)
        val frame = FrameLayout(context.applicationContext)
        val image = ImageView(context.applicationContext).apply {
            setImageBitmap(picture)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        // Camera apps put a 3:4 preview a little above the middle; the ghost goes there, full width.
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels
        val h = (w * picture.height.toFloat() / picture.width).toInt().coerceAtMost(dm.heightPixels)
        frame.addView(image, FrameLayout.LayoutParams(w, h, Gravity.CENTER).apply { bottomMargin = (dm.heightPixels * 0.06f).toInt() })
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { this.alpha = alpha.coerceIn(0.1f, 0.75f) }
        runCatching { wm.addView(frame, lp) }.onSuccess { view = frame }
    }

    fun hide(context: Context) {
        val v = view ?: return
        runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }
}
