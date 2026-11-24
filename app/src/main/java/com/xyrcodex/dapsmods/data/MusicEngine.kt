package com.xyrcodex.dapsmods.data

import android.content.Context
import com.xyrcodex.dapsmods.data.auth.AuthManager
import com.xyrcodex.dapsmods.data.lyrics.LyricsData
import com.xyrcodex.dapsmods.data.lyrics.LyricsRepository
import com.xyrcodex.dapsmods.data.model.Track
import com.xyrcodex.dapsmods.data.remote.HeastDownloader
import com.xyrcodex.dapsmods.data.remote.MusicRepository
import com.xyrcodex.dapsmods.data.settings.AudioQuality
import com.xyrcodex.dapsmods.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe

data class Song(val id:String,val title:String,val artist:String,val thumbnail:String,val duration:String,val url:String="https://www.youtube.com/watch?v=$id",val durationSeconds:Long=0L)
data class Lyrics(val synced:Boolean,val lines:List<Pair<Long,String>>)

object MusicEngine {
    @Volatile private var lyricsRepository: LyricsRepository? = null
    @Synchronized fun initialize(context: Context) {
        val app = context.applicationContext
        SettingsManager.init(app)
        AuthManager.init(app)
        val downloader = HeastDownloader.init(app)
        NewPipe.init(downloader)
        lyricsRepository = LyricsRepository(app)
    }
    private fun Track.toSong() = Song(id,title,artist,highResThumbnailUrl,formattedDuration,url,durationSeconds)
    suspend fun search(query:String):List<Song> = withContext(Dispatchers.IO) {
        if(query.isBlank()) return@withContext emptyList()
        MusicRepository.getInstance().searchTracks(query).map { it.toSong() }
    }
    suspend fun lyrics(song:Song):Lyrics? = withContext(Dispatchers.IO) {
        val repo = lyricsRepository ?: return@withContext null
        val data = repo.getLyrics(song.id,song.title,song.artist,song.durationSeconds,true) ?: return@withContext null
        Lyrics(data.isSynced,data.lines.map { it.timeMs to (it.translation?.takeIf(String::isNotBlank) ?: it.text) })
    }
    suspend fun resolve(song:Song):String = withContext(Dispatchers.IO) {
        MusicRepository.getInstance().getAudioStreamUrl(song.url,AudioQuality.HIGH) ?: error("Audio stream tidak tersedia")
    }
}