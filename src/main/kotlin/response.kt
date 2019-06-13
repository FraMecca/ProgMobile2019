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
}

fun generateReply(response: Response): Buffer {
    val map = when(response){
        is Response.Song -> hashMapOf(
            "response" to "song",
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
        else -> {throw Exception("unreachable code")}
    }
    val j: JsonObject = JsonObject(map as Map<String, Any>?)
    val buf = Buffer.buffer(j.toString())
    return buf
}