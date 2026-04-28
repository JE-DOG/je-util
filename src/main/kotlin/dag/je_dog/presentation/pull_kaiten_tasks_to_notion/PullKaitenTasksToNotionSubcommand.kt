package dag.je_dog.presentation.pull_kaiten_tasks_to_notion

import dag.je_dog.common.env.EnvironmentKeys
import dag.je_dog.common.logger.AppLogger
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.PullKaitenTaskOutcome
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.PullKaitenTasksSource
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.PullKaitenTasksToNotionRequest
import dag.je_dog.presentation.pull_kaiten_tasks_to_notion.di.PullKaitenTasksToNotionComponent
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class PullKaitenTasksToNotionSubcommand : Subcommand(
    name = "pkttn",
    actionDescription =
        "(pull-kaiten-tasks-to-notion) Import Kaiten tasks into a Notion database by comma-separated ids or by board id.",
) {

    private val idsOverride by option(
        type = ArgType.String,
        fullName = "ids",
        shortName = "i",
        description = "Comma-separated Kaiten task ids. Example: 123,456",
    )

    private val boardIdOverride by option(
        type = ArgType.String,
        fullName = "board",
        shortName = "b",
        description = "Kaiten board id to import all active tasks from.",
    )

    private val notionDatabaseOverride by option(
        type = ArgType.String,
        fullName = "database",
        shortName = "d",
        description = "Notion database id or full URL.",
    )

    private val notionTokenOverride by option(
        type = ArgType.String,
        fullName = "notion-token",
        shortName = "n",
        description = "Notion integration token. If omitted, NOTION_API_TOKEN env var is used.",
    )

    private val kaitenApiUrlOverride by option(
        type = ArgType.String,
        fullName = "kaiten-api-url",
        shortName = "u",
        description = "Kaiten API base URL. If omitted, KAITEN_API_URL env var is used.",
    )

    private val kaitenApiTokenOverride by option(
        type = ArgType.String,
        fullName = "kaiten-token",
        shortName = "k",
        description = "Kaiten API token. If omitted, KAITEN_API_TOKEN env var is used.",
    )

    private val kaitenDefaultSpaceIdOverride by option(
        type = ArgType.String,
        fullName = "kaiten-space-id",
        shortName = "s",
        description =
            "Optional Kaiten space id used to build task links when API response has no space id.",
    )

    override fun execute(): Unit = runBlocking {
        val notionToken = optionOrEnv(
            optionValue = notionTokenOverride,
            envKey = EnvironmentKeys.NOTION_API_TOKEN,
        )
        val notionDatabaseInput = optionOrEnv(
            optionValue = notionDatabaseOverride,
            envKey = EnvironmentKeys.NOTION_TASKS_DATABASE_LINK,
        )
        val kaitenApiUrl = optionOrEnv(
            optionValue = kaitenApiUrlOverride,
            envKey = EnvironmentKeys.KAITEN_API_URL,
        )
        val kaitenApiToken = optionOrEnv(
            optionValue = kaitenApiTokenOverride,
            envKey = EnvironmentKeys.KAITEN_API_TOKEN,
        )
        val idsInput = optionOrEnv(
            optionValue = idsOverride,
            envKey = EnvironmentKeys.KAITEN_TASK_IDS,
        )
        val boardInput = optionOrEnv(
            optionValue = boardIdOverride,
            envKey = EnvironmentKeys.KAITEN_BOARD_ID,
        )
        val defaultSpaceId = optionOrEnv(
            optionValue = kaitenDefaultSpaceIdOverride,
            envKey = EnvironmentKeys.KAITEN_DEFAULT_SPACE_ID,
        ).ifBlank { null }

        if (notionToken.isBlank()) {
            AppLogger.e(
                "Notion token is missing. Pass --notion-token or set ${EnvironmentKeys.NOTION_API_TOKEN}.",
            )
            return@runBlocking
        }

        if (notionDatabaseInput.isBlank()) {
            AppLogger.e(
                "Notion database is missing. Pass --database or set ${EnvironmentKeys.NOTION_TASKS_DATABASE_LINK}.",
            )
            return@runBlocking
        }

        if (kaitenApiUrl.isBlank()) {
            AppLogger.e(
                "Kaiten API URL is missing. Pass --kaiten-api-url or set ${EnvironmentKeys.KAITEN_API_URL}.",
            )
            return@runBlocking
        }

        if (kaitenApiToken.isBlank()) {
            AppLogger.e(
                "Kaiten API token is missing. Pass --kaiten-token or set ${EnvironmentKeys.KAITEN_API_TOKEN}.",
            )
            return@runBlocking
        }

        val source = parseSource(idsInput = idsInput, boardInput = boardInput)
            .onFailure { error ->
                AppLogger.e(
                    message = error.message ?: "Failed to parse source arguments.",
                    throwable = error,
                )
            }
            .getOrNull() ?: return@runBlocking

        val result = runCatching {
            PullKaitenTasksToNotionComponent.pullKaitenTasksToNotionUseCase.execute(
                request = PullKaitenTasksToNotionRequest(
                    notionDatabaseInput = notionDatabaseInput,
                    notionToken = notionToken,
                    kaitenApiUrl = kaitenApiUrl,
                    kaitenApiToken = kaitenApiToken,
                    source = source,
                    kaitenDefaultSpaceId = defaultSpaceId,
                ),
            )
        }.onFailure { error ->
            AppLogger.e(
                message = "Failed to import Kaiten tasks to Notion.",
                throwable = error,
            )
        }.getOrNull() ?: return@runBlocking

        result.outcomes.forEach { outcome ->
            when (outcome) {
                is PullKaitenTaskOutcome.Added -> {
                    AppLogger.i(
                        "Task ${outcome.task.id} \"${outcome.task.title}\" was added to Notion successfully.",
                    )
                }

                is PullKaitenTaskOutcome.AlreadyExists -> {
                    AppLogger.i(
                        "Task ${outcome.task.id} \"${outcome.task.title}\" is already present in Notion. Skipped.",
                    )
                }

                is PullKaitenTaskOutcome.FailedToLoadFromKaiten -> {
                    AppLogger.e(
                        message = "Failed to load Kaiten task id=${outcome.taskId}.",
                        throwable = outcome.error,
                    )
                }

                is PullKaitenTaskOutcome.FailedToLoadBoardFromKaiten -> {
                    AppLogger.e(
                        message = "Failed to load tasks from Kaiten board id=${outcome.boardId}.",
                        throwable = outcome.error,
                    )
                }

                is PullKaitenTaskOutcome.FailedToAddToNotion -> {
                    AppLogger.e(
                        message =
                            "Failed to add task ${outcome.task.id} \"${outcome.task.title}\" to Notion.",
                        throwable = outcome.error,
                    )
                }
            }
        }

        val addedCount = result.outcomes.count { outcome -> outcome is PullKaitenTaskOutcome.Added }
        val skippedCount = result.outcomes.count { outcome -> outcome is PullKaitenTaskOutcome.AlreadyExists }
        val failedCount = result.outcomes.count { outcome ->
            outcome is PullKaitenTaskOutcome.FailedToAddToNotion ||
                    outcome is PullKaitenTaskOutcome.FailedToLoadFromKaiten ||
                    outcome is PullKaitenTaskOutcome.FailedToLoadBoardFromKaiten
        }

        AppLogger.i(
            "Import finished. added=$addedCount, skipped=$skippedCount, failed=$failedCount",
        )
    }

    private fun parseSource(
        idsInput: String,
        boardInput: String,
    ): Result<PullKaitenTasksSource> {
        if (idsInput.isBlank() && boardInput.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "Source is missing. Provide --ids or --board (or set KAITEN_TASK_IDS/KAITEN_BOARD_ID).",
                ),
            )
        }

        if (idsInput.isNotBlank() && boardInput.isNotBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "Only one source is allowed. Use either --ids or --board.",
                ),
            )
        }

        if (idsInput.isNotBlank()) {
            val taskIds = idsInput.split(',')
                .map { token -> token.trim() }
                .filter { token -> token.isNotBlank() }
                .map { token ->
                    token.toLongOrNull()
                        ?: return Result.failure(
                            IllegalArgumentException("Invalid task id: $token"),
                        )
                }

            if (taskIds.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException("Task ids are empty. Example: --ids 101,102"),
                )
            }

            return Result.success(PullKaitenTasksSource.ByIds(taskIds = taskIds))
        }

        val boardId = boardInput.trim().toLongOrNull()
            ?: return Result.failure(
                IllegalArgumentException("Invalid board id: $boardInput"),
            )

        return Result.success(PullKaitenTasksSource.ByBoard(boardId = boardId))
    }

    private fun optionOrEnv(
        optionValue: String?,
        envKey: String,
    ): String {
        return optionValue
            .orEmpty()
            .ifBlank { System.getenv(envKey).orEmpty() }
    }
}
