package com.xyrcodex.dapsmods.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.json.JSONObject

data class FbMedia(val type:String,val quality:String,val extension:String,val size:String,val video:Boolean,val audio:Boolean,val url:String)
data class FbResult(val source:String,val url:String,val duration:String,val thumbnail:String,val media:List<FbMedia>)

object FacebookDownloader {
    private const val BASE="https://getfvido.com"
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(true).build()
    private fun headers()=mapOf("Accept" to "*/*","Accept-Language" to "id-ID,id;q=0.9","Origin" to BASE,"Referer" to "$BASE/","User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
    suspend fun fetch(input:String):FbResult=withContext(Dispatchers.IO) {
        val target=Regex("https?://[^\\s]+",RegexOption.IGNORE_CASE).find(input)?.value?.trim()?:error("URL Facebook tidak valid")
        require(target.contains("facebook.com") || target.contains("fb.watch")){"Masukkan URL Facebook yang valid"}
        val h=headers()
        val home=Request.Builder().url("$BASE/").apply{h.forEach{(k,v)->header(k,v)}}.get().build()
        val (html,cookie)=client.newCall(home).execute().use { r ->
            if(!r.isSuccessful) error("Tidak dapat membuka layanan downloader: HTTP ${r.code}")
            val c=r.headers("Set-Cookie").map{it.substringBefore(';')}.filter{it.isNotBlank()}.joinToString("; ")
            (r.body?.string().orEmpty()) to c
        }
        val token=Regex("""name=["']token["'][^>]*value=["']([^"']+)["']""",RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?:Regex("""value=["']([^"']+)["'][^>]*name=["']token["']""",RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?:Regex("""id=["']token["'][^>]*value=["']([^"']+)["']""",RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        val form=FormBody.Builder().add("url",target).apply{if(!token.isNullOrBlank()) add("token",token)}.build()
        val post=Request.Builder().url("$BASE/wp-json/aio-dl/video-data/").apply{h.forEach{(k,v)->header(k,v)};header("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");header("X-Requested-With","XMLHttpRequest");if(cookie.isNotBlank())header("Cookie",cookie)}.post(form).build()
        client.newCall(post).execute().use { r ->
            val raw=r.body?.string().orEmpty()
            if(!r.isSuccessful) error("HTTP ${r.code}: $raw")
            val json=JSONObject(raw)
            val arr=json.optJSONArray("medias")?:error("Media tidak ditemukan")
            if(arr.length()==0) error("Media tidak ditemukan")
            val media=buildList {
                for(i in 0 until arr.length()) {
                    val item=arr.optJSONObject(i)?:continue
                    val url=item.optString("url")
                    if(url.isBlank()) continue
                    val video=item.optBoolean("videoAvailable")
                    val audio=item.optBoolean("audioAvailable")
                    add(FbMedia(if(video)"video" else if(audio)"audio" else "media",item.optString("quality"),item.optString("extension"),item.optString("formattedSize"),video,audio,url))
                }
            }
            if(media.isEmpty()) error("Tidak ada tautan media yang bisa digunakan")
            FbResult(json.optString("source"),json.optString("url",target),json.optString("duration"),json.optString("thumbnail"),media)
        }
    }
}
