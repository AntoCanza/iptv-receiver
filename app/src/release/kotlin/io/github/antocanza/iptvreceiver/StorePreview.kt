package io.github.antocanza.iptvreceiver

import android.content.Intent
import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
import io.github.antocanza.iptvreceiver.model.CatalogRepository

/** Production builds never accept screenshot fixtures or alternate accounts. */
internal object StorePreview {
    fun create(@Suppress("UNUSED_PARAMETER") intent: Intent?): Pair<CatalogRepository, SavedConnectionStore>? = null
}
