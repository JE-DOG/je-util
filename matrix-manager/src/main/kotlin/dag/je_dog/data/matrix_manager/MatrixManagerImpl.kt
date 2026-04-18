package dag.je_dog.data.matrix_manager

import dag.je_dog.domain.matrix_manager.MatrixManager
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.createCryptoModule
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.store.repository.inMemory
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.classic
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import io.ktor.client.engine.okhttp.*
import io.ktor.http.*
import org.koin.dsl.module

class MatrixManagerImpl : MatrixManager {

    override suspend fun sendMessage(
        roomId: String,
        message: String,
        accessToken: String,
        homeServerUrl: String,
    ): Result<String> {
        if (roomId.isBlank()) {
            return Result.failure(IllegalArgumentException("Matrix room id is blank."))
        }
        if (message.isBlank()) {
            return Result.failure(IllegalArgumentException("Matrix message is blank."))
        }
        if (accessToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Matrix access token is blank."))
        }
        if (homeServerUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Matrix home server url is blank."))
        }

        val normalizedRoomId = roomId.trim()
        val normalizedToken = accessToken.trim()
        val normalizedHomeServerUrl = homeServerUrl.trim()

        val repositoriesModule = RepositoriesModule.inMemory()
        val mediaStoreModule = MediaStoreModule.inMemory()
        val cryptoDriverModule = CryptoDriverModule { createCryptoModule() }
        val authProviderData = MatrixClientAuthProviderData.classic(
            baseUrl = Url(normalizedHomeServerUrl),
            accessToken = normalizedToken,
        )

        val matrixClient = MatrixClient.create(
            repositoriesModule = repositoriesModule,
            mediaStoreModule = mediaStoreModule,
            cryptoDriverModule = cryptoDriverModule,
            authProviderData = authProviderData,
        ) {
            this.httpClientEngine = OkHttp.create()
            modulesFactories = modulesFactories + listOf {
                module {
                    single<CryptoDriver> { LibOlmCryptoDriver }
                }
            }
        }
            .getOrThrow()

        return runCatching {
            matrixClient.startSync()

            matrixClient.api.room.sendMessageEvent(
                roomId = RoomId(normalizedRoomId),
                eventContent = RoomMessageEventContent.TextBased.Text(
                    body = message,
                ),
            )
                .getOrNull()
                ?.full.orEmpty()
        }
            .onFailure {
                return Result.failure(
                    exception = RuntimeException(
                        "Failed send message to Matrix",
                        it,
                    )
                )
            }
    }
}
