package com.streaming.main

import io.vertx.core.*
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.shareddata.LocalMap
import io.vertx.core.shareddata.SharedData
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.IOException
import java.io.InputStream

fun String.runCommand(workingDir: File): InputStream? {
    try {
        val parts = this.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        return proc.inputStream
    } catch(e: IOException) {
        e.printStackTrace()
        return null
    }
}

var i = AtomicInteger(0)

fun logic(vertx: Vertx ,ws: ServerWebSocket){

    var action = "idle"

    ws.closeHandler({ ch ->
        println("Closing ws-connection to client " + ws.textHandlerID())
        println("Closed ws-con")
    })

    ws.handler(object:Handler<Buffer> {
        override fun handle(data:Buffer) {
            val cnt = i.getAndAdd(1)
            action = "send"

            val msg = data.getString(0, data.length())
            println("Message from ws-client " + ws.textHandlerID()+ ": " + msg)
            val resp = Buffer.buffer()
           // resp.appendString("O Ding Dong asd asda sdasd a")

            var size = 0
            val file = "/home/user/out.webm"
            val command = "ffmpeg -i " + file + " -f ogg -q 5 pipe:1"
            val oggStream = command.runCommand(File("/home/user"))
            while(action == "send") {
                val buf = oggStream?.readBytes()
                resp.setBytes(0, buf)
                print(resp)
                println(" " + buf)
                ws.write(resp)
                size++
                if(size >= 10)
                    action = "idle"
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