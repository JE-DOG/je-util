package dag.je_dog.data.notion_daily_report

import dag.je_dog.common.logger.AppLogger
import dag.je_dog.domain.notion_daily_report.DailySections
import dag.je_dog.domain.notion_daily_report.NotionDailyReportRepository
import org.jraf.klibnotion.client.NotionClient
import org.jraf.klibnotion.model.block.*
import org.jraf.klibnotion.model.page.Page
import org.jraf.klibnotion.model.pagination.Pagination
import org.jraf.klibnotion.model.property.spec.DatePropertySpec
import org.jraf.klibnotion.model.property.value.DatePropertyValue
import org.jraf.klibnotion.model.property.value.TitlePropertyValue
import org.jraf.klibnotion.model.property.value.UrlPropertyValue
import org.jraf.klibnotion.model.richtext.PageMentionRichText
import org.jraf.klibnotion.model.richtext.RichText
import org.jraf.klibnotion.model.richtext.RichTextList
import java.time.LocalDate
import java.time.ZoneId

class NotionDailyReportRepositoryImpl(
    private val notionClientFactory: NotionClientFactory,
) : NotionDailyReportRepository {

    override suspend fun loadDailySections(
        rootPageInput: String,
        notionToken: String,
        currentDate: LocalDate,
        previousWorkdayDate: LocalDate,
    ): DailySections {
        val normalizedRootPageId = runCatching { normalizeNotionId(rootPageInput) }
            .onFailure { throwable ->
                AppLogger.e(
                    message = "Failed to parse Notion page id from input: $rootPageInput",
                    throwable = throwable,
                )
            }
            .getOrNull() ?: return EMPTY_SECTIONS

        val notionClient = notionClientFactory.create(accessToken = notionToken.trim())
        return try {
            val cache = mutableMapOf<String, LinkedPageData>()
            val calendarDatabaseId = findFirstCalendarDatabaseId(
                notionClient = notionClient,
                rootPageId = normalizedRootPageId,
            ) ?: return EMPTY_SECTIONS

            val calendarPages = loadAllDatabasePages(
                notionClient = notionClient,
                databaseId = calendarDatabaseId,
            )
            val calendarDatabase = notionClient.databases.getDatabase(id = calendarDatabaseId)
            val dateProperty = calendarDatabase.propertySpecs
                .filterIsInstance<DatePropertySpec>()
                .firstOrNull()

            val currentDayPage = findPageByDate(
                pages = calendarPages,
                targetDate = currentDate,
                datePropertyIdOrName = dateProperty?.let { property -> setOf(property.id, property.name) },
            )

            val previousDayPage = findPageByDate(
                pages = calendarPages,
                targetDate = previousWorkdayDate,
                datePropertyIdOrName = dateProperty?.let { property -> setOf(property.id, property.name) },
            )

            val currentExpectedLines = currentDayPage?.let { page ->
                extractSectionLines(
                    notionClient = notionClient,
                    pageId = page.id,
                    sectionHeading = SECTION_EXPECTED,
                    linkedPageCache = cache,
                )
            } ?: emptyList()

            val previousActualLines = previousDayPage?.let { page ->
                extractSectionLines(
                    notionClient = notionClient,
                    pageId = page.id,
                    sectionHeading = SECTION_ACTUAL,
                    linkedPageCache = cache,
                )
            } ?: emptyList()

            DailySections(
                previousDayActualLines = previousActualLines,
                currentDayExpectedLines = currentExpectedLines,
            )
        } catch (throwable: Throwable) {
            AppLogger.e(
                message = "Failed to load Notion daily sections.",
                throwable = throwable,
            )
            EMPTY_SECTIONS
        } finally {
            runCatching { notionClient.close() }
        }
    }

    private suspend fun findFirstCalendarDatabaseId(
        notionClient: NotionClient,
        rootPageId: String,
    ): String? {
        val allBlocks = notionClient.blocks.getAllBlockListRecursively(parentId = rootPageId)
        val databaseBlocks = allBlocks.filterIsInstance<ChildDatabaseBlock>()
        if (databaseBlocks.isEmpty()) {
            AppLogger.e("No child database found in the provided Notion page content.")
            return null
        }
        val calendarBlock = databaseBlocks.first()

        return calendarBlock.id
    }

    private suspend fun loadAllDatabasePages(
        notionClient: NotionClient,
        databaseId: String,
    ): List<Page> {
        val result = mutableListOf<Page>()
        var pagination = Pagination()
        while (true) {
            val page = notionClient.databases.queryDatabase(
                id = databaseId,
                pagination = pagination,
            )
            result += page.results
            val next = page.nextPagination ?: break
            pagination = next
        }
        return result
    }

    private fun findPageByDate(
        pages: List<Page>,
        targetDate: LocalDate,
        datePropertyIdOrName: Set<String>?,
    ): Page? {
        val matching = pages.firstOrNull { page ->
            extractPageDate(
                page = page,
                datePropertyIdOrName = datePropertyIdOrName,
            ) == targetDate
        }
        if (matching == null) {
            AppLogger.w("No Notion page found in calendar for date: $targetDate")
        }
        return matching
    }

    private fun extractPageDate(
        page: Page,
        datePropertyIdOrName: Set<String>?,
    ): LocalDate? {
        val dateProperty = page.propertyValues
            .filterIsInstance<DatePropertyValue>()
            .firstOrNull { property ->
                datePropertyIdOrName == null ||
                        datePropertyIdOrName.contains(property.id) ||
                        datePropertyIdOrName.contains(property.name)
            } ?: return null

        val timestamp = dateProperty.value?.start?.timestamp ?: return null
        return timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private suspend fun extractSectionLines(
        notionClient: NotionClient,
        pageId: String,
        sectionHeading: String,
        linkedPageCache: MutableMap<String, LinkedPageData>,
    ): List<String> {
        val topLevelBlocks = loadTopLevelBlocks(
            notionClient = notionClient,
            pageId = pageId,
        )

        val sectionBlocks = findSectionBlocks(
            topLevelBlocks = topLevelBlocks,
            sectionHeading = sectionHeading,
        )

        if (sectionBlocks.isEmpty()) {
            AppLogger.w("Section \"$sectionHeading\" is missing or empty on page $pageId")
            return emptyList()
        }

        return sectionBlocks
            .flatMap { block ->
                blockToLines(
                    notionClient = notionClient,
                    block = block,
                    linkedPageCache = linkedPageCache,
                )
            }
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
    }

    private suspend fun loadTopLevelBlocks(
        notionClient: NotionClient,
        pageId: String,
    ): List<Block> {
        val result = mutableListOf<Block>()
        var pagination = Pagination()
        while (true) {
            val page = notionClient.blocks.getBlockList(
                parentId = pageId,
                pagination = pagination,
            )
            result += page.results
            val next = page.nextPagination ?: break
            pagination = next
        }
        return result
    }

    private fun findSectionBlocks(
        topLevelBlocks: List<Block>,
        sectionHeading: String,
    ): List<Block> {
        val headingIndex = topLevelBlocks.indexOfFirst { block ->
            isHeadingWithText(block = block, expected = sectionHeading)
        }
        if (headingIndex == -1) return emptyList()

        var index = headingIndex + 1
        if (index < topLevelBlocks.size && topLevelBlocks[index] is DividerBlock) {
            index += 1
        }

        val result = mutableListOf<Block>()
        while (index < topLevelBlocks.size) {
            val block = topLevelBlocks[index]
            if (block is DividerBlock || block is Heading1Block || block is Heading2Block || block is Heading3Block) {
                break
            }
            result += block
            index += 1
        }
        return result
    }

    private fun isHeadingWithText(
        block: Block,
        expected: String,
    ): Boolean {
        val text = when (block) {
            is Heading1Block -> block.text?.plainText
            is Heading2Block -> block.text?.plainText
            is Heading3Block -> block.text?.plainText
            else -> null
        }
        return text?.trim()?.equals(expected, ignoreCase = true) == true
    }

    private suspend fun blockToLines(
        notionClient: NotionClient,
        block: Block,
        linkedPageCache: MutableMap<String, LinkedPageData>,
    ): List<String> {
        return when (block) {
            is ChildPageBlock -> listOf(
                resolveLinkedPageText(
                    notionClient = notionClient,
                    pageId = block.id,
                    linkedPageCache = linkedPageCache,
                    fallbackTitle = block.title,
                ),
            )

            is BulletedListItemBlock -> toTextLine(
                richText = block.text,
                notionClient = notionClient,
                linkedPageCache = linkedPageCache,
                prefix = "- ",
            )

            is NumberedListItemBlock -> toTextLine(
                richText = block.text,
                notionClient = notionClient,
                linkedPageCache = linkedPageCache,
                prefix = "1. ",
            )

            is ToDoBlock -> toTextLine(
                richText = block.text,
                notionClient = notionClient,
                linkedPageCache = linkedPageCache,
                prefix = if (block.checked) "- [x] " else "- [ ] ",
            )

            is ParagraphBlock -> toTextLine(block.text, notionClient, linkedPageCache)
            is QuoteBlock -> toTextLine(block.text, notionClient, linkedPageCache, prefix = "> ")
            is CalloutBlock -> toTextLine(block.text, notionClient, linkedPageCache)
            is ToggleBlock -> toTextLine(block.text, notionClient, linkedPageCache)
            is Heading1Block -> toTextLine(block.text, notionClient, linkedPageCache)
            is Heading2Block -> toTextLine(block.text, notionClient, linkedPageCache)
            is Heading3Block -> toTextLine(block.text, notionClient, linkedPageCache)
            is CodeBlock -> toTextLine(block.text, notionClient, linkedPageCache)
            is BookmarkBlock -> listOf(block.url)
            is EmbedBlock -> listOf(block.url)
            is EquationBlock -> listOf(block.expression)
            is ImageBlock -> toTextLine(block.caption, notionClient, linkedPageCache)
            is VideoBlock -> toTextLine(block.caption, notionClient, linkedPageCache)
            else -> emptyList()
        }
    }

    private suspend fun toTextLine(
        richText: RichTextList?,
        notionClient: NotionClient,
        linkedPageCache: MutableMap<String, LinkedPageData>,
        prefix: String = "",
    ): List<String> {
        val text = richText?.let {
            richTextToPlainText(
                richTextList = it.richTextList,
                notionClient = notionClient,
                linkedPageCache = linkedPageCache,
            )
        }.orEmpty().trim()
        if (text.isBlank()) return emptyList()
        return listOf(prefix + text)
    }

    private suspend fun richTextToPlainText(
        richTextList: List<RichText>,
        notionClient: NotionClient,
        linkedPageCache: MutableMap<String, LinkedPageData>,
    ): String {
        return buildString {
            richTextList.forEach { richText ->
                when (richText) {
                    is PageMentionRichText -> append(
                        resolveLinkedPageText(
                            notionClient = notionClient,
                            pageId = richText.pageId,
                            linkedPageCache = linkedPageCache,
                        ),
                    )

                    else -> append(richText.plainText)
                }
            }
        }
    }

    private suspend fun resolveLinkedPageText(
        notionClient: NotionClient,
        pageId: String,
        linkedPageCache: MutableMap<String, LinkedPageData>,
        fallbackTitle: String = "Untitled",
    ): String {
        val cached = linkedPageCache[pageId]
        if (cached != null) {
            return if (cached.link.isNullOrBlank()) {
                cached.title
            } else {
                "[${cached.title}](${cached.link})"
            }
        }

        val page = runCatching { notionClient.pages.getPage(id = pageId) }
            .onFailure { throwable ->
                AppLogger.e(
                    message = "Failed to resolve linked Notion page: $pageId",
                    throwable = throwable,
                )
            }
            .getOrNull()

        if (page == null) {
            linkedPageCache[pageId] = LinkedPageData(
                title = fallbackTitle,
                link = null,
            )
            return fallbackTitle
        }

        val title = page.propertyValues
            .filterIsInstance<TitlePropertyValue>()
            .firstOrNull()
            ?.value
            ?.plainText
            ?.takeIf { text -> text.isNotBlank() }
            ?: fallbackTitle

        val link = page.propertyValues
            .filterIsInstance<UrlPropertyValue>()
            .firstOrNull { property -> property.name.equals(LINK_PROPERTY_NAME, ignoreCase = true) }
            ?.value

        if (link.isNullOrBlank()) {
            AppLogger.w("Linked page \"$title\" has no \"$LINK_PROPERTY_NAME\" property value.")
        }

        linkedPageCache[pageId] = LinkedPageData(
            title = title,
            link = link,
        )

        return if (link.isNullOrBlank()) {
            title
        } else {
            "[$title]($link)"
        }
    }

    private data class LinkedPageData(
        val title: String,
        val link: String?,
    )

    private companion object {
        private const val SECTION_EXPECTED = "Expected"
        private const val SECTION_ACTUAL = "Actual"
        private const val LINK_PROPERTY_NAME = "link"

        private val EMPTY_SECTIONS = DailySections(
            previousDayActualLines = emptyList(),
            currentDayExpectedLines = emptyList(),
        )

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
