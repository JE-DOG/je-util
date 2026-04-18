package dag.je_dog.common.logger

object AppLogger {

    private val logger: System.Logger by lazy {
        System.getLogger(LOGGER_NAME)
    }

    fun i(message: String) {
        logger.log(System.Logger.Level.INFO, message)
    }

    fun w(message: String) {
        logger.log(System.Logger.Level.WARNING, message)
    }

    fun e(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            logger.log(System.Logger.Level.ERROR, message)
        } else {
            logger.log(System.Logger.Level.ERROR, message, throwable)
        }
    }

    private const val LOGGER_NAME = "JeUtilLogger"
}
