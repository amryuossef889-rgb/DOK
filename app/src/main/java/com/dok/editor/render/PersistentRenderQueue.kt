package com.dok.editor.render

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Durable render queue. Job specifications are persisted independently from
 * runtime runner lambdas so a process restart never corrupts metadata.
 */
class PersistentRenderQueue(
    context: Context,
    scope: CoroutineScope
) {
    private val store = RenderQueueStore(context)
    private val queue = RenderQueue(scope)
    val state: StateFlow<List<RenderJob>> = queue.state

    suspend fun restoreSpecs(): List<RenderJobSpec> = store.load()

    suspend fun enqueue(
        spec: RenderJobSpec,
        runner: suspend (suspend (Float) -> Unit) -> Unit
    ) {
        queue.enqueue(RenderJob(spec.id, spec.name, runner))
        val persisted = store.load().filterNot { it.id == spec.id } + spec
        store.save(persisted)
    }

    suspend fun persistSnapshot() {
        val persisted = store.load().associateBy { it.id }.toMutableMap()
        queue.snapshot().forEach { job ->
            val old = persisted[job.id]
            if (old != null) persisted[job.id] = old.copy(name = job.name)
        }
        store.save(persisted.values.toList())
    }

    suspend fun startQueuedSequentially() {
        queue.startQueuedSequentially()
        persistSnapshot()
    }

    suspend fun remove(id: String) {
        queue.remove(id)
        store.remove(id)
    }

    fun cancel(id: String) = queue.cancel(id)

    suspend fun snapshot() = queue.snapshot()
}
