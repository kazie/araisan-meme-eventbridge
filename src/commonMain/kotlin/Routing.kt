package se.araisan.meme

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket

fun Application.configureRouting() {
    routing {
        webSocket("/ws", handler = handleWebSocketConnection())
    }
}
