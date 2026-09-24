package com.dok.editor

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dok.editor.engine.export.ExportPreset
import com.dok.editor.service.ExportForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportPipelineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testExportPresetsConfiguration() {
        val yt = ExportPreset.YOUTUBE_1080P_60
        assertEquals(1920, yt.width)
        assertEquals(1080, yt.height)
        assertEquals(60, yt.fps)
        assertTrue(yt.videoBitrateBps >= 10_000_000)

        val tiktok = ExportPreset.TIKTOK_SHORTS_1080P_60
        assertEquals(1080, tiktok.width)
        assertEquals(1920, tiktok.height)
        assertEquals(60, tiktok.fps)

        val gaming4k = ExportPreset.GAMING_4K_30
        assertEquals(3840, gaming4k.width)
        assertEquals(2160, gaming4k.height)
        assertEquals(30, gaming4k.fps)
        assertEquals(256_000, gaming4k.audioBitrateBps)

        val fast720p = ExportPreset.FAST_720P_30
        assertEquals(1280, fast720p.width)
        assertEquals(720, fast720p.height)

        assertEquals(4, ExportPreset.ALL_PRESETS.size)
    }

    @Test
    fun testExportServiceIntentCreation() {
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_START_EXPORT
            putExtra(ExportForegroundService.EXTRA_PROJECT_FILE_PATH, "/tmp/test.dok")
            putExtra(ExportForegroundService.EXTRA_PRESET_ID, ExportPreset.YOUTUBE_1080P_60.id)
        }

        assertNotNull(intent)
        assertEquals(ExportForegroundService.ACTION_START_EXPORT, intent.action)
        assertEquals("/tmp/test.dok", intent.getStringExtra(ExportForegroundService.EXTRA_PROJECT_FILE_PATH))
        assertEquals(ExportPreset.YOUTUBE_1080P_60.id, intent.getStringExtra(ExportForegroundService.EXTRA_PRESET_ID))
    }
}
