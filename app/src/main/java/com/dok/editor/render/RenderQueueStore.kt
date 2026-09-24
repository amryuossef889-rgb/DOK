package com.dok.editor.render

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable metadata for queued renders. Execution lambdas remain runtime-only. */
class RenderQueueStore(context: Context) {
    private val file = File(context.filesDir, "render-queue.json")

    @Synchronized
    fun load(): List<RenderJobSpec> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val a = JSONArray(file.readText())
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(RenderJobSpec(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        projectId = o.optString("projectId"),
                        outputPath = o.optString("outputPath"),
                        presetId = o.optString("presetId")
                    ))
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun save(specs: List<RenderJobSpec>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray().apply {
            specs.forEach { spec ->
                put(JSONObject().apply {
                    put("id", spec.id)
                    put("name", spec.name)
                    put("projectId", spec.projectId)
                    put("outputPath", spec.outputPath)
                    put("presetId", spec.presetId)
                })
            }
        }.toString(2))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() { if (file.exists()) file.delete() }
}
