# Verification record

Date: October 28, 2026. Latest development version 0.1.15, versionCode 16. Package: `io.github.antocanza.iptvreceiver`.

## Offline downloads and real-service baseline — version 0.1.15

The app now downloads supported direct movie/episode files, exposes a dedicated Downloads destination and storage preferences, tracks durable transfer states, supports pause/retry/cancel and confirmed bulk removal, and plays completed media without provider authentication. Private files and granted local document trees use separate file/content-only Media3 sources with no network fallback. Current parental rules apply to the saved category/adult snapshot; restricted entries retain generic removal controls without title exposure. Pending request addresses use Android Keystore encryption and are excluded from the index and scheduler inputs. No library was added.

The complete README verification command and `:app:assembleDebugAndroidTest` pass with JDK 21: **192 JVM tests** (146 app, 6 model, 40 provider), exact dependency policy, both lint variants, debug APK, minified unsigned release APK and release AAB. Lint reports zero errors/warnings and the existing target-SDK hint. New tests cover atomic index reopening/corruption and account isolation, safe metadata, current offline restrictions, transfer ranges/restarts/redirects, cancellation, empty/truncated/error/playlist rejection, and the interrupted-validator replacement ordering. Ready entries require a nonempty complete length. A stale pause/completion cannot downgrade a completed download or discard a paused generation's request.

Physical device: Pixel 9 Pro XL, Android 17, data-preserving installation with the existing development certificate. A two-minute silent AVC video at 160×96/10 fps is generated using Android MediaCodec/MediaMuxer, with a valid MP4 free box bringing it to 2 MiB for interruption testing. No provider media or additional encoder library is bundled. Both private file storage and an explicitly selected local document tree pass transfer/index checks: exact bytes, encrypted unfinished requests, validated range resume, pause/stale-callback handling, completed-pause safety, rejected foreign paths, shared viewing bookmarks, running-transfer removal and externally missing files. The additional completed-pause check was run in the final chosen-folder pass.

For each storage path, three normal-UI groups pass at font scale **2.0**: cold launch with the saved connection removed reaches Downloads; local AVC frames render, resume at the bookmark and seek with the loopback fixture server stopped; bulk removal permits Cancel before deleting completed and already-missing entries. Screenshots were reviewed for readable, reachable controls. Missing documents required checking the still-accessible parent after Android's delete failure; the resulting fix preserves inaccessible entries rather than assuming a failed delete succeeded. Error feedback remains inside the screen insets, and missing files are excluded from the storage-used total. The chosen-folder grant is released after testing.

The saved real provider passes three download groups: native direct-file transfer, pause/retry through the production repository/scheduler, and removal of the test file/request. This is a bounded partial transfer, **not a completed real-provider movie download/offline watch**. Real-media playback passes eight groups: two live channels observed for one minute each with pause/resume, two movies and two series episodes with decoded frames and seeking, bounded catch-up playback/seek, and all 12 picture scale/aspect combinations with stream information. One additional series sample remained buffering beyond the 50-second startup window without a terminal player error; its cause is unresolved and it is not counted as passing.

Three further real-service UI groups pass: guide navigation/programme search/archive launch; fullscreen/rotation retaining the same paused player, position and 4:3 selection; and returning to the guide preserving its past filter/search while releasing playback. Separate synthetic track checks pass Italian/English rendered cues, Off including forced captions, and hiding the subtitle control when tracks disappear. The runner now reveals auto-hidden controls and selects the actual numbered track label. Eight screen-awake checks and eight existing viewing-storage/migration groups also pass. These scopes do not establish a complete application/accessibility regression or independently confirm audible output.

A locally signed minified APK, confirmed non-debuggable and signed with the same certificate, passes restored-account startup plus empty Downloads and private-storage settings smoke checks. Full download/codec/background validation of the minified build remains pending; the transfer/video checks above use the development build. The tested development build was reinstalled afterwards.

The private restore verifies all **11 original app files** against their snapshot before normal reopening; the saved account, original font/rotation/display settings and viewing records are retained. Test media, pending requests, synthetic records and the dedicated shared test folder were removed. At the user's request, USB stay-awake remains enabled (`stay_on_while_plugged_in=2`); it follows USB power and does not disable manual locking. No repository publication, release upload or store submission was performed.

All 14 managed dependency families, 212 unique resolved artifacts, Gradle, Sharp and five workflow Actions were rechecked against primary metadata; no update is needed. See [the dependency/native-code assessment](DEPENDENCY_AUDIT.md) and [the full validation plan](VALIDATION_PLAN.md) for the next work. The site checker and Markdown links pass; Chromium checks at 1440/768/390/320 px show no horizontal overflow or broken images, with screenshot review at phone/desktop sizes. The nine existing release-policy tests also pass.

Remaining hardware work includes API 26/tablets, SD/USB drives, revoked grants/low space, process termination during an active large transfer and reboot/background quotas, long playback/download sessions, actual TalkBack/Switch Access/keyboard operation, RTL/native-speaker review and the unresolved provider sample. Adaptive downloads, offline DRM licensing and live recording remain outside this implementation.

Development APK SHA-256: `c5272bdf5dcf7b5b815c5bcd9dfb46480ca3c7062fb0a63bddf98bb1531e26e8`.

## Idle screen sleep and shorter public documentation — October 28, 2026

The playback engine now releases the screen-awake hold on pause, stop, completion, suppressed playback and terminal failure. Startup, buffering and automatic live reconnects share a 60-second continuous-wait allowance; rotation retains the controller and manual Retry renews the allowance. Android handles user input and the actual display timeout. No new permissions, dependencies or system-setting changes were introduced.

The complete README verification command passes with JDK 21: **172 JVM tests** (126 app, 6 model, 40 provider), dependency policy, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB. The nine new screen-policy tests cover uninterrupted playback, idle/failed/suppressed states, bounded startup/buffering/reconnects, exhausted retries, explicit retry, pause/resume and cleanup. Both lint variants have zero errors/warnings and the existing target-SDK informational hint. The device-check APK builds and its updated runner passes debug lint.

On a physical Pixel 9 Pro XL running Android 17, eight checks pass through the actual PlaybackScreen and ExoPlayer: active playback sets the activity's screen-awake flag; pause, natural completion, stop and a failed load clear it; resume and Retry restore it; removing the player screen clears it. The fixture is a seekable silent WAV served on device loopback, with a deliberately unavailable loopback endpoint for failure. These are screen-state/UI checks, not new video/provider compatibility evidence. The data-preserving installation uses the existing signing certificate, does not use the real provider and creates no history/progress records. Normal app launch was restored afterwards; display settings were not changed.

Reproduce the device checks with `adb shell am instrument -w -e screenSleep true io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks`. Actual inactivity-to-screen-off timing, touch-reset timing, charging/always-on policies, buffering grace across physical rotation, API 26, tablets and the minified build on hardware remain unverified. The 60-second deadlines use virtual time in JVM tests; the phone checks assert window flags rather than forcing device sleep.

The README was reduced from roughly 1,730 to 570 words, retaining setup, the complete verification command and links to detailed guides. Both public HTML pages were shortened and the new behavior was added to the user/playback guides and roadmap. The site checker passes for metadata, structured data, images, anchors and the sitemap. Chromium screenshots were reviewed at 1440 and 390 pixels; layout checks at 1440, 768, 390 and 320 pixels confirm no horizontal overflow, loaded images and working FAQ expansion. Older verification entries below retain their original scope.

## Documentation synchronization — October 27, 2026

Reviewed the public website, README, user/developer guides and all ten store listing drafts against source commit `6e85f89` (0.1.14/versionCode 15), the Gradle files and Android workflow. GitHub's API confirmed `main:/docs` as the Pages source and a published 0.1.14 rolling release with APK/checksum assets. Updated stale feature, SDK/dependency, live-format, cache, storage and draft-privacy descriptions; the Google ownership file and sitemap page set are unchanged.

The static site checker passes for both public HTML pages and the canonical sitemap entry. Relative/repository Markdown links, listing JSON/length limits and the site's version metadata were checked. All nine synthetic release-policy tests pass. Chromium rendering was reviewed at 1440, 768, 390 and 320 pixels wide with no horizontal overflow, loaded artwork and working FAQ expansion.

This is a documentation/site-only update. Android application code and dependencies are unchanged; the version-specific app build and physical-device evidence below comes from the recorded checks, not a new hardware regression in this update. Draft store/privacy materials remain unsubmitted, and older preview screenshots still need refreshing before store submission.

## Detail watch progress — version 0.1.14

Distribution uses the repository's main-only [rolling APK workflow](CI_RELEASES.md). Its nine synthetic release-policy tests and workflow syntax checks pass locally. Signing the optimized APK was checked against the existing development installation's certificate. Hosted build/publication status is recorded in [Android CI](https://github.com/AntoCanza/iptv-receiver/actions/workflows/android.yml).

Movie details and episode rows show watched status, position/duration, remaining time and resume/restart controls. Series select the continuation season, show watched counts and continue the last unfinished or next unwatched regular episode in numeric order. A playback-ending event or 95% of a known duration completes the current viewing; the separate watched flag survives restarting and partial rewatching. Manual watched/unwatched edits do not invent playback dates or history entries. A briefly started episode stays selected before the five-second resume threshold.

Indexed SQLite storage updates one checkpoint row and reads history in 50-entry increments. Movies/episodes remain in history for 180 days, live channels for ten days; durable progress stays until explicitly cleared. Legacy account files migrate transactionally once. Unknown/corrupt versions remain untouched and can retry after repair. Account-specific clearing requires confirmation and rejects previously queued checkpoints and manual edits. No new dependency was added; all ten languages have matching resource keys/format arguments.

The full README verification command passes: **163 JVM tests** (117 app, 6 model, 40 provider), dependency policy, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB. Both lint variants have zero errors/warnings and the existing target-SDK informational hint. The additional device-check APK builds and its latest runner changes pass debug lint.

Physical Pixel 9 Pro XL, Android 17, data-preserving installation of versionCode 15. Eight dedicated storage groups pass: version-one migration/source preservation, rewatch/stale-write handling, account/type/episode isolation, bounded history pages, retention independent of bookmarks, reopen/clear without resurrection, migration rollback/retry, and exclusion of provider credentials/addresses.

Four normal-navigation groups pass at font scales **1.15 and 2.0** with the fictional offline catalog: direct movie resume and Start over, watched state through landscape/portrait recreation, episode-three resume and manual marking advancing Continue/counts, and clearing cancellation/confirmation. Screen captures were visually reviewed for readable, reachable controls. Media is a seekable two-minute silent WAV served on device loopback; these checks validate Media3 positions and UI integration, not video codec/provider compatibility. The saved real provider is not used by these new checks. API 26/tablets, RTL, other providers and the minified build on hardware remain outside this verification.

Reproduction uses the existing runner with `-e viewingPhase data` or `-e viewingPhase ui`; neither requires a provider account. Eight original app files/preferences were verified against the private pre-update snapshot and restored where changed before normal reopening. Font scale, density and screen-power behavior were restored and verified. The updated app was reopened with offline test mode disabled. Screenshots and recovery snapshots remain private and outside Git.

Development APK SHA-256: `f45f5719e2109cd9cfb985011be5b8d60e205d1454de83d5ff2c824bd433b9ea`.

## Usability fixes — version 0.1.13

Applied the ten findings in [the usability audit](UX_AUDIT_2026-10-27.md), with explicit handling of the provider schedule conflict. Catalog search/refinements/results share one scroll container; large-font/short-window layouts use compact rows. Utility actions have named 48 dp icons and long-press tooltips. Live root search spans channels, search defaults to relevance while honoring explicit choices, and the keyboard no longer autocorrects search terms. Series headings omit ambiguous aggregate runtimes.

The optional connection name is labeled. An activity ViewModel retains unfinished credentials across configuration changes without a SavedStateHandle; successful authentication clears it. Remember and parental options associate their labels with the full toggle row. Password fields have reveal actions. Player tools use dedicated icons, numbered track names and subtitle state, replace the native settings action while retaining speed, and follow controller visibility. The visibility listener is refreshed in AndroidView.update so the normal navigation transition cannot retain an obsolete callback.

The complete README verification command passed on the working checkout: dependency policy, 148 JVM tests (102 app, 6 model, 40 provider), debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB. Both lint variants: zero errors, zero warnings, one existing target-SDK informational hint. The manual device-check APK builds. All ten resource sets pass key/format parity; no new dependency was introduced.

### Physical device and real service

Pixel 9 Pro XL, Android 17; data-preserving installation of 0.1.13/versionCode 14. These device checks use the saved real account, with no demo provider or synthetic stream. The runner requires real=true; ordinary JVM/CI fixtures remain synthetic.

| Check | Observed result |
| --- | --- |
| Login | Tapping the Remember label toggles the named checkbox; real server/username/password survive landscape and portrait recreation; password stays hidden; tapping Connect with an empty optional name authenticates and clears the draft |
| Live navigation | Normal home → Live → global Rai search → channel renders video; toolbar hides with automatic timeout and reappears; guide action opens the real schedule |
| Episode/player | Friends S2x01 renders frames; two audio tracks have unique numbered labels; 1.25× works; explicit hide/show and automatic timeout synchronize the toolbar; native settings action is absent; all 12 scale/aspect combinations and stream information pass |
| Global search/ranking | Rai produces 40 channels; Friends is first among five matching series |
| Large fonts | At font scale 2.0/density 600, portrait catalog rows are readable and landscape search/filter/results scroll together. Full result rows remain reachable. History actions use play/info icons and title-specific accessible names; long-press help is readable |
| Guide | Real overlapping entries produce an explicit conflict message and no ambiguous current badge. With font scale 2.0/density 600 and keyboard open, scrolling reaches Storie italiane and its replay action; tapping starts video |
| Series details | The ambiguous 5270-minute heading is omitted; individual episodes still show 22 minutes |
| Movie/fullscreen | Interstellar starts from history and renders video; fullscreen opens in landscape, shows the combined controls on tap, and hides them automatically |

The three manual runner phases pass eight groups (login 3, player 4, live 1):

~~~text
adb shell am instrument -w -e real true -e uxPhase login io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
adb shell am instrument -w -e real true -e uxPhase player io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
adb shell am instrument -w -e real true -e uxPhase live io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
~~~

Provider limitation: the archive selected as Storie italiane starts video displaying Unomattina branding. Successful playback does not verify programme identity or schedule accuracy. The app flags overlapping schedule data and preserves both provider timestamps and the archive-local clock; it does not guess an hour correction or delete repeated shows.

Coverage remains limited to this phone/account and selected streams. TalkBack listening, Switch Access/keyboard journeys, other devices/API 26/tablets, visual RTL, network fault injection, actual subtitle cues and the minified build on hardware remain unverified. Parental creation/unlock/reset was not exercised on the personal account. Screenshots, credentials, provider payloads and playback URLs stay outside Git.

Original watch-history/progress (three files) and preferences (three files) were restored with matching SHA-256 hashes. Density, font scale, automatic rotation and screen-power behavior were restored; the updated app opens on the real home screen.

Development APK SHA-256: 3d16500a03cb4a2017ea6ce5e95e3d243badc337a2241605ea7f951b02f4ab5f.

## Movie/episode resume and subscription reminders — version 0.1.12

- Movies and individual episodes checkpoint actual playback every five seconds and on seek/pause/background/exit. History shows position/duration or completion, with a Resume action when eligible. Selecting an unfinished title from history, movie details or an episode list offers Resume and Start over. Completed titles start over; live TV and catch-up do not acquire movie bookmarks.
- Progress is isolated by normalized provider/account, content type, title and episode IDs. Its separate version-1 atomic store survives process restart, logout and recent-history expiry without changing the existing history schema. It contains no credentials, titles, artwork or playback addresses. Final writes survive navigation disposal; older asynchronous snapshots cannot overwrite a newer seek/restart. Resume starts collecting positions in this version and cannot recover earlier viewing.
- Foreground subscription prompts start two local calendar months before the provider's sign-in expiry date. Repeat intervals tighten from 14 days to 7 days, 3 days, 1 day and 12 hours. OK/Back/outside dismissal is retained per account and expiry value. Playback and the resume choice defer prompts until browsing. No background/system notification is scheduled. Unknown dates are ignored; renewed dates are evaluated after authentication refreshes the account snapshot. See [the exact policy](CACHE_AND_PLAYBACK.md#subscription-expiry-reminders).
- All **145 JVM tests pass**: 6 model, 40 provider and 99 app tests. The 18 additional cases cover process reload, fifteen minutes of a 110-minute movie, retention independent of recent history, movie/show/episode/account isolation, changed passwords, excluded private values, completion/unknown durations, stale writes and sessions/players, corrupt/future schemas, pre-prepare seeking, periodic/pause/background/final saves, calendar-month boundaries/DST, urgency intervals, persisted acknowledgement, renewal, invalid/expired dates, clock rollback and storage failures.
- The complete README verification command passes: dependency policy, JVM suites, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB. Both lint variants report zero errors and zero warnings, with the existing informational target-SDK hint. The manual device-check APK also builds. All ten languages pass resource/format parity. Verification uses an isolated source copy; no new library dependency was added.

### Physical Pixel 9 Pro XL checks

| Check | Observed result |
| --- | --- |
| Real movie and real series episode | Each rendered video, sought beyond 15 minutes and persisted periodic/pause/exit checkpoints with known duration |
| Complete process shutdown/reopen | Saved connection/language and both independent positions survived an explicit force-stop between device phases |
| Actual history UI | Both titles displayed progress, opened the Resume/Start over dialog and resumed at the saved 15-minute point |
| Moving video after resume | New video-frame callbacks were observed for both real streams after the saved seek |
| Start over | Actual button returned each title to its beginning and reset its durable position below ten seconds |
| Expiry dialog | A controlled account/clock displayed date and days remaining; actual OK tap closed it; recreating the model retained acknowledgement |
| Next reminder and playback gate | Advancing the fixture clock made the next interval due; the playback visibility gate suppressed it until browsing, then OK closed it |
| Installation | Data-preserving update to 0.1.12/versionCode 13; real account and Italian interface retained |

The device runner reported eight passing groups: two real-playback checkpoint checks, four normal-navigation resume/restart checks and two controlled expiry-UI checks. The actual subscription expiry was not changed. Calendar and urgency intervals use controlled clocks rather than a multi-week hardware soak. These checks cover selected streams on this phone/provider with the development APK; API 26/tablets, other providers, non-seekable VOD and the minified build on hardware remain unverified. A forced process kill can lose a few seconds since the last completed checkpoint; records are local to this installation.

The existing manual runner accepts these additional phases after installing both debug APKs. Run `seed`, explicitly force-stop the main package, then run `verify`; real phases require a saved account and are never public CI:

```text
adb shell am instrument -w -e real true -e progressPhase seed io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
adb shell am force-stop io.github.antocanza.iptvreceiver
adb shell am instrument -w -e real true -e progressPhase verify io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
adb shell am instrument -w -e progressPhase reminder io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
```

Development APK SHA-256: `e0aff8f5e5e508f321b6a47ae8604cbba373fa09e77392f9407f6140a393551a`.

No store publication was performed.

## Player icons and programme guide version 0.1.11

- The player uses icons with translated accessibility labels: a gear for display settings, CC for available subtitles, information for actual stream formats, a guide for live channels and audio selection when multiple audio tracks exist. Display choices cover Fit/Zoom/Stretch and automatic/16:9/4:3/21:9 ratios. The selected ratio also applies when the input video size changes. CC follows prepared text tracks and the selector can disable all text, including forced subtitles.
- Home includes a channel-based TV guide. It loads the provider's schedule, decodes descriptions/titles, displays local programme times, supports search and current/upcoming/past views, and offers replay only for eligible advertised archives. Past programmes are ordered newest first. Past/search choices survive leaving for playback and returning. This is a channel schedule browser; a simultaneous multi-channel grid remains future work.
- Archive resolution uses the provider-local programme clock and a bounded duration. The physical archive probe exposed an incorrect TS duration of approximately 26 hours for a one-hour programme. The production engine therefore clips archive playback to the scheduled programme duration. Guide loading and archive selection recheck parental category policy and programme ownership; no history schema change or credential-bearing persistence was added.
- Fixed video reattachment when Compose reuses a player view with a different playback session. Earlier device checks exposed a stale picture while audio from the new player continued. The update now assigns the current player in the view update as well as its factory, and avoids redundant display-layout updates. Subsequent stream-switch and frame checks passed, and the user independently confirmed moving video with audio. The player retains SurfaceView and enables Media3's Android 14 Compose workaround.
- All **127 JVM tests pass**: 6 model, 40 provider and 81 app tests. New coverage includes guide parsing/Base64/plain text, malformed timestamps/local clocks, archive flags/retention and encoded URLs, finite archive duration, policy/ownership enforcement, guide cancellation/retry and refreshing historical channel capabilities, and display-state retention without rewinding media. All ten languages pass string/placeholder parity checks.
- The complete README verification command passed: dependency policy, JVM tests, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB. Lint reports zero errors and zero warnings, with the existing informational target-SDK hint. The device-check APK also builds. Checks used an isolated copy of the source; machine paths and device artifacts remain outside the repository.

### Physical Pixel 9 Pro XL checks

| Check | Observed result |
| --- | --- |
| All 12 scale and aspect combinations | Actual controls tapped; requested geometry and continued video-frame callbacks verified after every change |
| Stream information | Opened from its icon during real live playback; video/codec fields were present |
| Two real live channels | Each observed for 60 seconds after initial video; 1,701 and 1,490 frame callbacks across the checks; pause held position and resume produced new frames |
| Two real movies | Each rendered video, paused/resumed and sought to approximately 60 seconds |
| Two real episodes | Each rendered video, paused/resumed and sought to approximately 60 seconds |
| Catch-up | Real archived video rendered, the programme-duration upper bound held, and seeking to approximately 15 seconds resumed video |
| Subtitle languages and Off | Controlled English and Italian WebVTT cues actually rendered after selection; Off cleared the cues and disabled text tracks, including a forced-caption track |
| Subtitle availability changes | CC was absent without text tracks, appeared when two tracks arrived and disappeared when they were removed |
| Normal guide navigation | Home → guide category → channel → past programmes → search → catch-up Play succeeded through the actual navigation and repository guard |
| Fullscreen and rotation | The normal app entered/exited landscape while paused, retaining the same player, position within 600 ms and a selected 4:3 ratio; resume delivered new video frames |
| Return to guide | The guide icon returned to the saved past-programme filter and search; the player view was released |
| Installed application | Data-preserving update to 0.1.11/versionCode 12; saved connection and interface language retained |

The comprehensive device runner reported 11 passing check groups; the normal-navigation runner reported another three. One additional episode stayed buffering without video beyond the 50-second startup bound and is not counted as successful playback. Earlier failures while establishing the runner included accessibility-selection handling and the stale view/player connection described above; those are not evidence that the provider rejected those channels.

These physical results cover selected streams on this phone/provider and the development APK. They do not establish compatibility for every catalog item, extended sessions, genuine anamorphic/4:3 sources, HDR, surround sound, multiple real audio languages, tablets, API 26 or the minified build on hardware. Caption track advertisement alone does not establish that a live channel is transmitting cues; the two-language cue checks used controlled data.

The manual runner is under `app/src/androidTest`. After building/installing both debug APKs, its default mode uses a local synthetic audio/subtitle fixture. Real-provider mode is explicit and uses only the saved connection on the phone:

```text
adb shell am instrument -w io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
adb shell am instrument -w -e real true io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
adb shell am instrument -w -e real true -e normalUi true io.github.antocanza.iptvreceiver.test/io.github.antocanza.iptvreceiver.PlaybackDeviceChecks
```

Real mode must remain a private, explicitly requested device check, never public CI. It opens one player at a time and emits sanitized outcomes. Any failure capture stays in app-owned device storage; private captures, raw responses, accounts and media addresses are excluded from the repository. Temporary diagnostic packages were removed after testing and the original USB screen-awake setting was restored.

Development APK SHA-256: `94fac5c3cb5c2a48dfd063c69429895987fbf147b45e7e4c659b90d294021cb8`.

No store publication was performed.

## Parental controls — October 27, 2026

- Adult content defaults to blocked before password setup. Settings contains a protected panel with first-access password creation and confirmation, subsequent password checks, password changes and manual category blocks scoped to provider account/content type. Its warning says to remember the password because it cannot be recovered. Leaving the panel or backgrounding the app relocks it and clears password fields.
- Confirmed password reset restores every protected option to its defaults: adult blocking enabled, all accounts' manual category locks removed, password unset. Creating a new password after reset does not disable adult blocking. Password changes preserve options.
- Provider adult flags, adult labels and category descendants are checked for browsing, all-title searches, details, history and playback. Historical identifiers are resolved against provider listings; removed identifiers stay hidden without repeated complete-catalog downloads during the session. Policy revisions refilter history immediately. Unreadable settings fail closed until explicit reset.
- All 118 JVM tests pass: 6 model, 35 provider and 77 app tests. Sixteen added tests cover provider adult/category mapping, safe defaults, password verification/persistence/change, throttling, reset of every protected option, account/type isolation, corrupt storage, category cycles/missing classifications, catalog/detail/stream/history enforcement, episode ownership, panel validation/relocking and history refresh after reset. All ten languages pass resource/format parity.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB pass. Lint reports zero errors and zero warnings, with the existing target-SDK informational hint. Checks used an isolated source copy because OneDrive locks generated files in the checkout.
- This update has not been installed or visually exercised on the connected phone. Earlier device checks below apply to their recorded builds. Remaining checks include first-run setup, wrong password, background relocking, reset confirmation, large-font layouts, API 26, and classification across real providers. Automatic labeling is not independent age classification; manually block mislabeled categories.

## Mobile metadata, Settings and all-series search — version 0.1.10

- Home now has icon-and-label destinations for Live TV, Movies, Series and Watch history, plus a Settings action. Account status/expiry/connection limits, confirmed logout, server information and language selection live under Settings. Server time and active connections are clearly sign-in snapshots. Passwords are never displayed in account/server settings.
- Compact title cards show available year/rating; details add optional runtime, genres, artwork and trailers, with expandable synopsis/credits. Movie Play remains reachable while scrolling. Series uses a season picker and episode thumbnails/summaries/runtimes, with one season displayed at a time. Extra artwork uses the existing bounded caches and viewport gating.
- Movies and Series root search spans all categories in the current connection. All movies/All series and result counts reuse the complete catalog while the screen exists. Sorting and genre filters use available list metadata without per-title detail requests. Title/year/genre search excludes cast/director names so a query such as Dexter does not surface unrelated titles because of an actor's name.
- Fixed poster visibility after search/browsing/filter state creates a new grid: viewport observation now follows the current grid state. Optional metadata parsing covers aliases, five-to-ten-point ratings, duration formats, artwork arrays, detail/list fallbacks and malformed/missing values. Trailer links reject local schemes and embedded credentials, and open only after a tap.
- All 102 JVM tests pass: 6 model, 34 provider and 62 app tests. Added provider metadata/server-clock/unfiltered-series coverage, catalog sorting/filtering and Dexter search relevance, all-series loading/cache/retry/scoping, and localized settings date handling. All ten languages pass key/format parity checks.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB pass. Each lint variant reports zero errors and zero warnings, with the existing informational target-SDK hint. The build includes the completed 0.1.9 HLS playback work. Verification used an isolated source copy because the OneDrive checkout still locks generated build files.
- On the Pixel 9 Pro XL, an isolated fictional-catalog preview verified icon navigation, Settings/account/server information, logout cancellation, English/Italian language selection, sort/genre choices, year search, expanded details, season selection, and reachable Play. Portrait review covered the normal viewport and a roughly 358 dp viewport with 1.3x and 2x text. Switching into All movies now loads actual posters rather than leaving placeholders.
- With the real saved connection, complete catalog requests mapped 4,866 live channels, 68,633 movies and 5,101 series, across 48/346/19 categories. One observed full-movie load/map took 8,864 ms and full-series load/map took 1,428 ms. Movie listings supplied 44,411 usable ratings but no genres, so movie genre controls remain hidden. Series listings supplied 4,835 genre values. These are one-provider observations, not universal field guarantees or performance promises.
- Twelve spread-out movie detail requests and four spread-out series detail requests all succeeded. Eight sampled movies supplied descriptions/year/genre/cast, all twelve supplied runtime, three supplied trailers and seven supplied backdrops. The sampled series exposed 174 episodes: all had runtime, 110 had images and 39 had descriptions. Missing fields stayed optional.
- A temporary device fixture exercised the production playback engine sequentially against real streams: two live channels, two movies and two series episodes each delivered at least 25 seconds of observed video after startup, with pause/resume checks. All four finite videos also passed seeking. Video formats included 1280x720, 3840x2160, 1920x802, 1920x800 and 1920x1080, with initialized audio decoders. Successful cases had no fatal player error; one movie and one episode reported a recovered loader error. VOD buffering counters included explicit seeks. This checks decoder initialization and rendered frame callbacks, not an independent audio listening assessment.
- Two additional episode attempts did not pass: one produced Media3 decoding error 4003, and one delivered no video within the 50-second startup limit. Reproducing the decoding failure through the normal app verified the new device-decoding message and Retry action instead of the generic connection suggestion. These attempts are retained as limitations, not counted as successful playback.
- The normal app verified real account/server settings, full-movie search/details and expanded credits, actual movie playback and landscape fullscreen (2992x1344), then returning through details to the saved search. Global series search returned 16 title matches for Star Wars and five for Dexter. Star Wars restored the exact visible text positions after opening a result and going Back. A Dexter result exposed 13 episodes; an episode played with a changing visible picture, and its query remained after returning from playback.
- Installed the final development APK as a data-preserving update. Temporary test apps were removed and the phone's density, font scale, rotation and screen timeout were restored. No real account values, raw provider responses, titles beyond the user-requested search terms, private screenshots or playback URLs are published here.

Development APK SHA-256: `e9b74c4ff905613985e761e63599606ccba94eb38e0a4a8e312eba20191ab7cb`.

No store publication was performed. Physical checks cover this phone/provider and selected streams, not every catalog item, long-duration playback, tablets, API 26 or the signed/minified release build on hardware.

## Buffered HLS Live TV and playlist session renewal — version 0.1.9

- The real Sky F1 test in version 0.1.6 showed four stream endings and four automatic restarts in 180 seconds. Video resumed each time, but the largest measured gap between frame callbacks was 1,762 ms. The user requested a larger buffer and reduced visible interruption.
- Live channels now prefer advertised HLS over TS, with normalized format names and the previous TS/legacy fallback when HLS is absent. This applies both to browsing and watch-history replay.
- Live playback requests 20 seconds before starting or after rebuffering, a 30-second live offset, and a 60–90 second loading window with a 64 MiB buffer target. Live HTTP connect/read timeouts are five seconds. These settings increase initial wait and delay relative to the broadcast; actual buffering also depends on the provider's live window and throughput. Movie/episode buffer behavior is retained.
- Temporary loader failures retry up to ten attempts, with delays capped at 1.5 seconds, while buffered samples stay playable. Invalid schemes, TLS validation failures, permanent HTTP rejections and Media3 non-retryable decisions remain terminal. Persistent outages still reach bounded player recovery from 0.1.6. Normal Media3 handling of finite responses and TS timestamps is retained.
- This provider also returns HTTP 509 when redirected HLS playlist sessions cease to work. For a single-media live playlist, a rejected redirected manifest is renewed through the original authenticated stream address, and future reloads use the newly resolved playlist address. This preserves buffered media and decoder state. Master playlists retain independent variant URLs; segment/key requests do not receive this manifest-only handling. Session addresses stay in memory and have redacted diagnostics.
- All 83 focused JVM tests pass: 6 model, 25 provider and 52 app tests. Three format-preference tests cover HLS priority, non-HLS fallback and normalization. Four loader-policy tests cover capped delays, attempt limits, invalid/TLS failures and preserved non-retryable decisions. Four session-state tests cover redirected URL renewal, master/variant isolation, origin-loop prevention and diagnostic redaction. All ten player recovery tests continue to pass.
- On the physical phone, a synthetic Android data-source fixture verified HTTP 509 followed by a successful manifest renewal through the original address, subsequent loading from the renewed session URL, and unchanged segment-error handling. Production-engine checks again passed repeated live EOF recovery, pause cancellation, single first-playback notification, finite VOD ending once and permanent HTTP 401 rejection.
- A real-provider Sky Sport F1 HLS check completed 180 seconds after the first video frame with 4,510 frame callbacks, zero ended events, zero player/loader errors, zero player reconnects and zero rebuffer events. The available buffer stayed between 20,155 and 35,327 ms; the largest sampled frame-progress interval was 266 ms. Player audio and the phone's media volume were enabled. This is a three-minute check on this provider and phone, not a long-duration or multi-provider guarantee.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB all pass. The completed login/navigation changes from the preceding entry remain included. Validation used the isolated verified source baseline with these playback changes; concurrent catalog/metadata work remains outside this build.
- Installed the final version 0.1.9/versionCode 10 APK as a data-preserving update and opened Sky Sport F1 HD from watch history in the normal app, confirming visible race video. Temporary instrumentation was uninstalled, the local synthetic server stopped and its USB port mapping removed. The app was left playing with sound at the phone's current volume.

Development APK SHA-256: `ae8518fbebbc5140c6e204392666d4a9b5dfe5eaaec8afc9d13d1022718f182f`.

No store publication was performed. The buffer and recovery policy are documented in [playback lifecycle](CACHE_AND_PLAYBACK.md).

## Live TV recovery and combined login/navigation update — version 0.1.6

- Live channels now reopen a fresh media source at its default position after unexpected EOF, expired live windows, temporary transport failures or 30 seconds of continuous buffering. Recovery uses delays of 1/2/4/8/8 seconds, renews its budget after ten seconds of actual playback, and leaves manual Retry available after persistent failures. Pausing, backgrounding, audio-focus/noisy-output pauses and leaving cancel pending recovery. Movies and episodes do not gain automatic looping or reconnects.
- Reconnects display buffering feedback; successful preparation clears stale error UI. Invalid URI schemes, TLS handshake/certificate failures, unsupported decoding and permanent HTTP rejections remain failures. Ordinary TLS connection closures can still recover. No provider URLs or raw errors are logged by the recovery controller.
- Combined the existing HTTP/HTTPS support with the password visibility eye on the left, corrected login messages, and accessible Back/Logout icons. HTTP 401/403 and explicit authentication rejection produce the credential error; unexplained login failures use neutral wording. Saved connections remain encrypted.
- All 72 JVM tests pass: 3 model, 25 provider and 44 app tests. Ten new recovery tests exercise EOF, Media3's error-to-IDLE ordering, network/TLS disconnects, expired live windows, buffering watchdog renewal without a new state callback, short buffering, pause/audio-focus/background cancellation, release, retry backoff/exhaustion/manual retry, repeated 15-second provider interruptions and VOD isolation.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB all pass. Both lint variants report zero errors and zero warnings, with only the existing informational target-SDK hint. Verification used an isolated source copy to avoid OneDrive locks; final UI and playback sources were built together.
- Installed the combined debug APK on the connected Pixel 9 Pro XL as a data-preserving update and confirmed version 0.1.6/versionCode 7.
- Physical-player checks used the production `ExoPlaybackEngine` through temporary instrumentation and a local synthetic HTTP audio stream. Four playback starts across three end-of-stream reconnects succeeded, with one first-playback/history callback. Pausing during the pending reconnect stopped further playback; finite VOD ended once without looping; HTTP 401 remained failed without automatic source reload.
- The coordinated login/navigation chat separately verified the final UI on the same phone in an isolated test package: password initially hidden, left eye toggles without changing its contents, HTTP 401 shows the credential error, HTTP 503 shows neutral login wording, corrected credentials sign in, Back icon navigates correctly, and Logout icon opens confirmation with Cancel preserving the session. The isolated app was removed and its rotation/timeout settings restored.

Development APK SHA-256: `bcc96e0d7a124532377b33a23dfa3095eadc62ac070f7fe55782f048c1d07971`.

Recovery behavior and its limits are described in [playback lifecycle](CACHE_AND_PLAYBACK.md). No store publication was performed.

## Automatic HTTP and HTTPS support — version 0.1.4

- Login accepts both HTTP and HTTPS base server addresses, preserving custom ports and base paths. Removed the HTTP checkbox, its explanation and the stored per-connection flag. Existing encrypted saved connections still load; their legacy HTTP option is ignored.
- Playback and artwork accept HTTP and HTTPS automatically, including cross-protocol delivery redirects. The player still rejects local files and other URI schemes, API redirects remain rejected, and HTTPS uses platform certificate validation. HTTP account requests and delivery are unencrypted.
- Updated the server label and validation message in all ten interface languages. Removed the obsolete HTTPS-only and HTTP opt-in messages and aligned provider/security documentation.
- All 59 JVM tests pass: 3 model, 24 provider and 32 app tests. Coverage includes HTTP/HTTPS URL construction and credential encoding, actual HTTP authentication and catalog requests through the production transport with a local synthetic server, all three playback URL types, artwork fallback/downloads, invalid base URLs and unsupported stream schemes.
- Dependency policy verification, debug/release lint, debug APK, minified unsigned release APK and unsigned release AAB all pass. Verification uses an isolated source copy because Windows locks existing generated resources in the OneDrive checkout. Static project-page checks also pass.
- Installed the debug APK over version 0.1.3 on a connected Pixel 9 Pro XL using a data-preserving replacement. Confirmed installed version 0.1.4/versionCode 5 and successful app launch. Live-provider HTTP login and playback were not rechecked in this update; earlier playback observations below apply to their recorded versions.

Development APK SHA-256: `b576fda7afb7db959d43d0e8e2aa5a3fbaed47181e7a84d0b2a3150fd0664d93`.

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

Test the signed/minified build on hardware, tablets, API 26, long sessions, network recovery, large catalogs, subtitles/audio tracks, and mixed-direction metadata. Daily/weekly cache maintenance is scheduled and temp pruning is unit-tested; a multi-day background soak is still needed. Artwork viewport gating was exercised in actual grids but has no automated network-count instrumentation test yet. Upstream missing artwork still displays a placeholder. Next-episode playback, a simultaneous multi-channel guide and Android TV remain roadmap work; the latest version above implements durable movie/episode progress and a channel guide.
