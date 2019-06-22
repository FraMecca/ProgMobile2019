package com.mozapp.server.request

import com.mozapp.server.main.*
import com.mozapp.server.streaming.*
import io.vertx.core.json.*
import io.vertx.core.buffer.Buffer

fun parse(req: Buffer): Request{
    val j: JsonObject
    try {
        j = req.toJsonObject()
    } catch(e: Exception){
        errLog(e.stackTrace.toString())
        return Request.Error("Invalid Json")
    }

    val user = j.getString("user")
    val pass = j.getString("password")
    if(user == null || pass == null)
        return Request.Error("Invalid auth params")
    else if(!authenticateUser(user, pass)){
        return Request.Error("Invalid auth")
    }
    if(j.getString("action") == null){
        return Request.Error("No action in response")
    }

    val action = j.getString("action")
    return when(action){
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
        "all-by-album" ->  Request.AllByAlbum()
        "all-by-genre" ->  Request.AllByGenre()
        "genre" ->  {
            val k = j.getString("key")
            Request.SingleGenre(k)
        }
        "artist" ->  {
            val k = j.getString("key")
            Request.SingleArtist(k)
        }
        "album" ->  {
            val title = j.getString("key")
            Request.SingleAlbum(title)
        }
        else -> Request.Error("Unknown action")

    }
}
open class Request private constructor() {
    class NewSong(val uri: String, val quality: String): Request()
    class SongDone(val uri: String, val quality: String): Request()
    class Error(val msg: String): Request()
    class Search(val keys: List<String>): Request()
    class AllByGenre(): Request()
    class AllByArtist(): Request()
    class AllByAlbum(): Request()
    class SingleAlbum(val title: String): Request()
    class SingleArtist(val name: String): Request()
    class SingleGenre(val key: String): Request()
}

fun Request.NewSong.quality(): QUALITY{
    return when(this.quality){
        "high" -> QUALITY.HIGH
        "low" -> QUALITY.LOW
        else -> QUALITY.MEDIUM
    }
}