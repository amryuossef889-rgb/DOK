package com.dok.editor

import com.dok.editor.model.ExternalEffectAsset
import com.dok.editor.model.Project
import com.dok.editor.persistence.ProjectSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

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

        val legacy = JSONObject().apply {
            put("id", "legacy")
            put("schemaVersion", 1)
            put("name", "Legacy")
            put("width", 1920)
            put("height", 1080)
            put("fps", 30)
            put("tracks", org.json.JSONArray())
            put("mediaPool", org.json.JSONArray())
        }
        val migrated = ProjectSerializer.deserializeFromJson(legacy.toString())
        assertEquals(2, migrated.schemaVersion)
        assertTrue(migrated.effectLibrary.isEmpty())
    }
}
