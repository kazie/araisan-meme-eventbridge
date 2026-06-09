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
    install(WebSockets) {}
}

object WebsocketState {
    val connections = mutableSetOf<DefaultWebSocketServerSession>()
    val connectionsMutex = Mutex()
}

fun handleWebSocketConnection(): suspend DefaultWebSocketServerSession.() -> Unit =
    {
        // websocketSession
        registerConnection()
        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue

                val text = frame.readText()

                currentConnections().filterNot { it == this }.forEach { recipient ->
                    runCatching {
                        recipient.send(text)
                    }.onFailure {
                        unregisterConnection(remove = recipient)
                    }
                }
            }
        } finally {
            unregisterConnection()
        }
    }

private suspend fun DefaultWebSocketServerSession.registerConnection() {
    connectionsMutex.withLock {
        connections += this
    }
}

private suspend fun DefaultWebSocketServerSession.unregisterConnection(remove: DefaultWebSocketServerSession = this) {
    connectionsMutex.withLock {
        connections -= remove
    }
}

private suspend fun currentConnections(): List<DefaultWebSocketServerSession> =
    connectionsMutex.withLock {
        connections.toList()
    }
