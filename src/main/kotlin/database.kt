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
