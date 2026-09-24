package com.dok.editor.engine.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.dok.editor.engine.audio.PcmMixer
import com.dok.editor.engine.audio.StreamingAudioDecoder
import com.dok.editor.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

object WaveformAndThumbnailService {

    private val thumbnailCache = object : LruCache<String, Bitmap>(50) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val waveformCache = HashMap<String, FloatArray>()

    /**
     * Computes RMS amplitude downsampled to [binsCount] buckets.
     * Guaranteed fast and non-blocking via Dispatchers.IO.
     */
    suspend fun generateWaveformRms(
        context: Context,
        mediaUri: Uri,
        durationUs: Long,
        binsCount: Int = 100
    ): FloatArray = withContext(Dispatchers.IO) {
        val cacheKey = "${mediaUri}_$binsCount"
        waveformCache[cacheKey]?.let { return@withContext it }

        val totalFrames = ((durationUs.toDouble() / 1_000_000.0) * PcmMixer.SAMPLE_RATE_44K).toLong()
        val framesPerBin = maxOf(1L, totalFrames / binsCount)
        val rmsBins = FloatArray(binsCount)

        try {
            StreamingAudioDecoder(context, mediaUri).use { decoder ->
                for (bin in 0 until binsCount) {
                    val startFrame = bin * framesPerBin
                    val framesToRead = minOf(framesPerBin, 1024L).toInt() // sample window for speed
                    val samples = decoder.readFrames(startFrame, framesToRead)

                    var sumSquares = 0.0
                    for (s in samples) {
                        sumSquares += (s * s)
                    }
                    val meanSquare = if (samples.isNotEmpty()) sumSquares / samples.size else 0.0
                    val rms = sqrt(meanSquare).toFloat().coerceIn(0f, 1f)
                    rmsBins[bin] = rms
                }
            }
        } catch (_: Exception) {
            // A failed decode is represented as silence/empty data, never synthetic audio.
            rmsBins.fill(0f)
        }

        waveformCache[cacheKey] = rmsBins
        return@withContext rmsBins
    }

    /**
     * Retrieves or generates a video thumbnail for a clip at [timeUs].
     */
    suspend fun getThumbnail(
        context: Context,
        mediaUri: Uri,
        timeUs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${mediaUri}_${timeUs / 1_000_000L}" // 1 thumbnail per second cache bucket
        thumbnailCache.get(key)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(context, mediaUri)
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(bitmap, 160, 90, true)
                thumbnailCache.put(key, scaled)
                scaled
            } else null
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}

object RelinkManager {

    /**
     * Relinks an offline clip to a newly selected media URI.
     * Preserves all trims, speeds, keyframes, transitions, effects, and color grading.
     */
    fun relinkClip(
        project: Project,
        clipId: String,
        newMediaUri: String,
        newMediaName: String
    ): Project {
        val newTracks = project.tracks.map { track ->
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            if (clipIndex == -1) {
                track
            } else {
                val clip = track.clips[clipIndex]
                val updatedClip = clip.copy(
                    mediaUri = newMediaUri,
                    mediaName = newMediaName,
                    isOffline = false
                )
                val updatedClips = track.clips.toMutableList()
                updatedClips[clipIndex] = updatedClip
                track.copy(clips = updatedClips)
            }
        }
        return project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
    }

    /**
     * Scans project and marks clips with inaccessible URIs as offline.
     */
    fun checkProjectOfflineClips(context: Context, project: Project): Project {
        val resolver = context.contentResolver
        var modified = false

        val newTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                val isAccessible = try {
                    val uri = Uri.parse(clip.mediaUri)
                    resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
                val offlineState = !isAccessible
                if (clip.isOffline != offlineState) {
                    modified = true
                    clip.copy(isOffline = offlineState)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }

        return if (modified) project.copy(tracks = newTracks) else project
    }
}
