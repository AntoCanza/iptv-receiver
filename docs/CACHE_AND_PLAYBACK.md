# Playback and artwork lifecycle

## Rotation and fullscreen

Each player navigation entry owns one PlaybackViewModel and ExoPlayer, built with the application context. Preparing the stream happens once when that ViewModel is created. Activity recreation detaches and reattaches PlayerView without preparing or releasing the player, preserving position and play/pause state. Leaving the player releases it; backgrounding pauses it. This is session continuity, not durable resume after process death.

Landscape automatically hides the header and system bars. The player fullscreen button can request landscape; exit/Back releases that request and returns to the device's orientation policy. Synchronizing Media3's icon temporarily disconnects its listener, because Media3 also emits the fullscreen callback for programmatic state changes.

## Live TV recovery

Live channels use the provider's `PlaybackSource.isLive` flag to enable recovery. When advertised by the provider, live playback prefers HLS (`m3u8`) over a progressive TS connection; TS remains the fallback. Format names are normalized before preference selection. HLS provides a rolling segmented stream rather than a sequence of ended TS media items.

Live playback requests 20 seconds before starting or resuming after rebuffering, a 30-second live offset, and a 60–90 second loading window with a 64 MiB buffer target. The byte target takes priority over duration, so high-bitrate streams remain bounded. These are in-memory targets, not a downloaded recording. Starting a channel can take longer and playback runs farther behind the broadcast; the provider's throughput, timestamps and HLS live window also affect the actual available buffer.

Live HTTP connect/read timeouts are five seconds. Temporary loader failures can retry up to ten attempts, with delays capped at 1.5 seconds, while already-buffered samples remain usable. Invalid schemes, TLS validation failures, permanent HTTP rejections and Media3's non-retryable failures retain their terminal decisions. Requests and their headers stay under Media3's normal loading lifecycle; no TS byte-stream concatenation is used.

Some providers reject an expired redirected live playlist with HTTP 509. For a single-media live HLS playlist, manifest loading can renew that session through the original authenticated stream address and retain the latest resolved delivery address for subsequent refreshes. This happens inside manifest loading, preserving already-buffered samples and decoder state. The parser enables it only when the initial result is a media playlist: master/variant routing stays under Media3's normal control, and segment/key requests never use this manifest-only renewal. The original request is never recursively renewed. All addresses stay in memory and the session's diagnostic string is redacted.

If loader recovery fails, a normal end-of-stream on a live channel, an expired HLS live window, a temporary network failure, or 30 seconds of continuous buffering schedules player-level recovery. Each such reconnect replaces the media source and resets to its default position, so it opens a fresh progressive stream or current HLS playlist instead of retaining an ended source or stale byte offset. Movies and episodes retain their existing finite playback behavior and buffer settings.

Reconnect delays are 1, 2, 4, 8 and 8 seconds. Ten seconds of uninterrupted actual playback renews the retry budget, allowing recovery across recurring provider disconnects while bounding retries during a persistent outage. After five unsuccessful attempts, the existing Retry action is shown. Invalid URI schemes, TLS handshake/certificate failures, unsupported decoding and permanent HTTP failures such as 401/403 do not trigger automatic source reloads. Temporary HTTP responses (404/408/410/429 and 5xx) can be retried.

The recovery controller belongs to the retained player and runs on its main thread. Rotation cannot duplicate it. Pause, backgrounding, audio-focus/noisy-output pauses and leaving the player cancel pending recovery; reconnects never force `playWhenReady` back on. Buffering/reconnects show a spinner, successful playback clears stale errors, and the first-playback listener still records each viewing session only once. No stream URLs or provider errors are logged by recovery.

Media3 documents its [buffer controls](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder), [error-to-IDLE event order and prepare behavior](https://developer.android.com/media/media3/exoplayer/listening-to-player-events) and [default-position recovery for expired live windows](https://developer.android.com/media/media3/exoplayer/live-streaming).

## Artwork requests

Provider artwork fields are normalized into ordered HTTP(S) candidates. Relative URLs resolve against the provider. Invalid primary values no longer hide usable alternate fields; movie details can supply additional artwork. HTTP sources are tried through HTTPS first and only fall back to HTTP when that connection explicitly allows it. The image client's redirect policy enforces the same choice. Invalid or unavailable artwork keeps a stable placeholder; a client cannot repair a provider's missing file.

Compose lazy layouts may precompose cells outside the viewport. The browser checks visible grid keys and instantiates AsyncImage only for visible cards; a cell leaving the viewport disposes its request. Details artwork is gated by visible list keys. Memory/disk cache hits are reused on return. No independent prefetcher is installed.

The secure and HTTP-compatible Coil loaders use separate caches: each has a 12 MiB memory limit and 50 MiB disk limit. Decoding uses rendered dimensions and supports common bitmap formats plus SVG. Cache-Control is respected by the Coil 3 cache-control strategy.

## Periodic app maintenance

WorkManager schedules one unique daily job with a six-hour flex window, battery-not-low constraint and initial one-day delay. It retries while the process is in the foreground. While backgrounded it removes files older than 24 hours from the app's `cache/tmp` and `cache/temporary` directories. It clears both artwork disk caches through Coil's API once at least seven days have elapsed since the last clearing. Cache size limits apply independently between maintenance runs.

Only app-owned cache directories are eligible. Canonical containment and symlink checks prevent temp pruning from following external paths. Preferences, encrypted account data, databases, user media and project workspace files are excluded. Android can delay background work; the app promises no precise cleanup time. Streaming uses no persistent video cache, and the current app creates no downloaded-media directory.
