package com.streaming.request

import com.streaming.main.*
import io.vertx.core.json.*
import io.vertx.core.buffer.Buffer

fun parse(req: Buffer): Request{
    val j: JsonObject
    try {
        j = req.toJsonObject()
    } catch(e: Exception){
        println(e)
        return Request.Error("Invalid Json")
    }

    val user = j.getString("user")
    val pass = j.getString("password")
    if(user == null || pass == null)
        Request.Error("Invalid auth params")
    else if(!(user == "mario" && pass == "rossi")){ // TODO
        Request.Error("Invalid auth")
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
            return Request.NewSong(uri, quality)
            }
        "song-done" -> {
            val uri = j.getString("uri")
            val quality = j.getString("quality")
            return Request.SongDone(uri, quality)
        }
        "search" -> {
            val keys = j.getString("keys")
            return Request.Search(keys.split(" "))
        }
        else -> { assert(false); Request.Error("Assertion error") }
    }
}
open class Request private constructor() {
    class NewSong(val uri: String, val quality: String): Request()
    class SongDone(val uri: String, val quality: String): Request()
    class Error(val msg: String): Request()
    class Search(val keys: List<String>): Request()
}

fun Request.NewSong.quality(): QUALITY{
    return when(this.quality){
        "high" -> QUALITY.HIGH
        "low" -> QUALITY.LOW
        else -> QUALITY.MEDIUM
    }
}