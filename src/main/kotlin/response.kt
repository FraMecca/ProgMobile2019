package com.mozapp.server.response

import com.mozapp.server.main.*
import com.mozapp.server.streaming.*
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.*

open class Response private constructor() {
    data class Song(val uri: String, val metadata: SongMetadata, val quality: String): Response()
    data class Search(val songs: JsonArray): Response()
    class Error(val msg: String): Response()
    class Ok(): Response()
    class AllByArtist(val all: JsonArray): Response()
    class AllByAlbum(val all: JsonArray): Response()
    class AllByGenre(val all: JsonObject): Response()
    class SingleGenre(val key: String, val content: JsonObject): Response()
    class SingleArtist(val content: JsonObject): Response()
    class SingleAlbum(val content: JsonObject): Response()
}

fun generateReply(response: Response): Buffer {
    val map = when(response){
        is Response.Song -> hashMapOf(
            "response" to "new-song",
            "metadata" to response.metadata,
            "quality" to response.quality,
            "uri" to response.uri
        )
        is Response.Error -> hashMapOf(
            "response" to "error",
            "msg" to response.msg
        )
        is Response.Ok -> hashMapOf(
            "response" to "ok"
        )
        is Response.Search -> hashMapOf(
            "response" to "search",
            "values" to response.songs
        )
        is Response.AllByAlbum -> hashMapOf(
            "response" to "all-albums",
            "values" to response.all
        )
        is Response.AllByArtist -> hashMapOf(
            "response" to "all-artists",
            "values" to response.all
        )
        is Response.AllByGenre -> hashMapOf(
            "response" to "all-genres",
            "values" to response.all
        )
        is Response.SingleAlbum -> hashMapOf(
            "response" to "album",
            "album" to response.content
        )
        is Response.SingleGenre -> hashMapOf(
            "response" to "genre",
            "key" to response.key,
            "genre" to response.content
        )
        is Response.SingleArtist -> hashMapOf(
            "response" to "artist",
            "artist" to response.content
        )
        else -> {throw Exception("unreachable code")}
    }
    val j: JsonObject = JsonObject(map as Map<String, Any>?)
    val buf = Buffer.buffer(j.toString())
    return buf
}