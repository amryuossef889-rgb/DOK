package com.dok.editor.engine.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

data class ExtractedMediaInfo(
    val uri: String,
    val name: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val durationUs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val fps: Int = 30,
    val videoMime: String? = null,
    val audioSampleRate: Int = 44100,
    val audioChannels: Int = 2,
    val audioMime: String? = null
)

object MediaMetadataExtractor {

    fun extractInfo(context: Context, uri: Uri, fallbackName: String): ExtractedMediaInfo {
        var durationUs = 0L
        var width = 0
        var height = 0
        var rotation = 0
        var fps = 30
        var isVideo = false
        var isAudio = false
        var videoMime: String? = null
        var audioSampleRate = 44100
        var audioChannels = 2
        var audioMime: String? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("video/")) {
                    isVideo = true
                    videoMime = mime
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    }
                } else if (mime.startsWith("audio/")) {
                    isAudio = true
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback default duration for preview/testing
            durationUs = 10_000_000L
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        return ExtractedMediaInfo(
            uri = uri.toString(),
            name = fallbackName,
            isVideo = isVideo,
            isAudio = isAudio,
            durationUs = durationUs,
            width = width,
            height = height,
            rotationDegrees = rotation,
            fps = fps,
            videoMime = videoMime,
            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels,
            audioMime = audioMime
        )
    }
}
