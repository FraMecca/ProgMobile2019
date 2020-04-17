package com.apollon.server.streaming

import com.apollon.server.main.errLog
import com.apollon.server.response.Response
import io.vertx.core.json.JsonObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

val DATABASE = "/home/user/.mpd/db2.json"
val WORKDIR = File("/tmp/apollon/")
val LIBRARY = File("/media/asparagi/vibbra/")

data class AudioFileData(val played: Int, val process: Process, val size: Long)

val audioFiles: MutableMap<String, AudioFileData> = LinkedHashMap() // sha -> ( references to File , ffmpegProcess )
val metadataMap: MutableMap<String, SongMetadata> = LinkedHashMap<String, SongMetadata>() // uri -> metadata

fun incrementReference(sha: String) {
    val r = audioFiles[sha]!!
    audioFiles[sha] = AudioFileData(r.played+1, r.process, r.size)
}

fun decrementReference(sha: String) {
    val r = audioFiles[sha]!!
    audioFiles[sha] = AudioFileData(r.played-1, r.process, r.size)
}

fun removeReference(sha: String) {
    val r = audioFiles[sha]!!
    val proc = r.process
    proc.destroyForcibly()
    File(getFullPath(sha)).delete()
    audioFiles.remove(sha)
}

fun getMetadataFromUri(uri: String): SongMetadata {
    if (uri in metadataMap)
        return metadataMap[uri]!!
    else {
        val metadata = uri.getMetadata()
        metadataMap[uri] = metadata
        return metadata
    }
}

fun computeSha(uri: String, quality: String): String {
    fun bytesToHex(hash: ByteArray): String {
        val hexString = StringBuffer()
        for (i in hash.indices) {
            val hex = Integer.toHexString(0xff and hash[i].toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    val inputStr = uri.toByteArray() + quality.toByteArray()
    val sha = MessageDigest.getInstance("SHA-1").digest(inputStr)
    return bytesToHex(sha)
}

fun getFullPath(sha: String): String {
    val p = WORKDIR.absolutePath + "/" + sha + ".mp3"
    return p
}

fun checkFileAccess(uri: String, access: File): Boolean {
    val fp = File(uri)
    val ret = fp.toPath().startsWith(access.toPath())
    return ret
}

fun generateNewFile(uri: String, quality: String, newFile: String, sha: String): Response {
    val conv = runConversion(uri, newFile, quality)
    val doFFMPEG = conv.first
    return when (doFFMPEG) {
        is FFMPEGStream.Invalid -> Response.Error(doFFMPEG.msg)
        else -> {
            val metadata = getMetadataFromUri(uri)
            audioFiles[sha] = AudioFileData(1, conv.second!!, -1)
            metadataMap[uri] = metadata
            val abstractUri = newFile.replace(WORKDIR.absolutePath, "/file")
            Response.Song(abstractUri, metadata, quality, newFile, true)
        }
    }
}

data class SongMetadata(val json: JsonObject)
enum class QUALITY { HIGH, MEDIUM, LOW }

fun String.getMetadata(): SongMetadata {
    val proc = ProcessBuilder("mediainfo", "--Output=JSON", this)
        .directory(WORKDIR)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor() // TODO is blocking
    if (proc.exitValue() != 0) {
        errLog("Mediainfo Failed")
        throw Exception("Mediainfo failed" + proc.exitValue())
    } else {
        val str = String(proc.inputStream.readBytes(), Charsets.UTF_8)
        val j = JsonObject(str.toString())
        return SongMetadata(j)
    }
}

sealed class FFMPEGStream {
    class Valid() : FFMPEGStream()
    data class Invalid(val msg: String) : FFMPEGStream()
}

fun runConversion(src: String, dst: String, quality: String): Pair<FFMPEGStream, Process?> {
//    val command = "ffmpeg -i " +  src + " -f ogg -q 5 " + dst
    val q = when (quality) {
        "High" -> 10
        "Low" -> 3
        else -> 8
    }.toString()
    try {
        println(src + " " + dst)
        val proc = ProcessBuilder("ffmpeg", "-i", src, "-acodec", "libmp3lame", "-q", q, dst) // QUALITY TODO
            .directory(LIBRARY)
            /*
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
             */
            .start()
        if (!proc.isAlive)
            return Pair(FFMPEGStream.Invalid("error: code = " + proc.exitValue().toString()), null)
        else
            return Pair(FFMPEGStream.Valid(), proc)
    } catch (e: IOException) {
        errLog(e.stackTrace.toString())
        return Pair(FFMPEGStream.Invalid("IOException"), null)
    }
}

fun conversionDone(src: String): Boolean{
    val proc = ProcessBuilder("mediainfo", "--Output=JSON", src)
        .directory(WORKDIR)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor() // TODO is blocking
    if (proc.exitValue() != 0) {
        errLog("Mediainfo Failed")
        throw Exception("Mediainfo failed" + proc.exitValue())
    } else {
        val str = String(proc.inputStream.readBytes(), Charsets.UTF_8)
        val j = JsonObject(str.toString())
        val mp3Info = j.getJsonObject("media")
            .getJsonArray("track")
        if (mp3Info.size() >= 2)
            return "Duration" in mp3Info.getJsonObject(1).map
        else
            return false
    }
}

fun computeOffset(file: String, percentage: Long): Long{
    return when(percentage){
        0L -> 0L
        98L, 99L, 100L -> {
            val sha = file.substringAfterLast('/').substringBeforeLast(".mp3")
            val diskSize = audioFiles[sha]!!.size
            diskSize/100L*98
        }
        else -> {
            val sha = file.substringAfterLast('/').substringBeforeLast(".mp3")
            val diskSize = audioFiles[sha]!!.size
            diskSize/100L*percentage
        }
    }
}
