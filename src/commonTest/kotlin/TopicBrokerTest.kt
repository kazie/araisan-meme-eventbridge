package se.araisan.meme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicBrokerTest {
    private class RecordingSubscriber(
        private val capacity: Int = Int.MAX_VALUE,
    ) : Subscriber {
        val received = mutableListOf<String>()

        override fun offer(text: String): Boolean = (received.size < capacity).also { if (it) received += text }

        fun seqs(): List<Long> =
            received.map {
                protocolJson
                    .parseToJsonElement(it)
                    .jsonObject["seq"]!!
                    .jsonPrimitive.long
            }
    }

    @Test
    fun numbersMessagesPerTopic() =
        runBlocking {
            val broker = TopicBroker()
            assertEquals(1, broker.publish("a", JsonPrimitive(1)))
            assertEquals(2, broker.publish("a", JsonPrimitive(2)))
            assertEquals(1, broker.publish("b", JsonPrimitive(3)))
        }

    @Test
    fun allSubscribersSeeTheSameOrderUnderConcurrentPublishers() =
        runBlocking {
            val broker = TopicBroker()
            val subscribers = List(3) { RecordingSubscriber() }
            subscribers.forEach { broker.subscribe(it, "t") }

            List(8) { publisher ->
                async(Dispatchers.Default) {
                    repeat(100) { broker.publish("t", JsonPrimitive("$publisher-$it")) }
                }
            }.awaitAll()

            val expected = (1L..800L).toList()
            subscribers.forEach { assertEquals(expected, it.seqs()) }
            assertEquals(subscribers[0].received, subscribers[1].received)
        }

    @Test
    fun dropsSubscribersThatCannotKeepUpWithoutAffectingOthers() =
        runBlocking {
            val broker = TopicBroker()
            val slow = RecordingSubscriber(capacity = 2)
            val fast = RecordingSubscriber()
            broker.subscribe(slow, "t")
            broker.subscribe(fast, "t")

            repeat(5) { broker.publish("t", JsonPrimitive(it)) }

            assertEquals(listOf(1L, 2L), slow.seqs())
            assertEquals((1L..5L).toList(), fast.seqs())
            assertTrue(slow !in broker.subscribers("t"))
        }
}
