package com.dok.editor

import com.dok.editor.engine.audio.PcmMixer
import com.dok.editor.engine.plan.TimelineRenderPlan
import com.dok.editor.model.InterpolationType
import com.dok.editor.model.Keyframe
import com.dok.editor.model.KeyframeProperty
import com.dok.editor.model.Project
import com.dok.editor.model.TextOverlayConfig
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.model.Transform2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PcmMixerAndRenderPlanTest {

    @Test
    fun testPanLawUnityCenter() {
        // Center pan must have unity gain (1.0, 1.0)
        val center = PcmMixer.calculatePanGains(0.0f)
        assertEquals(1.0f, center.first, 0.0001f)
        assertEquals(1.0f, center.second, 0.0001f)

        // Hard Left: Left = 1.0, Right = 0.0
        val hardLeft = PcmMixer.calculatePanGains(-1.0f)
        assertEquals(1.0f, hardLeft.first, 0.0001f)
        assertEquals(0.0f, hardLeft.second, 0.0001f)

        // Hard Right: Left = 0.0, Right = 1.0
        val hardRight = PcmMixer.calculatePanGains(1.0f)
        assertEquals(0.0f, hardRight.first, 0.0001f)
        assertEquals(1.0f, hardRight.second, 0.0001f)

        // Half Left (-0.5f) -> Left = 1.0, Right = 0.5
        val halfLeft = PcmMixer.calculatePanGains(-0.5f)
        assertEquals(1.0f, halfLeft.first, 0.0001f)
        assertEquals(0.5f, halfLeft.second, 0.0001f)

        // Half Right (+0.5f) -> Left = 0.5, Right = 1.0
        val halfRight = PcmMixer.calculatePanGains(0.5f)
        assertEquals(0.5f, halfRight.first, 0.0001f)
        assertEquals(1.0f, halfRight.second, 0.0001f)
    }

    @Test
    fun testDbToLinear() {
        assertEquals(1.0f, PcmMixer.dbToLinear(0.0f), 0.001f)
        assertEquals(0.501f, PcmMixer.dbToLinear(-6.0f), 0.005f)
        assertEquals(1.995f, PcmMixer.dbToLinear(6.0f), 0.005f)
        assertEquals(0.0f, PcmMixer.dbToLinear(-100.0f), 0.0001f)
    }

    @Test
    fun testFadeEnvelope() {
        val startUs = 1_000_000L
        val durationUs = 4_000_000L
        val fadeInUs = 1_000_000L
        val fadeOutUs = 1_000_000L

        // Before clip
        assertEquals(0f, PcmMixer.calculateFadeEnvelope(500_000L, startUs, durationUs, fadeInUs, fadeOutUs), 0.001f)
        // Mid-fade-in (500ms into 1s fade)
        assertEquals(0.5f, PcmMixer.calculateFadeEnvelope(1_500_000L, startUs, durationUs, fadeInUs, fadeOutUs), 0.001f)
        // Full volume steady region
        assertEquals(1.0f, PcmMixer.calculateFadeEnvelope(3_000_000L, startUs, durationUs, fadeInUs, fadeOutUs), 0.001f)
        // Mid-fade-out (500ms before end)
        assertEquals(0.5f, PcmMixer.calculateFadeEnvelope(4_500_000L, startUs, durationUs, fadeInUs, fadeOutUs), 0.001f)
        // At or after end
        assertEquals(0f, PcmMixer.calculateFadeEnvelope(5_000_000L, startUs, durationUs, fadeInUs, fadeOutUs), 0.001f)
    }

    @Test
    fun testAbsolutePositionResamplingMatchesOneShot() {
        // Create synthetic 48kHz audio ramp
        val sourceFrames = 4800
        val sourcePcm = FloatArray(sourceFrames * 2)
        for (i in 0 until sourceFrames) {
            val sample = (i.toFloat() / sourceFrames.toFloat()) * 2f - 1f // -1.0 .. +1.0
            sourcePcm[i * 2] = sample
            sourcePcm[i * 2 + 1] = -sample
        }

        val totalOutputFrames = 4410
        val chunk1Frames = 2200
        val chunk2Frames = totalOutputFrames - chunk1Frames

        // Render one-shot (all 4410 frames in one go)
        val oneShot = PcmMixer.resampleByAbsolutePosition(
            sourcePcm = sourcePcm,
            sourceSampleRate = 48000,
            absoluteOutputStartFrame = 0L,
            outputFrameCount = totalOutputFrames,
            targetSampleRate = 44100
        )

        // Render in 2 separate chunks by absolute position
        val chunk1 = PcmMixer.resampleByAbsolutePosition(
            sourcePcm = sourcePcm,
            sourceSampleRate = 48000,
            absoluteOutputStartFrame = 0L,
            outputFrameCount = chunk1Frames,
            targetSampleRate = 44100
        )

        val chunk2 = PcmMixer.resampleByAbsolutePosition(
            sourcePcm = sourcePcm,
            sourceSampleRate = 48000,
            absoluteOutputStartFrame = chunk1Frames.toLong(),
            outputFrameCount = chunk2Frames,
            targetSampleRate = 44100
        )

        // Verify chunk1 matches first half of one-shot
        for (i in 0 until chunk1Frames * 2) {
            assertEquals("Chunk1 sample $i mismatch", oneShot[i], chunk1[i], 1e-6f)
        }

        // Verify chunk2 matches second half of one-shot exactly without seam/drift
        for (i in 0 until chunk2Frames * 2) {
            val oneShotIdx = chunk1Frames * 2 + i
            assertEquals("Chunk2 sample $i mismatch at boundary", oneShot[oneShotIdx], chunk2[i], 1e-6f)
        }
    }

    @Test
    fun testSoftKneeLimiter() {
        // Normal signal within threshold (< 0.89f) must be untouched
        val cleanSignal = floatArrayOf(0.1f, -0.5f, 0.7f, -0.8f)
        val cleanCopy = cleanSignal.clone()
        PcmMixer.applySoftKneeLimiter(cleanSignal)
        for (i in cleanSignal.indices) {
            assertEquals(cleanCopy[i], cleanSignal[i], 1e-6f)
        }

        // Hot summing signals > 1.0f must be softly compressed strictly below 1.0f
        val hotSignal = floatArrayOf(1.2f, -1.8f, 2.5f, -4.0f)
        PcmMixer.applySoftKneeLimiter(hotSignal)
        for (sample in hotSignal) {
            assertTrue("Sample $sample must be strictly < 1.0", abs(sample) < 1.0f)
            assertTrue("Sample $sample must be compressed above threshold", abs(sample) > 0.89f)
        }
    }

    @Test
    fun testTimelineRenderPlanEvaluation() {
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaUri = "content://test/video",
            startTimeUs = 0L,
            durationUs = 10_000_000L,
            transform = Transform2D(scaleX = 1.0f, scaleY = 1.0f, opacity = 1.0f),
            keyframes = listOf(
                Keyframe(timestampOffsetUs = 0L, property = KeyframeProperty.SCALE_X, value = 1.0f),
                Keyframe(timestampOffsetUs = 4_000_000L, property = KeyframeProperty.SCALE_X, value = 2.0f, interpolation = InterpolationType.LINEAR)
            ),
            textOverlay = TextOverlayConfig(text = "KILL FEED")
        )

        val track = Track(id = "t1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip))
        val project = Project(tracks = listOf(track))

        // Mid-point at 2s (scaleX should be 1.5)
        val planAt2s = TimelineRenderPlan.evaluateVideoAt(project, 2_000_000L)
        assertEquals(1, planAt2s.frameInstructions.size)
        assertEquals("c1", planAt2s.frameInstructions[0].clipId)
        assertEquals(1.5f, planAt2s.frameInstructions[0].transform.scaleX, 0.01f)
        assertEquals(1, planAt2s.textInstructions.size)
        assertEquals("KILL FEED", planAt2s.textInstructions[0].text)
    }
}
