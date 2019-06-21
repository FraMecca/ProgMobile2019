package com.mozapp.server.database

import io.vertx.core.Vertx
import io.vertx.core.json.*
import java.io.*


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
    //loadAlbums()
    //loadGenre()
}

fun loadArtists(){
    fun getArtistImg(img:String) : String{
        return "TODO" // TODO
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
            byArtist.put(artist, mutableMapOf("album" to mutableListOf<String>(), "img" to img))
        }
        (byArtist[artist]!!["album"]!! as MutableList<String>).add(album)
    }
}

fun loadAlbums(){
    fun getAlbumImg(img:String) : String{
        return "TODO" // TODO
    }

    for(it in database){
        val artist = it.artist
        var album = it.album
        if(album == ""){
            if(artist == "")
                continue
            else album = "unknown"
        }

        if(album !in byAlbum) {
            val img = getAlbumImg(album)
            byAlbum.put(album, mutableMapOf("artist" to mutableListOf<String>(), "img" to img))
        }
        (byAlbum[album]!!["artist"]!! as MutableList<String>).add(artist)
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
