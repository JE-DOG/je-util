package dag.je_dog.data.pull_kaiten_tasks_to_notion

import dag.je_dog.domain.pull_kaiten_tasks_to_notion.NotionTask
import org.jraf.klibnotion.model.base.reference.Reference
import org.jraf.klibnotion.model.base.reference.WorkspaceReference
import org.jraf.klibnotion.model.date.Timestamp
import org.jraf.klibnotion.model.file.File
import org.jraf.klibnotion.model.page.Page
import org.jraf.klibnotion.model.property.value.PropertyValue
import org.jraf.klibnotion.model.property.value.PropertyValueList
import kotlin.test.Test
import kotlin.test.assertEquals

class NotionTasksGatewayImplTest {

    @Test
    fun `keeps existing task when title matches even if id is blank`() {
        val page = FakePage(
            propertyValues = PropertyValueList()
                .text("id", "")
                .title("Name", "Task A")
                .getAll(),
        )

        assertEquals(
            NotionTask(id = "", title = "Task A"),
            extractExistingTask(
                page = page,
                idPropertyNames = setOf("id"),
                titlePropertyNames = setOf("Name"),
            ),
        )
    }

    private class FakePage(
        override val propertyValues: List<PropertyValue<*>>,
    ) : Page {

        override val id: String = "page-id"
        override val parent: Reference = WorkspaceReference
        override val archived: Boolean = false
        override val created: Timestamp
            get() = error("Not used in this test")
        override val lastEdited: Timestamp
            get() = error("Not used in this test")
        override val url: String = "https://notion.so/page-id"
        override val icon: org.jraf.klibnotion.model.base.EmojiOrFile? = null
        override val cover: File? = null
    }
}
