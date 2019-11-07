package com.apollon.server.request

import com.apollon.server.main.authenticateUser
import com.apollon.server.main.errLog
import com.apollon.server.streaming.QUALITY
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject

fun parse(req: Buffer): Request {
    val j: JsonObject
    try {
        j = req.toJsonObject()
    } catch (e: Exception) {
        errLog(e.stackTrace.toString())
        return Request.Error("Invalid Json")
    }

    val user = j.getString("user")
    val pass = j.getString("password")
    if (user == null || pass == null)
        return Request.Error("Invalid auth params")
    else if (!authenticateUser(user, pass)) {
        return Request.Error("Invalid auth")
    }
    if (j.getString("action") == null) {
        return Request.Error("No action in response")
    }

    val action = j.getString("action")
    return when (action) {
        "error" -> Request.Error(j.getString("msg"))
        "new-song" -> {
            val uri = j.getString("uri")
            val quality = j.getString("quality")
            Request.NewSong(uri, quality)
        }
        "song-done" -> {
            val uri = j.getString("uri")
            val quality = j.getString("quality")
            Request.SongDone(uri, quality)
        }
        "search" -> {
            val keys = j.getString("keys")
            Request.Search(keys.split(" "))
        }
        "all-by-artist" -> Request.AllByArtist()
        "all-by-album" -> Request.AllByAlbum()
        "all-by-genre" -> Request.AllByGenre()
        "genre" -> {
            val k = j.getString("key")
            Request.SingleGenre(k)
        }
        "artist" -> {
            val k = j.getString("key")
            Request.SingleArtist(k)
        }
        "album" -> {
            val title = j.getString("key")
            Request.SingleAlbum(title)
        }
        "lyrics" -> {
            val artist = j.getString("artist")
            val song = j.getString("song")
            Request.Lyrics(artist, song)
        }
        "challenge-login" -> {
            Request.ChallengeLogin()
        }
        else -> Request.Error("Unknown action")
    }
}

sealed class Request {
    class NewSong(val uri: String, val quality: String) : Request()
    class SongDone(val uri: String, val quality: String) : Request()
    class Error(val msg: String) : Request()
    class Search(val keys: List<String>) : Request()
    class AllByGenre() : Request()
    class AllByArtist() : Request()
    class AllByAlbum() : Request()
    class SingleAlbum(val title: String) : Request()
    class SingleArtist(val name: String) : Request()
    class SingleGenre(val key: String) : Request()
    class Lyrics(val artist: String, val song: String) : Request()
    class ChallengeLogin() : Request()
}

fun Request.NewSong.quality(): QUALITY {
    return when (this.quality) {
        "high" -> QUALITY.HIGH
        "low" -> QUALITY.LOW
        else -> QUALITY.MEDIUM
    }
}
