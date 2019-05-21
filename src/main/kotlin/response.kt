package com.streaming.response

import io.vertx.core.json.*
import com.streaming.status.QUALITY
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.buffer.Buffer

open class Response private constructor() {
    class Song(val metadata: SongMetadata, val quality: String): Response()
    class Stream(val bytes: ByteArray): Response()
    class Error(val msg: String): Response()
    class Close(): Response()
    class Ok(): Response()
    class SuccessfulAuth(): Response()
    class InvalidAction(): Response()
}

data class SongMetadata(val title: String){
    fun toJson(): String {
        var j = JsonObject()
        j.put("title", title)

        return j.toString()
    }
}

fun reply(ws: ServerWebSocket, response: Response){
    val map = when(response){
        is Response.Song -> hashMapOf(
            "response" to "song",
            "metadata" to response.metadata.toJson(),
            "quality" to response.quality
        )
        is Response.Stream -> hashMapOf(
            "response" to "stream",
            "buf" to response.bytes // should be encoded automatically when put into VertX.JSONObj
        )
        is Response.SuccessfulAuth -> hashMapOf(
            "response" to "auth"
        )
        is Response.Error -> hashMapOf(
            "response" to "error",
            "msg" to response.msg
        )
        is Response.Close -> hashMapOf(
            "response" to "close"
        )
        is Response.Ok -> hashMapOf(
            "response" to "ok"
        )
        is Response.InvalidAction -> hashMapOf(
            "response" to "error",
            "msg" to "Invalid Action"
        )
        else -> {assert(false); hashMapOf("response" to "error", "msg" to "assert false")}
    }
    var j: JsonObject = JsonObject(map as Map<String, Any>?)
    ws.write(j.toBuffer())
}