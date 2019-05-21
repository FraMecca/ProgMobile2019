package com.streaming.request

import com.streaming.status.QUALITY
import io.vertx.core.json.*

fun parse(resp: String): Request{
    var j: JsonObject
    try {
        j = JsonObject(resp)
    } catch(e: Exception){
        println(e)
        return Request.Error("Invalid Json")
    }
    if(j.getString("action") == null){
        println("return")
        return Request.Error("No action in response")
    }
    val action = j.getString("action")
    return when(action){
        "close" -> Request.Close()
        "pause" -> Request.Pause()
        "error" -> Request.Error(j.getString("msg"))
        "auth" -> {
            val user = j.getString("user")
            val pass = j.getString("password")
            if(user == null || pass == null)
                Request.Error("Invalid auth request")
            else
                Request.Auth(user, pass)
        }
        "continue" -> Request.Continue()
        "new-song" -> {
            val uri = j.getString("uri")
            val quality = j.getString("quality")
            val startTime = j.getDouble("start-time")
            if(startTime == null || quality == null || uri == null){
                return Request.Error("Invalid json request for new-song action")
            } else {
                // TODO validate song
                return Request.NewSong(uri, startTime, quality)
            }
        }
        else -> { assert(false); Request.Error("Assertion error") }
    }
}
// TODO SEARCH
open class Request private constructor() {
    class NewSong(val uri: String, val startTime: Double, val quality: String): Request()
    class Pause(): Request()
    class Error(val msg: String): Request()
    class Close(): Request()
    class Continue(): Request()
    class Auth(val user:String, val pass: String): Request()
}

fun Request.NewSong.quality(): QUALITY{
    return when(this.quality){
        "high" -> QUALITY.HIGH
        "low" -> QUALITY.LOW
        else -> QUALITY.MEDIUM
    }
}