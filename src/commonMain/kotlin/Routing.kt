package se.araisan.meme

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket

fun Application.configureRouting(broker: TopicBroker) {
    routing {
        webSocket("/ws", handler = handleWebSocketConnection(broker))

        // Fire-and-forget publishing for services that don't keep a websocket open.
        // The body is the same publish frame that is sent over the websocket.
        post("/publish") {
            val message = parseClientMessage(call.receiveText())
            when {
                message !is ClientMessage.Publish -> {
                    call.respondText("Body must be a publish message", status = HttpStatusCode.BadRequest)
                }

                !isValidTopic(message.topic) -> {
                    call.respondText("Invalid topic", status = HttpStatusCode.BadRequest)
                }

                else -> {
                    val seq = broker.publish(message.topic, message.data)
                    call.respondText(
                        ServerMessage.Published(message.topic, seq).encode(),
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                }
            }
        }
    }
}
