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
        "new-playlist" -> {
            val user = j.getString("user")
            val title = j.getString("title")
            val uris = j.getJsonArray("uris").toList().map { it -> it as String }
            Request.NewPlaylist(user, title, uris)
        }
        "remove-playlist" -> {
            val user = j.getString("user")
            val title = j.getString("title")
            Request.RemovePlaylist(user, title)
        }
        "modify-playlist" -> {
            val user = j.getString("user")
            val title = j.getString("title")
            val uris = j.getJsonArray("uris").toList().map { it -> it as String }
            val action = j.getString("playlist-action")
            if (action == "remove")
                Request.RemoveFromPlaylist(user, title, uris)
            else if (action == "add")
                Request.AddToPlaylist(user, title, uris)
            else
                return Request.Error("Invalid playlist-action")
        }
        "list-playlists" -> {
            val user = j.getString("user")
            return Request.ListPlaylists(user)
        }
        "get-playlist" -> {
            val user = j.getString("user")
            val title = j.getString("title")
            return Request.GetPlaylist(user, title)
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
    class NewPlaylist(val user: String, val title: String, val uris: List<String>) : Request()
    class RemovePlaylist(val user: String, val title: String) : Request()
    class AddToPlaylist(val user: String, val title: String, val uris: List<String>) : Request()
    class RemoveFromPlaylist(val user: String, val title: String, val uris: List<String>) : Request()
    class ListPlaylists(val user: String) : Request()
    class GetPlaylist(val user: String, val title: String) : Request()
}

fun Request.NewSong.quality(): QUALITY {
    return when (this.quality) {
        "high" -> QUALITY.HIGH
        "low" -> QUALITY.LOW
        else -> QUALITY.MEDIUM
    }
}
