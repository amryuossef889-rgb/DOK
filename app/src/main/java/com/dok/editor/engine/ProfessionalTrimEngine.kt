package com.dok.editor.engine

import com.dok.editor.model.Project
import com.dok.editor.model.TimelineClip
import kotlin.math.max

enum class TrimMode { RIPPLE, ROLL, SLIP, SLIDE, INSERT, OVERWRITE, REPLACE }

object ProfessionalTrimEngine {
    fun apply(project:Project,mode:TrimMode,clipId:String,deltaUs:Long):Project = when(mode){
        TrimMode.ROLL -> AdvancedTimelineEditingEngine.rollEdit(project,neighborLeft(project,clipId),clipId,deltaUs)
        TrimMode.SLIP -> AdvancedTimelineEditingEngine.slipEdit(project,clipId,deltaUs)
        TrimMode.SLIDE -> AdvancedTimelineEditingEngine.slideEdit(project,clipId,deltaUs)
        TrimMode.RIPPLE -> ripple(project,clipId,deltaUs)
        TrimMode.INSERT -> ripple(project,clipId,deltaUs)
        TrimMode.OVERWRITE,TrimMode.REPLACE -> project
    }
    private fun ripple(p:Project,id:String,d:Long):Project{
        val c=find(p,id)?:return p
        val tracks=p.tracks.map{t->t.copy(clips=t.clips.map{if(it.trackId==c.trackId&&it.startTimeUs>=c.endTimeUs)it.copy(startTimeUs=(it.startTimeUs+d).coerceAtLeast(c.startTimeUs)) else it})}
        return p.copy(tracks=tracks,modifiedAtMs=System.currentTimeMillis())
    }
    private fun neighborLeft(p:Project,id:String):String=findTrack(p,id)?.clips?.sortedBy{it.startTimeUs}?.let{xs->val i=xs.indexOfFirst{x->x.id==id};if(i>0)xs[i-1].id else id}?:id
    private fun find(p:Project,id:String)=p.tracks.asSequence().flatMap{it.clips.asSequence()}.firstOrNull{it.id==id}
    private fun findTrack(p:Project,id:String)=p.tracks.firstOrNull{t->t.clips.any{it.id==id}}
}
