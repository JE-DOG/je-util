package dag.je_dog.domain.notion_daily_report

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkdayResolverTest {

    private val resolver = WorkdayResolver()

    @Test
    fun `returns friday and label when yesterday is sunday`() {
        val result = resolver.resolvePreviousWorkday(today = LocalDate.parse("2026-04-20"))

        assertEquals(LocalDate.parse("2026-04-17"), result.date)
        assertEquals("Пятницу", result.label)
    }

    @Test
    fun `returns friday and label when yesterday is saturday`() {
        val result = resolver.resolvePreviousWorkday(today = LocalDate.parse("2026-04-19"))

        assertEquals(LocalDate.parse("2026-04-17"), result.date)
        assertEquals("Пятницу", result.label)
    }

    @Test
    fun `returns yesterday and default label for weekday`() {
        val result = resolver.resolvePreviousWorkday(today = LocalDate.parse("2026-04-21"))

        assertEquals(LocalDate.parse("2026-04-20"), result.date)
        assertEquals("Вчера", result.label)
    }

    @Test
    fun `returns yesterday and default label for saturday`() {
        val result = resolver.resolvePreviousWorkday(today = LocalDate.parse("2026-04-18"))

        assertEquals(LocalDate.parse("2026-04-17"), result.date)
        assertEquals("Вчера", result.label)
    }
}

