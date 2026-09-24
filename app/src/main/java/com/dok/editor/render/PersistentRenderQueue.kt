package com.dok.editor.render

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Durable queue facade. Specs survive process death; executable runners are
 * attached by the caller when the project/preset is available again.
 */
class PersistentRenderQueue(
    context: Context,
    scope: CoroutineScope
) {
    private val store = RenderQueueStore(context)
    private val queue = RenderQueue(scope)

    suspend fun restoreSpecs(): List<RenderJobSpec> = store.load()

    suspend fun enqueue(spec: RenderJobSpec, runner: suspend (suspend (Float) -> Unit) -> Unit) {
        queue.enqueue(RenderJob(spec.id, spec.name, runner))
        store.save(queue.snapshot().map { RenderJobSpec(it.id, it.name, spec.projectId, spec.outputPath, spec.presetId) })
    }

    suspend fun startQueuedSequentially() = queue.startQueuedSequentially()

    suspend fun remove(id: String) {
        queue.remove(id)
        store.save(queue.snapshot().map { RenderJobSpec(it.id, it.name, "", "", "") })
    }

    fun cancel(id: String) = queue.cancel(id)

    suspend fun snapshot() = queue.snapshot()
}
