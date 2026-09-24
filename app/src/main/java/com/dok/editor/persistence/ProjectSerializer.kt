package com.dok.editor.persistence

import com.dok.editor.model.ColorGradingParams
import com.dok.editor.model.Effect
import com.dok.editor.model.EffectType
import com.dok.editor.model.InterpolationType
import com.dok.editor.model.Keyframe
import com.dok.editor.model.KeyframeProperty
import com.dok.editor.model.Project
import com.dok.editor.model.MediaAsset
import com.dok.editor.model.ExternalEffectAsset
import com.dok.editor.model.TextOverlayConfig
import com.dok.editor.model.TimelineClip
import com.dok.editor.model.Track
import com.dok.editor.model.TrackType
import com.dok.editor.model.Transform2D
import com.dok.editor.model.TransitionConfig
import com.dok.editor.model.TransitionType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ProjectSerializer {

    const val CURRENT_SCHEMA_VERSION = 2

    fun serializeToJson(project: Project): String {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("width", project.width)
        root.put("height", project.height)
        root.put("fps", project.fps)
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("createdAtMs", project.createdAtMs)
        root.put("modifiedAtMs", project.modifiedAtMs)

        val tracksArray = JSONArray()
        for (track in project.tracks) {
            val trackObj = JSONObject()
            trackObj.put("id", track.id)
            trackObj.put("name", track.name)
            trackObj.put("type", track.type.name)
            trackObj.put("isMuted", track.isMuted)
            trackObj.put("isSolo", track.isSolo)
            trackObj.put("isLocked", track.isLocked)
            trackObj.put("volumeDb", track.volumeDb.toDouble())
            trackObj.put("pan", track.pan.toDouble())

            val clipsArray = JSONArray()
            for (clip in track.clips) {
                val clipObj = JSONObject()
                clipObj.put("id", clip.id)
                clipObj.put("trackId", clip.trackId)
                clipObj.put("mediaUri", clip.mediaUri)
                clipObj.put("mediaName", clip.mediaName)
                clipObj.put("isOffline", clip.isOffline)
                clipObj.put("startTimeUs", clip.startTimeUs)
                clipObj.put("durationUs", clip.durationUs)
                clipObj.put("trimInUs", clip.trimInUs)
                clipObj.put("trimOutUs", clip.trimOutUs)
                clipObj.put("sourceDurationUs", clip.sourceDurationUs)
                clipObj.put("speed", clip.speed.toDouble())
                clipObj.put("volumeDb", clip.volumeDb.toDouble())
                clipObj.put("pan", clip.pan.toDouble())
                clipObj.put("fadeInUs", clip.fadeInUs)
                clipObj.put("fadeOutUs", clip.fadeOutUs)
                clipObj.put("isMuted", clip.isMuted)

                // Transform2D
                val transformObj = JSONObject()
                transformObj.put("positionX", clip.transform.positionX.toDouble())
                transformObj.put("positionY", clip.transform.positionY.toDouble())
                transformObj.put("scaleX", clip.transform.scaleX.toDouble())
                transformObj.put("scaleY", clip.transform.scaleY.toDouble())
                transformObj.put("rotationDegrees", clip.transform.rotationDegrees.toDouble())
                transformObj.put("opacity", clip.transform.opacity.toDouble())
                clipObj.put("transform", transformObj)

                // Keyframes
                val keyframesArray = JSONArray()
                for (kf in clip.keyframes) {
                    val kfObj = JSONObject()
                    kfObj.put("id", kf.id)
                    kfObj.put("timestampOffsetUs", kf.timestampOffsetUs)
                    kfObj.put("property", kf.property.name)
                    kfObj.put("value", kf.value.toDouble())
                    kfObj.put("interpolation", kf.interpolation.name)
                    keyframesArray.put(kfObj)
                }
                clipObj.put("keyframes", keyframesArray)

                // Color grading
                val colorObj = JSONObject()
                colorObj.put("brightness", clip.colorParams.brightness.toDouble())
                colorObj.put("contrast", clip.colorParams.contrast.toDouble())
                colorObj.put("saturation", clip.colorParams.saturation.toDouble())
                colorObj.put("temperature", clip.colorParams.temperature.toDouble())
                colorObj.put("vignette", clip.colorParams.vignette.toDouble())
                clip.colorParams.lutCubeUri?.let { colorObj.put("lutCubeUri", it) }
                clipObj.put("colorParams", colorObj)

                // Effects
                val effectsArray = JSONArray()
                for (fx in clip.effects) {
                    val fxObj = JSONObject()
                    fxObj.put("id", fx.id)
                    fxObj.put("effectType", fx.effectType.name)
                    fxObj.put("intensity", fx.intensity.toDouble())
                    val paramsObj = JSONObject()
                    for ((k, v) in fx.params) {
                        paramsObj.put(k, v.toDouble())
                    }
                    fxObj.put("params", paramsObj)
                    effectsArray.put(fxObj)
                }
                clipObj.put("effects", effectsArray)

                // Transitions
                clip.transitionIn?.let { tin ->
                    val tinObj = JSONObject()
                    tinObj.put("type", tin.type.name)
                    tinObj.put("durationUs", tin.durationUs)
                    clipObj.put("transitionIn", tinObj)
                }
                clip.transitionOut?.let { tout ->
                    val toutObj = JSONObject()
                    toutObj.put("type", tout.type.name)
                    toutObj.put("durationUs", tout.durationUs)
                    clipObj.put("transitionOut", toutObj)
                }

                clipObj.put("linkedClipId", clip.linkedClipId ?: JSONObject.NULL)
                val waveformArray = JSONArray()
                clip.waveform.forEach { waveformArray.put(it.toDouble()) }
                clipObj.put("waveform", waveformArray)

                // Text Overlay
                clip.textOverlay?.let { txt ->
                    val txtObj = JSONObject()
                    txtObj.put("text", txt.text)
                    txtObj.put("fontSizeSp", txt.fontSizeSp.toDouble())
                    txtObj.put("textColorHex", txt.textColorHex)
                    txtObj.put("backgroundColorHex", txt.backgroundColorHex)
                    txtObj.put("positionX", txt.positionX.toDouble())
                    txtObj.put("positionY", txt.positionY.toDouble())
                    txtObj.put("isBold", txt.isBold)
                    txtObj.put("isItalic", txt.isItalic)
                    clipObj.put("textOverlay", txtObj)
                }

                clipsArray.put(clipObj)
            }
            trackObj.put("clips", clipsArray)
            tracksArray.put(trackObj)
        }
        root.put("tracks", tracksArray)

        val mediaPoolArray = JSONArray()
        project.mediaPool.forEach { asset ->
            mediaPoolArray.put(JSONObject().apply {
                put("id", asset.id); put("uri", asset.uri); put("name", asset.name)
                put("durationUs", asset.durationUs); put("width", asset.width); put("height", asset.height)
                put("fps", asset.fps.toDouble()); put("sampleRate", asset.sampleRate); put("channels", asset.channels)
                put("codec", asset.codec); put("sizeBytes", asset.sizeBytes); put("isOffline", asset.isOffline)
                put("proxyUri", asset.proxyUri ?: "")
            })
        }
        root.put("mediaPool", mediaPoolArray)
        val effectsArray = JSONArray()
        project.effectLibrary.forEach { asset ->
            effectsArray.put(JSONObject().apply {
                put("id", asset.id); put("name", asset.name); put("uri", asset.uri)
                put("kind", asset.kind); put("mimeType", asset.mimeType); put("metadataJson", asset.metadataJson)
            })
        }
        root.put("effectLibrary", effectsArray)
        return root.toString(2)
    }

    fun deserializeFromJson(jsonString: String): Project {
        val root = JSONObject(jsonString)
        val schemaVersion = root.optInt("schemaVersion", 0)

        // Migrate if needed
        val migratedRoot = if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            migrate(root, schemaVersion, CURRENT_SCHEMA_VERSION)
        } else {
            root
        }

        val id = migratedRoot.getString("id")
        val name = migratedRoot.optString("name", "Untitled Gaming Edit")
        val width = migratedRoot.optInt("width", 1920)
        val height = migratedRoot.optInt("height", 1080)
        val fps = migratedRoot.optInt("fps", 30)
        val createdAtMs = migratedRoot.optLong("createdAtMs", System.currentTimeMillis())
        val modifiedAtMs = migratedRoot.optLong("modifiedAtMs", System.currentTimeMillis())

        val mediaPool = ArrayList<MediaAsset>()
        val mediaPoolArray = migratedRoot.optJSONArray("mediaPool") ?: JSONArray()
        for (i in 0 until mediaPoolArray.length()) {
            val o = mediaPoolArray.getJSONObject(i)
            mediaPool.add(
                MediaAsset(
                    id = o.optString("id"),
                    uri = o.optString("uri"),
                    name = o.optString("name"),
                    durationUs = o.optLong("durationUs"),
                    width = o.optInt("width"),
                    height = o.optInt("height"),
                    fps = o.optDouble("fps").toFloat(),
                    sampleRate = o.optInt("sampleRate"),
                    channels = o.optInt("channels"),
                    codec = o.optString("codec"),
                    sizeBytes = o.optLong("sizeBytes"),
                    isOffline = o.optBoolean("isOffline"),
                    proxyUri = o.optString("proxyUri").ifBlank { null }
                )
            )
        }

        val effectLibrary = ArrayList<ExternalEffectAsset>()
        val effectArray = migratedRoot.optJSONArray("effectLibrary") ?: JSONArray()
        for (i in 0 until effectArray.length()) {
            val o = effectArray.getJSONObject(i)
            effectLibrary.add(ExternalEffectAsset(o.optString("id"), o.optString("name"), o.optString("uri"), o.optString("kind", "effect"), o.optString("mimeType"), o.optString("metadataJson")))
        }

        val tracksArray = migratedRoot.optJSONArray("tracks") ?: JSONArray()
        val tracks = ArrayList<Track>()

        for (i in 0 until tracksArray.length()) {
            val trackObj = tracksArray.getJSONObject(i)
            val trackId = trackObj.getString("id")
            val trackName = trackObj.optString("name", "Track ${i + 1}")
            val trackType = TrackType.valueOf(trackObj.optString("type", TrackType.VIDEO.name))
            val isMuted = trackObj.optBoolean("isMuted", false)
            val isSolo = trackObj.optBoolean("isSolo", false)
            val isLocked = trackObj.optBoolean("isLocked", false)
            val volumeDb = trackObj.optDouble("volumeDb", 0.0).toFloat()
            val pan = trackObj.optDouble("pan", 0.0).toFloat()

            val clipsArray = trackObj.optJSONArray("clips") ?: JSONArray()
            val clips = ArrayList<TimelineClip>()

            for (j in 0 until clipsArray.length()) {
                val clipObj = clipsArray.getJSONObject(j)
                val clipId = clipObj.getString("id")
                val mediaUri = clipObj.getString("mediaUri")
                val mediaName = clipObj.optString("mediaName", "")
                val isOffline = clipObj.optBoolean("isOffline", false)
                val startTimeUs = clipObj.getLong("startTimeUs")
                val durationUs = clipObj.getLong("durationUs")
                val trimInUs = clipObj.optLong("trimInUs", 0L)
                val trimOutUs = clipObj.optLong("trimOutUs", 0L)
                val sourceDurationUs = clipObj.optLong("sourceDurationUs", 0L)
                val speed = clipObj.optDouble("speed", 1.0).toFloat()
                val volumeDbClip = clipObj.optDouble("volumeDb", 0.0).toFloat()
                val panClip = clipObj.optDouble("pan", 0.0).toFloat()
                val fadeInUs = clipObj.optLong("fadeInUs", 0L)
                val fadeOutUs = clipObj.optLong("fadeOutUs", 0L)
                val isMutedClip = clipObj.optBoolean("isMuted", false)

                // Transform2D
                val transformObj = clipObj.optJSONObject("transform")
                val transform = if (transformObj != null) {
                    Transform2D(
                        positionX = transformObj.optDouble("positionX", 0.0).toFloat(),
                        positionY = transformObj.optDouble("positionY", 0.0).toFloat(),
                        scaleX = transformObj.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = transformObj.optDouble("scaleY", 1.0).toFloat(),
                        rotationDegrees = transformObj.optDouble("rotationDegrees", 0.0).toFloat(),
                        opacity = transformObj.optDouble("opacity", 1.0).toFloat()
                    )
                } else {
                    Transform2D()
                }

                // Keyframes
                val keyframesArray = clipObj.optJSONArray("keyframes") ?: JSONArray()
                val keyframes = ArrayList<Keyframe>()
                for (k in 0 until keyframesArray.length()) {
                    val kfObj = keyframesArray.getJSONObject(k)
                    keyframes.add(
                        Keyframe(
                            id = kfObj.optString("id"),
                            timestampOffsetUs = kfObj.getLong("timestampOffsetUs"),
                            property = KeyframeProperty.valueOf(kfObj.getString("property")),
                            value = kfObj.getDouble("value").toFloat(),
                            interpolation = InterpolationType.valueOf(
                                kfObj.optString("interpolation", InterpolationType.LINEAR.name)
                            )
                        )
                    )
                }

                // Color grading
                val colorObj = clipObj.optJSONObject("colorParams")
                val colorParams = if (colorObj != null) {
                    ColorGradingParams(
                        brightness = colorObj.optDouble("brightness", 0.0).toFloat(),
                        contrast = colorObj.optDouble("contrast", 1.0).toFloat(),
                        saturation = colorObj.optDouble("saturation", 1.0).toFloat(),
                        temperature = colorObj.optDouble("temperature", 0.0).toFloat(),
                        vignette = colorObj.optDouble("vignette", 0.0).toFloat(),
                        lutCubeUri = if (colorObj.has("lutCubeUri")) colorObj.getString("lutCubeUri") else null
                    )
                } else {
                    ColorGradingParams()
                }

                // Effects
                val effectsArray = clipObj.optJSONArray("effects") ?: JSONArray()
                val effects = ArrayList<Effect.ParametricEffect>()
                for (e in 0 until effectsArray.length()) {
                    val fxObj = effectsArray.getJSONObject(e)
                    val fxType = EffectType.valueOf(fxObj.getString("effectType"))
                    val intensity = fxObj.optDouble("intensity", 1.0).toFloat()
                    val pObj = fxObj.optJSONObject("params")
                    val pMap = HashMap<String, Float>()
                    if (pObj != null) {
                        for (key in pObj.keys()) {
                            pMap[key] = pObj.getDouble(key).toFloat()
                        }
                    }
                    effects.add(
                        Effect.ParametricEffect(
                            id = fxObj.optString("id"),
                            effectType = fxType,
                            intensity = intensity,
                            params = pMap
                        )
                    )
                }

                // Transitions
                val tinObj = clipObj.optJSONObject("transitionIn")
                val transitionIn = tinObj?.let {
                    TransitionConfig(
                        type = TransitionType.valueOf(it.optString("type", TransitionType.CROSSFADE.name)),
                        durationUs = it.optLong("durationUs", 500_000L)
                    )
                }
                val toutObj = clipObj.optJSONObject("transitionOut")
                val transitionOut = toutObj?.let {
                    TransitionConfig(
                        type = TransitionType.valueOf(it.optString("type", TransitionType.CROSSFADE.name)),
                        durationUs = it.optLong("durationUs", 500_000L)
                    )
                }

                val linkedClipId = clipObj.optString("linkedClipId").ifBlank { null }
                val waveform = clipObj.optJSONArray("waveform")?.let { array ->
                    List(array.length()) { index -> array.optDouble(index).toFloat() }
                } ?: emptyList()

                // Text Overlay
                val txtObj = clipObj.optJSONObject("textOverlay")
                val textOverlay = txtObj?.let {
                    TextOverlayConfig(
                        text = it.optString("text", "Text Overlay"),
                        fontSizeSp = it.optDouble("fontSizeSp", 24.0).toFloat(),
                        textColorHex = it.optString("textColorHex", "#FFFFFF"),
                        backgroundColorHex = it.optString("backgroundColorHex", "#00000000"),
                        positionX = it.optDouble("positionX", 0.5).toFloat(),
                        positionY = it.optDouble("positionY", 0.5).toFloat(),
                        isBold = it.optBoolean("isBold", true),
                        isItalic = it.optBoolean("isItalic", false)
                    )
                }

                clips.add(
                    TimelineClip(
                        id = clipId,
                        trackId = trackId,
                        mediaUri = mediaUri,
                        mediaName = mediaName,
                        isOffline = isOffline,
                        startTimeUs = startTimeUs,
                        durationUs = durationUs,
                        trimInUs = trimInUs,
                        trimOutUs = trimOutUs,
                        sourceDurationUs = sourceDurationUs,
                        speed = speed,
                        volumeDb = volumeDbClip,
                        pan = panClip,
                        fadeInUs = fadeInUs,
                        fadeOutUs = fadeOutUs,
                        isMuted = isMutedClip,
                        transform = transform,
                        keyframes = keyframes,
                        colorParams = colorParams,
                        effects = effects,
                        transitionIn = transitionIn,
                        transitionOut = transitionOut,
                        textOverlay = textOverlay,
                        linkedClipId = linkedClipId,
                        waveform = waveform
                    )
                )
            }

            tracks.add(
                Track(
                    id = trackId,
                    name = trackName,
                    type = trackType,
                    isMuted = isMuted,
                    isSolo = isSolo,
                    isLocked = isLocked,
                    volumeDb = volumeDb,
                    pan = pan,
                    clips = clips
                )
            )
        }

        return Project(
            id = id,
            name = name,
            width = width,
            height = height,
            fps = fps,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            tracks = tracks,
            mediaPool = mediaPool,
            effectLibrary = effectLibrary,
            createdAtMs = createdAtMs,
            modifiedAtMs = modifiedAtMs
        )
    }

    fun migrate(oldJson: JSONObject, oldVersion: Int, targetVersion: Int): JSONObject {
        var current = oldJson
        var v = oldVersion
        while (v < targetVersion) {
            current = when (v) {
                0 -> migrateV0ToV1(current)
                1 -> migrateV1ToV2(current)
                else -> current
            }
            v++
        }
        current.put("schemaVersion", targetVersion)
        return current
    }

    private fun migrateV0ToV1(v0: JSONObject): JSONObject {
        val v1 = JSONObject(v0.toString())
        // In v0 -> v1 migration, ensure all clips have default transform, effects array, and colorParams
        val tracksArray = v1.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until tracksArray.length()) {
            val trackObj = tracksArray.getJSONObject(i)
            val clipsArray = trackObj.optJSONArray("clips") ?: JSONArray()
            for (j in 0 until clipsArray.length()) {
                val clipObj = clipsArray.getJSONObject(j)
                if (!clipObj.has("transform")) {
                    val defaultTransform = JSONObject()
                    defaultTransform.put("positionX", 0.0)
                    defaultTransform.put("positionY", 0.0)
                    defaultTransform.put("scaleX", 1.0)
                    defaultTransform.put("scaleY", 1.0)
                    defaultTransform.put("rotationDegrees", 0.0)
                    defaultTransform.put("opacity", 1.0)
                    clipObj.put("transform", defaultTransform)
                }
                if (!clipObj.has("keyframes")) {
                    clipObj.put("keyframes", JSONArray())
                }
                if (!clipObj.has("effects")) {
                    clipObj.put("effects", JSONArray())
                }
                if (!clipObj.has("colorParams")) {
                    val defaultColor = JSONObject()
                    defaultColor.put("brightness", 0.0)
                    defaultColor.put("contrast", 1.0)
                    defaultColor.put("saturation", 1.0)
                    defaultColor.put("temperature", 0.0)
                    defaultColor.put("vignette", 0.0)
                    clipObj.put("colorParams", defaultColor)
                }
            }
        }
        return v1
    }

    private fun migrateV1ToV2(v1: JSONObject): JSONObject {
        val v2 = JSONObject(v1.toString())
        if (!v2.has("effectLibrary")) v2.put("effectLibrary", JSONArray())
        return v2
    }

    /**
     * Atomically writes project JSON to destination file by writing to a temporary file
     * and renaming it into place.
     */
    @Synchronized
    fun saveProjectAtomically(file: File, project: Project) {
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        val tempFile = File(file.parentFile, "${file.name}.tmp.${System.nanoTime()}")
        val json = serializeToJson(project)
        FileOutputStream(tempFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(json)
                writer.flush()
            }
            try {
                fos.fd.sync() // Ensure write hits persistent storage where supported
            } catch (_: Exception) {
                // Ignore on virtual/tmpfs environments
            }
        }

        if (file.exists()) {
            file.delete()
        }
        if (!tempFile.renameTo(file)) {
            // Fallback copy if rename fails across mount boundaries
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    @Synchronized
    fun loadProject(file: File): Project {
        val json = file.readText(StandardCharsets.UTF_8)
        return deserializeFromJson(json)
    }
}
