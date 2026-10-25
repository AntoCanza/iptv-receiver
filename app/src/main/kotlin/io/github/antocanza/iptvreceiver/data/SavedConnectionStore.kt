package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.ProviderConnection

interface SavedConnectionStore {
    fun save(connection: ProviderConnection)
    fun load(): ProviderConnection?
    fun clear()
}
