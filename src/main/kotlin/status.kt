package com.streaming.status

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import java.io.File
import java.io.IOException
import java.io.InputStream
import com.streaming.status.*

/*
val qualityEnum = when(quality){
    "high" -> QUALITY.HIGH
    "medium" -> QUALITY.MEDIUM
    "low" -> QUALITY.LOW
}
*/
enum class STATUS { IDLE, SEND, ERROR, WAITING, DONE, CLOSED } // SEARCH
enum class QUALITY { HIGH, MEDIUM, LOW }

open class FFMPEGStream private constructor() {
    data class Valid(val file: String, val proc: Process, val ogg: InputStream, var size: Int = 0): FFMPEGStream()
    class Invalid(): FFMPEGStream()
}

fun createFFMPEGStream(file: String, command: String): FFMPEGStream{
    val proc = command.runCommand(File("/home/user")) // TODO/FIXME: hardcoded path
    return when(proc){
        null ->  FFMPEGStream.Invalid()
        else -> FFMPEGStream.Valid(file, proc, proc.inputStream, 0)
    }
}

open class Payload private constructor() { // private constructor to prevent creating more subclasses outside
    data class Song(val uri: String, val startTime: Double, val quality: QUALITY) : Payload()
    class Error(msg: String) : Payload()
    class WaitingAuth() : Payload()
}

abstract class Status private constructor() {
    abstract val authenticated: Boolean
    abstract val status: STATUS
    abstract val payload: Payload
    abstract val stream: FFMPEGStream

    class Idle(_song: Payload.Song, override val stream: FFMPEGStream) : Status() {
        override val authenticated = true
        override val status = STATUS.IDLE
        override val payload = _song
    }

    class Send(_song: Payload.Song, override val stream: FFMPEGStream): Status(){
        override val authenticated = true
        override val status = STATUS.SEND
        override val payload = _song
    }

    class Error(msg: String) : Status() {
        override val authenticated = false // TODO: understand if should always change to false or not
        override val status = STATUS.ERROR
        override val stream = FFMPEGStream.Invalid()
        override val payload = Payload.Error(msg)
    }

    class WaitingAuth: Status {
        override val authenticated = false
        override val status = STATUS.WAITING
        override val payload = Payload.WaitingAuth()
        override val stream = FFMPEGStream.Invalid()

        constructor(oldStream: FFMPEGStream) {
            if (oldStream is FFMPEGStream.Valid)
                oldStream.kill()
        }
    }

    class Done(override val stream: FFMPEGStream.Invalid): Status() {
        override val authenticated = false
        override val status = STATUS.DONE
        override val payload = Payload.WaitingAuth()
    }

    class Closed(override val stream: FFMPEGStream.Invalid): Status() {
        override val authenticated = true
        override val status = STATUS.CLOSED
        override val payload = Payload.Error("Connection was closed")
    }
}

object StatusFactory {
    fun waitingAuth(ffmpegStream: FFMPEGStream): Status.WaitingAuth {
        return Status.WaitingAuth(ffmpegStream)
    }
    fun waitingAuth(): Status.WaitingAuth {
        return Status.WaitingAuth(FFMPEGStream.Invalid())
    }
    fun done(ffmpegStream: FFMPEGStream): Status.Done {
        when(ffmpegStream){
            is FFMPEGStream.Valid -> ffmpegStream.kill()
            else -> {}
        }
        return Status.Done(FFMPEGStream.Invalid())
    }
    fun error(msg: String, stream: FFMPEGStream): Status.Error {
        when(stream){
            is FFMPEGStream.Valid -> { assert(stream.proc.isAlive == false); stream.kill() }
            else -> {}
        }
        return Status.Error(msg)
    }
    fun idle(song: Payload.Song, stream: FFMPEGStream.Valid): Status.Idle {
        return Status.Idle(song, stream)
    }
    fun send(song: Payload.Song, stream: FFMPEGStream.Valid): Status.Send {
        return Status.Send(song, stream)
    }
    fun closed(stream: FFMPEGStream.Valid): Status.Closed {
        stream.kill()
        return Status.Closed(FFMPEGStream.Invalid())
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
