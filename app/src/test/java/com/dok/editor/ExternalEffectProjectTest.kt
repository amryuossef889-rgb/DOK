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

        val legacyObject = org.json.JSONObject(json).apply {
            put("schemaVersion", 1)
            remove("effectLibrary")
        }
        val legacyJson = legacyObject.toString()
        val migrated = runCatching { ProjectSerializer.deserializeFromJson(legacyJson) }
            .getOrElse { ProjectSerializer.deserializeFromJson(legacyJson.replace("\\\"schemaVersion\\":1", "\\\"schemaVersion\\": 1")) }
        assertEquals(2, migrated.schemaVersion)
        assertTrue(migrated.effectLibrary.isEmpty())
    }
}
