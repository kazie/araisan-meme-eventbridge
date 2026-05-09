package se.araisan.meme

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.araisan.meme.WebsocketState.connections
import se.araisan.meme.WebsocketState.connectionsMutex

fun Application.configureWebsockets() {
    install(WebSockets) {
    }
}

object WebsocketState {
    val connections = mutableSetOf<DefaultWebSocketServerSession>()
    val connectionsMutex = Mutex()
}

fun handleWebSocketConnection(): suspend DefaultWebSocketServerSession.() -> Unit =
    {
        // websocketSession
        connectionsMutex.withLock {
            connections += this
        }
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val currentConnections =
                        connectionsMutex.withLock {
                            connections.toList()
                        }
                    currentConnections.forEach {
                        if (it != this) {
                            it.send(text)
                        }
                    }
                }
            }
        } finally {
            connectionsMutex.withLock {
                connections -= this
            }
        }
    }
