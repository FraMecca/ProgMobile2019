package com.streaming.jsonResponse

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser

fun parse(resp: String): Response{
    val parser = Parser.default()
    val j = parser.parse(resp) as JsonObject
    if(j.string("action") == null){
        return Response.Error("No action in response")
    }
    val action = j.string("action")
    return when(action){
        "close" -> Response.Close()
        "auth" -> {
            val user = j.string("user")
            val pass = j.string("password")
            if(user == null || pass == null)
                Response.Error("Invalid auth request")
            else{
                if(user == "mario" && pass == "rossi") // TODO FIXME
                    Response.Auth()
                else
                    Response.Error("invalid credentials")
            }

        }
        "continue" -> Response.Continue()
        "new-song" -> {
            val uri = j.string("uri")
            val quality = j.string("quality")
            val startTime = j.double("startTime")
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
    class Error(val msg: String): Response()
    class Close(): Response()
    class Continue(): Response()
    class Auth(): Response()
}
