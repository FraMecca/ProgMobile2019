package com.streaming.main

import com.streaming.database.*
import io.vertx.core.Vertx
import io.vertx.core.http.*
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import java.io.IOException
import com.streaming.request.*
import com.streaming.response.*
import java.security.MessageDigest
import java.io.File

///// CONSTANTS
//val DATABASE = "/home/user/.mpd/database"
val DATABASE = "/home/user/db2.json" // TODO come on...
val WORKDIR = File("/tmp/mozapp/")
val LIBRARY = File("/media/asparagi/vibbra/")

val audioFiles = mutableMapOf("bottom" to 0)

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
    val src = fp.canonicalPath
    val ret =  fp.canonicalFile.toPath().startsWith(access.toPath())
    return ret
}

fun generateNewFile(uri: String, quality: String) : Response
{
    // check file access: do not evade LIBRARY
    if(!checkFileAccess(uri, LIBRARY)) return Response.Error("Invalid file")

    val sha = computeSha(uri, quality)
    val newFile = getFullPath(sha)
    // check if file exists and can be reused
    if(sha in audioFiles) {
        audioFiles[sha] = audioFiles[sha]!! + 1
        assert(File(newFile).exists())
        val metadata = uri.getMetadata()
        return Response.Song(newFile, metadata, quality)
    } else {
        val doFFMPEG = uri.runConversion(newFile)
        return when(doFFMPEG) {
            is FFMPEGStream.Invalid -> Response.Error(doFFMPEG.msg)
            else -> {
                val metadata = uri.getMetadata()
                audioFiles[sha] = 1
                assert(File(newFile).exists())
                Response.Song(newFile, metadata, quality)
            }
        }
    }
}

// Here a Response is generated in reply to a Request
fun handle(buf: Buffer): Response{
    val req: Request = parse(buf)

    return when(req){
        is Request.Error -> Response.Error(req.msg)
        is Request.NewSong -> generateNewFile(req.uri, req.quality)
        is Request.SongDone -> {
            val sha = computeSha(req.uri, req.quality)
            assert(sha in audioFiles)
            assert(File(getFullPath(sha)).exists())

            val nUses = audioFiles[sha]!!
            when(nUses) {
                0 -> assert(false)
                1 -> {
                    audioFiles.remove(sha)
                    File(getFullPath(sha)).delete()
                }
                else -> audioFiles[sha] = nUses - 1
            }
            Response.Ok()
        }
        is Request.Search -> {
            val results = search(req.keys)
            val array = JsonArray( results.map { it.json })
            Response.Search(array)
        }
        else -> {assert(false); Response.Error("assert false")}
    }
}


fun routing(req: HttpServerRequest){
    print("New request: " + req.path())
    val pathArray = req.path().split("/")
    println(pathArray)
    val resp = req.response()
    when(pathArray[1]){
        "file" -> {
            try {
                val file = WORKDIR.absolutePath + "/" + pathArray.slice(2..pathArray.size - 1).joinToString("/")
            if(checkFileAccess(file, WORKDIR) && File(file).exists()) {
                    resp.sendFile(file)
                } else {
                    resp.statusCode = 404
                    resp.end()
                }
            } catch (e: Exception){
                resp.statusCode = 500
                resp.end()
            }
        }
        else -> req.bodyHandler({ buf ->
            val respStruct: Response = handle(buf)
            val buffer = generateReply(respStruct)
            resp.putHeader("content-length", buffer.length().toString())
            resp.putHeader("content-type", "application/json")
            resp.write(buffer)
            resp.end()
        })
    }
}

fun main(args: Array<String>){
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()

    println("STARTING")
    updateDatabase(vertx, DATABASE)

    val host = "0.0.0.0"
    server.requestHandler({ request ->
        routing(request)
    })
    server.listen(8080, host, { res-> if (res.succeeded()) {
        println("Listening...")
    }else{
        println(("Failed to bind!"))
    } })
}

data class SongMetadata(val json: JsonObject){}
enum class QUALITY { HIGH, MEDIUM, LOW }

open class FFMPEGStream private constructor() {
    class Valid(): FFMPEGStream()
    data class Invalid(val msg: String): FFMPEGStream()
}

fun String.runConversion(dst: String): FFMPEGStream {
    val src = this
    val command = "ffmpeg -i " +  src + " -f ogg -q 5 " + dst
    try {
        val parts = command.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(LIBRARY)
            /*
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
             */
            .start()
        if(!proc.isAlive) return FFMPEGStream.Invalid("error: code = " + proc.exitValue().toString())
        else return FFMPEGStream.Valid()
    } catch(e: IOException) {
        e.printStackTrace()
        return FFMPEGStream.Invalid("IOException")
    }
}

fun String.getMetadata(): SongMetadata {
    val command = "mediainfo --Output=JSON " + this
    val parts = command.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
        .directory(WORKDIR)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    proc.waitFor() // TODO is blocking
    if(proc.exitValue() != 0) throw Exception("Mediainfo failed")
    else{
        val str = String(proc.inputStream.readBytes(), Charsets.UTF_8)
        val j = JsonObject(str.toString())
        return SongMetadata(j)
    }
}
