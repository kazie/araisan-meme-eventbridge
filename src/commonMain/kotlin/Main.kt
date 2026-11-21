package se.araisan.meme

import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer

fun main(args: Array<String>) {
    val port = args.find { it.startsWith("-port=") }?.substringAfter("=")?.toIntOrNull() ?: 8080
    val host = args.find { it.startsWith("-host=") }?.substringAfter("=") ?: "0.0.0.0"

    val broker = TopicBroker()

    embeddedServer(
        CIO,
        configure = {
            connector {
                this.port = port
                this.host = host
            }
            // A restart while clients are connected leaves the port in TIME_WAIT for up to a minute. Without
            // SO_REUSEADDR the new process can't bind it until then and crashes with EADDRINUSE.
            reuseAddress = true
        },
    ) {
        configureWebsockets()
        configureRouting(broker)
    }.start(wait = true)
}
