package com.dok.editor

import com.dok.editor.history.UndoRedoManager
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.InterpolationType
import com.dok.editor.model.Keyframe
import com.dok.editor.model.KeyframeProperty
import com.dok.editor.model.Project
import com.dok.editor.model.TextOverlayConfig
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.model.Transform2D
import com.dok.editor.model.TransitionConfig
import com.dok.editor.model.TransitionType
import com.dok.editor.persistence.ProjectSerializer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectModelAndPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createSampleProject(): Project {
        val clip1 = TimelineClip(
            id = "clip-1",
            trackId = "track-v1",
            mediaUri = "content://media/external/video/100",
            mediaName = "gameplay_clutch.mp4",
            startTimeUs = 0L,
            durationUs = 5_000_000L,
            trimInUs = 1_000_000L,
            trimOutUs = 6_000_000L,
            sourceDurationUs = 10_000_000L,
            speed = 1.25f,
            volumeDb = -3.0f,
            pan = -0.2f,
            fadeInUs = 500_000L,
            fadeOutUs = 500_000L,
            isMuted = false,
            transform = Transform2D(
                positionX = 10f,
                positionY = -5f,
                scaleX = 1.1f,
                scaleY = 1.1f,
                rotationDegrees = 45f,
                opacity = 0.95f
            ),
            keyframes = listOf(
                Keyframe(
                    id = "kf-1",
                    timestampOffsetUs = 1_000_000L,
                    property = KeyframeProperty.SCALE_X,
                    value = 1.5f,
                    interpolation = InterpolationType.EASE_IN_OUT
                )
            ),
            colorParams = ColorGradingParams(
                brightness = 0.1f,
                contrast = 1.2f,
                saturation = 1.3f,
                temperature = 0.2f,
                vignette = 0.4f,
                lutCubeUri = "content://luts/vintage.cube"
            ),
            effects = listOf(
                Effect.ParametricEffect(
                    id = "fx-1",
                    effectType = EffectType.SHAKE,
                    intensity = 0.8f,
                    params = mapOf("frequency" to 12.0f)
                )
            ),
            transitionIn = TransitionConfig(
                type = TransitionType.CROSSFADE,
                durationUs = 400_000L
            ),
            transitionOut = TransitionConfig(
                type = TransitionType.DIP_TO_BLACK,
                durationUs = 500_000L
            ),
            textOverlay = TextOverlayConfig(
                text = "ACE ROUND",
                fontSizeSp = 32f,
                textColorHex = "#FF4A9E",
                backgroundColorHex = "#80000000",
                positionX = 0.5f,
                positionY = 0.2f,
                isBold = true,
                isItalic = false
            )
        )

        val trackV1 = Track(
            id = "track-v1",
            name = "V1 Gameplay",
            type = TrackType.VIDEO,
            isMuted = false,
            isSolo = false,
            isLocked = false,
            volumeDb = 0.0f,
            pan = 0.0f,
            clips = listOf(clip1)
        )

        val clipA1 = TimelineClip(
            id = "clip-a1",
            trackId = "track-a1",
            mediaUri = "content://media/external/audio/200",
            mediaName = "bg_music.mp3",
            startTimeUs = 0L,
            durationUs = 8_000_000L,
            speed = 1.0f,
            volumeDb = -6.0f,
            pan = 0.5f
        )

        val trackA1 = Track(
            id = "track-a1",
            name = "A1 Background Music",
            type = TrackType.AUDIO,
            isMuted = false,
            isSolo = false,
            isLocked = false,
            volumeDb = -2.0f,
            pan = 0.1f,
            clips = listOf(clipA1)
        )

        return Project(
            id = "proj-123",
            name = "Valorant Montage",
            width = 1920,
            height = 1080,
            fps = 60,
            tracks = listOf(trackV1, trackA1)
        )
    }

    @Test
    fun testSerializationRoundTrip() {
        val original = createSampleProject()
        val json = ProjectSerializer.serializeToJson(original)
        val deserialized = ProjectSerializer.deserializeFromJson(json)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.width, deserialized.width)
        assertEquals(original.height, deserialized.height)
        assertEquals(original.fps, deserialized.fps)
        assertEquals(original.tracks.size, deserialized.tracks.size)

        val origTrack1 = original.tracks[0]
        val desTrack1 = deserialized.tracks[0]
        assertEquals(origTrack1.id, desTrack1.id)
        assertEquals(origTrack1.type, desTrack1.type)
        assertEquals(origTrack1.clips.size, desTrack1.clips.size)

        val origClip = origTrack1.clips[0]
        val desClip = desTrack1.clips[0]
        assertEquals(origClip.id, desClip.id)
        assertEquals(origClip.mediaName, desClip.mediaName)
        assertEquals(origClip.speed, desClip.speed, 0.001f)
        assertEquals(origClip.volumeDb, desClip.volumeDb, 0.001f)
        assertEquals(origClip.pan, desClip.pan, 0.001f)
        assertEquals(origClip.transform.scaleX, desClip.transform.scaleX, 0.001f)
        assertEquals(origClip.keyframes.size, desClip.keyframes.size)
        assertEquals(origClip.colorParams.contrast, desClip.colorParams.contrast, 0.001f)
        assertEquals(origClip.colorParams.lutCubeUri, desClip.colorParams.lutCubeUri)
        assertEquals(origClip.effects.size, desClip.effects.size)
        assertEquals(origClip.effects[0].effectType, desClip.effects[0].effectType)
        assertEquals(origClip.transitionIn?.type, desClip.transitionIn?.type)
        assertNotNull(desClip.textOverlay)
        assertEquals("ACE ROUND", desClip.textOverlay?.text)
    }

    @Test
    fun testAtomicSaveAndLoad() {
        val project = createSampleProject()
        val file = File(tempFolder.root, "project.dok")

        ProjectSerializer.saveProjectAtomically(file, project)
        assertTrue("Saved file must exist", file.exists())
        assertTrue("Saved file must not be empty", file.length() > 0)

        val loaded = ProjectSerializer.loadProject(file)
        assertEquals(project.id, loaded.id)
        assertEquals(project.name, loaded.name)
        assertEquals(2, loaded.tracks.size)
        assertEquals(1, loaded.tracks[0].clips.size)
    }

    @Test
    fun testSchemaMigrationV0ToV1() {
        val legacyJson = JSONObject()
        legacyJson.put("id", "legacy-proj-1")
        legacyJson.put("name", "Legacy Project")
        legacyJson.put("schemaVersion", 0)

        val tracksArray = JSONArray()
        val trackObj = JSONObject()
        trackObj.put("id", "legacy-t1")
        trackObj.put("name", "V1")
        trackObj.put("type", "VIDEO")

        val clipsArray = JSONArray()
        val clipObj = JSONObject()
        clipObj.put("id", "legacy-c1")
        clipObj.put("trackId", "legacy-t1")
        clipObj.put("mediaUri", "content://video/1")
        clipObj.put("startTimeUs", 0L)
        clipObj.put("durationUs", 3_000_000L)
        // Note: transform, keyframes, effects, colorParams are omitted in legacy v0
        clipsArray.put(clipObj)
        trackObj.put("clips", clipsArray)
        tracksArray.put(trackObj)
        legacyJson.put("tracks", tracksArray)

        val migrated = ProjectSerializer.deserializeFromJson(legacyJson.toString())
        assertEquals(ProjectSerializer.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals("Legacy Project", migrated.name)
        val clip = migrated.tracks[0].clips[0]
        assertEquals(1.0f, clip.transform.scaleX, 0.001f)
        assertEquals(0, clip.keyframes.size)
        assertEquals(0, clip.effects.size)
        assertEquals(1.0f, clip.colorParams.contrast, 0.001f)
    }

    @Test
    fun testUndoRedoManager() {
        val undoManager = UndoRedoManager(maxHistorySize = 5)
        val state0 = Project(id = "0", name = "State 0")
        val state1 = Project(id = "1", name = "State 1")
        val state2 = Project(id = "2", name = "State 2")

        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        undoManager.pushState(state0)
        assertTrue(undoManager.canUndo)
        assertFalse(undoManager.canRedo)

        undoManager.pushState(state1)
        assertEquals(2, undoManager.undoCount)

        // Undo from state2
        val undone = undoManager.undo(state2)
        assertNotNull(undone)
        assertEquals("1", undone?.id)
        assertTrue(undoManager.canRedo)

        // Redo back to state2
        val redone = undoManager.redo(undone!!)
        assertNotNull(redone)
        assertEquals("2", redone?.id)
        assertFalse(undoManager.canRedo)

        // Push new state clears redo stack
        undoManager.undo(redone!!)
        assertTrue(undoManager.canRedo)
        val state3 = Project(id = "3", name = "State 3")
        undoManager.pushState(state3)
        assertFalse("New push must clear redo stack", undoManager.canRedo)
    }
}
