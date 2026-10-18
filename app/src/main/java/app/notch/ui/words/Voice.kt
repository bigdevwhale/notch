package app.notch.ui.words

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records a word the way the child says it: AAC in an .m4a, a few seconds long. */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    var file: File? = null
        private set
    private var startedAt = 0L

    fun start(): Boolean = runCatching {
        stop()
        val f = File(context.cacheDir, "voice-" + System.currentTimeMillis() + ".m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setMaxDuration(60_000)
        r.setOutputFile(f.path)
        r.prepare()
        r.start()
        rec = r
        file = f
        startedAt = System.currentTimeMillis()
        true
    }.getOrDefault(false)

    /** Stops; returns the recording, or null when it was too short to be anything. */
    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file
        if (!ok || f == null || System.currentTimeMillis() - startedAt < 400) { f?.delete(); file = null; return null }
        return f
    }

    val recording: Boolean get() = rec != null
}

/** Plays one recording at a time. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    var playing: String? = null
        private set

    fun play(f: File, key: String, onDone: () -> Unit) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(f.path)
                setOnCompletionListener { stop(); onDone() }
                prepare()
                start()
            }
            playing = key
        }.onFailure { onDone() }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        playing = null
    }
}
