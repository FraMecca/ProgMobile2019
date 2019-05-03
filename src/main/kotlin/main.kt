package com.streaming.main

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import java.io.File
import java.io.IOException
import java.io.InputStream

fun String.runCommand(workingDir: File): InputStream? {
    try {
        val parts = this.split("\\s".toRegex())
        println(parts)
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        return proc.inputStream
    } catch(e: IOException) {
        println("ERROR")
        e.printStackTrace()
        return null
    }
}

fun killProcess(pid: Long){
    assert(false)
}

enum class STATUS { IDLE, SEND, ERROR, WAITING, DONE }
enum class QUALITY { HIGH, MEDIUM, LOW }

open class FFMPEGStream private constructor() {
    data class Valid(val file: String, val ogg: InputStream, var size: Int = 0): FFMPEGStream()
    class Invalid(): FFMPEGStream()
}

fun createFFMPEGStream(file: String, command: String): FFMPEGStream.Valid {
    val oggStream = command.runCommand(File("/home/user")) // TODO/FIXME: hardcoded path
    return when(oggStream){
        null ->  FFMPEGStream.Invalid()
        else -> FFMPEGStream.Valid(file, oggStream)
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
    abstract val pid: Long?

    class Idle(_song: Payload.Song, override val pid: Long, override val stream: FFMPEGStream) : Status() {
        override val authenticated = true
        override val status = STATUS.IDLE
        override val payload = _song
    }

    class Send(_song: Payload.Song, override val pid: Long, override val stream: FFMPEGStream): Status(){
        override val authenticated = true
        override val status = STATUS.SEND
        override val payload = _song
    }

    class Error(val msg: String, override val pid: Long?): Status() {
        override val authenticated = false // TODO: understand if should always change to false or not
        override val status = STATUS.ERROR
        override val payload = Payload.Error(msg)
        override val stream = FFMPEGStream.Invalid()
    }

    class WaitingAuth(override val pid: Long?): Status() {
        override val authenticated = false
        override val status = STATUS.WAITING
        override val payload = Payload.WaitingAuth()
        override val stream = FFMPEGStream.Invalid()
    }

    class Done(_pid: Long?, override val stream: FFMPEGStream): Status() {
        override val authenticated = false
        override val status = STATUS.DONE
        override val payload = Payload.WaitingAuth()
        override val pid = _pid // when to kill process? TODO
    }
}

object StatusFactory {
    fun waitingAuth(pid: Long): Status.WaitingAuth {
        return Status.WaitingAuth(pid)
    }
    fun waitingAuthNoPid(): Status.WaitingAuth {
        return Status.WaitingAuth(null)
    }
    fun done(pid: Long): Status.Done {
        return Status.Done(pid)
    }
    fun doneNoPid(): Status.Done {
        return Status.Done(null)
    }
    fun error(msg: String, pid: Long?, stream: FFMPEGStream): Status.Error {
        when(stream){
            is FFMPEGStream.Valid -> { assert(pid != null); killProcess(pid as Long)}
            else -> {}
        }
        return Status.Error(msg, pid)
    }
    fun idle(song: Payload.Song, _pid: Long, stream: FFMPEGStream): Status.Idle {
        return Status.Idle(song, _pid, stream)
    }
    fun send(song: Payload.Song, _pid: Long, stream: FFMPEGStream.Valid): Status.Send {
        return Status.Send(song, _pid, stream)
    }
}
fun handleAuth(data: Buffer) = StatusFactory.error("unimplemented", null)

fun handleData(ws: ServerWebSocket, status: Status, data: Buffer): Status {
    val resp = Buffer.buffer()
    return when (status) {
        is Status.Error-> {
            resp.appendString("Error")
            ws.write(resp)
            ws.close()
            if(status.pid != null)
                killProcess(status.pid)
            StatusFactory.doneNoPid()
        }
        is Status.WaitingAuth-> handleAuth(data)
        is Status.Idle-> assert(false)
        is Status.Send-> {
            val file = "Implement"; val command = "Film"
            val oggStream: FFMPEGStream.Valid = when(status.stream){
                is FFMPEGStream.Valid -> status.stream as FFMPEGStream.Valid
                else -> createFFMPEGStream(file, command)
            }
            StatusFactory.send(Payload.Song("", 0, QUALITY.HIGH), 30, oggStream)
        }
        is Status.Done-> {
            // can only request new songs
        }
    }

}

fun logic(vertx: Vertx, ws: ServerWebSocket){

    var status: Status = StatusFactory.waitingAuthNoPid()
    val command = "ffmpeg -i " + "file" + " -f ogg -q 5 pipe:1"
    val file = "implement"

    ws.closeHandler({ ch ->
        println("Closing ws-connection to client " + ws.textHandlerID())
    })

    ws.handler(object:Handler<Buffer> {
        override fun handle(data:Buffer) {
            status = handleData(ws, status, data)

            loop@ while(status is Status.Send){
                val resp = Buffer.buffer()
                val oggStream: FFMPEGStream.Valid = when(status.stream){
                    is FFMPEGStream.Valid -> status.stream as FFMPEGStream.Valid
                    else -> break@loop
                }
                var buf: ByteArray = ByteArray(1024) // 1 KiB
                val rc = oggStream.ogg.read(buf)
                resp.setBytes(0, buf)
                ws.write(resp)
                oggStream.size++
                if(oggStream.size >= 2048)
                    status = StatusFactory.idle(status.payload, status.pid, oggStream)

                }
            }
        }
    })
}

fun main(args: Array<String>){
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()

    server.websocketHandler({ ws->
        println("Websocket-handshake...")
        println("path = " + ws.path())
        println("uri = " + ws.uri())
        println("localAdress = " + ws.localAddress().toString())
        println("remoteAddress = " + ws.remoteAddress())
        println(ws.toString())

        if (!ws.path().equals("/chat")){
            ws.reject()
        }else{
            logic(vertx, ws)
        } })

    server.listen(8080, { res-> if (res.succeeded()) {
        println("Listening...")
    }else{
        println(("Failed to bind!"))
    } })
}


/*
val msg = data.getString(0, data.length())
println("Message from ws-client " + ws.textHandlerID()+ ": " + msg)
val resp = Buffer.buffer()

var size = 0
val file = "/home/user/train.flac"
while(status == "send") {
    var buf: ByteArray = ByteArray(1024) // 1 KiB
    val rc = oggStream.read(buf)
    resp.setBytes(0, buf)
    ws.write(resp)
    size++
    if(size >= 2048)
        status = "idle"
}

}
 */
