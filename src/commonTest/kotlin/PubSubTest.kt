package se.araisan.meme

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.client.engine.cio.CIO as ClientCIO

class PubSubTest {
    /** Runs [block] against a real CIO server, since the in-memory test host lacks websockets on native. */
    private fun pubSubTest(block: suspend CoroutineScope.(TopicBroker, HttpClient) -> Unit) =
        runBlocking {
            val broker = TopicBroker()
            val server =
                embeddedServer(CIO, port = 0, host = "127.0.0.1") {
                    configureWebsockets()
                    configureRouting(broker)
                }.start(wait = false)
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            val client =
                HttpClient(ClientCIO) {
                    install(WebSockets)
                    defaultRequest { url("http://127.0.0.1:$port") }
                }
            try {
                block(broker, client)
            } finally {
                client.close()
                server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
            }
        }

    private suspend fun DefaultClientWebSocketSession.receiveText(): String =
        withTimeout(5_000) { (incoming.receive() as Frame.Text).readText() }

    private suspend fun DefaultClientWebSocketSession.receiveNothing() = assertNull(withTimeoutOrNull(300) { incoming.receive() })

    /** Waits until the broker has registered [count] subscribers on [topic]. */
    private suspend fun TopicBroker.awaitSubscribers(
        topic: String,
        count: Int,
    ) = withTimeout(5_000) {
        while (subscribers(topic).size < count) delay(10)
    }

    @Test
    fun deliversPublishedMessagesOnlyToSubscribersOfTheTopic() =
        pubSubTest { broker, client ->
            val subscribed = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()

            coroutineScope {
                val onTopic =
                    async {
                        client.webSocket("/ws") {
                            send("""{"op":"subscribe","topic":"meme/control"}""")
                            broker.awaitSubscribers("meme/control", 1)
                            subscribed.complete(Unit)
                            assertEquals(
                                """{"op":"message","topic":"meme/control","seq":1,"data":{"hello":1}}""",
                                receiveText(),
                            )
                            done.await()
                        }
                    }
                val otherTopic =
                    async {
                        client.webSocket("/ws") {
                            send("""{"op":"subscribe","topic":"other"}""")
                            broker.awaitSubscribers("other", 1)
                            done.await()
                            receiveNothing()
                        }
                    }

                client.webSocket("/ws") {
                    subscribed.await()
                    broker.awaitSubscribers("other", 1)
                    send("""{"op":"publish","topic":"meme/control","data":{"hello":1}}""")
                    // Publisher is not subscribed, so it must not get its own message back.
                    receiveNothing()
                }
                done.complete(Unit)
                onTopic.await()
                otherTopic.await()
            }
        }

    @Test
    fun echoesToSubscribedSenderByDefault() =
        pubSubTest { broker, client ->
            val peerReady = CompletableDeferred<Unit>()

            coroutineScope {
                val peer =
                    async {
                        client.webSocket("/ws") {
                            send("""{"op":"subscribe","topic":"t"}""")
                            peerReady.complete(Unit)
                            assertEquals("""{"op":"message","topic":"t","seq":1,"data":"x"}""", receiveText())
                        }
                    }

                client.webSocket("/ws") {
                    send("""{"op":"subscribe","topic":"t"}""")
                    peerReady.await()
                    broker.awaitSubscribers("t", 2)
                    send("""{"op":"publish","topic":"t","data":"x"}""")
                    assertEquals("""{"op":"message","topic":"t","seq":1,"data":"x"}""", receiveText())
                }
                peer.await()
            }
        }

    @Test
    fun publisherCanOptOutOfEcho() =
        pubSubTest { broker, client ->
            val peerReady = CompletableDeferred<Unit>()

            coroutineScope {
                val peer =
                    async {
                        client.webSocket("/ws") {
                            send("""{"op":"subscribe","topic":"t"}""")
                            peerReady.complete(Unit)
                            assertEquals("""{"op":"message","topic":"t","seq":1,"data":"x"}""", receiveText())
                        }
                    }

                client.webSocket("/ws") {
                    send("""{"op":"subscribe","topic":"t"}""")
                    peerReady.await()
                    broker.awaitSubscribers("t", 2)
                    send("""{"op":"publish","topic":"t","data":"x","echo":false}""")
                    receiveNothing()
                }
                peer.await()
            }
        }

    @Test
    fun stopsDeliveringAfterUnsubscribe() =
        pubSubTest { broker, client ->
            client.webSocket("/ws") {
                send("""{"op":"subscribe","topic":"t"}""")
                broker.awaitSubscribers("t", 1)
                send("""{"op":"unsubscribe","topic":"t"}""")
                withTimeout(5_000) { while (broker.subscribers("t").isNotEmpty()) delay(10) }

                broker.publish("t", JsonPrimitive(1))
                receiveNothing()
            }
        }

    @Test
    fun repliesWithErrorOnInvalidMessages() =
        pubSubTest { _, client ->
            client.webSocket("/ws") {
                send("not json")
                assertEquals("""{"op":"error","message":"Malformed message"}""", receiveText())

                send("""{"op":"subscribe","topic":"  "}""")
                assertEquals("""{"op":"error","message":"Invalid topic"}""", receiveText())

                send("""{"op":"publish","topic":"t","data":{"a":nope}}""")
                assertEquals("""{"op":"error","message":"Malformed message"}""", receiveText())
            }
        }

    @Test
    fun publishesOverHttp() =
        pubSubTest { broker, client ->
            client.webSocket("/ws") {
                send("""{"op":"subscribe","topic":"services/deploy"}""")
                broker.awaitSubscribers("services/deploy", 1)

                val response =
                    client.post("/publish") {
                        setBody("""{"op":"publish","topic":"services/deploy","data":{"version":"1.2.3"}}""")
                    }
                assertEquals(HttpStatusCode.Accepted, response.status)
                assertEquals("""{"op":"published","topic":"services/deploy","seq":1}""", response.bodyAsText())
                assertEquals(
                    """{"op":"message","topic":"services/deploy","seq":1,"data":{"version":"1.2.3"}}""",
                    receiveText(),
                )

                assertEquals(HttpStatusCode.BadRequest, client.post("/publish") { setBody("nope") }.status)
                assertEquals(
                    HttpStatusCode.BadRequest,
                    client.post("/publish") { setBody("""{"op":"subscribe","topic":"x"}""") }.status,
                )
                assertEquals(
                    HttpStatusCode.BadRequest,
                    client.post("/publish") { setBody("""{"op":"publish","topic":" ","data":1}""") }.status,
                )
            }
        }
}
