package com.mozapp.server.streaming

import com.mozapp.server.main.*
import com.mozapp.server.response.*
import java.io.File
import java.security.MessageDigest
import java.io.IOException
import io.vertx.core.json.JsonObject


val DATABASE = "/home/user/.mpd/db2.json"
val WORKDIR = File("/tmp/mozapp/")
val LIBRARY = File("/media/asparagi/vibbra/")

val audioFiles: MutableMap<String, Pair<Int, Process>> = LinkedHashMap<String, Pair<Int, Process>>() // sha -> ( references to File , ffmpegProcess )
val metadataMap: MutableMap<String, SongMetadata> = LinkedHashMap<String, SongMetadata>() // uri -> metadata

fun incrementReference(sha: String){
    val r = audioFiles[sha]!!
    val ref = r.first + 1
    audioFiles[sha] = Pair(ref, r.second)
}

fun decrementReference(sha: String){
    val r = audioFiles[sha]!!
    val ref = r.first - 1
    audioFiles[sha] = Pair(ref, r.second)
}

fun removeReference(sha: String){
    val r = audioFiles[sha]!!
    val proc = r.second
    proc.destroyForcibly()
    File(getFullPath(sha)).delete()
    audioFiles.remove(sha)

}

fun getMetadataFromUri(uri: String): SongMetadata{
    if(uri in metadataMap)
        return metadataMap[uri]!!
    else {
        val metadata = uri.getMetadata()
        metadataMap[uri] = metadata
        return metadata
    }
}

fun computeSha(uri: String, quality: String) : String{
    fun bytesToHex(hash: ByteArray): String {
        val hexString = StringBuffer()
        for (i in hash.indices) {
            val hex = Integer.toHexString(0xff and hash[i].toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    val inputStr = uri.toByteArray()+quality.toByteArray()
    val sha = MessageDigest.getInstance("SHA-1").digest(inputStr)
    return bytesToHex(sha)
}

fun getFullPath(sha: String): String {
    val p =  WORKDIR.absolutePath + "/" + sha + ".ogg"
    return p
}

fun checkFileAccess(uri: String, access: File): Boolean {
    val fp = File(uri)
    val ret =  fp.canonicalFile.toPath().startsWith(access.toPath())
    return ret
}

fun generateNewFile(uri: String, quality: String, newFile: String, sha: String) : Response
{
    val conv = runConversion(uri, newFile, quality)
    val doFFMPEG = conv.first
    return when(doFFMPEG) {
        is FFMPEGStream.Invalid -> Response.Error(doFFMPEG.msg)
        else -> {
            val metadata = getMetadataFromUri(uri)
            audioFiles[sha] = Pair(1, conv.second!!)
            metadataMap[uri] = metadata
            /*
            if(File(newFile).exists() == false)
                Response.Error("ffmpeg conversion error")
               -- > Can't do this, it could take some time to have a file in the working folder
             */
                Response.Song(newFile, metadata, quality)
        }
    }
}

data class SongMetadata(val json: JsonObject){}
enum class QUALITY { HIGH, MEDIUM, LOW }

fun String.getMetadata(): SongMetadata {
    val proc = ProcessBuilder("mediainfo", "--Output=JSON", this)
        .directory(WORKDIR)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor() // TODO is blocking
    if(proc.exitValue() != 0) throw Exception("Mediainfo failed" + proc.exitValue())
    else{
        val str = String(proc.inputStream.readBytes(), Charsets.UTF_8)
        val j = JsonObject(str.toString())
        return SongMetadata(j)
    }
}

open class FFMPEGStream private constructor() {
    class Valid(): FFMPEGStream()
    data class Invalid(val msg: String): FFMPEGStream()
}

fun runConversion(src: String, dst: String, quality: String): Pair<FFMPEGStream, Process?> {
//    val command = "ffmpeg -i " +  src + " -f ogg -q 5 " + dst
    val q = when(quality){
        "High" -> 10
        "Low" -> 3
        else -> 6
    }.toString()
    try {
        val proc = ProcessBuilder("ffmpeg", "-i", src, "-f", "ogg", "-q", q, dst)
            .directory(LIBRARY)
            /*
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
             */
            .start()
        if(!proc.isAlive)
            return Pair(FFMPEGStream.Invalid("error: code = " + proc.exitValue().toString()), null)
        else
            return Pair(FFMPEGStream.Valid(), proc)
    } catch(e: IOException) {
        e.printStackTrace()
        return Pair(FFMPEGStream.Invalid("IOException"), null)
    }
}