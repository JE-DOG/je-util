package dag.je_dog

import dag.je_dog.presentation.notion_daily_report.NotionDailyReportSubcommand
import dag.je_dog.presentation.pull_kaiten_tasks_to_notion.PullKaitenTasksToNotionSubcommand
import dag.je_dog.subcommands.EchoSubcommand
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser(programName = "je-util")
    parser.subcommands(
        EchoSubcommand(),
        NotionDailyReportSubcommand(),
        PullKaitenTasksToNotionSubcommand(),
    )
    parser.parse(args)
}
