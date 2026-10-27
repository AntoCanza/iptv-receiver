# Implementation and validation roadmap

Baseline: Android 0.1.14 (versionCode 15), October 27, 2026. Checked items describe implemented behavior or the specific recorded verification, not compatibility with every device/provider. See the [user guide](USER_GUIDE.md) for current features and [verification](VERIFICATION.md) for evidence by version.

## Milestone 0 — Public foundation

- [x] Public-repository structure, Gradle Wrapper, version catalog and contributor guidance.
- [x] Shared content models and provider repository contract.
- [x] HTTPS authentication/categories checked privately against the supplied service.
- [x] Synthetic tests for request construction, response handling and credential redaction.
- [x] CI definition for unit tests, Android lint and debug/release builds.
- [x] Ten UI languages, device-language default with English fallback, language picker and translation-parity tests.
- [ ] Owner selects a source-code license before external redistribution/contribution policy is finalized.

## Milestone 1 — Prove the three playback paths

All three playback paths are implemented. The remaining checks broaden hardware, format and provider coverage:

A Pixel 9 Pro XL smoke test has passed for the three playback paths and all ten language choices; see [verification](VERIFICATION.md). Tablet and broader format/device coverage remain open.

- [ ] Phone and tablet can log in without source-code changes.
- [x] Live TV category -> channel -> visible broadcast video and audio decoder initialization on the Pixel.
- [x] Movie category -> movie details -> video playback and controls on the Pixel.
- [x] Series category -> series -> season -> episode -> correct episode video playback on the Pixel.
- [ ] Independent audio listening check and supported seek/track combinations.
- [ ] HLS and MPEG-TS tested when the provider supports both.
- [ ] Confirm movie/episode extensions, audio codecs, subtitles, and any provider-required headers.
- [x] Selected Pixel checks for single-player navigation, fullscreen/rotation continuity and live recovery; exact versions and limitations are recorded in verification.
- [ ] Extend those lifecycle/recovery checks to the oldest supported Android, tablets and the minified build.

A successful login/catalog request alone does not complete playback validation. The additional features below have been implemented while these broader release gates remain open.

## Milestone 2 — Reliable mobile browsing

- [ ] Room metadata cache scoped to provider, account and content type.
- [ ] Fast cached opening, explicit refresh and visible stale-data state.
- [ ] Measure catalog size; incremental ingestion/paging strategy where required.
- [ ] Restore categories and scroll positions after playback and process recreation.
- [x] Keep the current search and scroll position when returning from descriptions or playback within a session.
- [ ] Handle uncategorized items, malformed parent relationships and cyclic hierarchies.
- [x] Selected Pixel navigation/progress checks, large-font layouts, icon labels and screenshot review.
- [ ] Broader phone/tablet navigation automation, TalkBack/Switch Access/keyboard checks and screenshot review.
- [ ] Native-speaker review and device checks for all ten languages, including Arabic RTL and Chinese/Japanese text.

Live TV, Movies and Series roots support full-catalog search and all-content browsing through unfiltered provider requests. They stream large responses into mapped metadata and reuse each list while its screen exists. Individual categories retain category-scoped search. Results cover the current connection; multiple separate IPTV accounts/servers are not combined. Durable indexing and catalogs beyond the bounded transfer size remain future work.

## Milestone 3 — Playback continuity

- [x] Durable, account-scoped history: newest first, 180-day movie/episode and ten-day live retention, preserved during cache cleanup, with bounded paging.
- [x] Save progress by provider/content/episode ID; never persist stream URLs.
- [x] Resume/restart choice, visible history progress and completion threshold.
- [x] Detail-page progress and watched markers, per-season counts, Continue episode/season selection, manual correction and rewatch-safe watched state.
- [x] Indexed checkpoint updates, transactional legacy migration and confirmed account-specific clearing; durable progress kept until cleared.
- [x] In-app subscription reminders from two calendar months before expiry, increasingly frequent and dismissible with OK.
- [ ] Remember audio and subtitle preferences when available.
- [x] In-session aspect/scale selection, visible subtitle Off/language selection and current stream information.
- [x] Channel programme guide with search, current/upcoming/past programmes and advertised catch-up playback.
- [ ] Next-episode playback with correct numeric season/episode ordering.
- [x] Retained player, immersive fullscreen and rotation continuity, with selected Pixel checks.
- [ ] Broader fullscreen/inset behavior across API levels, tablets and the minified build.
- [ ] Categorize offline, access-denied, unavailable-stream and unsupported-codec failures.

## Milestone 4 — Release hardening

- [ ] Physical-device matrix: oldest supported Android, current Android, phone and tablet.
- [ ] Connectivity loss, provider timeout, expired/disabled account and large-catalog checks.
- [ ] Verify credential storage, logout removal, backup behavior and sanitized diagnostics.
- [ ] Verify the minified release build on hardware.
- [x] Signed rolling GitHub APK from verified `main`, certificate pinning, checksum and old-download cleanup; see [release policy](CI_RELEASES.md).
- [ ] Google Play upload signing, final privacy/contact declarations, store review and publication.
- [ ] Review the API 36 target against the latest Android behavior and distribution requirements before publishing a release.
- [ ] Review dependency updates, license selection and repository protections.

## Milestone 5 — Android TV

- [ ] Dedicated Compose for TV interface and launcher integration.
- [ ] D-pad focus, focus restoration, remote text entry and Back behavior.
- [ ] Remote playback controls and readable living-room layouts.
- [ ] Reuse `core:model` and `core:provider`; extract shared player/storage modules as needed.
- [ ] Test on physical Google TV/Android TV hardware.

## Windows, webOS and Tizen expansion

The product direction is an installed application for each platform, prioritizing
device playback and responsiveness. The owner selected compiled native TV apps on
supported SDK versions: LG webOS 26 Re:New or later and Samsung Tizen 6.0 / 2021
models or later. See [platform architecture](ARCHITECTURE.md#platform-expansion)
for implementation candidates and native TV SDK limits. This is a plan only;
the additional clients and their playback integrations remain unimplemented.

- [ ] Select supported Windows versions/architectures and LG/Samsung TV versions
  and model groups; confirm physical test-device access.
- [ ] Validate native TV SDK builds, public media integrations, release packaging
  and applicable Store/partnership requirements before committing to a full port.
- [ ] Capture Android playback and interaction baselines without interrupting
  ongoing mobile work.
- [ ] Prepare reusable provider/data/error contracts, synthetic compatibility
  fixtures, parental-control cases and history/progress rules.
- [ ] Prove a Windows native client: login, catalog, live playback, movie/episode
  playback and seek, using C#/WinUI 3 and a compatible hardware media path.
- [ ] Prove compiled LG and Samsung TV clients with vendor-supported players,
  remote focus/navigation, HTTP/HTTPS streams and network recovery.
- [ ] Compare release-build startup, first-frame/channel-switch/seek/recovery
  latency, frame drops, CPU, memory and catalog responsiveness on actual devices.
- [ ] Extract shared TV code only after both SDKs and playback implementations
  pass; keep native media, credentials, persistence and lifecycle platform-specific.
- [ ] Port the remaining catalog, history, parental, localization and settings
  features, then validate separately signed releases and Store listings.

## Later product choices

Password-protected parental controls with default adult blocking and settings reset are implemented; device and provider classification coverage remains part of release hardening. Favorites, multiple accounts, a simultaneous multi-channel EPG grid/XMLTV import, casting, downloads, M3U import, background playback and cloud sync remain later product choices. The existing channel guide and Xtream catch-up path still need broader provider and device coverage.
