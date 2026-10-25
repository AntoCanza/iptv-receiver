# IPTV Receiver — Native Android IPTV Client

Native Android IPTV client for phones and tablets, with an Android TV interface planned next.

Connect to an Xtream-compatible provider, browse Live TV, Movies and Series, and play content using Android Media3. The application does not include an IPTV subscription or bundled content.

**[Project website](https://antocanza.github.io/iptv-receiver/)** · [Source code](https://github.com/AntoCanza/iptv-receiver) · [Roadmap](docs/ROADMAP.md)

## Status

This repository contains an initial implementation and an implementation plan. It is a development baseline, not a production release.

Implemented:

- HTTPS provider login and active/expired account validation.
- Optional encrypted, device-local connection storage using Android Keystore.
- Live TV, movie and series categories, including parent categories when supplied.
- Search across all movies from the Movies screen, an All movies browser, category-scoped search, responsive catalog grids, movie details, and series seasons/episodes.
- Foreground Media3 playback with rotation continuity, immersive fullscreen, buffering feedback and retry.
- Device language by default, with English fallback and a manual picker for ten languages including Italian, Spanish, French, German, Portuguese, Arabic, Russian, Simplified Chinese and Japanese.
- Viewport-gated poster requests with alternate provider artwork, SVG support and bounded image caches.
- Daily background maintenance of app-owned temporary files and approximately weekly artwork-cache clearing.
- Provider contract tests with synthetic responses; no live account is required by automated checks.

Live TV, movie and episode playback have been checked on a Pixel 9 Pro XL. See [verification](docs/VERIFICATION.md) for the latest build, test and device evidence and limits.

Still planned: durable catalog caching, watch-progress resume, next-episode playback, a full TV guide, UI/device automation, and Android TV. See [the roadmap](docs/ROADMAP.md) for milestones and remaining device/release gates.

## Run locally

1. Install an Android Studio release compatible with AGP 9.4, Android SDK Platform 37.2 and Build Tools 36.0.0.
2. Open this repository in Android Studio and use JDK 17 or 21 for Gradle.
3. Let Gradle sync, then run the `app` configuration on an Android 8.0+ phone or tablet.
4. Enter your own provider's **HTTPS base server address**, username and password in the login screen. A connection name is optional.

No account, provider hostname or password belongs in source, build settings, examples, screenshots or CI secrets. Login is runtime input. Remembering a connection is optional and off by default. Account/catalog servers must support HTTPS. For services that deliver video over HTTP, **Allow HTTP streams** is a separate per-connection option, off by default. Enabling it permits unencrypted video delivery and playback redirects for that connection.

For a command-line build, set `ANDROID_HOME` to your Android SDK or create an untracked `local.properties` containing `sdk.dir=/path/to/android-sdk`.

```sh
./gradlew verifyDependencyPolicy :core:model:test :core:provider:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

On Windows use `gradlew.bat`. Debug APKs are produced under `app/build/outputs/apk/debug/`; unsigned AABs under `app/build/outputs/bundle/release/`. Production signing material must remain outside Git. Follow the [dependency support policy](docs/DEPENDENCY_POLICY.md): official LTS where offered, otherwise latest compatible stable. See the [current audit](docs/DEPENDENCY_AUDIT.md).

## Project structure

```text
app/                      Android application and composition root
  src/main/.../data/      Android Keystore connection storage
  src/main/.../ui/        Login, catalog, details and playback screens/ViewModels
core/
  model/                  Platform-independent models and repository contract
  provider/               Xtream HTTP, response mapping and stream URL resolution
docs/                     Architecture, provider contract, security and build plan
.github/                  CI, dependency updates, ownership and PR template
gradle/                   Version catalog and checksum-verified Gradle Wrapper
```

The dependency direction is `app -> core:provider -> core:model`; the UI depends on `CatalogRepository`. These boundaries allow a later TV app to reuse provider behavior and content models. Features currently use packages within `app`; extract modules when there is an actual reuse or ownership need.

Read [architecture](docs/ARCHITECTURE.md), [provider integration](docs/PROVIDER.md), [security](SECURITY.md), and [contributing](CONTRIBUTING.md) before making changes.

The public project website is static HTML served by GitHub Pages from `main:/docs`, with a canonical URL, social previews, structured data and a sitemap. See [search indexing](docs/SEARCH_INDEXING.md) for maintenance and Google Search Console setup.

See [localization](docs/LOCALIZATION.md) for supported locales, translated UI scope and translation checks. Provider-supplied titles and descriptions remain in their original language.

The [Google Play preparation package](store/README.md) includes original icon/feature assets, fictional-catalog screenshots, localized listing drafts and release/privacy worksheets. It is preparation only; no store publication has been performed.

## License

No open-source license has been selected yet. Public visibility alone does not grant a general license to redistribute this code. The repository owner should choose a license before accepting external contributions intended for redistribution.
