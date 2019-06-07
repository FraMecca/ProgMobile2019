package com.streaming.database

import io.vertx.core.Vertx

import io.vertx.core.json.*
import java.io.*


open class Mpd private constructor (json: JsonObject) {
    data class Song(
        val uri: String, val artist: String, val album: String, val title: String, val genre: String,
        val performer: String, val composer: String, val track: String, val json: JsonObject) : Mpd(json)

    data class Playlist(val uri: String, val title: String, val json: JsonObject) : Mpd(json)
}

var database = mutableListOf<Mpd>()
fun updateDatabase(vertx: Vertx, location: String){

    val tmp = mutableListOf<Mpd>()
    val file = File(location)
    val reader = BufferedReader(FileReader(file))
    reader.readLines().forEach { tmp.add(decode(JsonObject(it))) }
    database = tmp
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
        else -> { assert(false); return Mpd.Playlist("", "", j) }
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

 // TODO do this better and without casts
    var haystack = database
    var tmp = mutableListOf<Mpd.Song>()

    for (key in keys) {
        haystack.forEach { if (isSimilar(it, key))
            tmp.add (it as Mpd.Song)
        }
        haystack = tmp as MutableList<Mpd>
        tmp = mutableListOf<Mpd.Song>()
    }

    return haystack as MutableList<Mpd.Song>
}
