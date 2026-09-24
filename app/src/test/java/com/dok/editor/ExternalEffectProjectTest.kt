package com.dok.editor

import com.dok.editor.model.ExternalEffectAsset
import com.dok.editor.model.Project
import com.dok.editor.persistence.ProjectSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalEffectProjectTest {
    @Test
    fun externalEffectLibraryRoundTripsAndOldSchemaMigrates() {
        val asset = ExternalEffectAsset(
            id = "effect-1",
            name = "Film Look",
            uri = "file:///data/user/0/com.dok/effect-library/film.cube",
            kind = "lut",
            mimeType = "text/plain"
        )
        val project = Project(id = "p1", effectLibrary = listOf(asset))
        val json = ProjectSerializer.serializeToJson(project)
        assertTrue(json.contains("\"effectLibrary\""))
        val restored = ProjectSerializer.deserializeFromJson(json)
        assertEquals(2, restored.schemaVersion)
        assertEquals(1, restored.effectLibrary.size)
        assertEquals("Film Look", restored.effectLibrary.single().name)
        assertEquals("lut", restored.effectLibrary.single().kind)

        // Schema v1 is intentionally accepted without v2-only fields.
        val legacy = org.json.JSONObject().apply {
            put("id", "legacy")
            put("schemaVersion", 1)
            put("tracks", org.json.JSONArray())
        }
        val migrated = ProjectSerializer.deserializeFromJson(legacy.toString())
        assertEquals(2, migrated.schemaVersion)
        assertEquals("p1", migrated.id)
        assertTrue(migrated.effectLibrary.isEmpty())
    }
}
