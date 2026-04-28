package dag.je_dog.presentation.pull_kaiten_tasks_to_notion.di

import dag.je_dog.data.notion_daily_report.NotionClientFactory
import dag.je_dog.data.pull_kaiten_tasks_to_notion.KaitenTasksGatewayImpl
import dag.je_dog.data.pull_kaiten_tasks_to_notion.NotionTasksGatewayImpl
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.KaitenTasksGateway
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.NotionTasksGateway
import dag.je_dog.domain.pull_kaiten_tasks_to_notion.PullKaitenTasksToNotionUseCase

object PullKaitenTasksToNotionComponent {

    val notionClientFactory: NotionClientFactory by lazy { NotionClientFactory() }

    val kaitenTasksGateway: KaitenTasksGateway by lazy { KaitenTasksGatewayImpl() }
    val notionTasksGateway: NotionTasksGateway by lazy { NotionTasksGatewayImpl(notionClientFactory) }

    val pullKaitenTasksToNotionUseCase: PullKaitenTasksToNotionUseCase by lazy {
        PullKaitenTasksToNotionUseCase(
            kaitenTasksGateway = kaitenTasksGateway,
            notionTasksGateway = notionTasksGateway,
        )
    }
}
