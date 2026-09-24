package com.dok.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dok.editor.engine.player.PlayerState
import com.dok.editor.engine.player.PreviewPlayer
import com.dok.editor.engine.player.PreviewScaleMode
import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewPlayerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun createSampleProject(): Project {
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaUri = "content://media/test",
            startTimeUs = 0L,
            durationUs = 10_000_000L
        )
        val track = Track(id = "t1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip))
        return Project(id = "p1", tracks = listOf(track))
    }

    @Test
    fun testPlayerStateTransitions() {
        val player = PreviewPlayer(context)
        assertEquals(PlayerState.IDLE, player.playerState.value)

        val project = createSampleProject()
        player.setProject(project)
        assertEquals(PlayerState.PAUSED, player.playerState.value)
        assertEquals(10_000_000L, player.totalDurationUs.value)

        // Scrubbing transitions
        player.startScrubbing()
        assertEquals(PlayerState.SCRUBBING, player.playerState.value)

        player.scrubTo(5_000_000L)
        assertEquals(5_000_000L, player.playbackPositionUs.value)

        player.stopScrubbing()
        assertEquals(PlayerState.PAUSED, player.playerState.value)

        // Bounds clamping
        player.seekTo(-1_000L)
        assertEquals(0L, player.playbackPositionUs.value)

        player.seekTo(20_000_000L)
        assertEquals(10_000_000L, player.playbackPositionUs.value)

        player.close()
        assertEquals(PlayerState.IDLE, player.playerState.value)
    }

    @Test
    fun testPreviewScaleModes() {
        assertEquals(1.0f, PreviewScaleMode.FULL.scaleFactor, 0.001f)
        assertEquals(0.5f, PreviewScaleMode.HALF.scaleFactor, 0.001f)
        assertEquals(0.25f, PreviewScaleMode.QUARTER.scaleFactor, 0.001f)
    }
}
