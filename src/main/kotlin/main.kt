package com.streaming.main

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket

import com.streaming.status.*
import com.streaming.jsonResponse.*
import java.util.concurrent.atomic.AtomicLong

fun sendMusic(ws: ServerWebSocket, stream: FFMPEGStream.Valid): Int {
    var buf: ByteArray = ByteArray(1024) // 1 KiB
    val rc = stream.ogg.read(buf)
    val resp = Buffer.buffer()
    resp.setBytes(0, buf)
    ws.write(resp)
    return rc
}

fun authenticateUser(data: Response.Auth): Status{
    val user = data.user
    val pass = data.pass
    if(user == "mario" && pass == "rossi") // TODO FIXME
        return Status.Waiting()
    else
        return Status.Error("invalid credentials")
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
            status = when(action){
                is Response.Auth -> {
                    when(status){
                        is Status.NoAuth -> authenticateUser(action)
                        else -> mutateStatus.invalidAction(status)
                    }
                }
                is Response.Close -> {
                    goodbye(ws)
                    mutateStatus.closed(status)
                }
                is Response.Continue -> {
                    val old = status
                    when(old){
                        is Status.SongPlaying -> mutateStatus.continuePlaying(old, 0)
                        is Status.SongPaused -> {
                            owner.set(id)
                            mutateStatus.continuePlaying(old)
                        }
                        else -> mutateStatus.invalidAction(old)
                    }
                }
                is Response.Error -> {
                    val old = status
                    when(old){
                        is Status.SongPlaying -> mutateStatus.error(old, action.msg)
                        is Status.SongPaused -> mutateStatus.error(old, action.msg)
                        is Status.Waiting -> mutateStatus.error(old, action.msg)
                        else -> mutateStatus.invalidAction(old)
                    }
                }
                is Response.NewSong -> {
                    val old = status
                    when (old){
                        is Status.SongPlaying -> mutateStatus.newSong(old, action.uri, action.startTime, action.quality())
                        is Status.SongPaused -> mutateStatus.newSong(old, action.uri, action.startTime, action.quality())
                        is Status.Waiting -> mutateStatus.newSong(old, action.uri, action.startTime, action.quality())
                        else -> mutateStatus.invalidAction(old)
                    }
                }
                is Response.Pause -> {
                    val old = status
                    when(old){
                        is Status.SongPlaying -> mutateStatus.paused(old)
                        is Status.SongPaused -> mutateStatus.paused(old)  // ignore
                        else -> mutateStatus.invalidAction(old)
                    }
                }
                else -> {  println(action); assert(false); mutateStatus.invalidAction(status) } // why do you want an else branch!?!?
            }
            println("Exiting first when with: " + status)

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
