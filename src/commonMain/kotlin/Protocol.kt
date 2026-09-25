package se.araisan.meme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

const val MAX_TOPIC_LENGTH = 256

val protocolJson =
    Json {
        classDiscriminator = "op"
        ignoreUnknownKeys = true
    }

/** Frames sent from a client to the bridge. */
@Serializable
sealed interface ClientMessage {
    @Serializable
    @SerialName("subscribe")
    data class Subscribe(
        val topic: String,
    ) : ClientMessage

    @Serializable
    @SerialName("unsubscribe")
    data class Unsubscribe(
        val topic: String,
    ) : ClientMessage

    @Serializable
    @SerialName("publish")
    data class Publish(
        val topic: String,
        val data: JsonElement,
        /** Whether the publisher itself receives the message too, if subscribed to the topic. */
        val echo: Boolean = true,
    ) : ClientMessage
}

/** Frames sent from the bridge to a client. */
@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("message")
    data class Message(
        val topic: String,
        /** Per-topic number assigned by the bridge, increasing by one per message since the bridge started. */
        val seq: Long,
        val data: JsonElement,
    ) : ServerMessage

    /** Reply to an HTTP publish. */
    @Serializable
    @SerialName("published")
    data class Published(
        val topic: String,
        val seq: Long,
    ) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
    ) : ServerMessage
}

fun isValidTopic(topic: String): Boolean = topic.isNotBlank() && topic.length <= MAX_TOPIC_LENGTH

fun parseClientMessage(raw: String): ClientMessage? =
    runCatching { protocolJson.decodeFromString<ClientMessage>(raw) }
        .getOrNull()
        ?.takeIf { it !is ClientMessage.Publish || it.data.isStrictJson() }

/**
 * kotlinx.serialization accepts unquoted literals such as `nope` when decoding a [JsonElement].
 * Relaying those would hand subscribers invalid JSON, so reject them.
 */
private fun JsonElement.isStrictJson(): Boolean =
    when (this) {
        is JsonNull -> true
        is JsonPrimitive -> isString || booleanOrNull != null || doubleOrNull != null
        is JsonArray -> all { it.isStrictJson() }
        is JsonObject -> values.all { it.isStrictJson() }
    }

fun ServerMessage.encode(): String = protocolJson.encodeToString(ServerMessage.serializer(), this)
