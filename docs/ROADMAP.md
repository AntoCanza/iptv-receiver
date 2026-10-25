# Implementation plan

## Milestone 0 — Public foundation

- [x] Public-repository structure, Gradle Wrapper, version catalog and contributor guidance.
- [x] Shared content models and provider repository contract.
- [x] HTTPS authentication/categories checked privately against the supplied service.
- [x] Synthetic tests for request construction, response handling and credential redaction.
- [x] CI definition for unit tests, Android lint and debug/release builds.
- [x] Ten UI languages, English first-run default, language picker and translation-parity tests.
- [ ] Owner selects a source-code license before external redistribution/contribution policy is finalized.

## Milestone 1 — Prove the three playback paths

Starter screens and provider operations are implemented. Finish by validating on actual hardware:

- [ ] Phone and tablet can log in without source-code changes.
- [ ] Live TV category -> channel -> visible video and audible audio.
- [ ] Movie category -> movie details -> playback and supported seeking.
- [ ] Series category -> series -> season -> episode -> correct episode playback.
- [ ] HLS and MPEG-TS tested when the provider supports both.
- [ ] Confirm movie/episode extensions, audio codecs, subtitles, and any provider-required headers.
- [ ] Verify one stream at a time, leaving the player, rotation and network recovery.

Do this before expanding features. A successful login/catalog request alone does not complete this milestone.

## Milestone 2 — Reliable mobile browsing

- [ ] Room metadata cache scoped to provider, account and content type.
- [ ] Fast cached opening, explicit refresh and visible stale-data state.
- [ ] Measure catalog size; incremental ingestion/paging strategy where required.
- [ ] Restore categories and scroll positions after playback and process recreation.
- [ ] Handle uncategorized items, malformed parent relationships and cyclic hierarchies.
- [ ] Phone/tablet navigation UI tests, accessibility checks and screenshot review.
- [ ] Native-speaker review and device checks for all ten languages, including Arabic RTL and Chinese/Japanese text.

The first baseline includes local search only for the loaded list. Full-catalog search requires an index or provider capability and belongs here.

## Milestone 3 — Playback continuity

- [ ] Save progress by provider/content/episode ID; never persist stream URLs.
- [ ] Resume/restart choice and sensible completion threshold.
- [ ] Remember audio and subtitle preferences when available.
- [ ] Next-episode playback with correct numeric season/episode ordering.
- [ ] Fullscreen/inset behavior and rotation without restarting the stream.
- [ ] Categorize offline, access-denied, unavailable-stream and unsupported-codec failures.

## Milestone 4 — Release hardening

- [ ] Physical-device matrix: oldest supported Android, current Android, phone and tablet.
- [ ] Connectivity loss, provider timeout, expired/disabled account and large-catalog checks.
- [ ] Verify credential storage, logout removal, backup behavior and sanitized diagnostics.
- [ ] Verify the minified release build on hardware.
- [ ] Owner-managed release signing and distribution decision.
- [ ] Review the API 36 target against the latest Android behavior and distribution requirements before publishing a release.
- [ ] Review dependency updates, license selection and repository protections.

## Milestone 5 — Android TV

- [ ] Dedicated Compose for TV interface and launcher integration.
- [ ] D-pad focus, focus restoration, remote text entry and Back behavior.
- [ ] Remote playback controls and readable living-room layouts.
- [ ] Reuse `core:model` and `core:provider`; extract shared player/storage modules as needed.
- [ ] Test on physical Google TV/Android TV hardware.

## Later product choices

Favorites, multiple accounts, a full EPG/XMLTV guide, catch-up, casting, downloads, parental controls, M3U import, background playback and cloud sync are outside the first release. Add them only after the core paths are reliable.
