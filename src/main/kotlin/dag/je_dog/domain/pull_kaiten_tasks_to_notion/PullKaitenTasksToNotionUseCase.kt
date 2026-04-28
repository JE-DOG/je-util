package dag.je_dog.domain.pull_kaiten_tasks_to_notion

class PullKaitenTasksToNotionUseCase(
    private val kaitenTasksGateway: KaitenTasksGateway,
    private val notionTasksGateway: NotionTasksGateway,
) {

    suspend fun execute(
        request: PullKaitenTasksToNotionRequest,
    ): PullKaitenTasksToNotionResult {
        val outcomes = mutableListOf<PullKaitenTaskOutcome>()

        val tasks = when (val source = request.source) {
            is PullKaitenTasksSource.ByIds -> {
                source.taskIds.mapNotNull { taskId ->
                    runCatching {
                        kaitenTasksGateway.getTaskById(
                            apiUrl = request.kaitenApiUrl,
                            apiToken = request.kaitenApiToken,
                            taskId = taskId,
                            defaultSpaceId = request.kaitenDefaultSpaceId,
                        )
                    }.onFailure { error ->
                        outcomes += PullKaitenTaskOutcome.FailedToLoadFromKaiten(
                            taskId = taskId,
                            error = error,
                        )
                    }.getOrNull()
                }
            }

            is PullKaitenTasksSource.ByBoard -> {
                runCatching {
                    kaitenTasksGateway.getBoardTasks(
                        apiUrl = request.kaitenApiUrl,
                        apiToken = request.kaitenApiToken,
                        boardId = source.boardId,
                        defaultSpaceId = request.kaitenDefaultSpaceId,
                    )
                }.onFailure { error ->
                    outcomes += PullKaitenTaskOutcome.FailedToLoadBoardFromKaiten(
                        boardId = source.boardId,
                        error = error,
                    )
                }.getOrDefault(emptyList())
            }
        }

        val existingKeys = notionTasksGateway.getExistingTasks(
            databaseInput = request.notionDatabaseInput,
            notionToken = request.notionToken,
        ).map { task ->
            duplicateKey(title = task.title)
        }.toMutableSet()

        tasks.forEach { task ->
            val duplicateKey = duplicateKey(title = task.title)

            if (existingKeys.contains(duplicateKey)) {
                outcomes += PullKaitenTaskOutcome.AlreadyExists(task)
                return@forEach
            }

            runCatching {
                notionTasksGateway.addTask(
                    databaseInput = request.notionDatabaseInput,
                    notionToken = request.notionToken,
                    task = task,
                )
            }.onSuccess {
                existingKeys += duplicateKey
                outcomes += PullKaitenTaskOutcome.Added(task)
            }.onFailure { error ->
                outcomes += PullKaitenTaskOutcome.FailedToAddToNotion(
                    task = task,
                    error = error,
                )
            }
        }

        return PullKaitenTasksToNotionResult(outcomes = outcomes)
    }

    private fun duplicateKey(
        title: String,
    ): String {
        return title.trim()
    }
}
