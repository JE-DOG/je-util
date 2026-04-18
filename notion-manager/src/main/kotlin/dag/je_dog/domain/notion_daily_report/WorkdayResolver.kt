package dag.je_dog.domain.notion_daily_report

import java.time.DayOfWeek
import java.time.LocalDate

class WorkdayResolver {

    fun resolvePreviousWorkday(today: LocalDate): PreviousWorkdayContext {
        val yesterday = today.minusDays(1)
        if (yesterday.dayOfWeek == DayOfWeek.SATURDAY || yesterday.dayOfWeek == DayOfWeek.SUNDAY) {
            var date = yesterday
            while (date.dayOfWeek != DayOfWeek.FRIDAY) {
                date = date.minusDays(1)
            }
            return PreviousWorkdayContext(
                date = date,
                label = "Пятницу",
            )
        }

        return PreviousWorkdayContext(
            date = yesterday,
            label = "Вчера",
        )
    }
}

data class PreviousWorkdayContext(
    val date: LocalDate,
    val label: String,
)

