package app.notch.make

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Writes pictures drawn on the phone into an H.264 film, frame by frame, with exact timestamps: no
 * screen, no GPU, so it behaves the same on every phone. Used for the growing-face film.
 */
class Encoder(private val out: File, val width: Int, val height: Int, private val fps: Int = 30, bitrate: Int = 6_000_000) : AutoCloseable {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track = -1
    private var started = false
    private var frame = 0L
    private val info = MediaCodec.BufferInfo()
    private val argb = IntArray(width * height)

    init {
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    private val yp = ByteArray(width * height)
    private val up = ByteArray((width / 2) * (height / 2))
    private val vp = ByteArray((width / 2) * (height / 2))

    /** Turns [b] into the planes of YUV 4:2:0, ready to be written as many times as needed. */
    private fun convert(b: Bitmap) {
        b.getPixels(argb, 0, width, 0, 0, width, height)
        val cw = width / 2
        for (row in 0 until height) {
            val base = row * width
            for (col in 0 until width) {
                val c = argb[base + col]
                val r = c shr 16 and 255
                val g = c shr 8 and 255
                val bl = c and 255
                yp[base + col] = (((66 * r + 129 * g + 25 * bl + 128) shr 8) + 16).toByte()
                if (row and 1 == 0 && col and 1 == 0) {
                    val j = (row / 2) * cw + col / 2
                    up[j] = (((-38 * r - 74 * g + 112 * bl + 128) shr 8) + 128).toByte()
                    vp[j] = (((112 * r - 94 * g - 18 * bl + 128) shr 8) + 128).toByte()
                }
            }
        }
    }

    private fun queue() {
        var index: Int
        while (true) {
            index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) break
            drain(false)
        }
        val image = codec.getInputImage(index) ?: error("No input image")
        fun plane(p: android.media.Image.Plane, src: ByteArray, w: Int, h: Int) {
            val buf = p.buffer
            if (p.pixelStride == 1) {
                for (row in 0 until h) {
                    buf.position(row * p.rowStride)
                    buf.put(src, row * w, w)
                }
            } else {
                for (row in 0 until h) {
                    val o = row * p.rowStride
                    for (col in 0 until w) buf.put(o + col * p.pixelStride, src[row * w + col])
                }
            }
        }
        plane(image.planes[0], yp, width, height)
        plane(image.planes[1], up, width / 2, height / 2)
        plane(image.planes[2], vp, width / 2, height / 2)
        codec.queueInputBuffer(index, 0, width * height * 3 / 2, frame * 1_000_000L / fps, 0)
        frame++
        drain(false)
    }

    /** Adds [b] (exactly [width] × [height]) as the next frame. */
    fun add(b: Bitmap) {
        convert(b)
        queue()
    }

    /** The same picture for [times] frames: a held moment. */
    fun hold(b: Bitmap, times: Int) {
        if (times <= 0) return
        convert(b)
        repeat(times) { queue() }
    }

    private fun drain(end: Boolean) {
        while (true) {
            val i = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                i >= 0 -> {
                    val buf = codec.getOutputBuffer(i)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started && buf != null) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Ends the film and writes it out. */
    fun finish() {
        var index: Int
        while (true) {
            index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) break
            drain(false)
        }
        codec.queueInputBuffer(index, 0, 0, frame * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        runCatching { if (started) muxer.stop() }
        muxer.release()
    }
}
