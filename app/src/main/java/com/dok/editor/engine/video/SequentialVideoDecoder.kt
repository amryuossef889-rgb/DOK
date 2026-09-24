package com.dok.editor.engine.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface
import java.io.Closeable

class SequentialVideoDecoder(
    private val context: Context,
    private val uri: Uri,
    private val surface: Surface
) : Closeable {

    companion object {
        private const val TIMEOUT_US = 10_000L
    }

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var trackIndex = -1

    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var rotationDegrees: Int = 0
        private set
    var durationUs: Long = 0L
        private set
    var frameRate: Int = 30
        private set

    private var currentPtsUs: Long = -1L
    private var isEos = false

    init {
        initDecoder()
    }

    private fun initDecoder() {
        val ext = MediaExtractor()
        ext.setDataSource(context, uri, null)
        extractor = ext

        for (i in 0 until ext.trackCount) {
            val format = ext.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                trackIndex = i
                ext.selectTrack(i)

                width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 1920
                height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 1080
                rotationDegrees = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else 30

                val decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, surface, null, 0)
                decoder.start()
                codec = decoder
                break
            }
        }
    }

    /**
     * Decodes sequentially to the requested targetPtsUs and renders to the surface.
     * Never uses MediaMetadataRetriever. Always frame-accurate MediaCodec sequential stepping.
     */
    @Synchronized
    fun decodeFrameToPts(targetPtsUs: Long): Boolean {
        val ext = extractor ?: return false
        val dec = codec ?: return false

        // Check if seek is needed (target is in the past, or ahead by more than 1 second)
        if (targetPtsUs < currentPtsUs || (targetPtsUs - currentPtsUs) > 1_000_000L) {
            ext.seekTo(targetPtsUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            dec.flush()
            currentPtsUs = -1L
            isEos = false
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var rendered = false
        val startTimeMs = System.currentTimeMillis()

        while (!rendered && !isEos) {
            // Guard against infinite loop with wall-clock timeout
            if (System.currentTimeMillis() - startTimeMs > 2000L) {
                break
            }

            // Feed input
            val inputIndex = dec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    val sampleSize = ext.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        dec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        val pts = ext.sampleTime
                        dec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                        ext.advance()
                    }
                }
            }

            // Drain output
            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                currentPtsUs = bufferInfo.presentationTimeUs
                val shouldRender = currentPtsUs >= targetPtsUs || (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                dec.releaseOutputBuffer(outputIndex, shouldRender)
                if (shouldRender) {
                    rendered = true
                }
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEos = true
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = dec.outputFormat
                if (newFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                    width = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                }
                if (newFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                    height = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
                }
            }
        }

        return rendered
    }

    override fun close() {
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        try {
            extractor?.release()
        } catch (_: Exception) {}
        codec = null
        extractor = null
    }
}
