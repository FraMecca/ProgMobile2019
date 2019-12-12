import com.apollon.server.database.databaseByUri
import com.apollon.server.main.users
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.File

object Playlists {
    val many = mutableListOf<Pair<Pair<String, String>, List<String>>> ()
    // [ (user, title), uris ]
    val file = File("playlists.json")

    fun ok(): Result.Ok {
        val j: JsonArray = JsonArray(many)
        val buf = j.toBuffer()
        file.writeText(buf.toString())
        return Result.Ok()
    }

    init {
        if (!file.exists()) {
            file.createNewFile()
        } else {
            val j = Buffer.buffer(file.readText()).toJsonArray()
            j.forEach { _it ->
                val it = _it as JsonObject
                val songs: List<String> = it.getJsonArray("song").toList().map { it as String }
                val user = it.getString("user")
                val title = it.getString("title")
                many.add(Pair(Pair(user, title), songs))
            }
        }
    }

    fun newPlaylist(user: String, title: String, uris: List<String>): Playlists.Result {
        val inDb = uris.filter { databaseByUri.containsKey(it) }
        if (inDb.size != uris.size)
            return Result.Error("'" + inDb.toString() + "': uris not in database")
        else if (many.filter { it.first.second == title && it.first.first == user }.size != 0)
            return Result.Error("There is a playlist with the same title and user already")
        else if (!users.containsKey(user))
            return Result.Error("Invalid user")
        else {
            many.add(Pair(Pair(user, title), uris))
            return ok()
        }
    }

    fun deletePlaylist(user: String, title: String): Playlists.Result {
        val el = many.filter { it.first.second == title && it.first.first == user }
        assert(el.size <= 1)

        if (!users.containsKey(user))
            return Result.Error("Invalid user")
        else if (el.size == 0)
            return Result.Error("There isn't a playlist with the same title and user")
        else {
            many.remove(el[0])
            return ok()
        }
    }

    fun removeElementsFromPlaylist(user: String, title: String, uris: List<String>): Playlists.Result {
        val ell = many.filter { it.first.second == title && it.first.first == user }
        val uriSet = uris.toSet()

        val checks = playlistChecks(user, title, uris)
        return when (checks) {
            null -> {
                val el = ell[0]
                val newEl = Pair(el.first, el.second.filter { !uriSet.contains(it) })
                if (newEl.second.size == el.second.size)
                    return Result.Error("No such uri in playlist")
                else {
                    many.remove(el)
                    many.add(newEl)
                    return ok()
                }
            }
            else -> Result.Error(checks)
        }
    }

    fun addElementsToPlaylist(user: String, title: String, uris: List<String>): Result {
        val ell = many.filter { it.first.second == title && it.first.first == user }
        val uriSet = uris.toSet()

        val checks = playlistChecks(user, title, uris)
        return when (checks) {
            null -> {
                val el = ell[0]
                val alreadyHere = el.second.filter { uriSet.contains(it) }
                val newUris: MutableList<String> = el.second.toMutableList()

                newUris.addAll(uris)
                val newEl = Pair(el.first, newUris)

                if (alreadyHere.size != 0)
                    return Result.Error("'" + alreadyHere.toString() + "' already in the playlist")
                else {
                    many.remove(el)
                    many.add(newEl)
                    return ok()
                }
            }
            else -> Result.Error(checks)
        }
    }

    private fun playlistChecks(user: String, title: String, uris: List<String>): String? {
        val uriSet = uris.toSet()
        val ell = many.filter { it.first.second == title && it.first.first == user }
        assert(ell.size <= 1)

        if (!users.containsKey(user))
            return "Invalid user"
        else if (ell.size == 0)
            return "There isn't a playlist with the same title and user"
        else {
            val el = ell[0]
            val newEl = Pair(el.first, el.second.filter { !uriSet.contains(it) })
            if (newEl.second.size == el.second.size)
                return "No such uri in playlist"
            else
                return null
        }
    }

    sealed class Result() {
        class Ok : Result()
        class Error(val msg: String) : Result()
    }
}
