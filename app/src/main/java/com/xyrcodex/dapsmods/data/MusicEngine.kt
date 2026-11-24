package com.xyrcodex.dapsmods.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Song(val id:String,val title:String,val artist:String,val thumbnail:String,val duration:String,val url:String="https://www.youtube.com/watch?v=$id")
data class Lyrics(val synced:Boolean,val lines:List<Pair<Long,String>>)

object MusicEngine {
    private val client = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
    private const val YTM = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private val headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36","Origin" to "https://music.youtube.com","Referer" to "https://music.youtube.com/")
    suspend fun search(query:String):List<Song> = withContext(Dispatchers.IO) {
        if(query.isBlank()) return@withContext emptyList()
        val body=JSONObject().put("context",JSONObject().put("client",JSONObject().put("clientName","WEB_REMIX").put("clientVersion","1.20260401.01.00").put("hl","en").put("gl","ID"))).put("query",query).put("params","Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo=").toString()
        val req=Request.Builder().url(YTM).headers(okhttp3.Headers.headersOf(*headers.flatMap{listOf(it.key,it.value)}.toTypedArray())).post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(),body)).build()
        client.newCall(req).execute().use { res ->
            if(!res.isSuccessful) error("Music search HTTP ${res.code}")
            val root=JSONObject(res.body?.string().orEmpty())
            val out=linkedMapOf<String,Song>()
            fun walk(node:Any?) {
                when(node) {
                    is JSONObject -> {
                        val renderer=node.optJSONObject("musicResponsiveListItemRenderer")
                        if(renderer!=null) {
                            val cols=renderer.optJSONArray("flexColumns")
                            val texts=mutableListOf<String>()
                            if(cols!=null) for(i in 0 until cols.length()) {
                                val runs=cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.optJSONArray("runs")
                                if(runs!=null) for(j in 0 until runs.length()) texts.add(runs.optJSONObject(j)?.optString("text").orEmpty())
                            }
                            val id=renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty().ifBlank {
                                findVideoId(renderer)
                            }
                            val title=texts.firstOrNull().orEmpty()
                            val artist=texts.drop(1).firstOrNull{it.isNotBlank() && !it.matches(Regex("\\d+:\\d+"))}.orEmpty().ifBlank{"Unknown Artist"}
                            val thumbs=renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumb=thumbs?.optJSONObject((thumbs.length()-1).coerceAtLeast(0))?.optString("url").orEmpty()
                            val duration=texts.firstOrNull{it.matches(Regex("\\d+:\\d+"))}.orEmpty()
                            if(id.isNotBlank() && title.isNotBlank()) out[id]=Song(id,title,artist,thumb,duration)
                        }
                        val keys=node.keys()
                        while(keys.hasNext()) walk(node.opt(keys.next()))
                    }
                    is JSONArray -> for(i in 0 until node.length()) walk(node.opt(i))
                }
            }
            walk(root)
            out.values.toList()
        }
    }
    private fun findVideoId(o:JSONObject):String {
        val stack=ArrayDeque<Any>(); stack.add(o)
        while(stack.isNotEmpty()) {
            when(val v=stack.removeLast()) {
                is JSONObject -> { val w=v.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty(); if(w.isNotBlank()) return w; val it=v.keys(); while(it.hasNext()) stack.add(v.opt(it.next())) }
                is JSONArray -> for(i in 0 until v.length()) stack.add(v.opt(i))
            }
        }
        return ""
    }
    suspend fun lyrics(song:Song):Lyrics? = withContext(Dispatchers.IO) {
        val q=java.net.URLEncoder.encode("${song.title} ${song.artist}","UTF-8")
        val req=Request.Builder().url("https://lrclib.net/api/search?q=$q").header("User-Agent","DapsMods/0.1.0").build()
        client.newCall(req).execute().use { res ->
            if(!res.isSuccessful) return@withContext null
            val arr=JSONArray(res.body?.string().orEmpty())
            if(arr.length()==0) return@withContext null
            var best:JSONObject?=null
            for(i in 0 until arr.length()) { val item=arr.optJSONObject(i)?:continue; if(item.optString("syncedLyrics").isNotBlank()){best=item;break} }
            val item=best?:arr.optJSONObject(0)?:return@withContext null
            val synced=item.optString("syncedLyrics")
            if(synced.isNotBlank()) {
                val lines=synced.lineSequence().mapNotNull { line ->
                    val m=Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]").find(line)?:return@mapNotNull null
                    val ms=(m.groupValues[1].toLong()*60000)+(m.groupValues[2].toLong()*1000)+(m.groupValues[3].padEnd(3,'0').take(3).toLongOrNull()?:0)
                    val txt=line.substring(m.range.last+1).trim()
                    if(txt.isBlank()) null else ms to txt
                }.toList()
                Lyrics(true,lines)
            } else {
                Lyrics(false,item.optString("plainLyrics").lines().filter{it.isNotBlank()}.map{0L to it})
            }
        }
    }
    private fun okhttp3.MediaType.Companion.toMediaTypeOrNull() = "application/json; charset=utf-8".toMediaType()
}
