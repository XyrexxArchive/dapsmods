package com.xyrcodex.dapsmods.data

import android.content.Context
import com.xyrcodex.dapsmods.data.auth.AuthManager
import com.xyrcodex.dapsmods.data.cache.HistoryStore
import com.xyrcodex.dapsmods.data.lyrics.LyricsRepository
import com.xyrcodex.dapsmods.data.model.Track
import com.xyrcodex.dapsmods.data.offline.OfflineMusicManager
import com.xyrcodex.dapsmods.data.player.PlayerQueueManager
import com.xyrcodex.dapsmods.data.playlist.PlaylistManager
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
    @Volatile private var appContext: Context? = null
    val backend: MusicRepository get() = MusicRepository.getInstance()
    @Synchronized fun initialize(context: Context) {
        val app = context.applicationContext
        if (appContext === app) return
        appContext = app
        SettingsManager.init(app)
        AuthManager.init(app)
        NewPipe.init(HeastDownloader.init(app))
        lyricsRepository = LyricsRepository(app)
        HistoryStore.getInstance(app)
        PlaylistManager.getInstance(app)
        OfflineMusicManager.getInstance(app)
    }
    private fun Track.toSong() = Song(id,title,artist,highResThumbnailUrl,formattedDuration,url,durationSeconds)
    fun Song.toTrack() = Track(id=id,url=url,title=title,artist=artist,thumbnailUrl=thumbnail,durationSeconds=durationSeconds)
    suspend fun search(query:String):List<Song> = withContext(Dispatchers.IO) {
        if(query.isBlank()) return@withContext emptyList()
        backend.searchTracks(query).map { it.toSong() }
    }
    suspend fun suggestions(query:String):List<String> = backend.getSuggestions(query)
    suspend fun homeShelves(forceRefresh:Boolean=false) = backend.getHomeShelves(forceRefresh)
    suspend fun trending() = backend.getTrendingTracks()
    suspend fun radio(videoId:String,forceRefresh:Boolean=false) = backend.getRadioTracks(videoId,forceRefresh=forceRefresh)
    suspend fun upNext(videoId:String) = backend.getUpNextTracks(videoId)
    suspend fun browse(browseId:String?,playlistId:String?) = backend.getPlaylistOrBrowseTracks(browseId,playlistId)
    suspend fun playlistDetails(browseId:String,playlistId:String?=null) = backend.getPlaylistBrowseDetails(browseId,playlistId)
    suspend fun categorySearch(query:String,filterType:String) = backend.searchByCategoryFilter(query,filterType)
    suspend fun likedTracks() = backend.getLikedTracks()
    suspend fun remotePlaylists() = backend.getUserPlaylists()
    suspend fun setLiked(videoId:String,liked:Boolean) = backend.setTrackLikeStatus(videoId,liked)
    suspend fun reportPlayback(videoId:String) = backend.reportPlaybackToYouTubeMusic(videoId)
    suspend fun resolve(song:Song):String = withContext(Dispatchers.IO) {
        backend.getAudioStreamUrl(song.url,AudioQuality.HIGH) ?: error("Audio stream tidak tersedia")
    }
    suspend fun lyrics(song:Song):Lyrics? = withContext(Dispatchers.IO) {
        val repo = lyricsRepository ?: return@withContext null
        val data = repo.getLyrics(song.id,song.title,song.artist,song.durationSeconds,true) ?: return@withContext null
        Lyrics(data.isSynced,data.lines.map { it.timeMs to (it.translation?.takeIf(String::isNotBlank) ?: it.text) })
    }
    fun setQueue(songs:List<Song>,startIndex:Int=0) = PlayerQueueManager.setQueue(songs.map { it.toTrack() },startIndex)
    fun addToQueue(song:Song) = PlayerQueueManager.addToQueue(song.toTrack())
    fun queue() = PlayerQueueManager
    fun history(context:Context) = HistoryStore.getInstance(context.applicationContext)
    fun playlists(context:Context) = PlaylistManager.getInstance(context.applicationContext)
    fun offline(context:Context) = OfflineMusicManager.getInstance(context.applicationContext)
}