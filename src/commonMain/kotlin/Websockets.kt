package se.araisan.meme

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Messages buffered per connection before it is considered too slow and disconnected. */
const val OUTBOX_CAPACITY = 1024

private class OutboxOverflow : Exception()

fun Application.configureWebsockets() {
    install(WebSockets) {}
}

fun handleWebSocketConnection(broker: TopicBroker): suspend DefaultWebSocketServerSession.() -> Unit =
    {
        // Each connection drains its own outbox, so a slow client only ever delays itself.
        val outbox = Channel<String>(OUTBOX_CAPACITY)
        val subscriber =
            Subscriber { text ->
                outbox.trySend(text).isSuccess.also { accepted -> if (!accepted) outbox.close(OutboxOverflow()) }
            }

        launch {
            // Delivers what was buffered, then fails with OutboxOverflow if the client fell too far behind.
            val failure = runCatching { for (text in outbox) send(text) }.exceptionOrNull()
            if (failure is OutboxOverflow) {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too slow to receive messages"))
            }
        }

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue

                val error = handleClientMessage(broker, subscriber, frame.readText())
                if (error != null) {
                    subscriber.offer(ServerMessage.Error(error).encode())
                }
            }
        } finally {
            broker.unsubscribeAll(subscriber)
            outbox.cancel()
        }
    }

/** Applies one client frame to the broker. Returns an error description, or null on success. */
private suspend fun handleClientMessage(
    broker: TopicBroker,
    subscriber: Subscriber,
    raw: String,
): String? {
    val message = parseClientMessage(raw) ?: return "Malformed message"

    val topic =
        when (message) {
            is ClientMessage.Subscribe -> message.topic
            is ClientMessage.Unsubscribe -> message.topic
            is ClientMessage.Publish -> message.topic
        }
    if (!isValidTopic(topic)) return "Invalid topic"

    when (message) {
        is ClientMessage.Subscribe -> broker.subscribe(subscriber, topic)
        is ClientMessage.Unsubscribe -> broker.unsubscribe(subscriber, topic)
        is ClientMessage.Publish -> broker.publish(topic, message.data, sender = subscriber, echo = message.echo)
    }
    return null
}
