package com.mozapp.server.thirdparties

import com.mozapp.server.main.errLog
import com.mozapp.server.streaming.LIBRARY
import com.mozapp.server.streaming.WORKDIR
import io.vertx.core.Vertx
import org.musicbrainz.controller.Artist
import org.musicbrainz.controller.Release
import org.musicbrainz.controller.Controller
import java.io.IOException
import java.util.logging.Level
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpMethod
import io.vertx.core.impl.VertxImpl.context



fun getArtistImage(artist: String): String{
    /*
    val controller: Artist = Artist()
    controller.searchFilter.limit = 5;
    controller.search(artist)
    val results = controller.fullSearchResultList

    val artist = results[0].artist
    */
    val url = getImgWikidata(artist)
    return url
}

@Deprecated("used to create db")
fun getImgWikidata(key: String): String { // TODO : understand how to get smaller size
    return getImgFromDb(key, "artist")
}

@Deprecated("used to create db")
fun getCoverArt(key: String): String{
    return getImgFromDb(key, "cover")
}

@Deprecated("used to create db")
fun getImgFromDb(key: String, type: String): String { // TODO : understand how to get smaller size
    try {
        val proc = ProcessBuilder("python3", "/home/user/.mpd/mm.py", type, key)
            .directory(LIBRARY)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
        proc.waitFor()
        if(proc.exitValue() != 0)
            return ""
        else
            return String(proc.inputStream.readBytes(), Charsets.UTF_8)
    } catch (e: IOException) {
        errLog(e.stackTrace.toString())
        return ""
    }
}

fun getLyricsResponse(vertx: Vertx, artist: String, song: String,
                      handleSuccessfulResponse: (r: String) -> Unit, handleFailure: () -> Unit){
    val fuckXml  = { xml:String ->
        if("<Lyric />" in xml)
            ""
        else 
            xml.substringAfterLast("<Lyric>")
                .replace("</GetLyricResult>", "")
                .replace("</Lyric>", "")
    }

    val url = "/apiv1.asmx/SearchLyricDirect?artist=$artist&song=$song"
    val host = "http://api.chartlyrics.com"

    // HTTP request:
    val client = vertx.createHttpClient()
    client.requestAbs( HttpMethod.GET, host+url, { response ->
        val r = response.bodyHandler {
            if(response.statusCode() == 200) {
                val responseXML = fuckXml(it.toString())
                handleSuccessfulResponse(responseXML)
            } else {
                // retry with python requests
                try{
                   val responseXML = fuckXml(getLyrics(host+url))
                    handleSuccessfulResponse(responseXML)
                } catch(e: Exception){
                    handleFailure()
                }
            }
        }
    }).end()
}

private fun getLyrics(url: String): String{

    println(url)
    val cmd = "import requests as r; print(r.get('$url').text)"
    val proc = ProcessBuilder("python3", "-c", cmd)
        .directory(WORKDIR)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor() // TODO is blocking
    if(proc.exitValue() != 0){
        errLog("requests failed")
        throw Exception("requests failed" + proc.exitValue())
    }
    else{
        return String(proc.inputStream.readBytes(), Charsets.UTF_8)
    }
}
