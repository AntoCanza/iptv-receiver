# IPTV Receiver

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Watch live TV, movies and series from your Xtream-compatible provider on an Android phone or tablet. Search your library, resume an episode and see what is on next.

Bring your own provider account. The app does not include channels, media or a subscription.

**[Download for Android](https://github.com/AntoCanza/iptv-receiver/releases/latest/download/iptv-receiver.apk)** · [Website](https://antocanza.github.io/iptv-receiver/) · [User guide](docs/USER_GUIDE.md)

## What you can do

- **Find something to watch.** Search live channels, movies and series, filter by genre and explore seasons and episodes.
- **Pick up where you left off.** Resume movies and episodes, see watched status and continue a series from its next unwatched episode.
- **Check the TV guide.** Browse schedules and replay past programmes when your provider offers catch-up.
- **Adjust playback.** Use fullscreen, picture sizing and the audio/subtitle tracks supplied by the stream. Live TV reconnects after temporary interruptions.
- **Let the screen rest.** Playback keeps it awake; pausing, finishing or stopping lets Android's normal inactivity timeout take over. Buffering and reconnects receive a bounded grace period.
- **Make it yours.** Choose from ten interface languages, optionally remember an encrypted connection and manage password-protected parental controls. Adult content is blocked by default.

History and viewing progress stay on your device, separately for each provider account. See the [user guide](docs/USER_GUIDE.md) for controls, data retention and troubleshooting.

## Install and connect

1. Download and open the APK on **Android 8.0 or later**. Allow installation from your browser or file manager if Android asks.
2. Enter your provider's **server address, username and password**. HTTP and HTTPS work automatically; prefer HTTPS when available.
3. Open Live TV, Movies, Series or the TV guide.

Install updates over your existing copy to keep local data; the signing certificate must match. The [latest release](https://github.com/AntoCanza/iptv-receiver/releases/latest) includes a checksum and its source commit.

This is a development build for phones and tablets, available through GitHub Releases. Google Play and dedicated TV/desktop clients remain planned. Selected playback flows have been checked on a Pixel 9 Pro XL; broader compatibility is still being tested. See [verification](docs/VERIFICATION.md) and the [roadmap](docs/ROADMAP.md).

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

## License

[MIT](LICENSE). You may use, modify and redistribute the project's original code, documentation and artwork, including commercially, while retaining the copyright and license notice. Third-party components keep their own licenses; provider content and subscriptions are not covered.
