package com.mozapp.server.thirdparties

import com.mozapp.server.main.errLog
import com.mozapp.server.streaming.LIBRARY
import org.musicbrainz.controller.Artist
import org.musicbrainz.controller.Release
import org.musicbrainz.controller.Controller
import java.io.IOException
import java.util.logging.Level

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
