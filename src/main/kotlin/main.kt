package com.apollon.server.main

import com.apollon.server.database.byAlbum
import com.apollon.server.database.database
import com.apollon.server.database.byArtist
import com.apollon.server.database.byGenre
import com.apollon.server.database.checkExistingFiles
import com.apollon.server.database.loadDatabase
import com.apollon.server.database.search
import com.apollon.server.request.Request
import com.apollon.server.request.parse
import com.apollon.server.response.Response
import com.apollon.server.response.generateReply
import com.apollon.server.streaming.DATABASE
import com.apollon.server.streaming.LIBRARY
import com.apollon.server.streaming.WORKDIR
import com.apollon.server.streaming.audioFiles
import com.apollon.server.streaming.checkFileAccess
import com.apollon.server.streaming.computeOffset
import com.apollon.server.streaming.computeSha
import com.apollon.server.streaming.conversionDone
import com.apollon.server.streaming.decrementReference
import com.apollon.server.streaming.generateNewFile
import com.apollon.server.streaming.getFullPath
import com.apollon.server.streaming.getMetadataFromUri
import com.apollon.server.streaming.incrementReference
import com.apollon.server.streaming.removeReference
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.util.logging.Level
import java.util.logging.Logger

val Log = Logger.getLogger("InfoLogging")
val ErrLog = Logger.getLogger("ErrorLogging")
val users = LinkedHashMap<String, String>()
val USERSFILE = "users.json"

fun loadUsers() {
    val file = File(USERSFILE)
    val reader = BufferedReader(FileReader(file) as Reader?)
    val people = JsonArray(reader.readText())
    people.forEach {
        val j = it as JsonObject
        val user = j.getString("user")
        val pass = j.getString("password")
        if (user == null || pass == null)
            throw Exception("Invalid user json in users.json")
        else
            users[user] = pass
    }
}

fun authenticateUser(user: String, password: String): Boolean {
    if (user !in users)
        return false
    else if (users[user] != password)
        return false
    else
        return true
}

// a Response is generated in reply to a Request
fun handle(buf: Buffer): Response {
    val req: Request = parse(buf)

    return when (req) {
        is Request.Error -> Response.Error(req.msg)
        // search for file if is in library and pass it to ffmpeg if it has not been converted already
        is Request.NewSong -> {
            // check file access: do not evade LIBRARY
            val uri = LIBRARY.path + "/" + req.uri
            val sha = computeSha(uri, req.quality)
            val newFile = getFullPath(sha)

            if (!checkFileAccess(uri, LIBRARY))
                Response.Error("Invalid file")
            // check if file exists and can be reused
            else if (sha in audioFiles) {
                incrementReference(sha)
                val metadata = getMetadataFromUri(uri)
                val abstractUri = newFile.replace(WORKDIR.absolutePath, "/file")
                Response.Song(abstractUri, metadata, req.quality, newFile, false)
            } else {
                generateNewFile(uri, req.quality, newFile, sha)
            }
        }
        // The client doesn't need the song anymore. Update references to file and delete if necessary
        is Request.SongDone -> {
            val sha = computeSha(LIBRARY.path + "/" + req.uri, req.quality)
            if (sha !in audioFiles)
                Response.Error("Song not playing")
            else if (File(getFullPath(sha)).exists() == false)
                Response.Error("File does not exist anymore")
            else {
                val nUses = audioFiles[sha]!!.played
                when (nUses) {
                    0 -> throw Exception("assertion: nUses can't be zero")
                    1 -> removeReference(sha)
                    else -> decrementReference(sha)
                }
                Response.Ok()
            }
        }
        is Request.SongConversionStatus -> {
            val loc = req.uri.replace("/file", WORKDIR.absolutePath)
            println(loc)
            val file = File(loc)
            if (!file.exists())
                Response.Error("file does not exist")
            else {
                when (conversionDone(file.absolutePath)) {
                    true -> Response.SongConversionDone(req.uri)
                    false -> Response.SongConversionOngoing(req.uri)
                }
            }
        }
        is Request.Search -> {
            val results = search(req.keys)
            val array = JsonArray(results.map { it.json })
            Response.Search(array)
        }
        is Request.AllByArtist -> {
            val all = JsonArray(ArrayList(byArtist.values))
            Response.AllByArtist(all)
        }
        is Request.AllByAlbum -> {
            val all = JsonArray(ArrayList(byAlbum.values.map { mapOf("title" to it["title"], "artist" to it["artist"], "img" to it["img"], "uri" to it["uri"], "#nsongs" to it["#nsongs"]) }))
            Response.AllByAlbum(all)
        }
        is Request.AllByGenre -> {
            val all = JsonArray(byGenre.map { listOf(it.key, it.value.size) }.toList())
            Response.AllByGenre(all)
        }
        is Request.AllSongs -> {
            val songsToImg = byAlbum.flatMap { entry -> 
                val songs = entry.value["songs"] as ArrayList<MutableMap<String, String>>;
                songs.map { it -> Pair(it["uri"], entry.value["img"]) }
            }.toMap()
            val all = JsonArray(
                database
                    .map { it.json }
                    .map { it.put("img", songsToImg[it.getString("uri", "")]) }
            )
            Response.AllSongs(all)
        }
        is Request.SingleAlbum -> {
            if (req.title in byAlbum)
                Response.SingleAlbum(JsonObject(byAlbum[req.title])) // TODO: doesn't consider different artists, same title
            else
                Response.Error("Not in db")
        }
        is Request.SingleArtist -> {
            if (req.name in byArtist) {
                val artist = byArtist[req.name]
                val albums = (artist!!["albums"]!! as MutableList<HashMap<String, String>>).map {
                    hashMapOf("uri" to it["uri"],
                        "#nsongs" to byAlbum[it["uri"]]!!["#nsongs"],
                        "title" to it["title"],
                        "img" to it["img"]
                    )
                }
                val res = artist.entries.associate {
                    it.key to
                            if (it.key != "albums")
                                it.value
                            else
                                albums
                }

                Response.SingleArtist(JsonObject(res))
            } else
                Response.Error("Not in db")
        }
        is Request.SingleGenre -> {
            if (req.key in byGenre) {
                // Response.SingleGenre(req.key, JsonObject(byGenre[req.key] as Map<String, Any>?))
                val artists = byGenre[req.key]!!.keys.toSet()
                Response.SingleGenre(req.key, JsonArray(byArtist
                                                            .filter { it.key in artists }
                                                            .map { it.value }))
            } else
                Response.Error("Not in db")
        }
        is Request.Lyrics -> {
            Response.Lyrics(req.artist, req.song)
        }
        is Request.ChallengeLogin -> {
            Response.Ok()
        }
        is Request.NewPlaylist -> {
            val res = Playlists.newPlaylist(req.user, req.title, req.uris)
            return when (res) {
                is Playlists.Result.Ok -> Response.Ok()
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Value -> { assert(false); Response.Error("can't go here") }
            }
        }
        is Request.RemovePlaylist -> {
            val res = Playlists.deletePlaylist(req.user, req.title)
            return when (res) {
                is Playlists.Result.Ok -> Response.Ok()
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Value -> { assert(false); Response.Error("can't go here") }
            }
        }
        is Request.RenamePlaylist -> {
            val res = Playlists.renamePlaylist(req.user, req.src, req.dst)
            return when (res) {
                is Playlists.Result.Ok -> Response.Ok()
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Value -> { assert(false); Response.Error("can't go here") }
            }
        }
        is Request.RemoveFromPlaylist -> {
            val res = Playlists.removeElementsFromPlaylist(req.user, req.title, req.uris)
            return when (res) {
                is Playlists.Result.Ok -> Response.Ok()
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Value -> { assert(false); Response.Error("can't go here") }
            }
        }
        is Request.AddToPlaylist -> {
            val res = Playlists.addElementsToPlaylist(req.user, req.title, req.uris)
            return when (res) {
                is Playlists.Result.Ok -> Response.Ok()
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Value -> { assert(false); Response.Error("can't go here") }
            }
        }
        is Request.ListPlaylists -> {
            val res = Playlists.listPlaylists(req.user)
            return when (res) {
                is Playlists.Result.Value -> Response.ListPlaylist(res.j)
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Ok -> { assert(false); Response.Error("can't go here") }
            }
        }
        is Request.GetPlaylist -> {
            val res = Playlists.getPlaylists(req.user, req.title)
            return when (res) {
                is Playlists.Result.Value -> Response.GetPlaylist(res.j[0])
                is Playlists.Result.Error -> Response.Error(res.msg)
                is Playlists.Result.Ok -> { assert(false); Response.Error("can't go here") }
            }
        }
    }
}

fun routing(vertx: Vertx, req: HttpServerRequest) {
    val pathArray = req.path().split("/")
    val resp = req.response()
    when (pathArray[1]) {
        "file" -> {
            val (file, offset) = {
                val requestedFile =
                    WORKDIR.absolutePath + "/" + pathArray.slice(2..pathArray.size - 1).joinToString("/")
                if (requestedFile.contains('+')) {
                    val idx = requestedFile.indexOf('+')
                    val len = requestedFile.length
                    Pair(requestedFile.substring(0, idx), requestedFile.substring(idx + 1, len).toLong())
                } else {
                    Pair(requestedFile, 0.toLong())
                }
            }()
            println("ASDASDDAS: " + offset.toString())
            try {
                if (checkFileAccess(file, WORKDIR) && File(file).exists()) {
                    val realOffset = computeOffset(file, offset)
                    resp.sendFile(file, realOffset)
                } else {
                    resp.statusCode = 404
                    resp.end()
                }
            } catch (e: Exception) {
                resp.statusCode = 500
                resp.end()
            } finally {
                Log.info("New request for /file: " + file +
                " --> status code = " + resp.statusCode)
            }
        }
        "" -> req.bodyHandler({ buf ->
            val responseObj: Response = try {
                handle(buf)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                Log.info(e.toString())
                resp.statusCode = 500
                Response.Error(e.message!!)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.info(e.toString())
                resp.statusCode = 500
                Response.Error("Internal Error")
            }
            generateReply(vertx, resp, responseObj)
        })
        else -> {
            resp.statusCode = 404
            resp.end()
        }
    }
}

fun main(args: Array<String>) {

    Log.info("Started")
    WORKDIR.mkdirs()

    loadUsers()
    Log.info("Users loaded")
    loadDatabase(DATABASE)
    Log.info("Song Database loaded")
    val rn = checkExistingFiles()
    Log.info("Loaded " + rn.toString() + " existing files")

    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val host = "0.0.0.0"
    server.requestHandler({ request ->
        routing(vertx, request)
    })

    server.listen(44448, host, { res -> if (res.succeeded()) {
        Log.info("Listening on 44448...")
    } else {
        Log.info(("Failed to bind!"))
    } })
}

fun errLog(msg: String) {
    ErrLog.log(Level.SEVERE, msg)
}
