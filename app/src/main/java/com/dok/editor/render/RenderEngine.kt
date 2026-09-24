package com.dok.editor.render

import com.dok.editor.model.Project

data class RenderFrame(
    val timeUs: Long,
    val width: Int,
    val height: Int,
    val isKeyFrame: Boolean = false
)

interface VideoRenderBackend {
    fun render(project: Project, timeUs: Long): RenderFrame
}

interface AudioRenderBackend {
    fun render(project: Project, startTimeUs: Long, durationUs: Long): FloatArray
}

class RenderEngine(
    private val video: VideoRenderBackend,
    private val audio: AudioRenderBackend
) {
    fun renderFrame(project: Project, timeUs: Long): RenderFrame =
        video.render(project, timeUs)

    fun renderAudio(project: Project, startTimeUs: Long, durationUs: Long): FloatArray =
        audio.render(project, startTimeUs, durationUs)
}
