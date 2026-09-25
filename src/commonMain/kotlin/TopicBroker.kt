package se.araisan.meme

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/** Anything that can receive published messages, e.g. a websocket session. */
fun interface Subscriber {
    /**
     * Hands [text] over for delivery without suspending, so one slow subscriber never holds up the others.
     * Returns false when the subscriber can't keep up; it is then dropped from all topics.
     */
    fun offer(text: String): Boolean
}

class TopicBroker {
    private val subscribersByTopic = mutableMapOf<String, MutableSet<Subscriber>>()
    private val seqByTopic = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    suspend fun subscribe(
        subscriber: Subscriber,
        topic: String,
    ) {
        mutex.withLock {
            subscribersByTopic.getOrPut(topic) { mutableSetOf() } += subscriber
        }
    }

    suspend fun unsubscribe(
        subscriber: Subscriber,
        topic: String,
    ) {
        mutex.withLock {
            removeLocked(subscriber, topic)
        }
    }

    suspend fun unsubscribeAll(subscriber: Subscriber) {
        mutex.withLock {
            removeAllLocked(subscriber)
        }
    }

    /**
     * Stamps [data] with the next sequence number of [topic] and offers it to every subscriber of the topic,
     * including [sender] unless [echo] is false. Numbering and offering happen under one lock, so every
     * subscriber receives a topic's messages in increasing sequence order.
     *
     * @return the sequence number assigned to the message
     */
    suspend fun publish(
        topic: String,
        data: JsonElement,
        sender: Subscriber? = null,
        echo: Boolean = true,
    ): Long =
        mutex.withLock {
            val seq = (seqByTopic[topic] ?: 0L) + 1
            seqByTopic[topic] = seq

            val text = ServerMessage.Message(topic, seq, data).encode()
            val recipients = subscribersByTopic[topic].orEmpty().filter { echo || it != sender }
            recipients.filterNot { it.offer(text) }.forEach(::removeAllLocked)

            seq
        }

    suspend fun subscribers(topic: String): List<Subscriber> =
        mutex.withLock {
            subscribersByTopic[topic]?.toList().orEmpty()
        }

    private fun removeAllLocked(subscriber: Subscriber) {
        subscribersByTopic.keys.toList().forEach { removeLocked(subscriber, it) }
    }

    private fun removeLocked(
        subscriber: Subscriber,
        topic: String,
    ) {
        val subscribers = subscribersByTopic[topic] ?: return
        subscribers -= subscriber
        if (subscribers.isEmpty()) subscribersByTopic -= topic
    }
}
