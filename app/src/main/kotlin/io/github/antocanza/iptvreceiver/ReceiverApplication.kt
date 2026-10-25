package io.github.antocanza.iptvreceiver

import android.app.Application
import io.github.antocanza.iptvreceiver.data.ConnectionStore
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.provider.XtreamCatalogRepository

class ReceiverApplication : Application() {
    val repository: CatalogRepository by lazy { XtreamCatalogRepository() }
    val connectionStore by lazy { ConnectionStore(this) }
}
