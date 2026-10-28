# Full application and real-provider validation

Prepared October 28, 2026. Run this after the offline-download change, using a data-preserving installation with the same signing certificate. Record actual results in [VERIFICATION.md](VERIFICATION.md); a checklist entry is not a passing result.

## Order and evidence

1. Run dependency policy, model/provider/app JVM tests, both lint variants, debug and minified release APKs, release AAB and the device-check APK using the README command and `:app:assembleDebugAndroidTest`.
2. Run synthetic storage/download checks first. They must not need a real account. Preserve the existing app data privately before device tests, keep fixture records isolated, and restore changed settings/account data afterwards.
3. Run normal navigation and playback against the saved real provider on the physical phone. Respect its connection limit; stop one stream before starting another. Do not print credentials, addresses, provider titles or raw errors. Save any private recordings outside Git.
4. Repeat affected flows on the signed/minified APK, an API 26 device/emulator and a tablet. Record model, OS, build/source revision, delivery format, steps, observed behavior and limitations separately for each device.
5. Run the accessibility and long-session passes below. Group findings by reproducible user impact; fix blockers and repeat only affected checks plus the required baseline.

## Functional matrix

| Area | Cases and expected behavior |
| --- | --- |
| Connection | Valid/invalid login, unavailable server, HTTP/HTTPS, cancellation, rotation with an unfinished form, remember on/off, cold start, logout Cancel/Back/confirm, renewal/expiry reminders. No credentials in logs, routes or saved UI state. |
| Home and catalog | Every destination, nested and empty categories, large catalogs, all-title/category search, clear/refine/sort, rapid query changes, scroll restoration, missing/invalid artwork and metadata, retry and cancellation. |
| Movies and series | Details, expandable synopsis, all seasons/episodes, unknown runtime, continuation, Resume/Start over, manual watched/unwatched, process restart and account separation. |
| Live playback | Provider HLS and TS where offered; actual frames and audio, startup delay, channel switching, pause/background/return, ended-stream recovery, temporary network loss, expired playlists, permanent access failures and bounded Retry. |
| Finite playback | Movie and episode video/audio, seek forward/back, completion, resume/restart, unknown duration, fullscreen/Back/rotation, picture scaling/aspect, subtitle Off including forced tracks, available audio languages and playback speed. |
| Guide and catch-up | Current/next/past programmes, provider time handling, unavailable archives, finite replay duration, guide opened from player, no overlapping player instances. |
| Downloads | Movie/episode enqueue, deduplication, queued/downloading/paused/ready/failed/missing states, constrained connection, background/process restart, cancel/retry, validated range resume, server ignoring Range, expired credentials and rejected HLS/error documents. Incomplete files never show Ready. |
| Offline playback | Complete a download, stop its server, remove saved login, cold launch, open Downloads, play/seek/resume/start over. No network fallback. Watch state remains after deleting media and is visible on subsequent account login. |
| Storage and deletion | Private default and user-chosen local folders, SD/USB where available, retained folder grant after restart, insufficient space, permission revocation, moved/deleted file, active-transfer deletion, bulk Cancel/confirm, no unrelated file deletion. Existing files retain their location after changing the default. |
| Parental controls | Default adult block, manual parent/child category blocks, blocked history/details/stream resolution, policy change during offline playback/download, corrupt settings, password throttling/reset and no metadata exposure in offline lists. |
| Settings and maintenance | All languages, device-language default, account/server display, clear viewing data with confirmation, progress surviving logout/cache cleanup, temporary-file pruning and downloads excluded from cleanup. |

## Accessibility and layout

- Check TalkBack reading and focus order on login, home, catalog, details, guide, player, settings and Downloads, including errors and dialogs. Buttons must have distinct, translated names with title context when repeated. Status/progress must be understandable without color.
- Check touch targets at least 48 dp, keyboard/Switch Access traversal, Back/dismissal and focus return after menus, dialogs, player fullscreen and the Android folder picker.
- Check font scales 1.0, the user's current scale and 2.0; portrait/landscape; display enlargement; phone/tablet widths; and Arabic RTL with mixed-direction provider titles. Important actions must remain reachable without clipping or horizontal scrolling.
- Measure text/control contrast against the rendered background, including disabled, error, selected and progress states. Check light/dark system behavior where supported. Review actual audio/subtitle accessibility with suitable provider tracks.
- Distinguish programmatic accessibility-tree/layout checks from an actual TalkBack or Switch Access session and from native-speaker translation review.

## Reliability and performance

- Run a two-hour live session and one full movie/episode; include background/foreground, rotation, network handover and temporary outages. Check screen-awake release on pause/end/failure, frame drops, decoder errors, battery/temperature and recovery latency.
- Run a large download across connection loss, process termination and reboot. Test scheduler quota/time limits on modern Android. A queued background job is not a promise of immediate execution.
- Measure cold/warm launch, complete catalog load, search responsiveness, first frame, seek time, channel switching, download throughput, memory and disk usage on repeatable fixtures and real service samples. Never equate a declared stream bitrate with measured throughput.

## Reproduction entry points

The existing instrumentation runner accepts `-e downloadPhase data` and `-e downloadPhase ui` for sequential synthetic download/storage and cold-start playback/deletion checks. Select a dedicated `IPTV-Download-Checks` local folder through the app first and use `-e downloadPhase folder` instead of data to repeat the same checks through ContentResolver. The UI phase then verifies local-content playback/deletion and releases that test folder's grant. The data/folder phase intentionally removes the saved connection after creating fictional downloads; use the private snapshot/restore harness and run the UI phase before restoring it. These checks must not run against an unprotected personal app-data state.

After restoration, `-e downloadPhase real -e real true` checks a bounded real-provider transfer, pause/retry and removal using the saved authorized account. It removes the test media/request afterwards; it does not establish that a full provider movie has been downloaded or watched offline.

Existing modes include `-e viewingPhase data`, `-e viewingPhase ui`, `-e screenSleep true`, and explicitly enabled real-provider modes documented in the runner and verification history. Pass `-e real true` only for the authorized saved test account. Do not treat older dated results as a fresh full regression.

## Dependency independence review

See [DEPENDENCY_AUDIT.md](DEPENDENCY_AUDIT.md) for the fresh upstream check and native replacement assessment. Review direct and transitive licenses and retain notices with the pinned sources/artifacts. Prefer platform APIs for narrow storage, cryptography, database and transfer needs; keep a library when removing it would lose required behavior or introduce an untested replacement. Evaluate each proposed removal with measurable regression and maintenance costs.
