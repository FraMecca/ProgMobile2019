package com.mozapp.server.thirdparties

import com.mozapp.server.streaming.LIBRARY
import org.musicbrainz.controller.Artist
import org.musicbrainz.controller.Release
import org.musicbrainz.controller.Controller
import java.io.IOException

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

fun getImgWikidata(key: String): String { // TODO : understand how to get smaller size
    return getImgFromDb(key, "artist")
}

fun getCoverArt(key: String): String{
    return getImgFromDb(key, "cover")
}

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
        e.printStackTrace()
        return ""
    }
}
