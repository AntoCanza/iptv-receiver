package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.ReceiverApplication
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.artworkUrls

@Composable
internal fun CatalogArtwork(item: CatalogItem, allowHttp: Boolean, modifier: Modifier = Modifier, visible: Boolean = true) {
    val context = LocalContext.current
    val application = context.applicationContext as ReceiverApplication
    val candidates = remember(item) { item.artworkUrls }
    var candidateIndex by remember(item, allowHttp) { mutableIntStateOf(0) }
    val current = candidates.getOrNull(candidateIndex)
    val placeholder = painterResource(R.drawable.artwork_placeholder)
    var artworkInViewport by remember(item) { mutableStateOf(false) }
    val bounds = modifier.fillMaxWidth().aspectRatio(if (item.type == ContentType.LIVE) 16f / 9 else 2f / 3)
        .onGloballyPositioned { coordinates ->
            val clipped = coordinates.boundsInWindow()
            artworkInViewport = clipped.width > 0f && clipped.height > 0f
        }
    if (!visible || !artworkInViewport) {
        // Lazy layouts may precompose off-screen cells; do not create a Coil request for those cells.
        Image(placeholder, contentDescription = null, modifier = bounds, contentScale = ContentScale.Crop)
        return
    }
    val request = remember(current, context) { ImageRequest.Builder(context).data(current).crossfade(true).build() }
    AsyncImage(model = request, imageLoader = application.artworkLoaderFor(allowHttp), contentDescription = null,
        placeholder = placeholder, error = placeholder, fallback = placeholder,
        modifier = bounds,
        contentScale = if (item.type == ContentType.LIVE) ContentScale.Fit else ContentScale.Crop,
        onError = { if (candidateIndex < candidates.lastIndex && current == candidates[candidateIndex]) candidateIndex++ })
}
