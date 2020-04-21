package com.apollon.server.response

import com.apollon.server.main.Log
import com.apollon.server.streaming.SongMetadata
import com.apollon.server.thirdparties.getLyricsResponse
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

sealed class Response {
    data class Song(val uri: String, val metadata: SongMetadata, val quality: String, val path: String, val isFirst: Boolean) : Response()
    data class Search(val songs: JsonArray) : Response()
    class Error(val msg: String) : Response()
    class Ok() : Response()
    class AllByArtist(val all: JsonArray) : Response()
    class AllByAlbum(val all: JsonArray) : Response()
    class AllByGenre(val all: JsonArray) : Response()
    class SingleGenre(val key: String, val artists: JsonArray) : Response()
    class SingleArtist(val content: JsonObject) : Response()
    class SingleAlbum(val content: JsonObject) : Response()
    class ListPlaylist(val list: JsonArray) : Response()
    class GetPlaylist(val obj: JsonObject) : Response()
    class Lyrics(val artist: String, val song: String) : Response()
    class SongConversionDone(val uri: String) : Response()
    class SongConversionOngoing(val uri: String) : Response()
}

fun Response.asString(): String {
    return when (this) {
        is Response.Song -> "Response.Song: " + this.uri
        is Response.Search -> "Response.Search: " + this.songs
        is Response.Error -> "Response.Error: " + this.msg
        is Response.Ok -> "Response.Ok"
        is Response.AllByArtist -> "Response.AllByArtist: " + this.all.size()
        is Response.AllByAlbum -> "Response.AllByAlbum: " + this.all.size()
        is Response.AllByGenre -> "Response.AllByGenre: " + this.all.size()
        is Response.SingleAlbum -> "Response.SingleAlbum"
        is Response.SingleGenre -> "Response.SingleGenre"
        is Response.SingleArtist -> "Response.SingleArtist"
        is Response.ListPlaylist -> "Response.ListPlaylist"
        is Response.GetPlaylist -> "Response.GetPlaylist"
        is Response.Lyrics -> "Response.Lyrics: " + this.song
        is Response.SongConversionDone -> "Response.SongConversionDone" + this.uri
        is Response.SongConversionOngoing -> "Response.SongConversionDone" + this.uri
    }
}

fun sendWhenFileExists(vertx: Vertx, httpResp: HttpServerResponse, _response: Response.Song, cnt: Int) {
    val response = when (cnt) {
        in 0..10 -> _response
        else -> Response.Error("No path on disk")
    }

    fun sendMap(map: Map<String, Any>) {
        val buffer = Buffer.buffer(JsonObject(map).toString())
        httpResp.putHeader("content-length", buffer.length().toString())
        httpResp.putHeader("content-type", "application/json")
        httpResp.write(buffer)
        httpResp.end()

        val result = when (response) { // captures response // ignore warning
            else -> response.javaClass.name
        }
        Log.info(
            "New request for /: \"" + response.asString() +
                    "\" --> status code = " + httpResp.statusCode +
                    " --> Content: " + result
        )
    }
    when (response) {
        is Response.Error -> sendMap(
            hashMapOf(
                "response" to "error",
                "msg" to response.msg
            )
        )
        is Response.Song -> {
            val op = { status: Boolean ->
                Log.info("Disk I/O: " + response.path + ":" + cnt + "=" + status.toString())
                if (status) {
                    val send = { sendMap(hashMapOf("response" to "new-song", "metadata" to response.metadata,
                            "quality" to response.quality, "uri" to response.uri)) }
                    if (response.isFirst)
                        vertx.setTimer(1000, { send() }) // TODO fittizio because Android
                    else
                        send()
                } else {
                    // repeat
                    vertx.setTimer(100, { sendWhenFileExists(vertx, httpResp, response, cnt + 1) })
                }
            }
            vertx.fileSystem().exists(response.path, { result -> op(result.result()) })
            /*
               val status = File(response.path).exists()
                op(status)
             */
        }
    }
}

fun generateReply(vertx: Vertx, httpResp: HttpServerResponse, response: Response) {

    fun sendMap(map: Map<String, Any>) {
        val buffer = Buffer.buffer(JsonObject(map).toString())
        httpResp.putHeader("content-length", buffer.length().toString())
        httpResp.putHeader("content-type", "application/json")
        httpResp.write(buffer)
        httpResp.end()
        val result = when (response) {
            is Response.Error -> response.msg
            else -> response.javaClass.name
        }
        Log.info("New request for /: \"" + response.asString() +
                "\" --> status code = " + httpResp.statusCode +
                " --> Content: " + result)
    }

    when (response) {
        is Response.Song -> sendWhenFileExists(vertx, httpResp, response, 0)
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
                "artists" to response.artists
            ))
        is Response.SingleArtist -> sendMap(
            hashMapOf(
                "response" to "artist",
                "artist" to response.content
            ))
        is Response.ListPlaylist -> sendMap(
            hashMapOf(
                "response" to "list-playlists",
                "result" to response.list
            )
        )
        is Response.GetPlaylist -> sendMap(
            hashMapOf(
                "response" to "get-playlist",
                "result" to response.obj
            )
        )
        is Response.SongConversionOngoing -> sendMap(
            hashMapOf(
                "response" to "conversion-status",
                "result" to "ongoing"
            )
        )
        is Response.SongConversionDone -> sendMap(
            hashMapOf(
                "response" to "conversion-status",
                "result" to "done"
            )
        )
        is Response.Lyrics -> {

            val success = { lyrics: String ->
                sendMap(
                    hashMapOf(
                        "response" to "lyrics",
                        "artist" to response.artist,
                        "song" to response.song,
                        "lyrics" to lyrics
                    )) }
            val failure = {
                generateReply(vertx, httpResp, Response.Error("no lyrics found"))
            }
            getLyricsResponse(vertx, response.artist, response.song, success, failure)
        }
    }
}
