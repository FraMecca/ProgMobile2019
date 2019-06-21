package com.mozapp.server.main

import com.mozapp.server.database.*
import com.mozapp.server.streaming.*
import com.mozapp.server.thirdparties.*
import com.mozapp.server.request.*
import com.mozapp.server.response.*
import io.vertx.core.Vertx
import io.vertx.core.http.*
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import java.io.File
import java.util.logging.Logger
import io.vertx.core.json.*
import java.io.*
import kotlin.system.exitProcess

val Log = Logger.getLogger("InfoLogging")
val users = LinkedHashMap<String, String>()
val USERSFILE = "users.json"


fun loadUsers(){
    val tmp = mutableListOf<Mpd.Song>()
    val file = File(USERSFILE)
    val reader = BufferedReader(FileReader(file) as Reader?)
    val people = JsonArray(reader.readText())
    people.forEach {
        val j = it as JsonObject
        val user = j.getString("user")
        val pass = j.getString("password")
        if(user == null || pass == null)
            throw Exception("Invalid user json in users.json")
        else
            users[user] = pass
    }
}

fun authenticateUser(user: String, password: String) : Boolean{
    println("AUTH: "+ user)
    if(user !in users)
        return false
    else if (users[user] != password)
        return false
    else
        return true
}

// a Response is generated in reply to a Request
fun handle(buf: Buffer): Response{
    val req: Request = parse(buf)

    return when(req){
        is Request.Error -> Response.Error(req.msg)
        // search for file if is in library and pass it to ffmpeg if it has not been converted already
        is Request.NewSong ->{
            // check file access: do not evade LIBRARY
            val uri = LIBRARY.path +"/"+ req.uri
            val sha = computeSha(uri, req.quality)
            val newFile = getFullPath(sha)

            if(!checkFileAccess(uri, LIBRARY))
                Response.Error("Invalid file")
            // check if file exists and can be reused
            else if(sha in audioFiles) {
                incrementReference(sha)
                val metadata = getMetadataFromUri(uri)
                Response.Song(newFile, metadata, req.quality)
            } else {
                generateNewFile(uri, req.quality, newFile, sha)
            }
        }
        // The client doesn't need the song anymore. Update references to file and delete if necessary
        is Request.SongDone -> {
            val sha = computeSha(LIBRARY.path + "/" + req.uri, req.quality)
            if(sha !in audioFiles)
                Response.Error("Song not playing")
            else if (File(getFullPath(sha)).exists() == false)
                Response.Error("File does not exist anymore")
            else {
                val nUses = audioFiles[sha]!!.first
                when (nUses) {
                    0 -> throw Exception("assertion: nUses can't be zero")
                    1 -> removeReference(sha)
                    else -> decrementReference(sha)
                }
                Response.Ok()
            }
        }
        is Request.Search -> {
            val results = search(req.keys)
            val array = JsonArray( results.map { it.json })
            Response.Search(array)
        }
        is Request.AllByArtist -> {
            val all = JsonObject(byArtist as Map<String, Any>?)
            Response.AllByArtist(all)
        }
        is Request.AllByAlbum -> {
            val all = JsonObject(byAlbum as Map<String, Any>?)
            Response.AllByAlbum(all)
        }
        is Request.AllByGenre -> {
            val all = JsonObject(byGenre as Map<String, Any>?)
            Response.AllByGenre(all)
        }
        is Request.SingleAlbum -> {
            if (req.title in byAlbum)
                Response.SingleAlbum(JsonObject(byAlbum[req.title]))
            else
                Response.Error("Not in db")
        }
        is Request.SingleArtist -> {
            if (req.name in byArtist)
                Response.SingleArtist(JsonObject(byArtist[req.name]))
            else
                Response.Error("Not in db")
        }
        is Request.SingleGenre -> {
            if (req.key in byGenre)
                Response.SingleGenre(req.key, JsonObject(byGenre[req.key] as Map<String, Any>?))
            else
                Response.Error("Not in db")
        }
        else -> {throw Exception("unreachable code")}
    }
}


fun routing(req: HttpServerRequest){
    val pathArray = req.path().split("/")
    val resp = req.response()
    when(pathArray[1]){
        "file" -> {
            val file = WORKDIR.absolutePath + "/" + pathArray.slice(2..pathArray.size - 1).joinToString("/")
            try {
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
            finally{
                Log.info("New request for /file: " + file
                + " --> status code = " + resp.statusCode)
            }
        }
        "" -> req.bodyHandler({ buf ->
            val respStruct: Response = try{
                handle(buf)
            } catch(e: Exception){
                resp.statusCode = 500
                Response.Error("Internal Error")
            }
            val buffer = generateReply(respStruct)
            resp.putHeader("content-length", buffer.length().toString())
            resp.putHeader("content-type", "application/json")
            resp.write(buffer)
            resp.end()
            val result = when(respStruct){
                is Response.Error -> respStruct.msg
                else -> respStruct.javaClass.name
            }
            Log.info("New request for /: \"" + buf
                    + "\" --> status code = " + resp.statusCode
                    + " --> Content: " + result)
        })
        else -> {
            resp.statusCode = 404
            resp.end()
        }
    }
}

fun main(args: Array<String>){

    Log.info("Started")
    WORKDIR.mkdirs();
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()

    loadUsers()
    Log.info("Users loaded")
    loadDatabase(DATABASE)
    Log.info("Song Database loaded")

    val host = "0.0.0.0"
    server.requestHandler({ request ->
        routing(request)
    })

    server.listen(8080, host, { res-> if (res.succeeded()) {
        Log.info("Listening...")
    }else{
        Log.info(("Failed to bind!"))
    } })
}
