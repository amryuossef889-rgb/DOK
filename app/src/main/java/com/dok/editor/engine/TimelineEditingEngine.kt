package com.dok.editor.engine

import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import java.util.UUID
import kotlin.math.abs

object TimelineEditingEngine {

    const val MIN_CLIP_DURATION_US = 100_000L // 0.1s min duration

    /**
     * Splits a clip at [splitTimeUs] into two separate clips.
     * Preserves media trims, speed, effects, and color grading.
     */
    fun splitClip(project: Project, clipId: String, splitTimeUs: Long): Project {
        var modified = false
        val newTracks = project.tracks.map { track ->
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            if (clipIndex == -1) {
                track
            } else {
                val clip = track.clips[clipIndex]
                if (splitTimeUs <= clip.startTimeUs + MIN_CLIP_DURATION_US ||
                    splitTimeUs >= clip.endTimeUs - MIN_CLIP_DURATION_US
                ) {
                    // Split point is out of bounds or too close to edge
                    return@map track
                }

                val leftDurationUs = splitTimeUs - clip.startTimeUs
                val rightDurationUs = clip.durationUs - leftDurationUs

                val mediaDeltaUs = (leftDurationUs * clip.speed).toLong()

                val leftClip = clip.copy(
                    id = UUID.randomUUID().toString(),
                    durationUs = leftDurationUs,
                    trimOutUs = clip.trimInUs + mediaDeltaUs
                )

                val rightClip = clip.copy(
                    id = UUID.randomUUID().toString(),
                    startTimeUs = splitTimeUs,
                    durationUs = rightDurationUs,
                    trimInUs = clip.trimInUs + mediaDeltaUs
                )

                val updatedClips = track.clips.toMutableList()
                updatedClips.removeAt(clipIndex)
                updatedClips.add(clipIndex, rightClip)
                updatedClips.add(clipIndex, leftClip)
                modified = true
                track.copy(clips = updatedClips)
            }
        }

        return if (modified) {
            project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
        } else {
            project
        }
    }

    fun splitLinkedClip(project: Project, clipId: String, splitTimeUs: Long): Project {
        val clip = project.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return project
        val linkedId = clip.linkedClipId ?: return splitClip(project, clipId, splitTimeUs)
        val linked = project.tracks.flatMap { it.clips }.firstOrNull { it.id == linkedId }
            ?: return splitClip(project, clipId, splitTimeUs)

        if (splitTimeUs <= clip.startTimeUs + MIN_CLIP_DURATION_US ||
            splitTimeUs >= clip.endTimeUs - MIN_CLIP_DURATION_US ||
            splitTimeUs <= linked.startTimeUs + MIN_CLIP_DURATION_US ||
            splitTimeUs >= linked.endTimeUs - MIN_CLIP_DURATION_US
        ) return project

        var updated = splitClip(project, clipId, splitTimeUs)
        updated = splitClip(updated, linkedId, splitTimeUs)

        val clipParts = updated.tracks.flatMap { it.clips }
            .filter { it.mediaUri == clip.mediaUri && it.startTimeUs >= clip.startTimeUs && it.endTimeUs <= clip.endTimeUs }
            .sortedBy { it.startTimeUs }
        val linkedParts = updated.tracks.flatMap { it.clips }
            .filter { it.mediaUri == linked.mediaUri && it.startTimeUs >= linked.startTimeUs && it.endTimeUs <= linked.endTimeUs }
            .sortedBy { it.startTimeUs }

        if (clipParts.size < 2 || linkedParts.size < 2) return updated
        val leftA = clipParts[0]; val rightA = clipParts[1]
        val leftB = linkedParts[0]; val rightB = linkedParts[1]

        return updated.copy(
            tracks = updated.tracks.map { track ->
                track.copy(clips = track.clips.map { item ->
                    when (item.id) {
                        leftA.id -> item.copy(linkedClipId = leftB.id)
                        rightA.id -> item.copy(linkedClipId = rightB.id)
                        leftB.id -> item.copy(linkedClipId = leftA.id)
                        rightB.id -> item.copy(linkedClipId = rightA.id)
                        else -> item
                    }
                })
            },
            modifiedAtMs = System.currentTimeMillis()
        )
    }


    /**
     * Trims in or out point of a clip, updating timeline bounds accordingly.
     */
    fun trimClip(
        project: Project,
        clipId: String,
        newStartTimeUs: Long,
        newDurationUs: Long,
        newTrimInUs: Long,
        newTrimOutUs: Long
    ): Project {
        val clampedDuration = maxOf(MIN_CLIP_DURATION_US, newDurationUs)
        val clampedStart = maxOf(0L, newStartTimeUs)

        val newTracks = project.tracks.map { track ->
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            if (clipIndex == -1) {
                track
            } else {
                val clip = track.clips[clipIndex]
                val updatedClip = clip.copy(
                    startTimeUs = clampedStart,
                    durationUs = clampedDuration,
                    trimInUs = maxOf(0L, newTrimInUs),
                    trimOutUs = maxOf(0L, newTrimOutUs)
                )
                val updatedClips = track.clips.toMutableList()
                updatedClips[clipIndex] = updatedClip
                track.copy(clips = updatedClips)
            }
        }
        return project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
    }

    /**
     * Lift Delete: removes clips and leaves a gap in the timeline (does not shift subsequent clips).
     */
    fun liftDelete(project: Project, clipIds: Set<String>): Project {
        if (clipIds.isEmpty()) return project
        val newTracks = project.tracks.map { track ->
            val updatedClips = track.clips.filter { it.id !in clipIds }
            track.copy(clips = updatedClips)
        }
        return project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
    }

    /**
     * Ripple Delete: removes clips and ripples (shifts) subsequent clips backward to close gaps.
     */
    fun rippleDelete(project: Project, clipIds: Set<String>): Project {
        if (clipIds.isEmpty()) return project

        val newTracks = project.tracks.map { track ->
            val sortedClips = track.clips.sortedBy { it.startTimeUs }
            val resultingClips = ArrayList<TimelineClip>()
            var shiftAccumulatorUs = 0L

            for (clip in sortedClips) {
                if (clip.id in clipIds) {
                    shiftAccumulatorUs += clip.durationUs
                } else {
                    if (shiftAccumulatorUs > 0L) {
                        resultingClips.add(clip.copy(startTimeUs = maxOf(0L, clip.startTimeUs - shiftAccumulatorUs)))
                    } else {
                        resultingClips.add(clip)
                    }
                }
            }
            track.copy(clips = resultingClips)
        }

        return project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
    }

    /**
     * Moves a clip to a target track at [newStartTimeUs].
     */
    fun moveClip(
        project: Project,
        clipId: String,
        targetTrackId: String,
        newStartTimeUs: Long
    ): Project {
        var foundClip: TimelineClip? = null

        // Remove clip from current track
        val strippedTracks = project.tracks.map { track ->
            val c = track.clips.find { it.id == clipId }
            if (c != null) {
                foundClip = c
                track.copy(clips = track.clips.filter { it.id != clipId })
            } else {
                track
            }
        }

        val clipToMove = foundClip ?: return project
        val clampedStart = maxOf(0L, newStartTimeUs)
        val updatedClip = clipToMove.copy(
            trackId = targetTrackId,
            startTimeUs = clampedStart
        )

        // Insert into target track
        val newTracks = strippedTracks.map { track ->
            if (track.id == targetTrackId) {
                val updatedList = (track.clips + updatedClip).sortedBy { it.startTimeUs }
                track.copy(clips = updatedList)
            } else {
                track
            }
        }

        return project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
    }

    /**
     * Changes playback speed of a clip.
     * If [maintainDuration] is false, timeline duration scales inversely with speed.
     */
    fun changeSpeed(
        project: Project,
        clipId: String,
        newSpeed: Float,
        maintainDuration: Boolean = false
    ): Project {
        val clampedSpeed = newSpeed.coerceIn(0.1f, 10.0f)
        val newTracks = project.tracks.map { track ->
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            if (clipIndex == -1) {
                track
            } else {
                val clip = track.clips[clipIndex]
                val newDurationUs = if (maintainDuration) {
                    clip.durationUs
                } else {
                    ((clip.durationUs * clip.speed) / clampedSpeed).toLong().coerceAtLeast(MIN_CLIP_DURATION_US)
                }
                val updatedClip = clip.copy(
                    speed = clampedSpeed,
                    durationUs = newDurationUs
                )
                val updatedList = track.clips.toMutableList()
                updatedList[clipIndex] = updatedClip
                track.copy(clips = updatedList)
            }
        }
        return project.copy(tracks = newTracks, modifiedAtMs = System.currentTimeMillis())
    }

    /**
     * Snaps a timestamp to nearby magnetic points (timeline 0, other clip starts, clip ends, playhead).
     */
    fun calculateSnapping(
        project: Project,
        targetTimeUs: Long,
        playheadUs: Long = 0L,
        thresholdUs: Long = 200_000L, // 200ms magnetic snapping window
        ignoreClipId: String? = null
    ): Long {
        val snapPoints = HashSet<Long>()
        snapPoints.add(0L)
        snapPoints.add(playheadUs)

        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.id == ignoreClipId) continue
                snapPoints.add(clip.startTimeUs)
                snapPoints.add(clip.endTimeUs)
            }
        }

        var closestPoint = targetTimeUs
        var smallestDist = Long.MAX_VALUE

        for (point in snapPoints) {
            val dist = abs(targetTimeUs - point)
            if (dist <= thresholdUs && dist < smallestDist) {
                smallestDist = dist
                closestPoint = point
            }
        }

        return closestPoint
    }
}
