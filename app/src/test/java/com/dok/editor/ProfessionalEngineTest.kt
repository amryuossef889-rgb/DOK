package com.dok.editor

import com.dok.editor.engine.AdvancedTimelineEditingEngine
import com.dok.editor.engine.KeyframeEngine
import com.dok.editor.model.Keyframe
import com.dok.editor.model.KeyframeProperty
import com.dok.editor.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalEngineTest {
    @Test fun keyframeInterpolationWorks() {
        val frames = listOf(
            Keyframe(timestampOffsetUs = 0, property = KeyframeProperty.POSITION_X, value = 0f),
            Keyframe(timestampOffsetUs = 1_000_000, property = KeyframeProperty.POSITION_X, value = 100f)
        )
        assertEquals(50f, KeyframeEngine.evaluate(frames, 500_000, 0f), 0.01f)
    }

    @Test fun keyframeUpsertReplacesSameTimeAndProperty() {
        val first = Keyframe(timestampOffsetUs = 0, property = KeyframeProperty.OPACITY, value = 0f)
        val result = KeyframeEngine.upsert(listOf(first), 0, KeyframeProperty.OPACITY, 1f)
        assertEquals(1, result.size)
        assertEquals(1f, result.single().value, 0.01f)
    }

    @Test fun timecodeIsFrameAware() {
        assertEquals("00:00:01:15", EditorViewModel.formatTimecode(1_500_000, 30))
    }

    @Test fun advancedEngineMissingClipIsSafe() {
        val project = EditorViewModel.createDefaultProject()
        val result = AdvancedTimelineEditingEngine.rollEdit(project, "missing-a", "missing-b", 10_000)
        assertTrue(result === project)
    }
}
