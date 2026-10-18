package app.notch.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything in one zip: a JSON index of the children, moments, words, heights and series, and every
 * picture, clip and recording. Nothing leaves the phone unless the parent exports it.
 */
object Backup {
    private const val VERSION = 1
    private const val INDEX = "notch.json"

    fun encode(all: Everything): String = JSONObject()
        .put("app", "notch")
        .put("version", VERSION)
        .put("children", JSONArray(all.children.map {
            JSONObject().put("id", it.id).put("name", it.name).put("born", it.born).put("gender", it.gender).put("crayon", it.crayon)
                .put("face", it.face).put("createdAt", it.createdAt)
        }))
        .put("moments", JSONArray(all.moments.map {
            JSONObject().put("id", it.id).put("kids", it.kids).put("file", it.file).put("kind", it.kind).put("takenAt", it.takenAt)
                .put("durationMs", it.durationMs).put("note", it.note).put("fading", it.fading).put("question", it.question)
                .put("seriesId", it.seriesId).put("source", it.source).put("favorite", it.favorite).put("shape", it.shape).put("measure", it.measure)
        }))
        .put("words", JSONArray(all.words.map {
            JSONObject().put("id", it.id).put("childId", it.childId).put("said", it.said).put("means", it.means).put("audio", it.audio)
                .put("at", it.at).put("phrase", it.phrase).put("fading", it.fading)
        }))
        .put("marks", JSONArray(all.marks.map { JSONObject().put("id", it.id).put("childId", it.childId).put("cm", it.cm.toDouble()).put("at", it.at) }))
        .put("series", JSONArray(all.series.map { JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt) }))
        .toString(2)

    private fun JSONObject.list(key: String) = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()

    /** Throws [IllegalArgumentException] when the text is not a Notch index. */
    fun decode(text: String): Everything {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "notch") { "Not a Notch backup" }
        return Everything(
            children = root.list("children").map {
                Child(
                    id = it.getLong("id"), name = it.getString("name"), born = it.getLong("born"), gender = it.optString("gender"),
                    crayon = it.optInt("crayon"), face = it.optLong("face"), createdAt = it.optLong("createdAt"),
                )
            },
            moments = root.list("moments").map {
                Moment(
                    id = it.getLong("id"), kids = it.getString("kids"), file = it.getString("file"), kind = it.optString("kind", Kind.PHOTO.name),
                    takenAt = it.getLong("takenAt"), durationMs = it.optLong("durationMs"), note = it.optString("note"), fading = it.optString("fading"),
                    question = it.optInt("question"), seriesId = it.optLong("seriesId"), source = it.optString("source"), favorite = it.optBoolean("favorite"),
                    shape = it.optString("shape"), measure = it.optInt("measure"),
                )
            },
            words = root.list("words").map {
                Word(
                    id = it.getLong("id"), childId = it.getLong("childId"), said = it.getString("said"), means = it.optString("means"),
                    audio = it.optString("audio"), at = it.getLong("at"), phrase = it.optBoolean("phrase"), fading = it.optString("fading"),
                )
            },
            marks = root.list("marks").map { Mark(id = it.getLong("id"), childId = it.getLong("childId"), cm = it.getDouble("cm").toFloat(), at = it.getLong("at")) },
            series = root.list("series").map { Series(id = it.getLong("id"), name = it.optString("name"), createdAt = it.getLong("createdAt")) },
        )
    }

    fun write(context: Context, uri: Uri, all: Everything, vault: Vault) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(INDEX))
                zip.write(encode(all).toByteArray())
                zip.closeEntry()
                val names = all.moments.map { it.file } + all.words.map { it.audio }
                names.filter { it.isNotEmpty() }.distinct().forEach { name ->
                    val file = vault.file(name)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("media/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Can't write there")
    }

    /** Reads a backup into a fresh folder first, so a broken file never wipes what is here. */
    fun read(context: Context, uri: Uri): Pair<Everything, File> {
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var index: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == INDEX -> index = zip.readBytes().decodeToString()
                        e.name.startsWith("media/") && !e.isDirectory -> {
                            val name = e.name.removePrefix("media/")
                            require(!name.contains('/') && !name.contains("..")) { "Bad entry" }
                            File(tmp, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        } ?: error("Can't read that")
        return decode(index ?: throw IllegalArgumentException("Not a Notch backup")) to tmp
    }
}
