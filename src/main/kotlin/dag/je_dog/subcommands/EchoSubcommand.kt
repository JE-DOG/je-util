package dag.je_dog.subcommands

import dag.je_dog.common.logger.AppLogger
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default

@OptIn(ExperimentalCli::class)
class EchoSubcommand : Subcommand(
    name = "echo",
    actionDescription = "Basic command to validate CLI wiring",
) {

    private val text by option(
        type = ArgType.String,
        fullName = "text",
        shortName = "t",
        description = "Text to print",
    ).default("hello from je-util")

    override fun execute() {
        AppLogger.i("Выполнение команды echo")
        println(text)
    }
}
