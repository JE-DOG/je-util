package dag.je_dog.presentation.notion_daily_report

import dag.je_dog.common.cli.CliUtil
import dag.je_dog.common.env.EnvironmentKeys
import dag.je_dog.common.logger.AppLogger
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class NotionDailyReportSubcommand : Subcommand(
    name = "notion_daily_report",
    actionDescription = "Build daily text from a Notion calendar page and optionally send it to Matrix room",
) {

    private val rootPageOverride by option(
        type = ArgType.String,
        fullName = "page",
        shortName = "p",
        description = "Notion page id or full page URL that contains the calendar",
    )

    private val notionTokenOverride by option(
        type = ArgType.String,
        fullName = "token",
        shortName = "t",
        description = "Notion integration token. If omitted, NOTION_API_TOKEN env var is used",
    )

    private val matrixRoomOverride by option(
        type = ArgType.String,
        fullName = "room",
        shortName = "r",
        description = "Matrix room id. If omitted, MATRIX_DAILY_REPORT_ROOM_ID env var is used",
    )

    private val matrixTokenOverride by option(
        type = ArgType.String,
        fullName = "matrix-token",
        shortName = "m",
        description = "Matrix access token. If omitted, MATRIX_ACCESS_TOKEN env var is used",
    )

    private val matrixHomeServerOverride by option(
        type = ArgType.String,
        fullName = "matrix-home-server",
        shortName = "s",
        description = "Matrix homeserver url. If omitted, MATRIX_HOME_SERVER_URL env var is used",
    )

    override fun execute(): Unit = runBlocking {
        val notionToken = optionOrEnv(
            optionValue = notionTokenOverride,
            envKey = EnvironmentKeys.NOTION_API_TOKEN,
        )
        val rootPage = optionOrEnv(
            optionValue = rootPageOverride,
            envKey = EnvironmentKeys.NOTION_TASKS_CALENDAR_LINK,
        )

        if (notionToken.isBlank()) {
            AppLogger.e(
                "Notion token is missing. Pass --token or set ${EnvironmentKeys.NOTION_API_TOKEN} environment variable.",
            )
            return@runBlocking
        }

        if (rootPage.isBlank()) {
            AppLogger.e(
                "Notion page is missing. Pass --page or set ${EnvironmentKeys.NOTION_TASKS_CALENDAR_LINK} environment variable.",
            )
            return@runBlocking
        }

        val reportText = runCatching {
            NotionDailyReportComponent.generateNotionDailyReportUseCase.execute(
                rootPage = rootPage,
                notionToken = notionToken,
            )
        }.onFailure { throwable ->
            AppLogger.e(
                message = "Failed to generate notion_daily_report output.",
                throwable = throwable,
            )
        }.getOrNull() ?: return@runBlocking

        println(reportText)

        val roomId = optionOrEnv(
            optionValue = matrixRoomOverride,
            envKey = EnvironmentKeys.MATRIX_DAILY_REPORT_ROOM_ID,
        )
        if (roomId.isBlank()) {
            AppLogger.w(
                "Matrix room id is missing. Pass --room or set ${EnvironmentKeys.MATRIX_DAILY_REPORT_ROOM_ID}.",
            )
            return@runBlocking
        }

        val confirmed = CliUtil.askYesNo(
            question = "Send report to Matrix room \"$roomId\"?",
            defaultValue = false,
        )
        if (!confirmed) {
            AppLogger.i("Report sending canceled by user.")
            return@runBlocking
        }

        val matrixToken = optionOrEnv(
            optionValue = matrixTokenOverride,
            envKey = EnvironmentKeys.MATRIX_ACCESS_TOKEN,
        )
        val matrixHomeServer = optionOrEnv(
            optionValue = matrixHomeServerOverride,
            envKey = EnvironmentKeys.MATRIX_HOME_SERVER_URL,
        )

        if (matrixToken.isBlank() || matrixHomeServer.isBlank()) {
            AppLogger.e(
                "Matrix token or homeserver is missing. Set --matrix-token/--matrix-home-server or ${EnvironmentKeys.MATRIX_ACCESS_TOKEN}/${EnvironmentKeys.MATRIX_HOME_SERVER_URL}.",
            )
            return@runBlocking
        }

        NotionDailyReportComponent.matrixManager.sendMessage(
            roomId = roomId,
            message = reportText,
            accessToken = matrixToken,
            homeServerUrl = matrixHomeServer,
        ).onSuccess { eventId ->
            AppLogger.i("Report was sent to Matrix successfully. eventId=$eventId")
        }.onFailure { throwable ->
            AppLogger.e(
                message = "Failed to send report to Matrix room \"$roomId\".",
                throwable = throwable,
            )
        }
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
