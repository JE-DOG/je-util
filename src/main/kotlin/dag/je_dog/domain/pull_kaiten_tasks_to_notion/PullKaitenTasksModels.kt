package dag.je_dog.domain.pull_kaiten_tasks_to_notion

data class PullKaitenTasksToNotionRequest(
    val notionDatabaseInput: String,
    val notionToken: String,
    val kaitenApiUrl: String,
    val kaitenApiToken: String,
    val source: PullKaitenTasksSource,
    val kaitenDefaultSpaceId: String? = null,
)

sealed interface PullKaitenTasksSource {

    data class ByIds(
        val taskIds: List<Long>,
    ) : PullKaitenTasksSource

    data class ByBoard(
        val boardId: Long,
    ) : PullKaitenTasksSource
}

data class KaitenTask(
    val id: Long,
    val title: String,
    val link: String,
)

data class NotionTask(
    val id: String,
    val title: String,
)

data class PullKaitenTasksToNotionResult(
    val outcomes: List<PullKaitenTaskOutcome>,
)

sealed interface PullKaitenTaskOutcome {

    data class Added(
        val task: KaitenTask,
    ) : PullKaitenTaskOutcome

    data class AlreadyExists(
        val task: KaitenTask,
    ) : PullKaitenTaskOutcome

    data class FailedToLoadFromKaiten(
        val taskId: Long,
        val error: Throwable,
    ) : PullKaitenTaskOutcome

    data class FailedToLoadBoardFromKaiten(
        val boardId: Long,
        val error: Throwable,
    ) : PullKaitenTaskOutcome

    data class FailedToAddToNotion(
        val task: KaitenTask,
        val error: Throwable,
    ) : PullKaitenTaskOutcome
}

interface KaitenTasksGateway {

    suspend fun getTaskById(
        apiUrl: String,
        apiToken: String,
        taskId: Long,
        defaultSpaceId: String?,
    ): KaitenTask

    suspend fun getBoardTasks(
        apiUrl: String,
        apiToken: String,
        boardId: Long,
        defaultSpaceId: String?,
    ): List<KaitenTask>
}

interface NotionTasksGateway {

    suspend fun getExistingTasks(
        databaseInput: String,
        notionToken: String,
    ): List<NotionTask>

    suspend fun addTask(
        databaseInput: String,
        notionToken: String,
        task: KaitenTask,
    )
}
