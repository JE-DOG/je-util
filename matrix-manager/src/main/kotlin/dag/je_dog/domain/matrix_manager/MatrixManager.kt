package dag.je_dog.domain.matrix_manager

interface MatrixManager {

    suspend fun sendMessage(
        roomId: String,
        message: String,
        accessToken: String,
        homeServerUrl: String,
    ): Result<String>
}
