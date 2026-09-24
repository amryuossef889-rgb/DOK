package com.dok.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.dok.editor.command.EditorCommand
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.Transform2D
import com.dok.editor.model.TransitionConfig
import com.dok.editor.model.TransitionType
import com.dok.editor.model.Project
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.model.TimelineClip
import com.dok.editor.persistence.ProjectSerializer
import com.dok.editor.ui.DokEditorApp
import com.dok.editor.ui.theme.DokEditorTheme
import com.dok.editor.viewmodel.EditorPanel
import com.dok.editor.viewmodel.EditorViewModel
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorCommandAndUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun newViewModel(): EditorViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(app.filesDir, "projects/current_project.json")
        file.parentFile?.mkdirs()
        val project = Project(
            id = "test-project",
            tracks = listOf(
                Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(
                    TimelineClip(trackId = "V1", mediaUri = "", mediaName = "Test Clip", startTimeUs = 0L, durationUs = 10_000_000L, sourceDurationUs = 10_000_000L)
                )),
                Track(id = "V2", name = "V2", type = TrackType.VIDEO),
                Track(id = "A1", name = "A1", type = TrackType.AUDIO),
                Track(id = "A2", name = "A2", type = TrackType.AUDIO),
                Track(id = "T1", name = "T1", type = TrackType.TEXT)
            )
        )
        ProjectSerializer.saveProjectAtomically(file, project)
        return EditorViewModel(app)
    }

    @Test
    fun testTimecodeFormatting() {
        assertEquals("00:00:00:00", EditorViewModel.formatTimecode(0L, 30))
        assertEquals("00:00:01:00", EditorViewModel.formatTimecode(1_000_000L, 30))
        assertEquals("00:01:00:00", EditorViewModel.formatTimecode(60_000_000L, 30))
        assertEquals("01:00:00:00", EditorViewModel.formatTimecode(3_600_000_000L, 30))
        // 1 hour, 1 minute, 1 second, 15 frames at 30fps = 3600 + 60 + 1 = 3661s + 0.5s = 3661500000 us
        assertEquals("01:01:01:15", EditorViewModel.formatTimecode(3_661_500_000L, 30))
    }

    @Test
    fun testViewModelTransportAndShuttleCommands() {
        val vm = newViewModel()
        assertFalse(vm.isPlaying.value)
        assertEquals(0L, vm.currentTimeUs.value)

        // Play / Pause
        vm.dispatch(EditorCommand.Play)
        assertTrue(vm.isPlaying.value)

        vm.dispatch(EditorCommand.Pause)
        assertFalse(vm.isPlaying.value)

        vm.dispatch(EditorCommand.TogglePlayPause)
        assertTrue(vm.isPlaying.value)

        vm.dispatch(EditorCommand.TogglePlayPause)
        assertFalse(vm.isPlaying.value)

        // Shuttle forward: 1x -> 2x -> 4x
        vm.dispatch(EditorCommand.Play)
        assertEquals(1.0f, vm.playbackSpeed.value, 0.01f)
        vm.dispatch(EditorCommand.ShuttleForward)
        assertEquals(2.0f, vm.playbackSpeed.value, 0.01f)
        vm.dispatch(EditorCommand.ShuttleForward)
        assertEquals(4.0f, vm.playbackSpeed.value, 0.01f)

        // Shuttle stop
        vm.dispatch(EditorCommand.ShuttleStop)
        assertFalse(vm.isPlaying.value)
        assertEquals(1.0f, vm.playbackSpeed.value, 0.01f)

        // Shuttle reverse: -1x -> -2x -> -4x
        vm.dispatch(EditorCommand.ShuttleReverse)
        assertEquals(-1.0f, vm.playbackSpeed.value, 0.01f)
        vm.dispatch(EditorCommand.ShuttleReverse)
        assertEquals(-2.0f, vm.playbackSpeed.value, 0.01f)

        // Scrub & Step
        vm.dispatch(EditorCommand.ScrubTo(2_000_000L))
        assertEquals(2_000_000L, vm.currentTimeUs.value)

        vm.dispatch(EditorCommand.StepFrames(1)) // 1 frame at 30fps is 33333us
        assertTrue(vm.currentTimeUs.value > 2_000_000L)

        vm.dispatch(EditorCommand.JumpToStart)
        assertEquals(0L, vm.currentTimeUs.value)

        vm.dispatch(EditorCommand.JumpToEnd)
        assertEquals(vm.project.value.durationUs, vm.currentTimeUs.value)
    }

    @Test
    fun testEditingCommandsAndUndoRedo() {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        val initialClipsCount = vm.project.value.tracks[0].clips.size
        val firstClip = vm.project.value.tracks[0].clips.first()

        // Select Clip
        vm.dispatch(EditorCommand.SelectClip(firstClip.id))
        assertEquals(firstClip.id, vm.selectedClipId.value)

        // Split clip at 2s
        vm.dispatch(EditorCommand.ScrubTo(2_000_000L))
        vm.dispatch(EditorCommand.SplitClipAtPlayhead)

        val splitClips = vm.project.value.tracks[0].clips
        assertEquals(initialClipsCount + 1, splitClips.size)
        assertTrue(vm.canUndo.value)

        // Undo split
        vm.dispatch(EditorCommand.Undo)
        assertEquals(initialClipsCount, vm.project.value.tracks[0].clips.size)

        // Redo split
        vm.dispatch(EditorCommand.Redo)
        assertEquals(initialClipsCount + 1, vm.project.value.tracks[0].clips.size)

        // In / Out markers
        vm.dispatch(EditorCommand.SetInPoint(1_000_000L))
        vm.dispatch(EditorCommand.SetOutPoint(4_000_000L))
        assertEquals(1_000_000L, vm.inPointUs.value)
        assertEquals(4_000_000L, vm.outPointUs.value)

        vm.dispatch(EditorCommand.ClearInOutPoints)
        assertNull(vm.inPointUs.value)
        assertNull(vm.outPointUs.value)

        // Snapping toggle
        val snapInitial = vm.isSnappingEnabled.value
        vm.dispatch(EditorCommand.ToggleSnapping)
        assertEquals(!snapInitial, vm.isSnappingEnabled.value)

        // Zoom timeline
        val initialZoom = vm.zoomLevel.value
        vm.dispatch(EditorCommand.ZoomTimeline(0.5f))
        assertEquals(initialZoom + 0.5f, vm.zoomLevel.value, 0.001f)
        vm.dispatch(EditorCommand.ZoomToFit)
        assertEquals(1.0f, vm.zoomLevel.value, 0.001f)
    }

    @Test
    fun testClipPropertiesAndEffectsCommands() {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        val firstClip = vm.project.value.tracks[0].clips.first()

        // Update Transform
        val newTransform = Transform2D(scaleX = 1.25f, scaleY = 1.25f, rotationDegrees = 15f)
        vm.dispatch(EditorCommand.UpdateClipTransform(firstClip.id, newTransform))
        val updatedClip1 = vm.project.value.tracks[0].clips.first { it.id == firstClip.id }
        assertEquals(1.25f, updatedClip1.transform.scaleX, 0.01f)
        assertEquals(15f, updatedClip1.transform.rotationDegrees, 0.01f)

        // Update Color Grading
        val colorGrading = ColorGradingParams(brightness = 0.2f, contrast = 1.3f, saturation = 1.1f)
        vm.dispatch(EditorCommand.UpdateColorGrading(firstClip.id, colorGrading))
        val updatedClip2 = vm.project.value.tracks[0].clips.first { it.id == firstClip.id }
        assertEquals(0.2f, updatedClip2.colorParams.brightness, 0.01f)
        assertEquals(1.3f, updatedClip2.colorParams.contrast, 0.01f)

        // Add Effect
        val effect = Effect.ParametricEffect(effectType = EffectType.SHAKE, intensity = 0.8f)
        vm.dispatch(EditorCommand.AddParametricEffect(firstClip.id, effect))
        val updatedClip3 = vm.project.value.tracks[0].clips.first { it.id == firstClip.id }
        assertEquals(1, updatedClip3.effects.size)
        assertEquals(EffectType.SHAKE, updatedClip3.effects.first().effectType)

        // Remove Effect
        vm.dispatch(EditorCommand.RemoveEffect(firstClip.id, effect.id))
        val updatedClip4 = vm.project.value.tracks[0].clips.first { it.id == firstClip.id }
        assertTrue(updatedClip4.effects.isEmpty())

        // Set Transitions
        val transitionIn = TransitionConfig(type = TransitionType.CROSSFADE, durationUs = 500_000L)
        vm.dispatch(EditorCommand.SetClipTransitionIn(firstClip.id, transitionIn))
        val updatedClip5 = vm.project.value.tracks[0].clips.first { it.id == firstClip.id }
        assertNotNull(updatedClip5.transitionIn)
        assertEquals(TransitionType.CROSSFADE, updatedClip5.transitionIn?.type)
    }

    @Test
    fun testDokEditorAppUiComposition() {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())

        composeTestRule.setContent {
            DokEditorTheme {
                DokEditorApp(viewModel = vm)
            }
        }

        // Verify root components render without crashing
        composeTestRule.onNodeWithTag("dok_editor_root").assertExists()
        composeTestRule.onNodeWithTag("viewer_panel").assertExists()
        composeTestRule.onNodeWithTag("timecode_display").assertExists()
        composeTestRule.onNodeWithTag("play_pause_button").assertExists()
        composeTestRule.onNodeWithTag("timeline_panel").assertExists()
        composeTestRule.onNodeWithTag("split_button").assertExists()
        composeTestRule.onNodeWithTag("bottom_nav_bar").assertExists()
    }
}
