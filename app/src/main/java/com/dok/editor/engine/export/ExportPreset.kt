package com.dok.editor.engine.export

data class ExportPreset(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateBps: Int,
    val audioBitrateBps: Int = 192_000,
    val audioSampleRate: Int = 44_100,
    val iFrameIntervalSec: Int = 1
) {
    companion object {
        val YOUTUBE_1080P_60 = ExportPreset(
            id = "yt_1080p60",
            name = "YouTube 1080p 60fps (High Quality)",
            width = 1920,
            height = 1080,
            fps = 60,
            videoBitrateBps = 14_000_000
        )

        val TIKTOK_SHORTS_1080P_60 = ExportPreset(
            id = "tiktok_1080p60",
            name = "TikTok / Shorts 1080x1920 60fps",
            width = 1080,
            height = 1920,
            fps = 60,
            videoBitrateBps = 12_000_000
        )

        val GAMING_4K_30 = ExportPreset(
            id = "gaming_4k30",
            name = "Gaming Master 4K 30fps",
            width = 3840,
            height = 2160,
            fps = 30,
            videoBitrateBps = 35_000_000,
            audioBitrateBps = 256_000
        )

        val FAST_720P_30 = ExportPreset(
            id = "fast_720p30",
            name = "Quick Share 720p 30fps",
            width = 1280,
            height = 720,
            fps = 30,
            videoBitrateBps = 4_500_000,
            audioBitrateBps = 128_000
        )

        val ALL_PRESETS = listOf(
            YOUTUBE_1080P_60,
            TIKTOK_SHORTS_1080P_60,
            GAMING_4K_30,
            FAST_720P_30
        )
    }
}
