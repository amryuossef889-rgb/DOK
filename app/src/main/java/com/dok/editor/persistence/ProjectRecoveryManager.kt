package com.dok.editor.persistence
import android.content.Context
import java.io.File
import java.util.UUID
class ProjectRecoveryManager(private val context:Context){
 private val root by lazy{File(context.filesDir,"recovery").apply{mkdirs()}}
 fun snapshot(projectId:String,json:String):File{val dir=File(root,projectId).apply{mkdirs()};val f=File(dir,System.currentTimeMillis().toString()+"_"+UUID.randomUUID()+".json");f.writeText(json);return f}
 fun autosave(projectId:String,json:String):File{val dir=File(root,projectId).apply{mkdirs()};val f=File(dir,"autosave.json");val t=File(dir,"autosave.tmp");t.writeText(json);if(!t.renameTo(f)){f.writeText(t.readText());t.delete()};return f}
 fun snapshots(projectId:String):List<File>{val dir=File(root,projectId);return dir.listFiles()?.filter{it.extension=="json" && it.name!="autosave.json"}?.sortedByDescending{it.lastModified()}?:emptyList()}
 fun deleteSnapshot(projectId:String,fileName:String):Boolean{val dir=File(root,projectId);val f=File(dir,fileName);return f.exists() && f.delete()}
 fun recover(projectId:String):String?{val dir=File(root,projectId);val a=File(dir,"autosave.json");if(a.exists())return a.readText();return dir.listFiles()?.filter{it.extension=="json"}?.maxByOrNull{it.lastModified()}?.readText()}
 fun prune(projectId:String,keep:Int=20){val dir=File(root,projectId);dir.listFiles()?.filter{it.name!="autosave.json"}?.sortedByDescending{it.lastModified()}?.drop(keep)?.forEach{it.delete()}}
}
