package com.streaming.main

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket

import com.streaming.status.*
import com.streaming.jsonResponse.*


fun handleAuth(data: Buffer) = StatusFactory.error("unimplemented", FFMPEGStream.Invalid())

fun handleData(ws: ServerWebSocket, status: Status, data: Buffer): Status {
    println(data.toString() + " " + status)
    val resp = Buffer.buffer()
    val jResp = parse(resp.toString())
    return when (jResp) {
        is Response.Error-> {
            resp.appendString(jResp.msg)
            ws.write(resp)
            ws.close()
            StatusFactory.error(jResp.msg)
        }

        is Response.Auth-> {
            resp.appendString("Successfully auth")
            ws.write(resp)
            StatusFactory.done(Payload.Invalid())
        }

        is Status.Idle-> {
            // either close
            // continue sending
            // request new song
            // or error
            when(jResp){
                is Response.Close -> {
                    ws.close()
                    when(status.stream){
                        is FFMPEGStream.Valid -> StatusFactory.closed(status.stream as FFMPEGStream.Valid)
                        else -> StatusFactory.error("Closed an invalid connection", status.stream)
                    }
                }
                is Response.NewSong -> {
                    StatusFactory.error("not implemented", status.stream) // TODO FIXME
                }
                is Response.Error -> {
                    resp.appendString(jResp.msg)
                    ws.write(resp)
                    StatusFactory.error(jResp.msg, status.stream)
                }
                else -> {
                    resp.appendString("Wrong status")
                    ws.write(resp)
                    ws.close()
                    StatusFactory.error("Wrong status transition: Idle -> ", status.stream) // FIXME
                }
            }
        }

        is Status.Send-> {
            val file = "Implement"; val command = "Film"
            val oggStream: FFMPEGStream = when(status.stream){
                is FFMPEGStream.Valid -> status.stream as FFMPEGStream.Valid
                else -> createFFMPEGStream(file, command)
            }
            when(oggStream){
                is FFMPEGStream.Valid ->
                    StatusFactory.send(Payload.Song("", 0.0, QUALITY.HIGH), oggStream)
                else -> StatusFactory.error("Can't into ffmpeg", status.stream)
            }
        }
        is Status.Done-> {
            // can only request new songs
            // or close
            when(jResp){
                is Response.Close -> {
                    ws.close()
                    StatusFactory.error("Closed an invalid connection", status.stream)
                }
                is Response.NewSong -> {
                    StatusFactory.error("not implemented", status.stream) // TODO FIXME
                }
                is Response.Error -> {
                    resp.appendString(jResp.msg)
                    ws.write(resp)
                    StatusFactory.error(jResp.msg, status.stream)
                }
                else -> {
                    resp.appendString("Wrong status")
                    ws.write(resp)
                    ws.close()
                    StatusFactory.error("Wrong status transition: Idle -> ", status.stream) // FIXME
                }
            }
        }

        /*
        is Status.Closed-> {
            {assert(false); StatusFactory.error("Impossible", status.stream)}
        }
         */

        else -> {assert(false); StatusFactory.error("Impossible", status.stream)}
    }

}

fun logic(vertx: Vertx, ws: ServerWebSocket){

    var status: Status = StatusFactory.waitingAuth()
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
                val song: Payload.Song = when(status.payload) {
                    is Payload.Song -> status.payload as Payload.Song
                    else -> break@loop
                }
                var buf: ByteArray = ByteArray(1024) // 1 KiB
                val rc = oggStream.ogg.read(buf)
                resp.setBytes(0, buf)
                ws.write(resp)
                oggStream.size++
                if(oggStream.size >= 2048)
                    status = StatusFactory.idle(song, oggStream)
                }
            }
        }
    )
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
