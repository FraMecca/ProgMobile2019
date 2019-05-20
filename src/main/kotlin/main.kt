package com.streaming.main

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket

import com.streaming.status.*
import com.streaming.request.*
import com.streaming.response.*
import java.util.concurrent.atomic.AtomicLong

fun sendMusic(ws: ServerWebSocket, stream: FFMPEGStream.Valid): Int {
    var buf: ByteArray = ByteArray(1024) // 1 KiB
    val rc = stream.ogg.read(buf)
    val resp = Buffer.buffer()
    resp.setBytes(0, buf)
    ws.write(resp)
    return rc
}

fun authenticateUser(data: Request.Auth): Pair<Status, Response>{
    val user = data.user
    val pass = data.pass
    if(user == "mario" && pass == "rossi") // TODO FIXME
        return Pair(Status.Waiting(), Response.SuccessfulAuth())
    else {
        val error = "invalid credentials"
        return Pair(Status.Error(error), Response.Error(error))
    }
}

fun goodbye(ws: ServerWebSocket){
    val resp = Buffer.buffer()
    resp.appendString("goodbye")
    ws.write(resp)
    ws.close()
}

fun logic(vertx: Vertx, ws: ServerWebSocket){

    var status: Status = mutateStatus.noAuth()
    var nHandler: AtomicLong = AtomicLong(0)
    var owner: AtomicLong = AtomicLong(0) // track handler that keeps ownership of the socket

    if(ws.path() != "/"){
        status = mutateStatus.error("Invalid path")
        val resp = Buffer.buffer()
        resp.appendString(status.msg)
        ws.write(resp)
        ws.close()
    } else {
        println("Websocket-handshake...")
        println("path = " + ws.path())
        println("uri = " + ws.uri())
        println("localAdress = " + ws.localAddress().toString())
        println("remoteAddress = " + ws.remoteAddress())
    }

    ws.closeHandler({ ch ->
        println("Closing ws-connection to client " + ws.textHandlerID())
    })

    ws.handler(object:Handler<Buffer> {
        override fun handle(data:Buffer) {

            val id = nHandler.incrementAndGet()
            if(id.equals(1))
                owner.set(id)
            val action = parse(data.toString())

            println("Entering handler with: " + status +" got: "+ data+" parsed as: " + action)
            val res: Pair<Status, Response>  = when(action){
                is Request.Auth -> {
                    when(status){
                        is Status.NoAuth -> authenticateUser(action)
                        else -> Pair(mutateStatus.invalidAction(status), Response.InvalidAction())
                    }
                }
                is Request.Close -> Pair(mutateStatus.closed(status), Response.Close())
                is Request.Continue -> {
                    val old = status
                    when(old){
                        is Status.SongPlaying -> Pair(mutateStatus.continuePlaying(old, 0), Response.Ok())
                        is Status.SongPaused -> {
                            owner.set(id)
                            Pair(mutateStatus.continuePlaying(old), Response.Ok())
                        }
                        else -> Pair(mutateStatus.invalidAction(old), Response.InvalidAction())
                    }
                }
                is Request.Error -> {
                    val old = status
                    when(old){
                        is Status.SongPlaying -> Pair(mutateStatus.error(old, action.msg), Response.Error(action.msg))
                        is Status.SongPaused -> Pair(mutateStatus.error(old, action.msg), Response.Error(action.msg))
                        is Status.Waiting -> Pair(mutateStatus.error(old, action.msg), Response.Error(action.msg))
                        else -> Pair(mutateStatus.invalidAction(old), Response.InvalidAction())
                    }
                }
                is Request.NewSong -> {
                    val old = status
                    when (old){
                        is Status.SongPlaying -> Pair(
                            mutateStatus.newSong(old, action.uri, action.startTime, action.quality()),
                            Response.Song(SongMetadata(action.uri), action.quality)
                        )
                        is Status.SongPaused -> Pair(
                            mutateStatus.newSong(old, action.uri, action.startTime, action.quality()),
                            Response.Ok()
                        )
                        is Status.Waiting -> Pair(
                            mutateStatus.newSong(old, action.uri, action.startTime, action.quality()),
                            Response.Ok()
                        )
                        else -> Pair(mutateStatus.invalidAction(old), Response.InvalidAction())
                    }
                }
                is Request.Pause -> {
                    val old = status
                    when(old){
                        is Status.SongPlaying -> Pair(mutateStatus.paused(old), Response.Ok())
                        is Status.SongPaused -> Pair(mutateStatus.paused(old), Response.Ok())
                        else -> Pair(mutateStatus.invalidAction(old), Response.InvalidAction())
                    }
                }
                else -> {  println(action); assert(false);
                    Pair(mutateStatus.invalidAction(status), Response.InvalidAction()) } // why do you want an else branch!?!?
            }
            println("Exiting first when with: " + res.first)
            status = res.first
            val response = res.second

            // reply
            reply(ws, response)

            loop@while(owner.get() == id){
                println("looping: " + status)
                val old = status
                status = when(old){
                    is Status.SongPlaying -> {
                        val rc = sendMusic(ws, old.stream)
                        if (rc >= 2048 * 1024) // 2 MiB
                            mutateStatus.paused(old)
                        else
                            mutateStatus.continuePlaying(old, rc)
                    }
                    is Status.SongPaused -> { /** wait **/ mutateStatus.paused(old) }
                    is Status.Waiting -> { /** wait **/ mutateStatus.waiting(old) }
                    is Status.Error -> {
                        val resp = Buffer.buffer()
                        resp.appendString(old.msg)
                        ws.write(resp)
                        mutateStatus.closed(old)
                    }
                    else -> mutateStatus.invalidAction(old)
                }
                if(!(status is Status.SongPlaying || status is Status.SongPaused)) break@loop
            }
            println("Exiting second when with: " + status)
        }
    })
}

fun main(args: Array<String>){
    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()

    server.websocketHandler({ ws ->
        println(ws.toString())

        logic(vertx, ws)
    })

    server.listen(8080, "0.0.0.0", { res-> if (res.succeeded()) {
        println("Listening...")
    }else{
        println(("Failed to bind!"))
    } })
}
