package com.dok.editor.media
import android.content.Context
import android.net.Uri
import com.dok.editor.model.MediaAsset
import java.io.File
data class ProxySpec(val width:Int,val height:Int,val bitrate:Int)
class MediaPool(private val context:Context){
 private val assets=LinkedHashMap<String,MediaAsset>()
 private val proxyDir by lazy{File(context.cacheDir,"proxies").apply{mkdirs()}}
 private val cacheDir by lazy{File(context.cacheDir,"media-cache").apply{mkdirs()}}
 fun add(asset:MediaAsset){assets[asset.id]=asset}
 fun all():List<MediaAsset>=assets.values.toList()
 fun relink(id:String,uri:Uri):MediaAsset?=assets[id]?.copy(uri=uri.toString(),isOffline=false)?.also{assets[id]=it}
 fun markOffline(id:String){assets[id]?.let{assets[id]=it.copy(isOffline=true)}}
 fun proxyFile(asset:MediaAsset,spec:ProxySpec)=File(proxyDir,asset.id+"_"+spec.width+"x"+spec.height+"_"+spec.bitrate+".mp4")
 fun cacheFile(asset:MediaAsset,key:String)=File(cacheDir,asset.id+"_"+key+".rgba")
 fun clearCache(){cacheDir.listFiles()?.forEach{it.delete()}}
}
