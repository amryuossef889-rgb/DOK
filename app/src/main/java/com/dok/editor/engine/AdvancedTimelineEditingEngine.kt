package com.dok.editor.engine

import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import kotlin.math.max

object AdvancedTimelineEditingEngine {
    fun rollEdit(project: Project, leftClipId: String, rightClipId: String, deltaUs: Long): Project {
        val left = find(project, leftClipId) ?: return project
        val right = find(project, rightClipId) ?: return project
        if (left.trackId != right.trackId || left.endTimeUs != right.startTimeUs) return project
        val newBoundary = (left.endTimeUs + deltaUs).coerceIn(
            left.startTimeUs + TimelineEditingEngine.MIN_CLIP_DURATION_US,
            right.endTimeUs - TimelineEditingEngine.MIN_CLIP_DURATION_US
        )
        return replace(project, left.copy(durationUs = newBoundary - left.startTimeUs),
            right.copy(startTimeUs = newBoundary, durationUs = right.endTimeUs - newBoundary))
    }

    fun slipEdit(project: Project, clipId: String, deltaSourceUs: Long): Project {
        val clip = find(project, clipId) ?: return project
        val maxIn = max(0L, clip.sourceDurationUs - clip.durationUs)
        val newIn = (clip.trimInUs + deltaSourceUs).coerceIn(0L, maxIn)
        return replace(project, clip.copy(trimInUs = newIn, trimOutUs = newIn + (clip.durationUs * clip.speed).toLong()))
    }

    fun slideEdit(project: Project, clipId: String, deltaUs: Long): Project {
        val clip = find(project, clipId) ?: return project
        val track = project.tracks.firstOrNull { it.id == clip.trackId } ?: return project
        val ordered = track.clips.sortedBy { it.startTimeUs }
        val index = ordered.indexOfFirst { it.id == clipId }
        if (index < 0 || index == 0 || index == ordered.lastIndex) return project
        val prev = ordered[index - 1]
        val next = ordered[index + 1]
        val minStart = prev.endTimeUs
        val maxEnd = next.startTimeUs
        val newStart = (clip.startTimeUs + deltaUs).coerceIn(minStart, maxStart(maxEnd - clip.durationUs, minStart))
        val shift = newStart - clip.startTimeUs
        return replace(project, clip.copy(startTimeUs = newStart),
            prev.copy(durationUs = prev.durationUs + shift),
            next.copy(startTimeUs = next.startTimeUs + shift, durationUs = next.durationUs - shift))
    }

    private fun maxStart(a: Long, b: Long) = max(a, b)
    private fun find(project: Project, id: String): TimelineClip? =
        project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == id }

    private fun replace(project: Project, vararg clips: TimelineClip): Project {
        val map = clips.associateBy { it.id }
        return project.copy(
            tracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { map[it.id] ?: it }.sortedBy { it.startTimeUs })
            },
            modifiedAtMs = System.currentTimeMillis()
        )
    }
}
