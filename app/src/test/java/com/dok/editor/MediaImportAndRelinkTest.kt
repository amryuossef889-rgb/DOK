package com.dok.editor

import com.dok.editor.engine.media.RelinkManager
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.InterpolationType
import com.dok.editor.model.Keyframe
import com.dok.editor.model.KeyframeProperty
import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.model.Transform2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaImportAndRelinkTest {

    @Test
    fun testRelinkOfflineClipPreservesAllEdits() {
        val originalClip = TimelineClip(
            id = "clip-offline-1",
            trackId = "v1",
            mediaUri = "content://media/missing/clip.mp4",
            mediaName = "lost_gameplay.mp4",
            isOffline = true,
            startTimeUs = 2_000_000L,
            durationUs = 5_000_000L,
            trimInUs = 1_500_000L,
            trimOutUs = 6_500_000L,
            speed = 1.25f,
            transform = Transform2D(positionX = 12f, scaleX = 1.2f, rotationDegrees = 15f),
            keyframes = listOf(
                Keyframe(
                    timestampOffsetUs = 500_000L,
                    property = KeyframeProperty.SCALE_X,
                    value = 1.8f,
                    interpolation = InterpolationType.EASE_IN
                )
            ),
            colorParams = ColorGradingParams(contrast = 1.4f, saturation = 1.2f),
            effects = listOf(
                Effect.ParametricEffect(effectType = EffectType.GLOW, intensity = 0.7f)
            )
        )

        val track = Track(id = "v1", name = "V1", type = TrackType.VIDEO, clips = listOf(originalClip))
        val project = Project(tracks = listOf(track))

        assertTrue(project.tracks[0].clips[0].isOffline)

        // Relink to recovered file
        val relinkedProject = RelinkManager.relinkClip(
            project = project,
            clipId = "clip-offline-1",
            newMediaUri = "content://media/external/recovered_clip.mp4",
            newMediaName = "recovered_clip.mp4"
        )

        val relinkedClip = relinkedProject.tracks[0].clips[0]
        assertFalse("Clip must no longer be offline", relinkedClip.isOffline)
        assertEquals("content://media/external/recovered_clip.mp4", relinkedClip.mediaUri)
        assertEquals("recovered_clip.mp4", relinkedClip.mediaName)

        // Verify cuts and edits are preserved
        assertEquals(2_000_000L, relinkedClip.startTimeUs)
        assertEquals(5_000_000L, relinkedClip.durationUs)
        assertEquals(1_500_000L, relinkedClip.trimInUs)
        assertEquals(6_500_000L, relinkedClip.trimOutUs)
        assertEquals(1.25f, relinkedClip.speed, 0.001f)
        assertEquals(1.2f, relinkedClip.transform.scaleX, 0.001f)
        assertEquals(15f, relinkedClip.transform.rotationDegrees, 0.001f)
        assertEquals(1, relinkedClip.keyframes.size)
        assertEquals(1.8f, relinkedClip.keyframes[0].value, 0.001f)
        assertEquals(1.4f, relinkedClip.colorParams.contrast, 0.001f)
        assertEquals(EffectType.GLOW, relinkedClip.effects[0].effectType)
    }
}
