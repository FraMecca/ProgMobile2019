package com.mozapp.server.database

import com.mozapp.server.thirdparties.getCoverArt
import com.mozapp.server.thirdparties.getImgWikidata
import io.vertx.core.Vertx
import io.vertx.core.json.*
import java.io.*
import java.nio.file.Paths


open class Mpd private constructor () {
    data class Song(
        val uri: String, val artist: String, val album: String, val title: String, val genre: String,
        val performer: String, val composer: String, val track: String, val json: JsonObject) : Mpd()

    data class Playlist(val uri: String, val title: String, val json: JsonObject) : Mpd()
}

var database = mutableListOf<Mpd.Song>()
var byArtist = mutableMapOf<String, MutableMap<String, Any>>()
var byAlbum = mutableMapOf<String, MutableMap<String, Any>>()
var byGenre = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

fun loadDatabase(location: String){

 //   val command = "bash -c  ' echo asd && kpd --list-json |head -n 1|  jq .[] -c > " + location +"'" // so long java
    val tmp = mutableListOf<Mpd.Song>()
    val file = File(location)
    val reader = BufferedReader(FileReader(file) as Reader?)
    reader.readLines().forEach {
        val t = decode(JsonObject(it))
        when(t){
            is Mpd.Song -> tmp.add(t)
            else -> {} // ignore
        }
    }
    database = tmp // update reference
    loadArtists()
    loadAlbums()
    loadGenres()
}

fun loadArtists(){
    val covers = {
        val file = File("/home/user/.mpd/artists.json")
        val reader = BufferedReader(FileReader(file) as Reader?)
        val content = JsonObject(reader.readLine())
        content.map as Map<String, String>
    }()

    fun getArtistImg(name:String) : String{
       // val ret  = getImgWikidata(name)
        val ret = covers.getOrDefault(name, "")
        return ret
    }

    for(it in database){
        var artist = it.artist
        val album = it.album
        if(artist == ""){
            if(album == "")
                continue
            else artist = "unknown"
        }

        if(artist !in byArtist) {
            val img = getArtistImg(artist)
            byArtist.put(artist, mutableMapOf("albums" to mutableListOf<String>(), "img" to img, "name" to artist))
        }
        (byArtist[artist]!!["albums"]!! as MutableList<String>).add(album)
    }
}

fun loadAlbums(){
    val covers = {
        val file = File("/home/user/.mpd/covers.json")
        val reader = BufferedReader(FileReader(file) as Reader?)
        val content = JsonObject(reader.readLine())
        content.map as Map<String, String>
    }()

    fun getAlbumImg(img:String) : String{
        //val ret=  getCoverArt(img)
        val ret = covers.getOrDefault(img, "")
        return ret
    }

    for(it in database){
        val artist = it.artist
        var album = it.album
        val path = Paths.get(it.uri) // the file
        if(path.parent == null)
            continue
        val uri = path.parent.toString()
        if(album == ""){
            if(artist == "")
                continue
            else album = "unknown"
        }

        if(uri !in byAlbum) {
            val img = getAlbumImg(album)
            val songs = ArrayList<MutableMap<String, String>>()
            songs.add(mutableMapOf("uri" to it.uri, "title" to it.title))
            byAlbum.put(uri, mutableMapOf("artist" to artist , "img" to img, "uri" to uri, "title" to album, "songs" to songs))

        } else {
            val img : String = byAlbum.get(uri)!!.get("img") as String
            val songs : ArrayList<MutableMap<String, String>> = byAlbum.get(uri)!!["songs"] as ArrayList<MutableMap<String, String>>
            songs.add(mutableMapOf("uri" to it.uri, "title" to it.title))
            byAlbum.put(uri, mutableMapOf("artist" to artist , "img" to img, "uri" to uri, "title" to album, "songs" to songs))
        }
    }
}

fun loadGenres(){
    for(it in database){
        var artist = it.artist
        var album = it.album
        var genre = it.genre

        if(artist == "" && album == "" && genre == "")
            continue // skip it else correct them
        if(artist == "")
            artist = "unknown"
        if(album == "")
            album = "unknown"
        if(genre == "")
            genre = "unknown"

        if(genre !in byGenre) {
            byGenre.put(genre, mutableMapOf<String, MutableSet<String>>())
        }
        val artists = (byGenre[genre])!!
        if(artist !in artists){
            artists.put(artist, mutableSetOf<String>())
        }
        val albums = artists[artist]!!
        if(!albums.contains(album))
            albums.add(album)

        // put it back in the map
        byGenre[genre]!!.put(artist, albums)
    }
}

fun decode(j: JsonObject) : Mpd {
    val type = j.getString("type")
    val el = when (type) {
        "playlist" -> Mpd.Playlist(j.getString("uri"), j.getString("title"), j)
        "song" -> {
            Mpd.Song(
                j.getString("uri"), j.getString("artist"), j.getString("album"),
                j.getString("title"), j.getString("genre"), j.getString("performer"),
                j.getString("composer"), j.getString("track"), j
            )
        }
        else -> {throw Exception("unreachable code")}
    }
    return el
}

fun search(keys: List<String>) : MutableList<Mpd.Song>
{

    fun isSimilar(_haystack : Mpd, _needle: String) : Boolean{
        val needle = _needle.toLowerCase()
        if(_haystack is Mpd.Playlist)
            return false
        val haystack = _haystack as Mpd.Song

        return haystack.uri.toLowerCase().contains(needle) ||
                haystack.album.toLowerCase().contains(needle) ||
                haystack.title.toLowerCase().contains(needle) ||
                haystack.artist.toLowerCase().contains(needle)

    }

    var haystack = database
    var tmp = mutableListOf<Mpd.Song>()

    for (key in keys) {
        haystack.forEach {
            if (isSimilar(it, key))
            tmp.add (it)
        }
        haystack = tmp
        tmp = mutableListOf<Mpd.Song>()
    }

    return haystack
}
