package app.notch.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The phone's own camera does the shooting. When the parent comes back, this finds what the camera put
 * in the gallery since it was opened and carves it into the door frame: a copy goes into the vault,
 * the original stays in the gallery.
 */
object Pickup {
    /** How long after opening the camera its pictures still count as this session's. */
    const val SESSION = 30 * 60_000L

    class Found(val uri: Uri, val video: Boolean, val takenAt: Long)

    /** What one session brought back. */
    class Got(val photos: Int, val clips: Int, val ids: List<Long>)

    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Full access to photos and videos; "selected photos only" can't see new pictures. */
    fun canRead(context: Context): Boolean = permissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** The camera app in photo mode (it can switch to video, portrait or anything else itself). */
    fun cameraIntent(): Intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Straight into video, for a day's second, where the camera app has such a door. */
    fun videoIntent(): Intent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun hasCamera(context: Context): Boolean = cameraIntent().resolveActivity(context.packageManager) != null

    /** New camera pictures and clips since [since], oldest first, skipping ones already taken in. */
    fun find(context: Context, since: Long, known: Set<String>): List<Found> {
        val out = mutableListOf<Found>()
        for (video in listOf(false, true)) {
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN)
            // Only the camera's folder: no screenshots, downloads or other apps' pictures.
            val (where, args) = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE 'DCIM/%' AND ${MediaStore.MediaColumns.IS_PENDING} = 0" to
                    arrayOf(((since / 1000) - 2).toString())
            } else {
                @Suppress("DEPRECATION")
                "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATA} LIKE '%/DCIM/%'" to arrayOf(((since / 1000) - 2).toString())
            }
            runCatching {
                context.contentResolver.query(collection, cols, where, args, "${MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        if (uri.toString() in known) continue
                        val taken = c.getLong(2).takeIf { it > 0 } ?: (c.getLong(1) * 1000)
                        out += Found(uri, video, taken)
                    }
                }
            }
        }
        return out.sortedBy { it.takenAt }
    }

    /** Looks at the gallery and carves anything new from this session. */
    suspend fun load(context: Context, db: NotchDatabase, vault: Vault, s: Session): Got {
        val dao = db.dao()
        val found = find(context, s.since, dao.sources().toSet())
        if (found.isEmpty() || s.kids.isBlank()) return Got(0, 0, emptyList())
        // Shot over a ghost: the new pictures join that picture's series, starting one if it had none.
        var series = 0L
        if (s.ghost > 0 && found.any { !it.video }) {
            dao.moment(s.ghost)?.let { g ->
                series = if (g.seriesId > 0) g.seriesId else dao.insert(Series(createdAt = System.currentTimeMillis())).also { dao.update(g.copy(seriesId = it)) }
            }
        }
        val ids = mutableListOf<Long>()
        var photos = 0
        var clips = 0
        found.forEach { f ->
            val name = copy(context, vault, f.uri, f.video) ?: return@forEach
            val kind = when {
                !f.video -> Kind.PHOTO
                s.seconds -> Kind.SECOND
                else -> Kind.VIDEO
            }
            ids += dao.insert(
                Moment(
                    kids = s.kids, file = name, kind = kind.name, takenAt = f.takenAt, source = f.uri.toString(), fading = s.fading,
                    durationMs = if (f.video) vault.durationMs(vault.file(name)) else 0, seriesId = if (!f.video) series else 0,
                )
            )
            if (f.video) clips++ else photos++
        }
        return Got(photos, clips, ids)
    }

    /**
     * Takes in pictures picked from the gallery, dated when they were taken, so the weeks before the app
     * fill in too. Returns how many came in.
     *
     * With [into] (a week, from its first millisecond to the next week's), they go into that week: a picture
     * whose own date falls elsewhere, or is lost, is set at midday of the week's first day, in the order picked.
     */
    suspend fun import(context: Context, db: NotchDatabase, vault: Vault, uris: List<Uri>, kids: String, into: LongRange? = null): Int {
        val dao = db.dao()
        val known = dao.sources().toSet()
        var n = 0
        uris.forEachIndexed { i, uri ->
            if (uri.toString() in known) return@forEachIndexed
            val video = context.contentResolver.getType(uri)?.startsWith("video") == true
            val name = copy(context, vault, uri, video) ?: return@forEachIndexed
            val f = vault.file(name)
            val own = takenAt(context, uri, f, video)
            val taken = when {
                into == null -> own ?: f.lastModified()
                own != null && own in into -> own
                else -> into.first + 12 * 3_600_000L + i * 60_000L
            }
            dao.insert(
                Moment(
                    kids = kids, file = name, kind = (if (video) Kind.VIDEO else Kind.PHOTO).name, takenAt = taken, source = uri.toString(),
                    durationMs = if (video) vault.durationMs(f) else 0,
                )
            )
            n++
        }
        return n
    }

    /** When a picture was taken: the gallery's date, else what the camera wrote inside the file. */
    private fun takenAt(context: Context, uri: Uri, f: java.io.File, video: Boolean): Long? {
        val fromStore = runCatching {
            val col = if (Build.VERSION.SDK_INT >= 33 && uri.authority == "media" && uri.path?.contains("picker") == true) MediaStore.PickerMediaColumns.DATE_TAKEN
            else MediaStore.MediaColumns.DATE_TAKEN
            context.contentResolver.query(uri, arrayOf(col), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0).takeIf { it > 0 } else null }
        }.getOrNull()
        if (fromStore != null) return fromStore
        return if (video) runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(f.path)
                    // "20261012T101530.000Z"
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(it.take(15))?.time
                    }
                } finally { release() }
            }
        }.getOrNull()?.takeIf { it > 0 }
        else runCatching {
            val x = ExifInterface(f.path)
            (x.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: x.getAttribute(ExifInterface.TAG_DATETIME))?.let {
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time
            }
        }.getOrNull()
    }

    private fun copy(context: Context, vault: Vault, uri: Uri, video: Boolean): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { vault.take(it, if (video) "mp4" else "jpg") }
    }.getOrNull()
}
