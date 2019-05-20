package com.streaming.jsonResponse

import com.streaming.status.QUALITY
import io.vertx.core.json.*

fun parse(resp: String): Response{
    var j: JsonObject
    try {
        j = JsonObject(resp)
    } catch(e: Exception){
        println(e)
        return Response.Error("Invalid Json")
    }
    if(j.getString("action") == null){
        println("return")
        return Response.Error("No action in response")
    }
    val action = j.getString("action")
    return when(action){
        "close" -> Response.Close()
        "pause" -> Response.Pause()
        "auth" -> {
            val user = j.getString("user")
            val pass = j.getString("password")
            if(user == null || pass == null)
                Response.Error("Invalid auth request")
            else
                Response.Auth(user, pass)
        }
        "continue" -> Response.Continue()
        "new-song" -> {
            val uri = j.getString("uri")
            val quality = j.getString("quality")
            val startTime = j.getDouble("startTime")
            if(startTime == null || quality == null || uri == null){
                return Response.Error("Invalid json request for new-song action")
            } else {
                // TODO validate song
                return Response.NewSong(uri, startTime, quality)
            }
        }
        else -> { assert(false); Response.Error("Assertion error") }
    }
}
// TODO SEARCH
open class Response private constructor() {
    class NewSong(val uri: String, val startTime: Double, val quality: String): Response()
    class Pause(): Response()
    class Error(val msg: String): Response()
    class Close(): Response()
    class Continue(): Response()
    class Auth(val user:String, val pass: String): Response()
}

fun Response.NewSong.quality(): QUALITY{
    return when(this.quality){
        "high" -> QUALITY.HIGH
        "low" -> QUALITY.LOW
        else -> QUALITY.MEDIUM
    }
}