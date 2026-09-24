package com.dok.editor.engine.effects

import android.content.Context
import android.net.Uri
import com.dok.editor.model.ExternalEffectAsset
import com.dok.editor.model.EffectPreset
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ExternalEffectLibrary(context: Context) {
    private val root = File(context.filesDir, "effect-library").apply { mkdirs() }
    private val indexFile = File(root, "index.json")

    fun all(): List<ExternalEffectAsset> = load().first
    fun presets(): List<EffectPreset> = load().second

    @Synchronized
    fun importAsset(context: Context, uri: Uri, name: String, kind: String = "effect", mimeType: String = ""): ExternalEffectAsset {
        val id = UUID.randomUUID().toString()
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "asset" }
        val target = File(root, id + "_" + safeName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open external asset" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val asset = ExternalEffectAsset(id, name, target.toURI().toString(), kind, mimeType)
        val current = load()
        save(current.first + asset, current.second)
        return asset
    }

    @Synchronized
    fun remove(id: String) {
        val current = load()
        current.first.firstOrNull { it.id == id }?.let { runCatching { File(Uri.parse(it.uri).path ?: "").delete() } }
        save(current.first.filterNot { it.id == id }, current.second)
    }

    @Synchronized
    fun addPreset(preset: EffectPreset) {
        val current = load()
        save(current.first, current.second.filterNot { it.id == preset.id } + preset)
    }

    @Synchronized
    fun removePreset(id: String) {
        val current = load()
        save(current.first, current.second.filterNot { it.id == id })
    }

    private fun load(): Pair<List<ExternalEffectAsset>, List<EffectPreset>> {
        if (!indexFile.exists()) return emptyList<ExternalEffectAsset>() to emptyList()
        return runCatching {
            val rootJson = JSONObject(indexFile.readText())
            val assets = mutableListOf<ExternalEffectAsset>()
            val a = rootJson.optJSONArray("assets") ?: JSONArray()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                assets += ExternalEffectAsset(o.optString("id"), o.optString("name"), o.optString("uri"), o.optString("kind"), o.optString("mimeType"), o.optString("metadataJson"))
            }
            val presets = mutableListOf<EffectPreset>()
            val p = rootJson.optJSONArray("presets") ?: JSONArray()
            for (i in 0 until p.length()) {
                val o = p.getJSONObject(i)
                val params = mutableMapOf<String, Float>()
                val po = o.optJSONObject("parameters")
                if (po != null) for (key in po.keys()) params[key] = po.optDouble(key).toFloat()
                presets += EffectPreset(o.optString("id"), o.optString("name"), o.optString("effectType"), o.optDouble("intensity", 1.0).toFloat(), params, o.optString("sourceUri").ifBlank { null })
            }
            assets to presets
        }.getOrElse { emptyList<ExternalEffectAsset>() to emptyList() }
    }

    private fun save(assets: List<ExternalEffectAsset>, presets: List<EffectPreset>) {
        val json = JSONObject()
        json.put("assets", JSONArray().apply { assets.forEach { a -> put(JSONObject().apply {
            put("id", a.id); put("name", a.name); put("uri", a.uri); put("kind", a.kind); put("mimeType", a.mimeType); put("metadataJson", a.metadataJson)
        }) } })
        json.put("presets", JSONArray().apply { presets.forEach { p -> put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("effectType", p.effectType); put("intensity", p.intensity.toDouble()); put("sourceUri", p.sourceUri ?: "")
            put("parameters", JSONObject().apply { p.parameters.forEach { (k, v) -> put(k, v.toDouble()) } })
        }) } })
        indexFile.writeText(json.toString(2))
    }
}
