package com.dok.editor

import com.dok.editor.engine.TimelineEditingEngine
import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditingEngineTest {

    private fun createTestProject(): Project {
        val clip1 = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaUri = "content://media/1",
            startTimeUs = 0L,
            durationUs = 6_000_000L, // 6s
            trimInUs = 1_000_000L,
            trimOutUs = 7_000_000L,
            speed = 1.0f
        )
        val clip2 = TimelineClip(
            id = "c2",
            trackId = "t1",
            mediaUri = "content://media/2",
            startTimeUs = 8_000_000L, // 8s
            durationUs = 4_000_000L, // 4s
            trimInUs = 0L,
            trimOutUs = 4_000_000L,
            speed = 1.0f
        )
        val track1 = Track(id = "t1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip1, clip2))
        val track2 = Track(id = "t2", name = "V2", type = TrackType.VIDEO, clips = emptyList())
        return Project(id = "p1", tracks = listOf(track1, track2))
    }

    @Test
    fun testSplitClip() {
        val project = createTestProject()
        // Split c1 at 2.5s (2_500_000 us)
        val splitProject = TimelineEditingEngine.splitClip(project, "c1", 2_500_000L)
        val clips = splitProject.tracks[0].clips
        assertEquals(3, clips.size)

        val left = clips[0]
        val right = clips[1]
        assertEquals(0L, left.startTimeUs)
        assertEquals(2_500_000L, left.durationUs)
        assertEquals(1_000_000L, left.trimInUs)
        assertEquals(3_500_000L, left.trimOutUs)

        assertEquals(2_500_000L, right.startTimeUs)
        assertEquals(3_500_000L, right.durationUs)
        assertEquals(3_500_000L, right.trimInUs)
    }

    @Test
    fun testTrimClip() {
        val project = createTestProject()
        val trimmed = TimelineEditingEngine.trimClip(
            project = project,
            clipId = "c1",
            newStartTimeUs = 500_000L,
            newDurationUs = 4_000_000L,
            newTrimInUs = 1_500_000L,
            newTrimOutUs = 5_500_000L
        )
        val clip = trimmed.tracks[0].clips.find { it.id == "c1" }!!
        assertEquals(500_000L, clip.startTimeUs)
        assertEquals(4_000_000L, clip.durationUs)
        assertEquals(1_500_000L, clip.trimInUs)
    }

    @Test
    fun testLiftDelete() {
        val project = createTestProject()
        // Lift delete c1: c2 stays at 8s
        val lifted = TimelineEditingEngine.liftDelete(project, setOf("c1"))
        val clips = lifted.tracks[0].clips
        assertEquals(1, clips.size)
        assertEquals("c2", clips[0].id)
        assertEquals(8_000_000L, clips[0].startTimeUs)
    }

    @Test
    fun testRippleDelete() {
        val project = createTestProject()
        // Ripple delete c1: c1 has 6s duration, so c2 (at 8s) shifts back by 6s to 2s!
        val rippled = TimelineEditingEngine.rippleDelete(project, setOf("c1"))
        val clips = rippled.tracks[0].clips
        assertEquals(1, clips.size)
        assertEquals("c2", clips[0].id)
        assertEquals(2_000_000L, clips[0].startTimeUs)
    }

    @Test
    fun testMoveClip() {
        val project = createTestProject()
        val moved = TimelineEditingEngine.moveClip(project, "c1", "t2", 3_000_000L)
        val t1Clips = moved.tracks.find { it.id == "t1" }!!.clips
        val t2Clips = moved.tracks.find { it.id == "t2" }!!.clips

        assertEquals(1, t1Clips.size)
        assertEquals("c2", t1Clips[0].id)

        assertEquals(1, t2Clips.size)
        assertEquals("c1", t2Clips[0].id)
        assertEquals("t2", t2Clips[0].trackId)
        assertEquals(3_000_000L, t2Clips[0].startTimeUs)
    }

    @Test
    fun testSpeedChange() {
        val project = createTestProject()
        // Speed up c1 from 1.0x to 2.0x -> duration becomes 3s (from 6s)
        val speedProject = TimelineEditingEngine.changeSpeed(project, "c1", 2.0f, maintainDuration = false)
        val clip = speedProject.tracks[0].clips.find { it.id == "c1" }!!
        assertEquals(2.0f, clip.speed, 0.001f)
        assertEquals(3_000_000L, clip.durationUs)
    }

    @Test
    fun testSnapping() {
        val project = createTestProject()
        // Clip 1 ends at 6_000_000 us. A point at 6_050_000 us is within 200ms (50ms away)
        val snapped = TimelineEditingEngine.calculateSnapping(
            project = project,
            targetTimeUs = 6_050_000L,
            thresholdUs = 200_000L
        )
        assertEquals(6_000_000L, snapped)

        // A point at 7_000_000 us is 1s away from both clip ends (6s and 8s) -> outside threshold, no snap
        val unsnapped = TimelineEditingEngine.calculateSnapping(
            project = project,
            targetTimeUs = 7_000_000L,
            thresholdUs = 200_000L
        )
        assertEquals(7_000_000L, unsnapped)
    }
    @Test
    fun testSplitLinkedClipKeepsPairs() {
        val video = TimelineClip(
            id = "video",
            trackId = "v1",
            mediaUri = "content://media/shared",
            startTimeUs = 0L,
            durationUs = 6_000_000L,
            trimInUs = 0L,
            trimOutUs = 6_000_000L,
            linkedClipId = "audio"
        )
        val audio = TimelineClip(
            id = "audio",
            trackId = "a1",
            mediaUri = "content://media/shared",
            startTimeUs = 0L,
            durationUs = 6_000_000L,
            trimInUs = 0L,
            trimOutUs = 6_000_000L,
            linkedClipId = "video"
        )
        val project = Project(
            id = "linked",
            tracks = listOf(
                Track(id = "v1", name = "V1", type = TrackType.VIDEO, clips = listOf(video)),
                Track(id = "a1", name = "A1", type = TrackType.AUDIO, clips = listOf(audio))
            )
        )

        val split = TimelineEditingEngine.splitLinkedClip(project, "video", 3_000_000L)
        val videos = split.tracks.first { it.id == "v1" }.clips.sortedBy { it.startTimeUs }
        val audios = split.tracks.first { it.id == "a1" }.clips.sortedBy { it.startTimeUs }

        assertEquals(2, videos.size)
        assertEquals(2, audios.size)
        assertEquals(audios[0].id, videos[0].linkedClipId)
        assertEquals(videos[0].id, audios[0].linkedClipId)
        assertEquals(audios[1].id, videos[1].linkedClipId)
        assertEquals(videos[1].id, audios[1].linkedClipId)
    }

}
