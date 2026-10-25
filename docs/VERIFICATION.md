# Verification record

Date: October 25, 2026. Latest development version 0.1.3, versionCode 4. Package: `io.github.antocanza.iptvreceiver`.

## Watch history, navigation and logout — version 0.1.3

- Added home-screen Watch history for live channels, movies and individual episodes. It records actual playback starts, lists each stream once with its latest timestamp first, and uses an exact rolling 10-day window. History can replay an entry or open movie/series descriptions.
- History is scoped to provider account and stored in versioned, atomically replaced app-private files outside cache directories. Credentials, artwork URLs and playback URLs are excluded. Signing out hides history without deleting it; reconnecting to the same account restores it. Cache maintenance only expires entries outside the retention window.
- Catalog grids now restore their saved position when returning from descriptions/playback. Search or browsing-mode changes still start a new result set at the top. Complete navigation restoration after process death remains future work.
- Logout now requires a confirmation popup; Cancel, outside dismissal and Android Back leave the session connected. The English and Italian button label is Logout. The popup explains that history is retained.
- All 58 JVM tests pass: 3 model, 23 provider and 32 app tests. New tests cover ordering, deduplication, exact expiry and rewatch renewal, episode/type/account isolation, password/connection-name changes, reload after cleanup, inactive-account expiry, excluded credentials/URLs, unsupported schemas, sign-out/reconnect and stale callbacks, storage failure/retry, and recording only actual playback.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB all pass. Local checks used JDK 25 with the existing Gradle 9.8.2/AGP 9.4.1 configuration; CI retains its configured JDK 21. Eight new UI strings have matching keys in all ten languages. The final build used an isolated source copy because Windows locked generated resources in the OneDrive checkout.
- On the Pixel 9 Pro XL, a full-movie search was scrolled twice, a description opened and Back pressed: the query, visible titles and their exact pixel bounds were restored. Changing the query reset the list to the top.
- Actual movie playback created one durable history entry. Its title matched the history screen; replaying it from history advanced the timestamp without adding a duplicate. History bytes survived app restart and installation of the final APK over the previous build. Saved login and Italian language were retained.
- Verified the real-account Logout popup, Cancel and Android Back without signing the account out. Confirming Logout in the offline demo returned to login. Restored the real account and original screen-timeout setting afterward. No real account data or private device captures are committed.

Development APK SHA-256: `2c2913a8f038e6ba598ecc563267e7dc6a4a30efd051a1be49b49a3f61a71a99`.

History expiry is tested with a controlled clock; a multi-day hardware soak, API 26/tablet checks and history replay for live channels/series episodes remain unverified on hardware in this update. Their metadata isolation and serialization are covered by JVM tests.

No store publication was performed. Existing playback history cannot be reconstructed; entries begin with playback in this version.

## Movie search update — version 0.1.2

- Movies now offers All movies and Search all movies at the section root. Subcategory searches remain scoped to that category. The complete movie list loads on demand, is reused while the root screen exists, and preserves the query when returning from details.
- The test provider's complete movie list exceeds the ordinary 20 MiB JSON limit. Item responses now stream through a record-by-record mapper, retaining only compact item metadata. Complete catalogs have a 128 MiB transfer bound; ordinary JSON and category responses retain their 20 MiB bound. Network timeouts, cancellation, HTTPS restrictions and safe errors remain enforced.
- All 47 JVM tests pass: 3 model, 23 provider and 21 app tests. New cases cover unfiltered requests, category scoping, query changes during loading, retries, cancellation during refresh, empty categories, a synthetic 21,000-record response exceeding 20 MiB, transfer bounds, malformed/truncated data and sanitized errors.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB all pass in a clean source copy. No lint checks were disabled. Empty generated resource folders in the original checkout still trigger its existing lint error; automatic approval review blocked their removal.
- On the Pixel 9 Pro XL, the actual complete movie list loads with visible posters. Root search finds a sampled movie and a title sampled from another category. Returning to category browsing clears the query and restores categories; opening details and returning preserves it. No playback changes were made in this update.
- All four new UI strings are present in all ten languages. The phone checks used Italian with the device-language preference.

Development APK SHA-256:

```text
590c5117d2aa601ab59b3ee1671654c7ce0ac28023125ae049e9f95de472504f
```

Catalog pagination and durable indexing are not implemented. Full movie search still waits for the bounded complete list to download; streaming avoids retaining the full raw JSON tree. Catalogs larger than the limit and providers without an unfiltered movie endpoint need further integration work. No store publication was performed.

## Earlier version 0.1.1

### Automated checks

- 35 JVM tests pass: 3 model/security, 17 provider and 15 app state/localization/artwork/temp-cleanup tests. Fixtures are synthetic and need no account.
- Exact stable-version policy check passes. All 14 managed dependency/plugin families and all four Actions were checked against current primary metadata. See [dependency audit](DEPENDENCY_AUDIT.md), including the complete 212-record resolved inventory.
- Debug APK, minified unsigned release APK and unsigned release AAB build successfully with JDK 21, Gradle 9.8.2, AGP 9.4.1, compile SDK 37.2, target API 36 and minSdk 26.
- Local debug/release lint reports only the empty, untracked `mipmap-anydpi-v26` directory left by icon generation. Automated approval review blocked removing it even after explicit user authorization. No other lint error was reported. A clean GitHub checkout contains no such directory; its CI runs both lint variants without disabling checks. Check the branch's latest CI status for the clean-checkout result.
- All 49 app-owned translatable keys are present in ten languages, with matching format arguments. All ten store listing drafts fit Play's title, short-description and full-description limits.
- Supplied private connection values and access tokens are excluded from the public files. Only fictional-catalog screenshots are included in store assets.

### Physical phone checks

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

### Development APK

SHA-256:

```text
8c6d1da332e6c95a650d9aa56d497dc8d7ac514ae2061988f2258c8c43cd0425
```

The APK uses development signing. The AAB is unsigned and cannot be submitted until the owner provides production signing and completes the [store checklist](../store/RELEASE_CHECKLIST.md).

### Remaining release validation

Test the signed/minified build on hardware, tablets, API 26, long sessions, network recovery, large catalogs, subtitles/audio tracks, and mixed-direction metadata. Daily/weekly cache maintenance is scheduled and temp pruning is unit-tested; a multi-day background soak is still needed. Artwork viewport gating was exercised in actual grids but has no automated network-count instrumentation test yet. Upstream missing artwork still displays a placeholder. Durable watch progress after process death, next-episode playback, a full TV guide and Android TV remain roadmap work.
