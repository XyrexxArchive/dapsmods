package com.xyrcodex.dapsmods.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

object StreamResolver {
    @Volatile private var ready=false
    @Synchronized fun initialize(context:Context) {
        if(ready)return
        val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
        NewPipe.init(object:Downloader(){
            override fun execute(request:Request):Response {
                val b=okhttp3.Request.Builder().url(request.url())
                request.headers().forEach{(k,vs)->vs.forEach{b.addHeader(k,it)}}
                if(request.headers()["User-Agent"]==null)b.header("User-Agent","Mozilla/5.0")
                val method=request.httpMethod()
                val body=if(method=="POST"||method=="PUT") (request.dataToSend()?:ByteArray(0)).toRequestBody(null) else null
                b.method(method,body)
                return client.newCall(b.build()).execute().use{r->Response(r.code,r.message,r.headers.toMultimap(),r.body?.string().orEmpty(),r.request.url.toString())}
            }
        })
        ready=true
    }
    fun resolve(song:Song):String {
        val info=StreamInfo.getInfo(ServiceList.YouTube,song.url)
        return info.audioStreams.sortedByDescending{it.averageBitrate}.firstOrNull()?.content
            ?: info.audioStreams.firstOrNull()?.content ?: error("Audio stream tidak tersedia")
    }
}
