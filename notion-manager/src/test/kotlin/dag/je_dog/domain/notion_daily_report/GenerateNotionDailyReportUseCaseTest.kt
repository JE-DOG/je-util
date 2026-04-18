package dag.je_dog.domain.notion_daily_report

import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerateNotionDailyReportUseCaseTest {

    @Test
    fun `formats output with friday label when previous day is weekend`() = runBlocking {
        val repository = FakeNotionDailyReportRepository(
            sections = DailySections(
                previousDayActualLines = listOf("done-1", "done-2"),
                currentDayExpectedLines = listOf("todo-1"),
            ),
        )

        val useCase = GenerateNotionDailyReportUseCase(
            repository = repository,
            workdayResolver = WorkdayResolver(),
            clock = fixedClock("2026-04-20T09:00:00Z"),
        )

        val actual = useCase.execute(
            rootPage = "root-page",
            notionToken = "token",
        )

        val expected = """
            Пятницу:
            done-1
            done-2
            
            Сегодня:
            todo-1
        """.trimIndent()

        assertEquals(expected, actual)
        assertEquals(LocalDate.parse("2026-04-20"), repository.capturedCurrentDate)
        assertEquals(LocalDate.parse("2026-04-17"), repository.capturedPreviousWorkdayDate)
    }

    @Test
    fun `formats output with default yesterday label for weekday`() = runBlocking {
        val repository = FakeNotionDailyReportRepository(
            sections = DailySections(
                previousDayActualLines = listOf("y-day"),
                currentDayExpectedLines = listOf("t-day"),
            ),
        )

        val useCase = GenerateNotionDailyReportUseCase(
            repository = repository,
            workdayResolver = WorkdayResolver(),
            clock = fixedClock("2026-04-21T09:00:00Z"),
        )

        val actual = useCase.execute(
            rootPage = "root-page",
            notionToken = "token",
        )

        val expected = """
            Вчера:
            y-day
            
            Сегодня:
            t-day
        """.trimIndent()

        assertEquals(expected, actual)
        assertEquals(LocalDate.parse("2026-04-21"), repository.capturedCurrentDate)
        assertEquals(LocalDate.parse("2026-04-20"), repository.capturedPreviousWorkdayDate)
    }

    private fun fixedClock(instantIso: String): Clock {
        return Clock.fixed(
            Instant.parse(instantIso),
            ZoneId.of("Europe/Moscow"),
        )
    }

    private class FakeNotionDailyReportRepository(
        private val sections: DailySections,
    ) : NotionDailyReportRepository {

        var capturedCurrentDate: LocalDate? = null
        var capturedPreviousWorkdayDate: LocalDate? = null

        override suspend fun loadDailySections(
            rootPageInput: String,
            notionToken: String,
            currentDate: LocalDate,
            previousWorkdayDate: LocalDate,
        ): DailySections {
            capturedCurrentDate = currentDate
            capturedPreviousWorkdayDate = previousWorkdayDate
            return sections
        }
    }
}

