package dag.je_dog.presentation.notion_daily_report

import dag.je_dog.data.matrix_manager.MatrixManagerImpl
import dag.je_dog.data.notion_daily_report.NotionClientFactory
import dag.je_dog.data.notion_daily_report.NotionDailyReportRepositoryImpl
import dag.je_dog.domain.matrix_manager.MatrixManager
import dag.je_dog.domain.notion_daily_report.GenerateNotionDailyReportUseCase
import dag.je_dog.domain.notion_daily_report.WorkdayResolver

object NotionDailyReportComponent {

    val notionClientFactory: NotionClientFactory by lazy { NotionClientFactory() }
    val repository: NotionDailyReportRepositoryImpl by lazy { NotionDailyReportRepositoryImpl(notionClientFactory) }
    val workdayResolver: WorkdayResolver by lazy { WorkdayResolver() }
    val matrixManager: MatrixManager by lazy { MatrixManagerImpl() }

    val generateNotionDailyReportUseCase: GenerateNotionDailyReportUseCase by lazy {
        GenerateNotionDailyReportUseCase(
            repository = repository,
            workdayResolver = workdayResolver,
        )
    }
}
