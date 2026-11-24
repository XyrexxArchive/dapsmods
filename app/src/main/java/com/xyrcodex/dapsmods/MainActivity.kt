package com.xyrcodex.dapsmods

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.MoreExecutors
import com.xyrcodex.dapsmods.service.PlaybackService
import com.xyrcodex.dapsmods.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class AppTab { MUSIC, LIBRARY, TOOLS, SETTINGS }
enum class AppTheme { LIGHT, DARK, GLASS, BRUTAL }

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicEngine.initialize(this)
        setContent { DapsModsApp(controller) }
        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }.onSuccess { connected ->
                    controller = connected
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Player service gagal dimulai", Toast.LENGTH_LONG).show()
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    override fun onDestroy() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}

@Composable fun DapsModsApp(player: Player?) {
    var tab by remember { mutableStateOf(AppTab.MUSIC) }
    var theme by remember { mutableStateOf(AppTheme.DARK) }
    val c=when(theme){
        AppTheme.LIGHT->ThemeColors(Color(0xFFF6F7FB),Color.White,Color(0xFF171923),Color(0xFF667085),Color(0xFF6558E8),Color(0xFFE9E7FF))
        AppTheme.DARK->ThemeColors(Color(0xFF0B0D12),Color(0xFF151922),Color(0xFFF4F5F7),Color(0xFF9299A8),Color(0xFF9B8CFF),Color(0xFF29243F))
        AppTheme.GLASS->ThemeColors(Color(0xFF101722),Color(0xCC293747),Color.White,Color(0xFFB2C0D0),Color(0xFF8BD8E8),Color(0x553B6574))
        AppTheme.BRUTAL->ThemeColors(Color(0xFFFFF4D8),Color(0xFFFFFEF8),Color(0xFF171717),Color(0xFF595347),Color(0xFFFF5B35),Color(0xFFFFD8C9))
    }
    MaterialTheme(colorScheme=lightColorScheme(primary=c.accent,background=c.bg,surface=c.card,onSurface=c.text,onBackground=c.text)) {
        Surface(Modifier.fillMaxSize(),color=c.bg) {
            Column {
                Box(Modifier.weight(1f)) {
                    when(tab){AppTab.MUSIC->MusicScreen(c,player);AppTab.LIBRARY->LibraryScreen(c,player);AppTab.TOOLS->ToolsScreen(c);AppTab.SETTINGS->SettingsScreen(c,theme){theme=it}}
                }
                NavigationBar(containerColor=c.card,tonalElevation=0.dp) {
                    NavigationBarItem(selected=tab==AppTab.MUSIC,onClick={tab=AppTab.MUSIC},icon={Icon(Icons.Rounded.MusicNote,null)},label={Text("Music")},colors=navColors(c))
                    NavigationBarItem(selected=tab==AppTab.LIBRARY,onClick={tab=AppTab.LIBRARY},icon={Icon(Icons.Rounded.LibraryMusic,null)},label={Text("Library")},colors=navColors(c))
                    NavigationBarItem(selected=tab==AppTab.TOOLS,onClick={tab=AppTab.TOOLS},icon={Icon(Icons.Rounded.Construction,null)},label={Text("Tools")},colors=navColors(c))
                    NavigationBarItem(selected=tab==AppTab.SETTINGS,onClick={tab=AppTab.SETTINGS},icon={Icon(Icons.Rounded.Settings,null)},label={Text("Pengaturan")},colors=navColors(c))
                }
            }
        }
    }
}
data class ThemeColors(val bg:Color,val card:Color,val text:Color,val muted:Color,val accent:Color,val soft:Color)
@Composable fun navColors(c:ThemeColors)=NavigationBarItemDefaults.colors(selectedIconColor=c.accent,selectedTextColor=c.accent,indicatorColor=c.soft,unselectedIconColor=c.muted,unselectedTextColor=c.muted)

@Composable fun MusicScreen(c:ThemeColors,player:Player?) {
    val context=LocalContext.current
    var query by remember{mutableStateOf("")}; var songs by remember{mutableStateOf(emptyList<Song>())}
    var loading by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}
    var homeSections by remember{mutableStateOf(emptyList<com.xyrcodex.dapsmods.data.model.HomeSection>())}
    var trending by remember{mutableStateOf(emptyList<com.xyrcodex.dapsmods.data.model.Track>())}
    var homeLoading by remember{mutableStateOf(true)}
    LaunchedEffect(Unit) {
        try {
            homeSections = MusicEngine.homeShelves()
            trending = MusicEngine.trending()
        } catch (_: Throwable) {
        } finally {
            homeLoading = false
        }
    }
    var playing by remember{mutableStateOf<Song?>(null)}; var lyrics by remember{mutableStateOf<Lyrics?>(null)}
    var showLyrics by remember{mutableStateOf(false)}; var showQueue by remember{mutableStateOf(false)}
    var positionMs by remember{mutableStateOf(0L)}; var durationMs by remember{mutableStateOf(0L)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(player) {
        while (true) {
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            durationMs = player?.duration?.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }
    suspend fun playQueuedTrack(track: com.xyrcodex.dapsmods.data.model.Track?) {
        if (track == null) return
        val song = MusicEngine.run { track.toSong() }
        playing = song
        try {
            val streamUrl = MusicEngine.resolve(song)
            val metadata = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                .setArtworkUri(Uri.parse(song.thumbnail)).build()
            player?.setMediaItem(MediaItem.Builder().setMediaId(song.id).setUri(streamUrl).setMediaMetadata(metadata).build())
            player?.prepare()
            player?.play()
            MusicEngine.history(context).recordTrackPlayed(track)
            MusicEngine.reportPlayback(song.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Toast.makeText(context,e.message ?: "Gagal memutar lagu",Toast.LENGTH_LONG).show()
        }
    }
    var searchJob by remember{mutableStateOf<Job?>(null)}
    var searchToken by remember{mutableStateOf(0)}
    val performSearch:()->Unit = {
        val term=query.trim()
        if(term.isNotEmpty()) {
            searchJob?.cancel()
            val token=searchToken+1
            searchToken=token
            loading=true
            error=""
            searchJob=scope.launch {
                try {
                    val result=MusicEngine.search(term)
                    if(searchToken==token) {
                        songs=result
                        if(result.isEmpty()) error="Musik tidak ditemukan. Coba kata kunci lain."
                    }
                } catch(e:CancellationException) {
                    throw e
                } catch(e:Exception) {
                    if(searchToken==token) error=e.message?: "Pencarian gagal"
                } finally {
                    if(searchToken==token) loading=false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(24.dp)); Text("DapsMods",fontSize=27.sp,fontWeight=FontWeight.Bold,color=c.text)
        Text("Temukan musik yang kamu suka",fontSize=13.sp,color=c.muted); Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(horizontal=14.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search,null,tint=c.muted)
            TextField(query,{query=it},Modifier.weight(1f),placeholder={Text("Cari judul atau artis",color=c.muted)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={performSearch()}),colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=c.accent))
            IconButton(onClick=performSearch){Icon(Icons.Rounded.ArrowForward,null,tint=c.accent)}
        }
        Spacer(Modifier.height(16.dp))
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=c.accent)
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
        if(loading) Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                CircularProgressIndicator(color=c.accent,strokeWidth=3.dp)
                Spacer(Modifier.height(12.dp))
                Text("Sedang mencari musik...",color=c.text,fontWeight=FontWeight.SemiBold)
                Text("Menghubungkan ke YouTube Music",color=c.muted,fontSize=12.sp)
            }
        } else if(songs.isEmpty()) LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text(if(error.isNotBlank()) "Belum ada hasil" else "Jelajahi musik",color=c.text,fontSize=19.sp,fontWeight=FontWeight.Bold) }
            if(trending.isNotEmpty()) {
                item { Text("Trending",color=c.text,fontWeight=FontWeight.Bold,fontSize=17.sp) }
                item {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(trending.take(12),key={it.id}) { track ->
                            Column(Modifier.width(142.dp).clickable {
                                MusicEngine.queue().setQueue(trending, trending.indexOf(track))
                                scope.launch { playQueuedTrack(track) }
                            }) {
                                AsyncImage(track.highResThumbnailUrl,null,Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(12.dp)))
                                Text(track.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=2,fontSize=13.sp)
                                Text(track.artist,color=c.muted,maxLines=1,fontSize=11.sp)
                            }
                        }
                    }
                }
            }
            items(homeSections,key={it.title}) { section ->
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(section.title,color=c.text,fontWeight=FontWeight.Bold,fontSize=17.sp)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(section.tracks,key={it.id}) { track ->
                            Column(Modifier.width(142.dp).clickable {
                                MusicEngine.queue().setQueue(section.tracks, section.tracks.indexOf(track))
                                scope.launch { playQueuedTrack(track) }
                            }) {
                                AsyncImage(track.highResThumbnailUrl,null,Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(12.dp)))
                                Text(track.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=2,fontSize=13.sp)
                                Text(track.artist,color=c.muted,maxLines=1,fontSize=11.sp)
                            }
                        }
                    }
                }
            }
            if(homeLoading && trending.isEmpty() && homeSections.isEmpty()) item {
                Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(color=c.accent) }
            }
        } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(songs,key={it.id}){song->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).clickable{
                playing=song
                scope.launch {
                    try {
                        val url = MusicEngine.resolve(song)
                        MusicEngine.setQueue(songs, songs.indexOf(song).coerceAtLeast(0))
                        val metadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(Uri.parse(song.thumbnail))
                            .build()
                        player?.setMediaItem(MediaItem.Builder().setMediaId(song.id).setUri(url).setMediaMetadata(metadata).build())
                        player?.prepare()
                        player?.play()
                        MusicEngine.history(context).recordTrackPlayed(MusicEngine.run { song.toTrack() })
                        MusicEngine.reportPlayback(song.id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Toast.makeText(context, e.message ?: "Gagal memutar lagu", Toast.LENGTH_LONG).show()
                    }
                }
            }.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                AsyncImage(song.thumbnail,null,Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(song.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(song.artist+" • "+song.duration,color=c.muted,fontSize=12.sp,maxLines=1)}
                IconButton(onClick={playing=song;showLyrics=true;scope.launch{lyrics=try{MusicEngine.lyrics(song)}catch(_:Exception){null}}}){Icon(Icons.Rounded.Lyrics,null,tint=c.accent)}
                IconButton(onClick={MusicEngine.offline(context).downloadTrack(MusicEngine.run { song.toTrack() });Toast.makeText(context,"Download offline dimulai",Toast.LENGTH_SHORT).show()}){Icon(Icons.Rounded.Download,null,tint=c.accent)}
                IconButton(onClick={MusicEngine.playlists(context).toggleWatchLater(MusicEngine.run { song.toTrack() });Toast.makeText(context,"Ditambahkan ke Putar Nanti",Toast.LENGTH_SHORT).show()}){Icon(Icons.Rounded.BookmarkAdd,null,tint=c.accent)}
                Icon(Icons.Rounded.PlayCircleFilled,null,tint=c.accent,modifier=Modifier.size(28.dp))
            }}
        }
        playing?.let{song->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.soft).padding(horizontal=12.dp,vertical=8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    AsyncImage(song.thumbnail,null,Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                        Text(song.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=1)
                        Text(song.artist,color=c.muted,fontSize=12.sp,maxLines=1)
                    }
                    IconButton(onClick={scope.launch{playQueuedTrack(MusicEngine.queue().getPreviousTrack())}}) { Icon(Icons.Rounded.SkipPrevious,null,tint=c.text) }
                    IconButton(onClick={if(player?.isPlaying==true)player.pause() else player?.play()}) { Icon(if(player?.isPlaying==true)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=c.accent) }
                    IconButton(onClick={scope.launch{playQueuedTrack(MusicEngine.queue().getNextTrack(true))}}) { Icon(Icons.Rounded.SkipNext,null,tint=c.text) }
                    IconButton(onClick={showQueue=true}) { Icon(Icons.Rounded.QueueMusic,null,tint=c.text) }
                }
                Slider(value=if(durationMs>0) positionMs.toFloat().coerceIn(0f,durationMs.toFloat()) else 0f,onValueChange={player?.seekTo(it.toLong())},valueRange=0f..durationMs.coerceAtLeast(1L).toFloat(),modifier=Modifier.fillMaxWidth().height(22.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(formatPlaybackTime(positionMs),color=c.muted,fontSize=10.sp)
                    Text(formatPlaybackTime(durationMs),color=c.muted,fontSize=10.sp)
                }
            }
        }
    }
    if(showQueue) AlertDialog(
        onDismissRequest={showQueue=false},
        containerColor=c.card,
        title={Text("Queue",color=c.text)},
        text={
            LazyColumn(Modifier.heightIn(max=420.dp)) {
                items(MusicEngine.queue().queueState.value, key={it.id}) { track ->
                    Row(Modifier.fillMaxWidth().clickable {
                        MusicEngine.queue().playTrack(track)
                        showQueue=false
                        scope.launch { playQueuedTrack(track) }
                    }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title,color=c.text,maxLines=1)
                            Text(track.artist,color=c.muted,fontSize=12.sp,maxLines=1)
                        }
                        if(MusicEngine.queue().currentTrack.value?.id==track.id) Icon(Icons.Rounded.GraphicEq,null,tint=c.accent)
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={showQueue=false}){Text("Tutup")}}
    )

    if(showLyrics)AlertDialog(onDismissRequest={showLyrics=false},containerColor=c.card,title={Text("Lyrics",color=c.text)},text={Column(Modifier.heightIn(max=420.dp)){
        Text(playing?.title.orEmpty(),color=c.text,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
        if(lyrics==null)Text("Lirik belum tersedia atau sedang dimuat.",color=c.muted) else LazyColumn{items(lyrics!!.lines){line->Text(line.second,color=c.text,modifier=Modifier.padding(vertical=5.dp))}}
    }},confirmButton={TextButton(onClick={showLyrics=false}){Text("Tutup")}})
}

@Composable fun LibraryScreen(c:ThemeColors,player:Player?) {
    val context=LocalContext.current
    val history=remember { MusicEngine.history(context) }
    val offline=remember { MusicEngine.offline(context) }
    val playlists=remember { MusicEngine.playlists(context) }
    val recent by history.recentlyPlayed.collectAsState()
    val downloaded by offline.offlineTracks.collectAsState()
    val userPlaylists by playlists.playlists.collectAsState()
    val scope=rememberCoroutineScope()
    var selected by remember { mutableStateOf("Riwayat") }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Library",fontSize=27.sp,fontWeight=FontWeight.Bold,color=c.text)
        Text("Koleksi musik kamu",fontSize=13.sp,color=c.muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Riwayat","Offline","Playlist").forEach { item ->
                FilterChip(selected=selected==item,onClick={selected=item},label={Text(item)})
            }
        }
        when(selected) {
            "Riwayat" -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp)) {
                items(recent,key={it.id}) { track -> LibraryTrackRow(track,c) { scope.launch { playLibraryTrack(track,player,context) } } }
            }
            "Offline" -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp)) {
                items(downloaded,key={it.track.id}) { item ->
                    LibraryTrackRow(item.track,c) {
                        val meta=MediaMetadata.Builder().setTitle(item.track.title).setArtist(item.track.artist).setArtworkUri(Uri.parse(item.track.thumbnailUrl)).build()
                        player?.setMediaItem(MediaItem.Builder().setMediaId(item.track.id).setUri(item.localUri).setMediaMetadata(meta).build())
                        player?.prepare();player?.play()
                    }
                }
            }
            else -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp)) {
                items(userPlaylists,key={it.id}) { playlist ->
                    Column(Modifier.fillMaxWidth().padding(vertical=10.dp)) {
                        Text(playlist.name,color=c.text,fontWeight=FontWeight.Bold)
                        Text("${playlist.tracks.size} lagu",color=c.muted,fontSize=12.sp)
                        playlist.tracks.forEach { track -> LibraryTrackRow(track,c) { scope.launch { playLibraryTrack(track,player,context) } } }
                    }
                }
            }
        }
    }
}

private suspend fun playLibraryTrack(track:com.xyrcodex.dapsmods.data.model.Track,player:Player?,context:Context) {
    try {
        val song=MusicEngine.run { track.toSong() }
        val url=MusicEngine.resolve(song)
        val meta=MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setArtworkUri(Uri.parse(track.thumbnailUrl)).build()
        player?.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(url).setMediaMetadata(meta).build())
        player?.prepare();player?.play()
        MusicEngine.history(context).recordTrackPlayed(track)
    } catch(e:Throwable) {
        Toast.makeText(context,e.message ?: "Gagal memutar",Toast.LENGTH_LONG).show()
    }
}

@Composable private fun LibraryTrackRow(track:com.xyrcodex.dapsmods.data.model.Track,c:ThemeColors,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        AsyncImage(track.thumbnailUrlForDisplay(180),null,Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(track.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=1)
            Text(track.artist,color=c.muted,fontSize=12.sp,maxLines=1)
        }
        Icon(Icons.Rounded.PlayArrow,null,tint=c.accent)
    }
}

@Composable fun ToolsScreen(c:ThemeColors) {
    val context=LocalContext.current; var url by remember{mutableStateOf("")}; var result by remember{mutableStateOf<FbResult?>(null)}
    var loading by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}; val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp));Text("Tools",fontSize=27.sp,fontWeight=FontWeight.Bold,color=c.text)
        Text("Alat praktis untuk kebutuhan sehari-hari",color=c.muted,fontSize=13.sp);Spacer(Modifier.height(22.dp))
        Text("Facebook Downloader",fontWeight=FontWeight.Bold,fontSize=18.sp,color=c.text)
        Text("Unduh video Facebook dalam kualitas yang tersedia.",color=c.muted,fontSize=13.sp);Spacer(Modifier.height(14.dp))
        OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),placeholder={Text("Tempel link Facebook")},singleLine=true,shape=RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick={loading=true;error="";result=null;scope.launch{try{result=FacebookDownloader.fetch(url)}catch(e:Exception){error=e.message?: "Download gagal"};loading=false}},enabled=url.isNotBlank()&&!loading,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
            if(loading)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(8.dp));Text("Cari media")
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=12.dp))
        result?.let{data->Spacer(Modifier.height(18.dp));Text("Media ditemukan",color=c.text,fontWeight=FontWeight.Bold)
            if(data.thumbnail.isNotBlank())AsyncImage(data.thumbnail,null,Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)))
            Text("Durasi: "+data.duration.ifBlank{"Tidak diketahui"},color=c.muted,fontSize=12.sp)
            data.media.forEachIndexed{index,item->Row(Modifier.fillMaxWidth().padding(vertical=5.dp).clip(RoundedCornerShape(12.dp)).background(c.card).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(item.type.uppercase()+" "+item.quality,color=c.text,fontWeight=FontWeight.SemiBold);Text(item.extension+" • "+item.size,color=c.muted,fontSize=12.sp)}
                TextButton(onClick={download(context,item.url,"facebook_${index+1}.${item.extension.ifBlank{"mp4"}}")}){Icon(Icons.Rounded.Download,null);Text("Unduh")}
            }}
        }
    }
}
fun download(context:Context,url:String,name:String) {
    try{val req=DownloadManager.Request(Uri.parse(url)).setTitle(name).setDescription("Mengunduh dari DapsMods").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,name).setAllowedOverMetered(true).setAllowedOverRoaming(true)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req);Toast.makeText(context,"Download dimulai",Toast.LENGTH_SHORT).show()
    }catch(e:Exception){Toast.makeText(context,e.message,Toast.LENGTH_LONG).show()}
}

@Composable fun SettingsScreen(c:ThemeColors,theme:AppTheme,onTheme:(AppTheme)->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp));Text("Pengaturan",fontSize=27.sp,fontWeight=FontWeight.Bold,color=c.text)
        Text("Atur tampilan dan informasi aplikasi",color=c.muted,fontSize=13.sp);Spacer(Modifier.height(24.dp))
        Text("Tampilan",fontWeight=FontWeight.Bold,color=c.text,fontSize=17.sp);Spacer(Modifier.height(10.dp))
        listOf(AppTheme.LIGHT to "Terang",AppTheme.DARK to "Gelap",AppTheme.GLASS to "Liquid Glass",AppTheme.BRUTAL to "Brutalism").forEach{(value,label)->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp).clip(RoundedCornerShape(14.dp)).background(c.card).clickable{onTheme(value)}.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(label,color=c.text,fontWeight=FontWeight.SemiBold);Text(when(value){AppTheme.LIGHT->"Bersih dan cerah";AppTheme.DARK->"Nyaman untuk malam";AppTheme.GLASS->"Transparan dan lembut";AppTheme.BRUTAL->"Kontras dan tegas"},color=c.muted,fontSize=12.sp)}
                RadioButton(selected=theme==value,onClick={onTheme(value)},colors=RadioButtonDefaults.colors(selectedColor=c.accent))
            }
        }
        Spacer(Modifier.height(20.dp));Text("Tentang aplikasi",fontWeight=FontWeight.Bold,color=c.text,fontSize=17.sp)
        Row(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(14.dp)).background(c.card).padding(15.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.Android,null,tint=c.accent,modifier=Modifier.size(30.dp))
            Column(Modifier.padding(start=12.dp)){Text("DapsMods",color=c.text,fontWeight=FontWeight.Bold);Text("Versi 0.1.0 • Developer: xyrcodex",color=c.muted,fontSize=12.sp)}
        }
        Text("Dibuat ringan dengan Kotlin dan Jetpack Compose.",color=c.muted,fontSize=12.sp,modifier=Modifier.padding(top=14.dp))
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    return String.format(java.util.Locale.US, "%d:%02d", total / 60L, total % 60L)
}
