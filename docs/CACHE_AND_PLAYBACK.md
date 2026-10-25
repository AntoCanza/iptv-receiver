# Playback and artwork lifecycle

## Rotation and fullscreen

Each player navigation entry owns one PlaybackViewModel and ExoPlayer, built with the application context. Preparing the stream happens once when that ViewModel is created. Activity recreation detaches and reattaches PlayerView without preparing or releasing the player, preserving position and play/pause state. Leaving the player releases it; backgrounding pauses it. This is session continuity, not durable resume after process death.

Landscape automatically hides the header and system bars. The player fullscreen button can request landscape; exit/Back releases that request and returns to the device's orientation policy. Synchronizing Media3's icon temporarily disconnects its listener, because Media3 also emits the fullscreen callback for programmatic state changes.

## Artwork requests

Provider artwork fields are normalized into ordered HTTP(S) candidates. Relative URLs resolve against the provider. Invalid primary values no longer hide usable alternate fields; movie details can supply additional artwork. HTTP sources are tried through HTTPS first and only fall back to HTTP when that connection explicitly allows it. The image client's redirect policy enforces the same choice. Invalid or unavailable artwork keeps a stable placeholder; a client cannot repair a provider's missing file.

Compose lazy layouts may precompose cells outside the viewport. The browser checks visible grid keys and instantiates AsyncImage only for visible cards; a cell leaving the viewport disposes its request. Details artwork is gated by visible list keys. Memory/disk cache hits are reused on return. No independent prefetcher is installed.

The secure and HTTP-compatible Coil loaders use separate caches: each has a 12 MiB memory limit and 50 MiB disk limit. Decoding uses rendered dimensions and supports common bitmap formats plus SVG. Cache-Control is respected by the Coil 3 cache-control strategy.

## Periodic app maintenance

WorkManager schedules one unique daily job with a six-hour flex window, battery-not-low constraint and initial one-day delay. It retries while the process is in the foreground. While backgrounded it removes files older than 24 hours from the app's `cache/tmp` and `cache/temporary` directories. It clears both artwork disk caches through Coil's API once at least seven days have elapsed since the last clearing. Cache size limits apply independently between maintenance runs.

Only app-owned cache directories are eligible. Canonical containment and symlink checks prevent temp pruning from following external paths. Preferences, encrypted account data, databases, user media and project workspace files are excluded. Android can delay background work; the app promises no precise cleanup time. Streaming uses no persistent video cache, and the current app creates no downloaded-media directory.
