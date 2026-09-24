package com.dok.editor.media

import android.content.Context
import android.net.Uri
import com.dok.editor.model.MediaAsset
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ProxySpec(val width: Int, val height: Int, val bitrate: Int)

class MediaPool(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("dok_media_pool", Context.MODE_PRIVATE) }
    private val assets = LinkedHashMap<String, MediaAsset>()
    private val proxyDir by lazy { File(context.filesDir, "media-pool/proxies").apply { mkdirs() } }
    private val cacheDir by lazy { File(context.filesDir, "media-pool/cache").apply { mkdirs() } }

    init { load() }

    @Synchronized fun add(asset: MediaAsset): MediaAsset {
        assets[asset.id] = asset
        persist()
        return asset
    }

    @Synchronized fun upsert(asset: MediaAsset): MediaAsset {
        assets[asset.id] = asset
        persist()
        return asset
    }

    @Synchronized fun find(id: String): MediaAsset? = assets[id]
    @Synchronized fun findByUri(uri: String): MediaAsset? = assets.values.firstOrNull { it.uri == uri }
    @Synchronized fun all(): List<MediaAsset> = assets.values.toList()

    @Synchronized fun remove(id: String): Boolean {
        val removed = assets.remove(id) != null
        if (removed) persist()
        return removed
    }

    @Synchronized fun relink(id: String, uri: Uri): MediaAsset? =
        assets[id]?.copy(uri = uri.toString(), isOffline = false)?.also {
            assets[id] = it
            persist()
        }

    @Synchronized fun markOffline(id: String) {
        assets[id]?.let {
            assets[id] = it.copy(isOffline = true)
            persist()
        }
    }

    fun proxyFile(asset: MediaAsset, spec: ProxySpec): File =
        File(proxyDir, "${asset.id}_${spec.width}x${spec.height}_${spec.bitrate}.mp4")

    fun cacheFile(asset: MediaAsset, key: String): File =
        File(cacheDir, "${asset.id}_$key.rgba")

    @Synchronized fun clearCache() { cacheDir.listFiles()?.forEach { it.delete() } }

    private fun load() {
        val raw = prefs.getString(KEY_ASSETS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val asset = MediaAsset(
                    id = o.optString("id"), uri = o.optString("uri"), name = o.optString("name"),
                    durationUs = o.optLong("durationUs"), width = o.optInt("width"), height = o.optInt("height"),
                    fps = o.optDouble("fps").toFloat(), sampleRate = o.optInt("sampleRate"),
                    channels = o.optInt("channels"), codec = o.optString("codec"), sizeBytes = o.optLong("sizeBytes"),
                    isOffline = o.optBoolean("isOffline"),
                    proxyUri = o.optString("proxyUri").ifBlank { null }
                )
                if (asset.id.isNotBlank() && asset.uri.isNotBlank()) assets[asset.id] = asset
            }
        }
    }

    private fun persist() {
        val array = JSONArray()
        assets.values.forEach { asset ->
            array.put(JSONObject().apply {
                put("id", asset.id); put("uri", asset.uri); put("name", asset.name)
                put("durationUs", asset.durationUs); put("width", asset.width); put("height", asset.height)
                put("fps", asset.fps.toDouble()); put("sampleRate", asset.sampleRate); put("channels", asset.channels)
                put("codec", asset.codec); put("sizeBytes", asset.sizeBytes); put("isOffline", asset.isOffline)
                put("proxyUri", asset.proxyUri ?: "")
            })
        }
        prefs.edit().putString(KEY_ASSETS, array.toString()).apply()
    }

    private companion object { const val KEY_ASSETS = "assets_v1" }
}
