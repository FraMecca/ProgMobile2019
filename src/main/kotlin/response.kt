package com.mozapp.server.response

import com.mozapp.server.main.*
import com.mozapp.server.streaming.*
import com.mozapp.server.thirdparties.getLyricsResponse
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.core.json.*

sealed class Response {
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
    class Lyrics(val artist: String, val song: String): Response()
}

fun generateReply(vertx: Vertx, httpResp: HttpServerResponse, response: Response){

    fun sendMap(map: Map<String, Any>){
        val buffer = Buffer.buffer(JsonObject(map).toString())
        httpResp.putHeader("content-length", buffer.length().toString())
        httpResp.putHeader("content-type", "application/json")
        httpResp.write(buffer)
        httpResp.end()
        val result = when(response){
            is Response.Error -> response.msg
            else -> response.javaClass.name
        }
        Log.info("New request for /: \"" + buffer
                + "\" --> status code = " + httpResp.statusCode
                + " --> Content: " + result)
    }

    when(response){
        is Response.Song -> sendMap(
            hashMapOf(
                "response" to "new-song",
                "metadata" to response.metadata,
                "quality" to response.quality,
                "uri" to response.uri
            ))
        is Response.Error -> sendMap(
            hashMapOf(
                "response" to "error",
                "msg" to response.msg
            ))
        is Response.Ok -> sendMap(
            hashMapOf(
                "response" to "ok"
            ))
        is Response.Search -> sendMap(
            hashMapOf(
                "response" to "search",
                "values" to response.songs
            ))
        is Response.AllByAlbum -> sendMap(
            hashMapOf(
                "response" to "all-albums",
                "values" to response.all
            ))
        is Response.AllByArtist -> sendMap(
            hashMapOf(
                "response" to "all-artists",
                "values" to response.all
            ))
        is Response.AllByGenre -> sendMap(
            hashMapOf(
                "response" to "all-genres",
                "values" to response.all
            ))
        is Response.SingleAlbum -> sendMap(
            hashMapOf(
                "response" to "album",
                "album" to response.content
            ))
        is Response.SingleGenre -> sendMap(
            hashMapOf(
                "response" to "genre",
                "key" to response.key,
                "genre" to response.content
            ))
        is Response.SingleArtist -> sendMap(
            hashMapOf(
                "response" to "artist",
                "artist" to response.content
            ))
        is Response.Lyrics -> {

            val success = { lyrics: String ->
                sendMap(
                    hashMapOf(
                        "response" to "lyrics",
                        "artist" to response.artist,
                        "song" to response.song,
                        "lyrics" to lyrics
                    ))}
            val failure = {
                generateReply(vertx, httpResp, Response.Error("no lyrics found"))
            }
            getLyricsResponse(vertx, response.artist, response.song, success, failure)
        }
    }
}

