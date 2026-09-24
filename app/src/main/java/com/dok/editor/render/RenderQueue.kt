package com.dok.editor.render

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class RenderStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class RenderJobSpec(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val projectId: String,
    val outputPath: String,
    val presetId: String,
    val projectSnapshotPath: String = ""
)

data class RenderJob(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val run: suspend (suspend (Float) -> Unit) -> Unit,
    var progress: Float = 0f,
    var status: RenderStatus = RenderStatus.QUEUED,
    var error: String? = null
)

class RenderQueue(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val mutex = Mutex()
    private val jobs = LinkedHashMap<String, RenderJob>()
    private val active = HashMap<String, Job>()
    private val _state = MutableStateFlow<List<RenderJob>>(emptyList())
    val state: StateFlow<List<RenderJob>> = _state.asStateFlow()

    private suspend fun publish() {
        _state.value = mutex.withLock { jobs.values.map { it.copy() } }
    }

    suspend fun contains(id: String): Boolean = mutex.withLock { jobs.containsKey(id) }

    suspend fun status(id: String): RenderStatus? = mutex.withLock { jobs[id]?.status }

    suspend fun enqueue(job: RenderJob) {
        mutex.withLock { jobs[job.id] = job }
        publish()
    }

    suspend fun snapshot(): List<RenderJob> =
        mutex.withLock { jobs.values.map { it.copy() } }

    suspend fun remove(id: String) {
        active[id]?.cancel()
        mutex.withLock { jobs.remove(id) }
        publish()
    }

    suspend fun clearFinished() {
        mutex.withLock { jobs.entries.removeIf { it.value.status == RenderStatus.COMPLETED || it.value.status == RenderStatus.CANCELLED } }
        publish()
    }

    suspend fun startQueuedSequentially() {
        while (true) {
            val next = mutex.withLock { jobs.values.firstOrNull { it.status == RenderStatus.QUEUED }?.id } ?: break
            start(next)
            active[next]?.join()
        }
    }

    suspend fun start(id: String) {
        val job = mutex.withLock {
            jobs[id]?.also {
                it.status = RenderStatus.RUNNING
                it.error = null
            }
        } ?: return
        active[id]?.cancel()
        active[id] = scope.launch {
            try {
                job.run { progress ->
                    mutex.withLock { job.progress = progress.coerceIn(0f, 1f) }
                    publish()
                }
                mutex.withLock {
                    job.status = RenderStatus.COMPLETED
                    job.progress = 1f
                }
                publish()
            } catch (_: CancellationException) {
                mutex.withLock { job.status = RenderStatus.CANCELLED }
                publish()
            } catch (t: Throwable) {
                mutex.withLock {
                    job.status = RenderStatus.FAILED
                    job.error = t.message ?: t::class.java.simpleName
                }
                publish()
            } finally {
                active.remove(id)
            }
        }
    }

    fun cancel(id: String) {
        active[id]?.cancel()
    }

    suspend fun retry(id: String) {
        mutex.withLock {
            jobs[id]?.apply {
                status = RenderStatus.QUEUED
                progress = 0f
                error = null
            }
        }
        publish()
        start(id)
    }
}
