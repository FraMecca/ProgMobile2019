package com.apollon.server.database

import com.apollon.server.streaming.AudioFileData
import com.apollon.server.streaming.LIBRARY
import com.apollon.server.streaming.WORKDIR
import com.apollon.server.streaming.audioFiles
import io.vertx.core.json.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.nio.file.Paths
import java.nio.file.Files
import java.util.stream.Collectors

open class Mpd private constructor () {
    data class Song(
        val uri: String,
        val artist: String,
        val album: String,
        val title: String,
        val genre: String,
        val performer: String,
        val composer: String,
        val track: String,
        val json: JsonObject
    ) : Mpd()

    data class Playlist(val uri: String, val title: String, val json: JsonObject) : Mpd()
}

var database = mutableListOf<Mpd.Song>()
var databaseByUri = mutableMapOf<String, Mpd.Song>()
var byArtist = mutableMapOf<String, MutableMap<String, Any>>()
var byAlbum = mutableMapOf<String, MutableMap<String, Any>>()
var byGenre = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

val nullProcess = ProcessBuilder("true").directory(LIBRARY).start()
fun checkExistingFiles(): Int{
    val fileDirMap = Files.list(Paths.get(WORKDIR.path)).
        collect(Collectors.partitioningBy( { it -> Files.isRegularFile(it)}))
    println("FILES:::::::::::::::::::::::::::::::::")
    fileDirMap[true]?.forEach {
        val size = File(it.toString()).length()
        audioFiles.put(it.fileName.toString().substringBeforeLast(".mp3"), AudioFileData(1, nullProcess, size))
    }
    return audioFiles.size
}

fun loadDatabase(location: String) {

    // val command = "bash -c  ' echo asd && kpd --list-json |head -n 1|  jq .[] -c > " + location +"'" // so long java
    val tmp = mutableListOf<Mpd.Song>()
    val file = File(location)
    val reader = BufferedReader(FileReader(file) as Reader?)
    reader.readLines().forEach {
        val t = decode(JsonObject(it))
        when (t) {
            is Mpd.Song -> tmp.add(t)
            else -> {} // ignore
        }
    }
    database = tmp // update reference
    databaseByUri = (database.map { it -> Pair(it.uri, it) }).toMap().toMutableMap()
    loadArtists()
    loadAlbums()
    loadGenres()
}

fun loadArtists() {
    val covers = {
        val file = File("/home/user/.mpd/artists.json")
        val reader = BufferedReader(FileReader(file) as Reader?)
        val content = JsonObject(reader.readLine())
        content.map as Map<String, String>
    }()

    fun getArtistImg(name: String): String {
        val ret = covers.getOrDefault(name, "")
        return ret
    }

    for (it in database) {
        var artist = it.artist
        val album = it.album
        val path = Paths.get(it.uri) // the file
        if (path.parent == null)
            continue
        if (artist == "") {
            if (album == "")
                continue
            else artist = "unknown"
        }

        if (artist !in byArtist) {
            val img = getArtistImg(artist)
            byArtist.put(artist, mutableMapOf("albums" to mutableListOf<HashMap<String, String>>(), "img" to img, "name" to artist, "#albums" to 0))
        }
        val albumUri = path.parent.toString()
        val allUris = (byArtist[artist]!!["albums"]!! as MutableList<HashMap<String, String>>).map { it["uri"] }.toSet()
        if (!allUris.contains(albumUri)) {
            val img = getAlbumImg(album)
            (byArtist[artist]!!["albums"]!! as MutableList<HashMap<String, String>>).add(
                hashMapOf(
                    "uri" to albumUri,
                    "title" to album,
                    "img" to img
                )
            )
            // increment album counter
            val nalbums = byArtist[artist]!!["#albums"]!! as Int
            byArtist[artist]!!["#albums"] = nalbums + 1
        }
    }
}

val covers = {
    val file = File("/home/user/.mpd/covers.json")
    val reader = BufferedReader(FileReader(file) as Reader?)
    val content = JsonObject(reader.readLine())
    content.map as Map<String, String>
}()

fun getAlbumImg(img: String): String {
    // val ret=  getCoverArt(img)
    val ret = covers.getOrDefault(img, "")
    return ret
}

fun loadAlbums() {
    for (it in database) {
        val artist = it.artist
        var album = it.album
        val path = Paths.get(it.uri) // the file
        if (path.parent == null)
            continue
        val uri = path.parent.toString()
        if (album == "") {
            if (artist == "")
                continue
            else album = "unknown"
        }

        if (uri !in byAlbum) {
            val img = getAlbumImg(album)
            val songs = ArrayList<MutableMap<String, String>>()
            songs.add(mutableMapOf("uri" to it.uri, "title" to it.title))
            byAlbum.put(uri, mutableMapOf("artist" to artist, "img" to img, "uri" to uri, "title" to album, "songs" to songs, "#nsongs" to 1))
        } else {
            val img: String = byAlbum.get(uri)!!.get("img") as String
            val nsongs: Int = byAlbum.get(uri)!!.get("#nsongs") as Int
            val songs: ArrayList<MutableMap<String, String>> = byAlbum.get(uri)!!["songs"] as ArrayList<MutableMap<String, String>>
            songs.add(mutableMapOf("uri" to it.uri, "title" to it.title))
            byAlbum.put(uri, mutableMapOf("artist" to artist, "img" to img, "uri" to uri, "title" to album, "songs" to songs, "#nsongs" to nsongs+1))
        }
    }
}

fun loadGenres() {
    for (it in database) {
        var artist = it.artist
        var album = it.album
        var genre = it.genre

        if (artist == "" && album == "" && genre == "")
            continue // skip it else correct them
        if (artist == "")
            artist = "unknown"
        if (album == "")
            album = "unknown"
        if (genre == "")
            genre = "unknown"

        if (genre !in byGenre)
            byGenre.put(genre, mutableMapOf<String, MutableSet<String>>())
        val artists = (byGenre[genre])!!
        if (artist !in artists)
            artists.put(artist, mutableSetOf<String>())
        val albums = artists[artist]!!
        if (!albums.contains(album))
            albums.add(album)

        // put it back in the map
        byGenre[genre]!!.put(artist, albums)
    }
}

fun decode(j: JsonObject): Mpd {
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
        else -> { throw Exception("unreachable code") }
    }
    return el
}

fun search(keys: List<String>): MutableList<Mpd.Song> {

    fun isSimilar(_haystack: Mpd, _needle: String): Boolean {
        val needle = _needle.toLowerCase()
        if (_haystack is Mpd.Playlist)
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
            tmp.add(it)
        }
        haystack = tmp
        tmp = mutableListOf<Mpd.Song>()
    }

    return haystack
}

fun wholeSongFromUri(uri: String): Map<String, Any> {
    val song = databaseByUri.getOrElse(uri, { throw Exception("Uri not in DB")})

    // first try to get album uri by splitting on path
    // else O(n) iterate over the db looking for one with the same name
    val idx = song.uri.reversed().indexOf("/")
    val path = song.uri.substring(0, song.uri.length - idx-1)

    val expensiveQuery = {
        val albums = byAlbum.filter{ (it.value.get("title") as String) == song.album}.values.toList()
        if (albums.size == 0)
            ""
        else
            albums[0]["img"] as String
    }

    val img = when(val it = byAlbum.get(path)) {
        null -> expensiveQuery()
        else -> it["img"] as String
    }

    return song.json.getMap().plus("img" to img)
}