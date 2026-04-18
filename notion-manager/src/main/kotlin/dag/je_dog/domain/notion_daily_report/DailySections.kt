package dag.je_dog.domain.notion_daily_report

data class DailySections(
    val previousDayActualLines: List<String>,
    val currentDayExpectedLines: List<String>,
)

