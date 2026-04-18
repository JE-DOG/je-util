package dag.je_dog.domain.notion_daily_report

import java.time.Clock
import java.time.LocalDate

class GenerateNotionDailyReportUseCase(
    private val repository: NotionDailyReportRepository,
    private val workdayResolver: WorkdayResolver = WorkdayResolver(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun execute(
        rootPage: String,
        notionToken: String,
    ): String {
        val today = LocalDate.now(clock)
        val previousWorkday = workdayResolver.resolvePreviousWorkday(today)

        val sections = repository.loadDailySections(
            rootPageInput = rootPage,
            notionToken = notionToken,
            currentDate = today,
            previousWorkdayDate = previousWorkday.date,
        )

        return buildString {
            append(previousWorkday.label)
            append(":\n\n")
            append(sections.previousDayActualLines.joinToString("\n\n"))
            append("\n\n")
            append("Сегодня:\n\n")
            append(sections.currentDayExpectedLines.joinToString("\n\n"))
        }
    }
}
