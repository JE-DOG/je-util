package dag.je_dog.domain.notion_daily_report

import java.time.LocalDate

interface NotionDailyReportRepository {

    suspend fun loadDailySections(
        rootPageInput: String,
        notionToken: String,
        currentDate: LocalDate,
        previousWorkdayDate: LocalDate,
    ): DailySections
}

