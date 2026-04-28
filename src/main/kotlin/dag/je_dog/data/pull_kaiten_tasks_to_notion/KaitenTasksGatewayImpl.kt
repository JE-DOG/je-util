package dag.je_dog.data.pull_kaiten_tasks_to_notion

import dag.je_dog.domain.pull_kaiten_tasks_to_notion.KaitenTask
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.KaitenTasksGateway
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class KaitenTasksGatewayImpl(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : KaitenTasksGateway {

    override suspend fun getTaskById(
        apiUrl: String,
        apiToken: String,
        taskId: Long,
        defaultSpaceId: String?,
    ): KaitenTask {
        val boardSpaceCache = mutableMapOf<Long, Long?>()
        val card = requestJsonObject(
            apiUrl = apiUrl,
            apiToken = apiToken,
            pathAndQuery = "/api/latest/cards/$taskId",
        )

        return parseTask(
            apiUrl = apiUrl,
            apiToken = apiToken,
            card = card,
            defaultSpaceId = defaultSpaceId,
            boardSpaceCache = boardSpaceCache,
        )
    }

    override suspend fun getBoardTasks(
        apiUrl: String,
        apiToken: String,
        boardId: Long,
        defaultSpaceId: String?,
    ): List<KaitenTask> {
        val boardSpaceCache = mutableMapOf<Long, Long?>()
        val tasks = mutableListOf<KaitenTask>()

        var skip = 0
        while (true) {
            val cardArray = requestJsonArray(
                apiUrl = apiUrl,
                apiToken = apiToken,
                pathAndQuery =
                    "/api/latest/boards/$boardId/cards?limit=$PAGE_LIMIT&skip=$skip&sort_by=created&sort_direction=desc&condition=1",
            )
            if (cardArray.isEmpty()) {
                break
            }

            cardArray.forEach { element ->
                tasks += parseTask(
                    apiUrl = apiUrl,
                    apiToken = apiToken,
                    card = element.jsonObject,
                    defaultSpaceId = defaultSpaceId,
                    boardSpaceCache = boardSpaceCache,
                )
            }

            if (cardArray.size < PAGE_LIMIT) {
                break
            }
            skip += PAGE_LIMIT
        }

        return tasks
    }

    private fun requestJsonObject(
        apiUrl: String,
        apiToken: String,
        pathAndQuery: String,
    ): JsonObject {
        return requestJsonElement(
            apiUrl = apiUrl,
            apiToken = apiToken,
            pathAndQuery = pathAndQuery,
        ).jsonObject
    }

    private fun requestJsonArray(
        apiUrl: String,
        apiToken: String,
        pathAndQuery: String,
    ): JsonArray {
        return requestJsonElement(
            apiUrl = apiUrl,
            apiToken = apiToken,
            pathAndQuery = pathAndQuery,
        ).jsonArray
    }

    private fun requestJsonElement(
        apiUrl: String,
        apiToken: String,
        pathAndQuery: String,
    ): JsonElement {
        val normalizedApiUrl = normalizeApiUrl(apiUrl)
        val requestUri = URI.create(normalizedApiUrl + pathAndQuery)

        val request = HttpRequest.newBuilder(requestUri)
            .header("Authorization", "Bearer ${apiToken.trim()}")
            .header("Content-Type", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Kaiten API request failed. status=${response.statusCode()}, url=$requestUri, body=${
                    response.body().take(MAX_ERROR_BODY_LENGTH)
                }",
            )
        }

        return runCatching { json.parseToJsonElement(response.body()) }
            .getOrElse { error ->
                throw IllegalStateException(
                    "Failed to parse Kaiten response from $requestUri",
                    error,
                )
            }
    }

    private fun parseTask(
        apiUrl: String,
        apiToken: String,
        card: JsonObject,
        defaultSpaceId: String?,
        boardSpaceCache: MutableMap<Long, Long?>,
    ): KaitenTask {
        val id = card.getLong("id")
            ?: error("Kaiten card payload has no id: $card")

        val title = card.getString("title")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("Kaiten card payload has no title. cardId=$id")

        val boardId = card.getLong("board_id")
        val spaceId = resolveSpaceId(
            apiUrl = apiUrl,
            apiToken = apiToken,
            card = card,
            boardId = boardId,
            defaultSpaceId = defaultSpaceId,
            boardSpaceCache = boardSpaceCache,
        )

        val link = buildTaskLink(
            apiUrl = apiUrl,
            taskId = id,
            spaceId = spaceId,
        )

        return KaitenTask(
            id = id,
            title = title,
            link = link,
        )
    }

    private fun resolveSpaceId(
        apiUrl: String,
        apiToken: String,
        card: JsonObject,
        boardId: Long?,
        defaultSpaceId: String?,
        boardSpaceCache: MutableMap<Long, Long?>,
    ): Long? {
        val fromCard = card.getLong("space_id")
            ?: card["board"]
                ?.asObjectOrNull()
                ?.getLong("space_id")
        if (fromCard != null) {
            return fromCard
        }

        if (boardId != null) {
            if (boardSpaceCache.containsKey(boardId)) {
                return boardSpaceCache[boardId]
            }
            val boardSpaceId = fetchBoardSpaceId(
                apiUrl = apiUrl,
                apiToken = apiToken,
                boardId = boardId,
            )
            boardSpaceCache[boardId] = boardSpaceId
            if (boardSpaceId != null) {
                return boardSpaceId
            }
        }

        return defaultSpaceId?.trim()?.toLongOrNull()
    }

    private fun fetchBoardSpaceId(
        apiUrl: String,
        apiToken: String,
        boardId: Long,
    ): Long? {
        return requestJsonObject(
            apiUrl = apiUrl,
            apiToken = apiToken,
            pathAndQuery = "/api/latest/boards/$boardId",
        ).getLong("space_id")
    }

    private fun buildTaskLink(
        apiUrl: String,
        taskId: Long,
        spaceId: Long?,
    ): String {
        val baseUiUrl = apiUrl.trim()
            .trimEnd('/')

        return "$baseUiUrl/$taskId"
    }

    private fun normalizeApiUrl(apiUrl: String): String {
        return apiUrl.trim().trimEnd('/')
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return this as? JsonObject
    }

    private fun JsonObject.getLong(key: String): Long? {
        return this[key]?.jsonPrimitive?.longOrNull
    }

    private fun JsonObject.getString(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private companion object {
        private const val PAGE_LIMIT = 200
        private const val MAX_ERROR_BODY_LENGTH = 1_000
    }
}
