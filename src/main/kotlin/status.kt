package com.streaming.status

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import java.io.File
import java.io.IOException
import java.io.InputStream
import com.streaming.status.*
import io.vertx.kotlin.ext.healthchecks.statusOf

enum class QUALITY { HIGH, MEDIUM, LOW }

open class FFMPEGStream private constructor() {
    data class Valid(val proc: Process, val ogg: InputStream, val consumed: Int): FFMPEGStream()
    class Invalid(): FFMPEGStream()
}

fun createFFMPEGStream(file: String, command: String): FFMPEGStream{
    val proc = command.runCommand(File("/home/user")) // TODO/FIXME: hardcoded path
    return when(proc){
        null ->  FFMPEGStream.Invalid()
        else -> FFMPEGStream.Valid(proc, proc.inputStream, 0)
    }
}

val command = "ffmpeg -i " + "file" + " -f ogg -q 5 pipe:1"
open class Status private constructor() { // private constructor to prevent creating more subclasses outside
    data class SongPlaying(val uri: String, val startTime: Double, val quality: QUALITY, val stream: FFMPEGStream.Valid, val consumed: Int) : Status()
    data class SongPaused(val uri: String, val time: Double, val quality: QUALITY, val stream: FFMPEGStream.Valid) : Status()
    class Waiting() : Status()
    class Closed(): Status()
    class Error(val msg: String) : Status()
    class NoAuth() : Status()
}

object mutateStatus {
    fun error(old: Status.SongPaused, msg: String): Status.Error {
        old.stream.kill()
        return Status.Error(msg)
    }
    fun error(old: Status.SongPlaying, msg: String): Status.Error {
        old.stream.kill()
        return Status.Error(msg)
    }
    fun error(old: Status.Waiting, msg: String): Status.Error {
        return Status.Error(msg)
    }
    fun error(msg: String): Status.Error {
        return Status.Error(msg)
    }
    fun closed(old: Status): Status.Closed {
        when(old){
            is Status.SongPlaying -> old.stream.kill()
            is Status.SongPaused -> old.stream.kill()
            else -> {}
        }
        return Status.Closed()
    }
    fun waiting(old: Status.Waiting): Status.Waiting {
        return old
    }
    fun waiting(old: Status.SongPlaying): Status.Waiting {
        old.stream.kill()
        return Status.Waiting()
    }
    private fun newSong(uri: String, start: Double, quality: QUALITY): Status{
        val stream = createFFMPEGStream(uri, command)
        return when (stream) {
            is FFMPEGStream.Valid -> Status.SongPlaying(uri, start, quality, stream, 0)
            else -> Status.Error("ffmpeg error")
        }
    }
    fun newSong(old: Status.SongPlaying, uri: String, start: Double, quality: QUALITY): Status{
        old.stream.kill()
        return newSong(uri, start, quality)
    }
    fun newSong(old: Status.SongPaused, uri: String, start: Double, quality: QUALITY): Status{
        old.stream.kill()
        return newSong(uri, start, quality)
    }
    fun newSong(old: Status.Waiting, uri: String, start: Double, quality: QUALITY): Status{
        return newSong(uri, start, quality)
    }
    fun continuePlaying(old: Status.SongPlaying, consumed: Int): Status.SongPlaying {
        return Status.SongPlaying(old.uri, old.startTime, old.quality, old.stream, consumed)
    }
    fun continuePlaying(old: Status.SongPaused): Status.SongPlaying{
        return Status.SongPlaying(old.uri, old.time, old.quality, old.stream, old.stream.consumed)
    }
    fun paused(old: Status.SongPlaying): Status.SongPaused {
        return Status.SongPaused(old.uri, old.startTime, old.quality, old.stream)
    }
    fun paused(old: Status.SongPaused): Status.SongPaused {
        return Status.SongPaused(old.uri, old.time, old.quality, old.stream)
    }
    fun noAuth(): Status.NoAuth {
        return Status.NoAuth()
    }
    fun invalidAction(old: Status): Status.Error {
        when(old){
            is Status.SongPaused -> old.stream.kill()
            is Status.SongPlaying -> old.stream.kill()
            else -> {}
        }
        return Status.Error("Invalid action")
    }
}

fun String.runCommand(workingDir: File): Process? {
    try {
        val parts = this.split("\\s".toRegex())
        println(parts)
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        return proc
    } catch(e: IOException) {
        println("ERROR")
        e.printStackTrace()
        return null
    }
}

fun FFMPEGStream.Valid.kill(){
    assert(this.proc.isAlive)
    this.proc.destroyForcibly()
}
