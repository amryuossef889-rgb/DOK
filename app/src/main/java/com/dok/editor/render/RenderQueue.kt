package com.dok.editor.render
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
enum class RenderStatus{QUEUED,RUNNING,PAUSED,COMPLETED,FAILED,CANCELLED}
data class RenderJob(val id:String=UUID.randomUUID().toString(),val name:String,val run:suspend (suspend (Float)->Unit)->Unit,var progress:Float=0f,var status:RenderStatus=RenderStatus.QUEUED,var error:String?=null)
class RenderQueue(private val scope:CoroutineScope=CoroutineScope(Dispatchers.Default)){
 private val mutex=Mutex();private val jobs=LinkedHashMap<String,RenderJob>();private val active=HashMap<String,Job>()
 suspend fun enqueue(job:RenderJob){mutex.withLock{jobs[job.id]=job}}
 suspend fun snapshot():List<RenderJob>=mutex.withLock{jobs.values.map{it.copy()}}
 suspend fun start(id:String){val j=mutex.withLock{jobs[id]?.also{it.status=RenderStatus.RUNNING}}?:return;active[id]=scope.launch{try{j.run{p->j.progress=p};mutex.withLock{j.status=RenderStatus.COMPLETED;j.progress=1f}}catch(_:CancellationException){mutex.withLock{j.status=RenderStatus.CANCELLED}}catch(t:Throwable){mutex.withLock{j.status=RenderStatus.FAILED;j.error=t.message}}}}
 fun cancel(id:String){active[id]?.cancel()}
 suspend fun retry(id:String){mutex.withLock{jobs[id]?.apply{status=RenderStatus.QUEUED;progress=0f;error=null}};start(id)}
}
