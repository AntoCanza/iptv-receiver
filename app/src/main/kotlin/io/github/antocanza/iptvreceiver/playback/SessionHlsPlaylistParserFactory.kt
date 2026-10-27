package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import io.github.antocanza.iptvreceiver.data.LiveManifestSession

@androidx.annotation.OptIn(UnstableApi::class)
internal class SessionHlsPlaylistParserFactory(private val session: LiveManifestSession) : HlsPlaylistParserFactory {
    private val delegate = DefaultHlsPlaylistParserFactory()
    override fun createPlaylistParser() = observe(delegate.createPlaylistParser())
    override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist, previousMediaPlaylist: HlsMediaPlaylist?) =
        observe(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))

    private fun observe(parser: ParsingLoadable.Parser<HlsPlaylist>) = ParsingLoadable.Parser<HlsPlaylist> { uri, input ->
        parser.parse(uri, input).also { session.parsed(uri.toString(), it is HlsMediaPlaylist) }
    }
}
