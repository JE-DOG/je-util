package dag.je_dog.domain.pull_kaiten_tasks_to_notion

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PullKaitenTasksToNotionUseCaseTest {

    @Test
    fun `skips task when same id and title already exist`() = runBlocking {
        val kaitenGateway = FakeKaitenTasksGateway(
            tasksById = mapOf(
                101L to KaitenTask(id = 101L, title = "Task A", link = "https://kaiten/task/101"),
            ),
        )
        val notionGateway = FakeNotionTasksGateway(
            existingTasks = mutableListOf(
                NotionTask(id = "101", title = "Task A"),
            ),
        )

        val result = PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenGateway,
            notionTasksGateway = notionGateway,
        ).execute(
            request = PullKaitenTasksToNotionRequest(
                notionDatabaseInput = "db",
                notionToken = "notion-token",
                kaitenApiUrl = "https://kaiten/api/latest",
                kaitenApiToken = "kaiten-token",
                source = PullKaitenTasksSource.ByIds(listOf(101L)),
            ),
        )

        assertEquals(1, result.outcomes.size)
        assertIs<PullKaitenTaskOutcome.AlreadyExists>(result.outcomes.first())
        assertEquals(emptyList(), notionGateway.addedTasks)
    }

    @Test
    fun `skips task when same title already exists`() = runBlocking {
        val kaitenGateway = FakeKaitenTasksGateway(
            tasksById = mapOf(
                101L to KaitenTask(id = 101L, title = "Task A", link = "https://kaiten/task/101"),
            ),
        )
        val notionGateway = FakeNotionTasksGateway(
            existingTasks = mutableListOf(
                NotionTask(id = "999", title = "Task A"),
            ),
        )

        val result = PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenGateway,
            notionTasksGateway = notionGateway,
        ).execute(
            request = PullKaitenTasksToNotionRequest(
                notionDatabaseInput = "db",
                notionToken = "notion-token",
                kaitenApiUrl = "https://kaiten/api/latest",
                kaitenApiToken = "kaiten-token",
                source = PullKaitenTasksSource.ByIds(listOf(101L)),
            ),
        )

        assertEquals(1, result.outcomes.size)
        assertIs<PullKaitenTaskOutcome.AlreadyExists>(result.outcomes.first())
        assertEquals(emptyList(), notionGateway.addedTasks)
    }

    @Test
    fun `adds task when same id exists but title differs`() = runBlocking {
        val kaitenGateway = FakeKaitenTasksGateway(
            tasksById = mapOf(
                101L to KaitenTask(id = 101L, title = "Task A", link = "https://kaiten/task/101"),
            ),
        )
        val notionGateway = FakeNotionTasksGateway(
            existingTasks = mutableListOf(
                NotionTask(id = "101", title = "Another title"),
            ),
        )

        val result = PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenGateway,
            notionTasksGateway = notionGateway,
        ).execute(
            request = PullKaitenTasksToNotionRequest(
                notionDatabaseInput = "db",
                notionToken = "notion-token",
                kaitenApiUrl = "https://kaiten/api/latest",
                kaitenApiToken = "kaiten-token",
                source = PullKaitenTasksSource.ByIds(listOf(101L)),
            ),
        )

        assertEquals(1, notionGateway.addedTasks.size)
        assertIs<PullKaitenTaskOutcome.Added>(result.outcomes.first())
    }

    @Test
    fun `continues adding when one task fails`() = runBlocking {
        val kaitenGateway = FakeKaitenTasksGateway(
            tasksById = mapOf(
                101L to KaitenTask(id = 101L, title = "Task A", link = "https://kaiten/task/101"),
                102L to KaitenTask(id = 102L, title = "Task B", link = "https://kaiten/task/102"),
            ),
        )
        val notionGateway = FakeNotionTasksGateway(
            existingTasks = mutableListOf(),
            failingIds = setOf("101"),
        )

        val result = PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenGateway,
            notionTasksGateway = notionGateway,
        ).execute(
            request = PullKaitenTasksToNotionRequest(
                notionDatabaseInput = "db",
                notionToken = "notion-token",
                kaitenApiUrl = "https://kaiten/api/latest",
                kaitenApiToken = "kaiten-token",
                source = PullKaitenTasksSource.ByIds(listOf(101L, 102L)),
            ),
        )

        assertEquals(2, result.outcomes.size)
        assertIs<PullKaitenTaskOutcome.FailedToAddToNotion>(result.outcomes[0])
        assertIs<PullKaitenTaskOutcome.Added>(result.outcomes[1])
        assertEquals(listOf(102L), notionGateway.addedTasks.map { it.id })
    }

    @Test
    fun `continues loading ids when one kaiten request fails`() = runBlocking {
        val kaitenGateway = FakeKaitenTasksGateway(
            tasksById = mapOf(
                102L to KaitenTask(id = 102L, title = "Task B", link = "https://kaiten/task/102"),
            ),
            failingIds = setOf(101L),
        )
        val notionGateway = FakeNotionTasksGateway(existingTasks = mutableListOf())

        val result = PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenGateway,
            notionTasksGateway = notionGateway,
        ).execute(
            request = PullKaitenTasksToNotionRequest(
                notionDatabaseInput = "db",
                notionToken = "notion-token",
                kaitenApiUrl = "https://kaiten/api/latest",
                kaitenApiToken = "kaiten-token",
                source = PullKaitenTasksSource.ByIds(listOf(101L, 102L)),
            ),
        )

        assertEquals(2, result.outcomes.size)
        assertIs<PullKaitenTaskOutcome.FailedToLoadFromKaiten>(result.outcomes[0])
        assertIs<PullKaitenTaskOutcome.Added>(result.outcomes[1])
    }

    @Test
    fun `loads board tasks when board mode is selected`() = runBlocking {
        val kaitenGateway = FakeKaitenTasksGateway(
            tasksByBoard = mapOf(
                500L to listOf(
                    KaitenTask(id = 101L, title = "Task A", link = "https://kaiten/task/101"),
                    KaitenTask(id = 102L, title = "Task B", link = "https://kaiten/task/102"),
                ),
            ),
        )
        val notionGateway = FakeNotionTasksGateway(existingTasks = mutableListOf())

        val result = PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenGateway,
            notionTasksGateway = notionGateway,
        ).execute(
            request = PullKaitenTasksToNotionRequest(
                notionDatabaseInput = "db",
                notionToken = "notion-token",
                kaitenApiUrl = "https://kaiten/api/latest",
                kaitenApiToken = "kaiten-token",
                source = PullKaitenTasksSource.ByBoard(500L),
            ),
        )

        assertEquals(2, result.outcomes.size)
        assertIs<PullKaitenTaskOutcome.Added>(result.outcomes[0])
        assertIs<PullKaitenTaskOutcome.Added>(result.outcomes[1])
        assertEquals(500L, kaitenGateway.lastRequestedBoardId)
    }

    private class FakeKaitenTasksGateway(
        private val tasksById: Map<Long, KaitenTask> = emptyMap(),
        private val tasksByBoard: Map<Long, List<KaitenTask>> = emptyMap(),
        private val failingIds: Set<Long> = emptySet(),
    ) : KaitenTasksGateway {

        var lastRequestedBoardId: Long? = null

        override suspend fun getTaskById(
            apiUrl: String,
            apiToken: String,
            taskId: Long,
            defaultSpaceId: String?,
        ): KaitenTask {
            if (failingIds.contains(taskId)) {
                error("Task $taskId is unavailable")
            }
            return tasksById[taskId] ?: error("Task $taskId was not configured")
        }

        override suspend fun getBoardTasks(
            apiUrl: String,
            apiToken: String,
            boardId: Long,
            defaultSpaceId: String?,
        ): List<KaitenTask> {
            lastRequestedBoardId = boardId
            return tasksByBoard[boardId] ?: emptyList()
        }
    }

    private class FakeNotionTasksGateway(
        private val existingTasks: MutableList<NotionTask>,
        private val failingIds: Set<String> = emptySet(),
    ) : NotionTasksGateway {

        val addedTasks = mutableListOf<KaitenTask>()

        override suspend fun getExistingTasks(
            databaseInput: String,
            notionToken: String,
        ): List<NotionTask> {
            return existingTasks.toList()
        }

        override suspend fun addTask(
            databaseInput: String,
            notionToken: String,
            task: KaitenTask,
        ) {
            if (failingIds.contains(task.id.toString())) {
                error("Cannot add task ${task.id}")
            }
            addedTasks += task
            existingTasks += NotionTask(id = task.id.toString(), title = task.title)
        }
    }
}
