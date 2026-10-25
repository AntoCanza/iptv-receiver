# Verification record

Date: October 25, 2026. Development version 0.1.1, versionCode 2. Package: `io.github.antocanza.iptvreceiver`.

## Automated checks

- 35 JVM tests pass: 3 model/security, 17 provider and 15 app state/localization/artwork/temp-cleanup tests. Fixtures are synthetic and need no account.
- Exact stable-version policy check passes. All 14 managed dependency/plugin families and all four Actions were checked against current primary metadata. See [dependency audit](DEPENDENCY_AUDIT.md), including the complete 212-record resolved inventory.
- Debug APK, minified unsigned release APK and unsigned release AAB build successfully with JDK 21, Gradle 9.8.2, AGP 9.4.1, compile SDK 37.2, target API 36 and minSdk 26.
- Local debug/release lint reports only the empty, untracked `mipmap-anydpi-v26` directory left by icon generation. Automated approval review blocked removing it even after explicit user authorization. No other lint error was reported. A clean GitHub checkout contains no such directory; its CI runs both lint variants without disabling checks. Check the pull request's latest CI status for the clean-checkout result.
- All 49 app-owned translatable keys are present in ten languages, with matching format arguments. All ten store listing drafts fit Play's title, short-description and full-description limits.
- Supplied private connection values and access tokens are excluded from the public files. Only fictional-catalog screenshots are included in store assets.

## Physical phone checks

Pixel 9 Pro XL, Android API 37. Private account and device identifiers are excluded from this record.

| Flow | Result |
| --- | --- |
| Upgrade from forced English | Opened in Italian with the device; English could still be selected manually |
| HTTPS provider login and catalog | Remembered encrypted connection restored and Movies/Series catalogs loaded after dependency upgrades |
| Movie posters and details | Visible movie posters and detail artwork loaded with the new image pipeline |
| Series posters | Four visible provider series covers loaded; no private screenshots are published |
| Portrait to landscape to portrait | Paused playback retained 00:04 throughout; returned to portrait without becoming stuck in landscape |
| Fullscreen button and Back | Entered immersive landscape with header/system bars hidden; Back returned to portrait player at the same 00:04 position |
| Session lifecycle | Leaving playback returned through details and catalog; rotation did not restart media |
| Store fixture | Actual home, movie grid, series episodes and language menu captured using an offline fictional catalog; fixture excluded from release |
| Final app state | Device-language mode selected again, showing Italian; real account retained; original rotation and USB screen-awake settings restored |

Earlier starter checks also verified real Live TV and series-episode video frames and H.264/AAC decoder initialization, default-off HTTP restrictions, and switching all ten home languages. Those complete playback flows were not all repeated after the latest dependency migration. The latest movie, rotation/fullscreen and artwork flows above were repeated.

The test provider requires HTTP delivery redirects for some video/artwork. Checks used its explicit per-connection compatibility option while account/catalog traffic remained HTTPS. Decoder initialization and visible frames were checked; this is not an independent listening test or a native-speaker review.

## Development APK

SHA-256:

```text
8c6d1da332e6c95a650d9aa56d497dc8d7ac514ae2061988f2258c8c43cd0425
```

The APK uses development signing. The AAB is unsigned and cannot be submitted until the owner provides production signing and completes the [store checklist](../store/RELEASE_CHECKLIST.md).

## Remaining release validation

Test the signed/minified build on hardware, tablets, API 26, long sessions, network recovery, large catalogs, subtitles/audio tracks, and mixed-direction metadata. Daily/weekly cache maintenance is scheduled and temp pruning is unit-tested; a multi-day background soak is still needed. Artwork viewport gating was exercised in actual grids but has no automated network-count instrumentation test yet. Upstream missing artwork still displays a placeholder. Durable watch progress after process death, next-episode playback, a full TV guide and Android TV remain roadmap work.