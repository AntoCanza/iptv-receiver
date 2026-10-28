package io.github.antocanza.iptvreceiver

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.DataOutputStream
import java.io.FileOutputStream

/** Original silent grayscale video, generated with Android APIs; no codec library or provider media is bundled. */
internal fun downloadVideoFixture(context: Context): ByteArray {
    val file = context.cacheDir.resolve("download-video-fixture.mp4")
    val width = 160
    val height = 96
    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var muxing = false
    try {
        encoder.configure(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 10)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        var frame = 0
        var sentEnd = false
        var finished = false
        var track = -1
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (!finished) {
            check(SystemClock.elapsedRealtime() < deadline)
            if (!sentEnd) {
                val input = encoder.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    if (frame == 1200) {
                        encoder.queueInputBuffer(input, 0, 0, 120_000_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sentEnd = true
                    } else {
                        val image = checkNotNull(encoder.getInputImage(input))
                        image.planes.forEachIndexed { index, plane ->
                            val rows = if (index == 0) height else height / 2
                            val columns = if (index == 0) width else width / 2
                            val value = if (index == 0) (32 + (frame / 10) % 180).toByte() else 128.toByte()
                            for (row in 0 until rows) for (column in 0 until columns) {
                                plane.buffer.put(row * plane.rowStride + column * plane.pixelStride, value)
                            }
                        }
                        encoder.queueInputBuffer(input, 0, width * height * 3 / 2, frame * 100_000L, 0)
                        frame++
                    }
                }
            }
            while (true) {
                val output = encoder.dequeueOutputBuffer(info, if (sentEnd) 10_000 else 0)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxing)
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start(); muxing = true
                } else if (output >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(muxing)
                        val bytes = checkNotNull(encoder.getOutputBuffer(output))
                        bytes.position(info.offset); bytes.limit(info.offset + info.size)
                        muxer.writeSampleData(track, bytes, info)
                    }
                    finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(output, false)
                    if (finished) break
                } else break
            }
        }
        encoder.stop()
        muxer.stop(); muxing = false
        // A valid free box makes the transfer slow enough to exercise pause/range resume.
        val padding = 2 * 1024 * 1024 - file.length().toInt()
        if (padding >= 8) DataOutputStream(FileOutputStream(file, true)).use {
            it.writeInt(padding); it.writeBytes("free"); it.write(ByteArray(padding - 8))
        }
        return file.readBytes()
    } finally {
        runCatching { encoder.release() }
        if (muxing) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        file.delete()
    }
}
