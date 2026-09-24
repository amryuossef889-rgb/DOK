package com.dok.editor.engine.plan

import com.dok.editor.engine.audio.PcmMixer
import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.InterpolationType
import com.dok.editor.model.Keyframe
import com.dok.editor.model.KeyframeProperty
import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.TrackType
import com.dok.editor.model.Transform2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class RenderFrameInstruction(
    val clipId: String,
    val mediaUri: String,
    val mediaSourceTimeUs: Long,
    val trackIndex: Int,
    val transform: Transform2D,
    val colorParams: ColorGradingParams,
    val effects: List<Effect.ParametricEffect>,
    val opacity: Float,
    val transitionProgress: Float? = null
)

data class RenderTextInstruction(
    val text: String,
    val fontSizeSp: Float,
    val textColorHex: String,
    val backgroundColorHex: String,
    val positionX: Float,
    val positionY: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val opacity: Float
)

data class CompositedVideoPlan(
    val timeUs: Long,
    val projectWidth: Int,
    val projectHeight: Int,
    val frameInstructions: List<RenderFrameInstruction>,
    val textInstructions: List<RenderTextInstruction>
)

data class AudioMixInstruction(
    val clipId: String,
    val mediaUri: String,
    val sourceStartFrame44k: Long,
    val frameCount: Int,
    val combinedLinearGain: Float,
    val panGains: Pair<Float, Float>,
    val fadeMultiplier: Float,
    val speed: Float
)

object TimelineRenderPlan {

    /**
     * Evaluates video compositing state for any timeline instant.
     * This is the single source of truth shared identically between preview and export.
     */
    fun evaluateVideoAt(project: Project, timeUs: Long): CompositedVideoPlan {
        val frameInstructions = ArrayList<RenderFrameInstruction>()
        val textInstructions = ArrayList<RenderTextInstruction>()

        val videoTracks = project.tracks.filter { it.type == TrackType.VIDEO }
        for ((trackIdx, track) in videoTracks.withIndex()) {
            if (track.isMuted) continue

            for (clip in track.clips) {
                if (clip.isMuted || clip.isOffline) continue
                if (timeUs in clip.startTimeUs until clip.endTimeUs) {
                    val timelineOffsetUs = timeUs - clip.startTimeUs
                    val mediaSourceTimeUs = clip.trimInUs + (timelineOffsetUs * clip.speed).toLong()

                    // Keyframe interpolated transform
                    val interpolatedTransform = interpolateTransform(clip, timelineOffsetUs)

                    // Fade envelope
                    val fadeGain = PcmMixer.calculateFadeEnvelope(
                        currentPositionUs = timeUs,
                        clipStartTimeUs = clip.startTimeUs,
                        clipDurationUs = clip.durationUs,
                        fadeInUs = clip.fadeInUs,
                        fadeOutUs = clip.fadeOutUs
                    )

                    // Transition in/out handling
                    var transitionProgress: Float? = null
                    var transitionOpacity = 1.0f

                    clip.transitionIn?.let { tin ->
                        if (timelineOffsetUs < tin.durationUs && tin.durationUs > 0) {
                            val prog = (timelineOffsetUs.toFloat() / tin.durationUs.toFloat()).coerceIn(0f, 1f)
                            transitionProgress = prog
                            transitionOpacity *= prog
                        }
                    }

                    clip.transitionOut?.let { tout ->
                        val remainingUs = clip.durationUs - timelineOffsetUs
                        if (remainingUs < tout.durationUs && tout.durationUs > 0) {
                            val prog = (remainingUs.toFloat() / tout.durationUs.toFloat()).coerceIn(0f, 1f)
                            transitionProgress = prog
                            transitionOpacity *= prog
                        }
                    }

                    val finalOpacity = (interpolatedTransform.opacity * fadeGain * transitionOpacity).coerceIn(0f, 1f)

                    frameInstructions.add(
                        RenderFrameInstruction(
                            clipId = clip.id,
                            mediaUri = clip.mediaUri,
                            mediaSourceTimeUs = mediaSourceTimeUs,
                            trackIndex = trackIdx,
                            transform = interpolatedTransform,
                            colorParams = clip.colorParams,
                            effects = clip.effects,
                            opacity = finalOpacity,
                            transitionProgress = transitionProgress
                        )
                    )

                    // Text overlay on clip if present
                    clip.textOverlay?.let { txt ->
                        textInstructions.add(
                            RenderTextInstruction(
                                text = txt.text,
                                fontSizeSp = txt.fontSizeSp,
                                textColorHex = txt.textColorHex,
                                backgroundColorHex = txt.backgroundColorHex,
                                positionX = txt.positionX,
                                positionY = txt.positionY,
                                isBold = txt.isBold,
                                isItalic = txt.isItalic,
                                opacity = finalOpacity
                            )
                        )
                    }
                }
            }
        }

        // Dedicated Text Tracks
        val textTracks = project.tracks.filter { it.type == TrackType.TEXT }
        for (track in textTracks) {
            if (track.isMuted) continue
            for (clip in track.clips) {
                if (clip.isMuted) continue
                if (timeUs in clip.startTimeUs until clip.endTimeUs) {
                    clip.textOverlay?.let { txt ->
                        val timelineOffsetUs = timeUs - clip.startTimeUs
                        val fadeGain = PcmMixer.calculateFadeEnvelope(
                            currentPositionUs = timeUs,
                            clipStartTimeUs = clip.startTimeUs,
                            clipDurationUs = clip.durationUs,
                            fadeInUs = clip.fadeInUs,
                            fadeOutUs = clip.fadeOutUs
                        )
                        textInstructions.add(
                            RenderTextInstruction(
                                text = txt.text,
                                fontSizeSp = txt.fontSizeSp,
                                textColorHex = txt.textColorHex,
                                backgroundColorHex = txt.backgroundColorHex,
                                positionX = txt.positionX,
                                positionY = txt.positionY,
                                isBold = txt.isBold,
                                isItalic = txt.isItalic,
                                opacity = fadeGain
                            )
                        )
                    }
                }
            }
        }

        return CompositedVideoPlan(
            timeUs = timeUs,
            projectWidth = project.width,
            projectHeight = project.height,
            frameInstructions = frameInstructions,
            textInstructions = textInstructions
        )
    }

    /**
     * Evaluates audio mixing instructions for a given frame window at 44.1kHz.
     */
    fun evaluateAudioRange(
        project: Project,
        startFrame44k: Long,
        frameCount: Int
    ): List<AudioMixInstruction> {
        val instructions = ArrayList<AudioMixInstruction>()
        val startUs = (startFrame44k * 1_000_000L) / PcmMixer.SAMPLE_RATE_44K
        val durationUs = (frameCount.toLong() * 1_000_000L) / PcmMixer.SAMPLE_RATE_44K
        val endUs = startUs + durationUs

        val hasSoloTrack = project.tracks.any { it.isSolo }

        for (track in project.tracks) {
            if (track.isMuted) continue
            if (hasSoloTrack && !track.isSolo) continue

            val trackGain = PcmMixer.dbToLinear(track.volumeDb)
            val trackPanGains = PcmMixer.calculatePanGains(track.pan)

            for (clip in track.clips) {
                if (clip.isMuted || clip.isOffline) continue
                if (clip.startTimeUs < endUs && clip.endTimeUs > startUs) {
                    val clipGain = PcmMixer.dbToLinear(clip.volumeDb)
                    val clipPanGains = PcmMixer.calculatePanGains(clip.pan)
                    val combinedPan = Pair(
                        trackPanGains.first * clipPanGains.first,
                        trackPanGains.second * clipPanGains.second
                    )

                    // Clip local source frame at start
                    val offsetUs = maxOf(0L, startUs - clip.startTimeUs)
                    val sourceMediaTimeUs = clip.trimInUs + (offsetUs * clip.speed).toLong()
                    val sourceStartFrame = (sourceMediaTimeUs * PcmMixer.SAMPLE_RATE_44K) / 1_000_000L

                    val fadeGain = PcmMixer.calculateFadeEnvelope(
                        currentPositionUs = startUs,
                        clipStartTimeUs = clip.startTimeUs,
                        clipDurationUs = clip.durationUs,
                        fadeInUs = clip.fadeInUs,
                        fadeOutUs = clip.fadeOutUs
                    )

                    instructions.add(
                        AudioMixInstruction(
                            clipId = clip.id,
                            mediaUri = clip.mediaUri,
                            sourceStartFrame44k = sourceStartFrame,
                            frameCount = frameCount,
                            combinedLinearGain = trackGain * clipGain,
                            panGains = combinedPan,
                            fadeMultiplier = fadeGain,
                            speed = clip.speed
                        )
                    )
                }
            }
        }

        return instructions
    }

    fun interpolateTransform(clip: TimelineClip, offsetUs: Long): Transform2D {
        val base = clip.transform
        if (clip.keyframes.isEmpty()) return base

        val posX = interpolateProperty(clip.keyframes, KeyframeProperty.POSITION_X, offsetUs, base.positionX)
        val posY = interpolateProperty(clip.keyframes, KeyframeProperty.POSITION_Y, offsetUs, base.positionY)
        val scaleX = interpolateProperty(clip.keyframes, KeyframeProperty.SCALE_X, offsetUs, base.scaleX)
        val scaleY = interpolateProperty(clip.keyframes, KeyframeProperty.SCALE_Y, offsetUs, base.scaleY)
        val rot = interpolateProperty(clip.keyframes, KeyframeProperty.ROTATION, offsetUs, base.rotationDegrees)
        val opac = interpolateProperty(clip.keyframes, KeyframeProperty.OPACITY, offsetUs, base.opacity)

        return Transform2D(
            positionX = posX,
            positionY = posY,
            scaleX = scaleX,
            scaleY = scaleY,
            rotationDegrees = rot,
            opacity = opac
        )
    }

    private fun interpolateProperty(
        keyframes: List<Keyframe>,
        property: KeyframeProperty,
        offsetUs: Long,
        defaultValue: Float
    ): Float {
        val propKeyframes = keyframes.filter { it.property == property }.sortedBy { it.timestampOffsetUs }
        if (propKeyframes.isEmpty()) return defaultValue
        if (offsetUs <= propKeyframes.first().timestampOffsetUs) return propKeyframes.first().value
        if (offsetUs >= propKeyframes.last().timestampOffsetUs) return propKeyframes.last().value

        for (i in 0 until propKeyframes.size - 1) {
            val k0 = propKeyframes[i]
            val k1 = propKeyframes[i + 1]
            if (offsetUs in k0.timestampOffsetUs..k1.timestampOffsetUs) {
                val span = (k1.timestampOffsetUs - k0.timestampOffsetUs).toFloat()
                if (span <= 0f) return k0.value
                val t = ((offsetUs - k0.timestampOffsetUs) / span).coerceIn(0f, 1f)
                val easedT = applyEasing(t, k0.interpolation)
                return k0.value + (k1.value - k0.value) * easedT
            }
        }

        return defaultValue
    }

    private fun applyEasing(t: Float, type: InterpolationType): Float {
        return when (type) {
            InterpolationType.LINEAR -> t
            InterpolationType.EASE_IN -> t * t
            InterpolationType.EASE_OUT -> sin(t * PI.toFloat() / 2f)
            InterpolationType.EASE_IN_OUT -> (1f - cos(t * PI.toFloat())) / 2f
        }
    }
}
