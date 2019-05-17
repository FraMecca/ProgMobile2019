package com.streaming.main

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket

import com.streaming.status.*
import com.streaming.jsonResponse.*
import java.util.concurrent.atomic.AtomicLong

fun sendMusic(ws: ServerWebSocket, stream: FFMPEGStream.Valid): Long {
    var buf: ByteArray = ByteArray(1024) // 1 KiB
    val rc = stream.ogg.read(buf)
    val resp = Buffer.buffer()
    resp.setBytes(0, buf)
    ws.write(resp)
    return 1024
}
fun logic(vertx: Vertx, ws: ServerWebSocket){

    var status: Status = mutateStatus.noAuth()
    var auth = false
    var nHandler: AtomicLong = AtomicLong(0)
    var owner: AtomicLong = AtomicLong(0) // Handler that keeps ownership of the socket

    ws.closeHandler({ ch ->
        println("Closing ws-connection to client " + ws.textHandlerID())
    })

    ws.handler(object:Handler<Buffer> {
        override fun handle(data:Buffer) {
            val id = nHandler.incrementAndGet()
            var action = parse(data.toString())


            loop@while(true){
                status = when(status) {
                    is Status.SongPlaying -> {
                        val old: Status.SongPlaying = status as Status.SongPlaying
                        when (action) {
                            is Response.Processed -> { // the old response
                                if (owner.get() == id) {
                                    // this is the owner of the stream, continue playing
                                    val rc = sendMusic(ws, old.stream)
                                    if (rc >= 2048 * 1024) { // 2 MiB
                                        status = mutateStatus.paused(old)
                                        break@loop
                                    } else
                                        mutateStatus.continuePlaying(old, rc)
                                } else {
                                    status = mutateStatus.waiting(old)
                                    break@loop
                                }
                            }
                            is Response.NewSong -> {
                                // a new song was requested
                                // change owner to this and start sending in next iteration
                                owner.set(id)
                                mutateStatus.newSong(old, action.uri, action.startTime, action.quality())
                            }
                            is Response.Continue -> mutateStatus.continuePlaying(old, 0)
                            is Response.Pause -> mutateStatus.paused(old)
                            is Response.Close -> mutateStatus.closed(old)
                            else -> mutateStatus.error(old, "Invalid operation")
                        }
                    }
                    is Status.SongPaused -> {
                        val old: Status.SongPaused = status as Status.SongPaused
                        when (action) {
                            is Response.NewSong -> mutateStatus.newSong(old, action.uri, action.startTime, action.quality())
                            is Response.Close -> mutateStatus.closed(old)
                            else -> mutateStatus.error(old, "Invalid operation")
                        }
                    }
                    is Status.Waiting -> {
                        val old: Status.Waiting = status as Status.Waiting
                        when (action) {
                            is Response.NewSong -> mutateStatus.playing(old, action.uri, action.startTime, action.quality())
                            is Response.Close -> mutateStatus.closed(old)
                            else -> mutateStatus.error(old, "Invalid operation")
                        }
                    }
                    is Status.Error -> break@loop
                    is Status.Closed -> break@loop
                    else ->
                }

                if(owner.get() != id)
                    break@loop
                action = Response.Processed()
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
