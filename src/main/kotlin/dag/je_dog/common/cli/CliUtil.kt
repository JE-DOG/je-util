package dag.je_dog.common.cli

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

object CliUtil {

    fun askYesNo(
        question: String,
        defaultValue: Boolean = false,
    ): Boolean {
        val defaultHint = if (defaultValue) " [Y/n]: " else " [y/N]: "
        while (true) {
            print(question + defaultHint)
            val answer = readUserInput().trim().lowercase()
            if (answer.isEmpty()) return defaultValue
            if (answer in YES_ANSWERS) return true
            if (answer in NO_ANSWERS) return false
            println("Некорректный ответ. Используйте y/yes или n/no.")
        }
    }

    fun readUserInput(): String {
        val reader = BufferedReader(InputStreamReader(System.`in`, Charset.defaultCharset()))
        return reader.readLine().orEmpty()
    }

    private val YES_ANSWERS = setOf("y", "yes", "д", "да")
    private val NO_ANSWERS = setOf("n", "no", "н", "нет")
}
