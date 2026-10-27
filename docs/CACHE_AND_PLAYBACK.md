# Playback and artwork lifecycle

## Rotation and fullscreen

Each player navigation entry owns one PlaybackViewModel and ExoPlayer, built with the application context. Preparing the stream happens once when that ViewModel is created. Activity recreation detaches and reattaches PlayerView without preparing or releasing the player, preserving position and play/pause state. Leaving the player releases it; backgrounding pauses it. Movie/episode checkpoints also support durable resume after process death, as described below.

Landscape automatically hides the header and system bars. The player fullscreen button can request landscape; exit/Back releases that request and returns to the device's orientation policy. Synchronizing Media3's icon temporarily disconnects its listener, because Media3 also emits the fullscreen callback for programmatic state changes.

## Durable movie and episode progress

After playback actually starts, movie and episode positions are checkpointed every five seconds and on seek, pause, backgrounding and player removal. Final writes use an application-owned I/O scope so leaving a navigation entry does not cancel them. A forced process kill can lose the last few seconds since the latest completed checkpoint. Live TV and catch-up never receive movie/episode bookmarks.

Positions are isolated by normalized provider/account, content type, title ID and episode ID in the app-private version-1 `viewing.db` SQLite database. Each checkpoint updates one row with position, optional duration, current completion, a persistent watched flag and last actual playback timestamp. No titles, credentials or URLs enter progress rows. History keeps identifiers, display titles, stream extensions and playback-start timestamps; it is read in 50-entry increments and enriched with individual progress lookups. Movies/episodes remain in recent history for 180 days; live channels for ten days. Watched status and resume points do not expire and survive logout/cache cleanup. Settings clears the connected account’s history/progress after confirmation. App-data clearing/uninstallation removes all local records; there is no cloud/device synchronization.

Movie details and episode rows show Unwatched, In progress, Watched or Watched/watching again, with position/duration, progress and remaining time where available. Resume starts directly at the latest stored position; Start over is separate. History retains its Resume/Start over choice. Saved seeks run before preparation without resetting on rotation. Start over resets the current bookmark while preserving duration and watched status. Manual Mark watched/Mark unwatched edits are durable; marking unwatched clears the current position. Opening details or manually marking a title never creates a playback-history entry or invents a play date.

A title becomes resumable after five seconds. Playback ending or reaching 95% of a known duration completes the current viewing and sets the watched flag. Completed titles start from the beginning; rewatching keeps the watched flag independently of the new position. Unknown durations permit resume when seeking is supported and complete only on playback end or a manual mark. The bar represents playback position, not accumulated viewing time. Out-of-order saves cannot overwrite a newer restart/manual edit; callbacks queued before confirmed clearing cannot repopulate the data.

Series Continue selects the latest played regular episode when resumable, otherwise the next unwatched episode in numeric season/episode order. Legacy bookmarks without dates still supply a resumable episode. Unwatched series start at the first regular episode; specials remain selectable and are the fallback when no regular episodes exist. Details initially select the continuation season and show its watched count. Player return, lifecycle resume and completed checkpoint writes refresh details. Automatic next-episode playback remains separate future work.

Existing version-1 progress/history account files import together in one transaction with a durable account import marker. The compatibility progress reader also supports version 2 (watched flag and optional playback timestamp). Unreadable/unsupported legacy files roll back import without overwriting sources; repaired files can retry. Existing history dates seed last-played timestamps only when still available; expired dates cannot be reconstructed. Imported files cannot overwrite later database edits or resurrect cleared progress.

## Subscription expiry reminders

The foreground browsing UI checks the provider's sign-in expiry snapshot on entry/resume and once per minute while visible. The first reminder becomes eligible when the local expiry date is at most two calendar months away. An unknown/invalid expiry produces no reminder. Renewed expiry values have independent acknowledgements; the provider date is refreshed by authentication on app reopening, not inferred from playback or a device notification schedule.

| Local days to expiry | Minimum time after the last acknowledgement |
| --- | --- |
| More than 30, within the two-month window | 14 days |
| 15–30 | 7 days |
| 8–14 | 3 days |
| 4–7 | 1 day |
| 3 or fewer, including expired active sessions | 12 hours |

OK, Android Back or outside dismissal closes the popup and records acknowledgement for that account and exact expiry value. Acknowledgements survive app restarts in app-private files outside caches. The current urgency band determines when a repeat becomes due, so entering a closer band can bring the next reminder forward. The player and resume-choice dialog defer expiry popups until browsing. These are in-app prompts while the app is open; no background/system notifications are scheduled.

## Display and track controls

The player exposes icon controls for display settings, available subtitles, stream information and a live channel's guide. Multiple audio tracks add an audio-language control. Subtitle selection includes a true disabled-text option, including forced tracks; the CC icon appears from prepared text tracks and reflects actual selection. Native track menus use Media3 translations. An advertised caption track does not establish that cues are transmitted continuously.

Fit, Zoom and Stretch are independent of automatic/16:9/4:3/21:9 aspect overrides. The retained PlaybackViewModel holds these choices during the session. The custom PlayerView applies overrides on subsequent video-size events and applies layout settings only when they change. The view reattaches its player when Compose reuses it with a different playback session; it retains Media3's SurfaceView and enables the library's Android 14 Compose synchronization workaround. Changing the display does not reload the source. Stream information reads actual video/audio input formats, tracks and timeline on the main thread while its dialog is open; missing fields remain unknown and declared bitrates are not throughput measurements.

Opening the guide from playback removes the player destination, releasing its resources before an archive or live stream is started. Catch-up uses finite playback and a programme-length clip; watch history continues to record the channel using the existing schema. It does not persist programme URLs or act as an archive bookmark.

## Live stream buffering and recovery

Live channels use the provider's `PlaybackSource.isLive` flag to enable recovery. When advertised by the provider, live playback prefers HLS (`m3u8`) over a progressive TS connection; TS remains the fallback. Format names are normalized before preference selection. HLS provides a rolling segmented stream rather than a sequence of ended TS media items.

Live playback requests 20 seconds before starting or resuming after rebuffering, a 30-second live offset, and a 60–90 second loading window with a 64 MiB buffer target. The byte target takes priority over duration, so high-bitrate streams remain bounded. These are in-memory targets, not a downloaded recording. Starting a channel can take longer and playback runs farther behind the broadcast; the provider's throughput, timestamps and HLS live window also affect the actual available buffer.

Live HTTP connect/read timeouts are five seconds. Temporary loader failures can retry up to ten attempts, with delays capped at 1.5 seconds, while already-buffered samples remain usable. Invalid schemes, TLS validation failures, permanent HTTP rejections and Media3's non-retryable failures retain their terminal decisions. Requests and their headers stay under Media3's normal loading lifecycle; no TS byte-stream concatenation is used.

Some providers reject an expired redirected live playlist with HTTP 509. For a single-media live HLS playlist, manifest loading can renew that session through the original authenticated stream address and retain the latest resolved delivery address for subsequent refreshes. This happens inside manifest loading, preserving already-buffered samples and decoder state. The parser enables it only when the initial result is a media playlist: master/variant routing stays under Media3's normal control, and segment/key requests never use this manifest-only renewal. The original request is never recursively renewed. All addresses stay in memory and the session's diagnostic string is redacted.

If loader recovery fails, a normal end-of-stream on a live channel, an expired HLS live window, a temporary network failure, or 30 seconds of continuous buffering schedules player-level recovery. Each such reconnect replaces the media source and resets to its default position, so it opens a fresh progressive stream or current HLS playlist instead of retaining an ended source or stale byte offset. Movies and episodes retain their existing finite playback behavior and buffer settings.

Reconnect delays are 1, 2, 4, 8 and 8 seconds. Ten seconds of uninterrupted actual playback renews the retry budget, allowing recovery across recurring provider disconnects while bounding retries during a persistent outage. After five unsuccessful attempts, the existing Retry action is shown. Invalid URI schemes, TLS handshake/certificate failures, unsupported decoding and permanent HTTP failures such as 401/403 do not trigger automatic source reloads. Temporary HTTP responses (404/408/410/429 and 5xx) can be retried.

The recovery controller belongs to the retained player and runs on its main thread. Rotation cannot duplicate it. Pause, backgrounding, audio-focus/noisy-output pauses and leaving the player cancel pending recovery; reconnects never force `playWhenReady` back on. Buffering/reconnects show a spinner, successful playback clears stale errors, and the first-playback listener still records each viewing session only once. No stream URLs or provider errors are logged by recovery.

Media3 documents its [buffer controls](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder), [error-to-IDLE event order and prepare behavior](https://developer.android.com/media/media3/exoplayer/listening-to-player-events) and [default-position recovery for expired live windows](https://developer.android.com/media/media3/exoplayer/live-streaming).

## Artwork requests

Provider artwork fields are normalized into ordered HTTP(S) candidates. Relative URLs resolve against the provider. Invalid primary values no longer hide usable alternate fields; movie details can supply additional artwork. HTTP sources are tried through HTTPS first, with the supplied HTTP URL retained as a fallback. The image client accepts both schemes and cross-protocol redirects automatically; there is no per-connection HTTP option. TLS validation remains enabled for HTTPS. Invalid or unavailable artwork keeps a stable placeholder; a client cannot repair a provider's missing file.

Compose lazy layouts may precompose cells outside the viewport. The browser checks visible grid keys and instantiates AsyncImage only for visible cards; a cell leaving the viewport disposes its request. Details artwork is gated by visible list keys. Memory/disk cache hits are reused on return. No independent prefetcher is installed.

One application-owned Coil loader uses a 12 MiB memory limit and a 50 MiB disk limit in `cache/artwork-compatible`. Decoding uses rendered dimensions and supports common bitmap formats plus SVG. Cache-Control is respected by the Coil 3 cache-control strategy.

## Periodic app maintenance

WorkManager schedules one unique daily job with a six-hour flex window, battery-not-low constraint and initial one-day delay. It retries while the process is in the foreground. While backgrounded it removes files older than 24 hours from the app's `cache/tmp` and `cache/temporary` directories and prunes expired recent-history entries. It clears the artwork disk cache through Coil's API once at least seven days have elapsed since the last clearing. Cache size limits apply independently between maintenance runs; durable progress is not pruned.

Only app-owned cache directories are eligible. Canonical containment and symlink checks prevent temp pruning from following external paths. Preferences, encrypted account data, databases, user media and project workspace files are excluded. Android can delay background work; the app promises no precise cleanup time. Streaming uses no persistent video cache, and the current app creates no downloaded-media directory.
