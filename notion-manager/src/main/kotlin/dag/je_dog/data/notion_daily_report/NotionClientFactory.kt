package dag.je_dog.data.notion_daily_report

import org.jraf.klibnotion.client.Authentication
import org.jraf.klibnotion.client.ClientConfiguration
import org.jraf.klibnotion.client.NotionClient

class NotionClientFactory {

    fun create(accessToken: String): NotionClient {
        return NotionClient.newInstance(
            configuration = ClientConfiguration(
                authentication = Authentication(accessToken = accessToken),
            ),
        )
    }
}

