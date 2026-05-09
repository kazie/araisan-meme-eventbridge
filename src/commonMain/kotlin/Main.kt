package se.araisan.meme

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main(args: Array<String>) {
    val port = args.find { it.startsWith("-port=") }?.substringAfter("=")?.toIntOrNull() ?: 8080
    val host = args.find { it.startsWith("-host=") }?.substringAfter("=") ?: "0.0.0.0"

    embeddedServer(CIO, port = port, host = host) {
        configureWebsockets()
        configureRouting()
    }.start(wait = true)
}
