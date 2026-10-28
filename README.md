# IPTV Receiver

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Watch live TV, movies and series from your Xtream-compatible provider on an Android phone or tablet. Search your library, resume an episode, check the guide or save supported movies and episodes for offline viewing.

Bring your own provider account. The app does not include channels, media or a subscription.

**[Download for Android](https://github.com/AntoCanza/iptv-receiver/releases/latest/download/iptv-receiver.apk)** · [Website](https://antocanza.github.io/iptv-receiver/) · [User guide](docs/USER_GUIDE.md)

## What you can do

- **Find something to watch.** Search live channels, movies and series, filter by genre and explore seasons and episodes.
- **Pick up where you left off.** Resume movies and episodes, see watched status and continue a series from its next unwatched episode.
- **Watch offline.** Download supported movie/episode files, choose a local folder and manage saved media in Downloads. Completed downloads work without a provider login or internet connection.
- **Check the TV guide.** Browse schedules and replay past programmes when your provider offers catch-up.
- **Adjust playback.** Use fullscreen, picture sizing and the audio/subtitle tracks supplied by the stream. Live TV reconnects after temporary interruptions.
- **Keep watching while using other apps.** Go Home while playing, or use the Picture-in-picture button, for a movable floating video with play/pause and supported seeking. Android 12+ also supports pinch resizing. Closing the window or locking the phone pauses playback. See the [floating-window guide](docs/USER_GUIDE.md#picture-in-picture) for gestures, permissions and troubleshooting.
- **Let the screen rest.** Playback keeps it awake; pausing, finishing or stopping lets Android's normal inactivity timeout take over. Buffering and reconnects receive a bounded grace period.
- **Make it yours.** Choose from ten interface languages, optionally remember an encrypted connection and manage password-protected parental controls. Adult content is blocked by default.

History and viewing progress stay on your device, separately for each provider account. See the [user guide](docs/USER_GUIDE.md) for controls, data retention and troubleshooting.

## Install and connect

1. Download and open the APK on **Android 8.0 or later**. Allow installation from your browser or file manager if Android asks.
2. Enter your provider's **server address, username and password**. HTTP and HTTPS work automatically; prefer HTTPS when available.
3. Open Live TV, Movies, Series or the TV guide. Previously saved titles are available in Downloads.

Install updates over your existing copy to keep local data; the signing certificate must match. The [latest release](https://github.com/AntoCanza/iptv-receiver/releases/latest) includes a checksum and its source commit.

This is a development build for phones and tablets, available through GitHub Releases. Google Play and dedicated TV/desktop clients remain planned. Selected playback flows have been checked on a Pixel 9 Pro XL; broader compatibility is still being tested. See [verification](docs/VERIFICATION.md) and the [roadmap](docs/ROADMAP.md).

## Watch offline

1. Open a supported movie or episode and choose **Download for offline viewing**.
2. Open **Downloads** from Home to track progress, pause/resume, retry, play or remove saved titles. Select several entries for confirmed bulk removal.
3. Use **Settings → Downloads** to choose private app storage or a local folder and change the default unmetered-connection preference.

Completed downloads work after restarting the app without internet or a fresh provider login. They stay until removed; deleting media keeps watch history and progress. Explicit logout hides the account's library until you reconnect. Current parental controls still apply offline.

This version supports direct media files. Adaptive-stream downloads, offline DRM licensing and live recording remain planned. See the [download guide](docs/USER_GUIDE.md#downloads-and-offline-viewing) for supported formats, shared-folder access and retention.

## Run locally

Open the repository in Android Studio with **JDK 21**, **Android SDK Platform 37.2** and **Build Tools 36.0.0**. Set `ANDROID_HOME` or put your SDK path in an untracked `local.properties`, sync Gradle and run the `app` configuration. Dependency versions are pinned in [the version catalog](gradle/libs.versions.toml).

Run the same checks as CI:

```sh
./gradlew --no-daemon verifyDependencyPolicy :core:model:test :core:provider:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

On Windows, use `.\gradlew.bat`. For website changes, run `python tools/check_site.py`. Additional checks for release-workflow changes are in [Contributing](CONTRIBUTING.md).

The app uses Kotlin, Jetpack Compose and Media3. The dependency direction is `app → core:provider → core:model`; see [architecture](docs/ARCHITECTURE.md) and [contributor guidance](CONTRIBUTING.md) before making changes. Automated tests use synthetic fixtures and need no provider account. Keep credentials and private stream addresses out of source, screenshots and bug reports.

## Further reading

- [User guide](docs/USER_GUIDE.md) — setup, playback, progress, settings and troubleshooting.
- [Architecture](docs/ARCHITECTURE.md) and [provider integration](docs/PROVIDER.md) — how the app fits together.
- [Playback behavior](docs/CACHE_AND_PLAYBACK.md) — recovery, screen sleep, progress and caches.
- [Security](SECURITY.md) and [languages](docs/LOCALIZATION.md) — local data, parental controls and translations.
- [Verification](docs/VERIFICATION.md), [roadmap](docs/ROADMAP.md) and [releases](docs/CI_RELEASES.md) — tested behavior, remaining work and APK publishing.
- [Full validation plan](docs/VALIDATION_PLAN.md) and [dependency review](docs/DEPENDENCY_AUDIT.md) — real-service/accessibility coverage and native-code choices.

## License

[MIT](LICENSE). You may use, modify and redistribute the project's original code, documentation and artwork, including commercially, while retaining the copyright and license notice. Third-party components keep their own licenses; provider content and subscriptions are not covered.
