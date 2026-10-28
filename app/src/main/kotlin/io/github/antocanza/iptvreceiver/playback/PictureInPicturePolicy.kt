package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player

/** A buffering/reconnecting video still represents the user's intent to keep watching. */
internal fun shouldAutoEnterPictureInPicture(
    playWhenReady: Boolean,
    playbackState: Int,
    suppressed: Boolean,
    recovery: PlaybackRecoveryState,
): Boolean = playWhenReady && !suppressed && recovery != PlaybackRecoveryState.FAILED &&
    (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING ||
        recovery == PlaybackRecoveryState.RECONNECTING)

internal fun showPictureInPicturePause(playWhenReady: Boolean, playbackState: Int,
    hasError: Boolean, recovery: PlaybackRecoveryState): Boolean = playWhenReady &&
    recovery != PlaybackRecoveryState.FAILED && (recovery == PlaybackRecoveryState.RECONNECTING ||
        (!hasError && playbackState != Player.STATE_ENDED))

/** Android accepts only a bounded ratio, including when a stream reports invalid dimensions. */
internal fun pictureInPictureAspect(width: Int, height: Int, pixelRatio: Float, override: Float?): Int {
    val source = override ?: if (width > 0 && height > 0) width.toFloat() * pixelRatio / height else 16f / 9
    val ratio = if (source.isFinite() && source > 0) source else 16f / 9
    return (ratio * 10_000).toInt().coerceIn(4_185, 23_900)
}
