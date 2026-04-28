package dag.je_dog.data.pull_kaiten_tasks_to_notion

import dag.je_dog.data.notion_daily_report.NotionClientFactory
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.KaitenTask
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.NotionTask
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.NotionTasksGateway
import org.jraf.klibnotion.client.NotionClient
import org.jraf.klibnotion.model.base.reference.DatabaseReference
import org.jraf.klibnotion.model.page.Page
import org.jraf.klibnotion.model.pagination.Pagination
import org.jraf.klibnotion.model.property.spec.*
import org.jraf.klibnotion.model.property.value.*

class NotionTasksGatewayImpl(
    private val notionClientFactory: NotionClientFactory,
) : NotionTasksGateway {

    override suspend fun getExistingTasks(
        databaseInput: String,
        notionToken: String,
    ): List<NotionTask> {
        return withNotionClient(notionToken) { notionClient ->
            val schema = resolveSchema(
                notionClient = notionClient,
                databaseInput = databaseInput,
            )

            loadAllPages(
                notionClient = notionClient,
                databaseId = schema.databaseId,
            ).mapNotNull { page ->
                val id = extractPropertyValue(
                    page = page,
                    propertySpec = schema.idProperty,
                )?.trim().orEmpty()
                val title = extractPropertyValue(
                    page = page,
                    propertySpec = schema.titleProperty,
                )?.trim().orEmpty()

                if (id.isBlank() || title.isBlank()) {
                    null
                } else {
                    NotionTask(
                        id = id,
                        title = title,
                    )
                }
            }
        }
    }

    override suspend fun addTask(
        databaseInput: String,
        notionToken: String,
        task: KaitenTask,
    ) {
        withNotionClient(notionToken) { notionClient ->
            val schema = resolveSchema(
                notionClient = notionClient,
                databaseInput = databaseInput,
            )

            val properties = PropertyValueList()
                .title(
                    idOrName = schema.titleProperty.name,
                    text = task.title,
                )

            applyIdProperty(
                properties = properties,
                idProperty = schema.idProperty,
                taskId = task.id,
            )
            applyLinkProperty(
                properties = properties,
                linkProperty = schema.linkProperty,
                taskLink = task.link,
            )
            applyPriorityProperty(
                properties = properties,
                priorityProperty = schema.priorityProperty,
            )

            notionClient.pages.createPage(
                parentDatabase = DatabaseReference(id = schema.databaseId),
                properties = properties,
            )
        }
    }

    private suspend fun <T> withNotionClient(
        notionToken: String,
        block: suspend (NotionClient) -> T,
    ): T {
        val notionClient = notionClientFactory.create(accessToken = notionToken.trim())
        return try {
            block(notionClient)
        } finally {
            runCatching { notionClient.close() }
        }
    }

    private suspend fun resolveSchema(
        notionClient: NotionClient,
        databaseInput: String,
    ): NotionDatabaseSchema {
        val databaseId = normalizeNotionId(databaseInput)
        val database = notionClient.databases.getDatabase(id = databaseId)

        val titleProperty = database.propertySpecs
            .filterIsInstance<TitlePropertySpec>()
            .firstOrNull()
            ?: error("Notion database has no title property.")

        val idProperty = findProperty(
            propertySpecs = database.propertySpecs,
            propertyName = PROPERTY_ID,
        ) ?: error("Notion database has no '$PROPERTY_ID' property.")

        val linkProperty = findProperty(
            propertySpecs = database.propertySpecs,
            propertyName = PROPERTY_LINK,
        ) ?: error("Notion database has no '$PROPERTY_LINK' property.")

        val priorityProperty = findProperty(
            propertySpecs = database.propertySpecs,
            propertyName = PROPERTY_PRIORITY,
        ) ?: error("Notion database has no '$PROPERTY_PRIORITY' property.")

        return NotionDatabaseSchema(
            databaseId = databaseId,
            titleProperty = titleProperty,
            idProperty = idProperty,
            linkProperty = linkProperty,
            priorityProperty = priorityProperty,
        )
    }

    private fun findProperty(
        propertySpecs: List<PropertySpec>,
        propertyName: String,
    ): PropertySpec? {
        return propertySpecs.firstOrNull { propertySpec ->
            propertySpec.id.equals(propertyName, ignoreCase = true) ||
                    propertySpec.name.equals(propertyName, ignoreCase = true)
        }
    }

    private suspend fun loadAllPages(
        notionClient: NotionClient,
        databaseId: String,
    ): List<Page> {
        val pages = mutableListOf<Page>()
        var pagination = Pagination()

        while (true) {
            val page = notionClient.databases.queryDatabase(
                id = databaseId,
                pagination = pagination,
            )
            pages += page.results

            val nextPagination = page.nextPagination ?: break
            pagination = nextPagination
        }

        return pages
    }

    private fun extractPropertyValue(
        page: Page,
        propertySpec: PropertySpec,
    ): String? {
        val propertyValue = page.propertyValues.firstOrNull { propertyValue ->
            isMatchingProperty(
                propertyValue = propertyValue,
                propertySpec = propertySpec,
            )
        } ?: return null

        return when (propertyValue) {
            is TitlePropertyValue -> propertyValue.value.plainText
            is RichTextPropertyValue -> propertyValue.value.plainText
            is NumberPropertyValue -> propertyValue.value?.toStableString()
            is UrlPropertyValue -> propertyValue.value
            is SelectPropertyValue -> propertyValue.value?.name
            is MultiSelectPropertyValue -> propertyValue.value
                .joinToString(separator = ",") { option -> option.name }
                .ifBlank { null }

            else -> null
        }
    }

    private fun isMatchingProperty(
        propertyValue: PropertyValue<*>,
        propertySpec: PropertySpec,
    ): Boolean {
        return propertyValue.id.equals(propertySpec.id, ignoreCase = true) ||
                propertyValue.name.equals(propertySpec.name, ignoreCase = true)
    }

    private fun applyIdProperty(
        properties: PropertyValueList,
        idProperty: PropertySpec,
        taskId: Long,
    ) {
        val taskIdValue = taskId.toString()
        when (idProperty) {
            is NumberPropertySpec -> properties.number(idProperty.name, taskId)
            is RichTextPropertySpec -> properties.text(idProperty.name, taskIdValue)
            is TitlePropertySpec -> properties.title(idProperty.name, taskIdValue)
            is UrlPropertySpec -> properties.url(idProperty.name, taskIdValue)
            else -> error(
                "Unsupported property type for '$PROPERTY_ID': ${idProperty::class.simpleName}.",
            )
        }
    }

    private fun applyLinkProperty(
        properties: PropertyValueList,
        linkProperty: PropertySpec,
        taskLink: String,
    ) {
        when (linkProperty) {
            is UrlPropertySpec -> properties.url(linkProperty.name, taskLink)
            is RichTextPropertySpec -> properties.text(linkProperty.name, taskLink)
            is TitlePropertySpec -> properties.title(linkProperty.name, taskLink)
            else -> error(
                "Unsupported property type for '$PROPERTY_LINK': ${linkProperty::class.simpleName}.",
            )
        }
    }

    private fun applyPriorityProperty(
        properties: PropertyValueList,
        priorityProperty: PropertySpec,
    ) {
        when (priorityProperty) {
            is SelectPropertySpec -> properties.selectByName(priorityProperty.name, PRIORITY_NORMAL)
            is MultiSelectPropertySpec -> properties.multiSelectByNames(priorityProperty.name, PRIORITY_NORMAL)
            is RichTextPropertySpec -> properties.text(priorityProperty.name, PRIORITY_NORMAL)
            is TitlePropertySpec -> properties.title(priorityProperty.name, PRIORITY_NORMAL)
            else -> error(
                "Unsupported property type for '$PROPERTY_PRIORITY': ${priorityProperty::class.simpleName}.",
            )
        }
    }

    private fun Number.toStableString(): String {
        return toString().removeSuffix(".0")
    }

    private data class NotionDatabaseSchema(
        val databaseId: String,
        val titleProperty: TitlePropertySpec,
        val idProperty: PropertySpec,
        val linkProperty: PropertySpec,
        val priorityProperty: PropertySpec,
    )

    private companion object {
        private const val PROPERTY_ID = "id"
        private const val PROPERTY_LINK = "link"
        private const val PROPERTY_PRIORITY = "Priority"
        private const val PRIORITY_NORMAL = "Normal"

        private val UUID_WITH_DASHES_REGEX =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val UUID_NO_DASHES_REGEX = Regex("[0-9a-fA-F]{32}")

        private fun normalizeNotionId(input: String): String {
            val trimmed = input.trim()
            UUID_WITH_DASHES_REGEX.find(trimmed)?.value?.let { return it.lowercase() }

            val noDashes = UUID_NO_DASHES_REGEX.find(trimmed)?.value
                ?: error("Notion id was not found in input: $input")

            return buildString {
                append(noDashes.substring(0, 8))
                append('-')
                append(noDashes.substring(8, 12))
                append('-')
                append(noDashes.substring(12, 16))
                append('-')
                append(noDashes.substring(16, 20))
                append('-')
                append(noDashes.substring(20, 32))
            }.lowercase()
        }
    }
}
