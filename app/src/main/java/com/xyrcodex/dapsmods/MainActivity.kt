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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.xyrcodex.dapsmods.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab { MUSIC, TOOLS, SETTINGS }
enum class AppTheme { LIGHT, DARK, GLASS, BRUTAL }

class MainActivity : ComponentActivity() {
    private var player:ExoPlayer?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        StreamResolver.initialize(this)
        player=ExoPlayer.Builder(this).build()
        setContent { DapsModsApp(player) }
    }
    override fun onDestroy(){player?.release();player=null;super.onDestroy()}
}

@Composable fun DapsModsApp(player:ExoPlayer?) {
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
                    when(tab){AppTab.MUSIC->MusicScreen(c,player);AppTab.TOOLS->ToolsScreen(c);AppTab.SETTINGS->SettingsScreen(c,theme){theme=it}}
                }
                NavigationBar(containerColor=c.card,tonalElevation=0.dp) {
                    NavigationBarItem(selected=tab==AppTab.MUSIC,onClick={tab=AppTab.MUSIC},icon={Icon(Icons.Rounded.MusicNote,null)},label={Text("Music")},colors=navColors(c))
                    NavigationBarItem(selected=tab==AppTab.TOOLS,onClick={tab=AppTab.TOOLS},icon={Icon(Icons.Rounded.Construction,null)},label={Text("Tools")},colors=navColors(c))
                    NavigationBarItem(selected=tab==AppTab.SETTINGS,onClick={tab=AppTab.SETTINGS},icon={Icon(Icons.Rounded.Settings,null)},label={Text("Pengaturan")},colors=navColors(c))
                }
            }
        }
    }
}
data class ThemeColors(val bg:Color,val card:Color,val text:Color,val muted:Color,val accent:Color,val soft:Color)
@Composable fun navColors(c:ThemeColors)=NavigationBarItemDefaults.colors(selectedIconColor=c.accent,selectedTextColor=c.accent,indicatorColor=c.soft,unselectedIconColor=c.muted,unselectedTextColor=c.muted)

@Composable fun MusicScreen(c:ThemeColors,player:ExoPlayer?) {
    val context=LocalContext.current
    var query by remember{mutableStateOf("")}; var songs by remember{mutableStateOf(emptyList<Song>())}
    var loading by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}
    var playing by remember{mutableStateOf<Song?>(null)}; var lyrics by remember{mutableStateOf<Lyrics?>(null)}
    var showLyrics by remember{mutableStateOf(false)}; val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(24.dp)); Text("DapsMods",fontSize=27.sp,fontWeight=FontWeight.Bold,color=c.text)
        Text("Temukan musik yang kamu suka",fontSize=13.sp,color=c.muted); Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(horizontal=14.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search,null,tint=c.muted)
            TextField(query,{query=it},Modifier.weight(1f),placeholder={Text("Cari judul atau artis",color=c.muted)},singleLine=true,colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=c.accent))
            IconButton(onClick={if(query.isNotBlank()){loading=true;error="";scope.launch{try{songs=MusicEngine.search(query)}catch(e:Exception){error=e.message?: "Pencarian gagal"};loading=false}}}){Icon(Icons.Rounded.ArrowForward,null,tint=c.accent)}
        }
        Spacer(Modifier.height(16.dp)); if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=c.accent)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
        if(songs.isEmpty()&&!loading) Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Rounded.LibraryMusic,null,tint=c.muted,modifier=Modifier.size(46.dp));Spacer(Modifier.height(10.dp));Text("Cari musik favoritmu",color=c.text,fontWeight=FontWeight.SemiBold);Text("Hasil pencarian dari YouTube Music",color=c.muted,fontSize=12.sp)}
        } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(songs,key={it.id}){song->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).clickable{
                playing=song;scope.launch{try{val url=withContext(Dispatchers.IO){StreamResolver.resolve(song)};player?.setMediaItem(MediaItem.fromUri(url));player?.prepare();player?.play()}catch(e:Exception){Toast.makeText(context,e.message?: "Gagal memutar",Toast.LENGTH_LONG).show()}}
            }.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                AsyncImage(song.thumbnail,null,Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(song.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(song.artist+" • "+song.duration,color=c.muted,fontSize=12.sp,maxLines=1)}
                IconButton(onClick={playing=song;showLyrics=true;scope.launch{lyrics=try{MusicEngine.lyrics(song)}catch(_:Exception){null}}}){Icon(Icons.Rounded.Lyrics,null,tint=c.accent)}
                Icon(Icons.Rounded.PlayCircleFilled,null,tint=c.accent,modifier=Modifier.size(28.dp))
            }}
        }
        playing?.let{song->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.soft).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(song.title,color=c.text,fontWeight=FontWeight.SemiBold,maxLines=1);Text(song.artist,color=c.muted,fontSize=12.sp)}
            IconButton(onClick={if(player?.isPlaying==true)player.pause() else player?.play()}){Icon(if(player?.isPlaying==true)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=c.accent)}
            IconButton(onClick={showLyrics=true;scope.launch{lyrics=try{MusicEngine.lyrics(song)}catch(_:Exception){null}}}){Icon(Icons.Rounded.Lyrics,null,tint=c.accent)}
        }}
    }
    if(showLyrics)AlertDialog(onDismissRequest={showLyrics=false},containerColor=c.card,title={Text("Lyrics",color=c.text)},text={Column(Modifier.heightIn(max=420.dp)){
        Text(playing?.title.orEmpty(),color=c.text,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
        if(lyrics==null)Text("Lirik belum tersedia atau sedang dimuat.",color=c.muted) else LazyColumn{items(lyrics!!.lines){line->Text(line.second,color=c.text,modifier=Modifier.padding(vertical=5.dp))}}
    }},confirmButton={TextButton(onClick={showLyrics=false}){Text("Tutup")}})
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
