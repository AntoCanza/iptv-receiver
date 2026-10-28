# IPTV Receiver user guide

This guide describes Android **0.1.16 (versionCode 17)** for phones and tablets running Android 8.0 or later. Android TV, Windows, LG webOS and Samsung Tizen apps remain planned. The app requires your own Xtream-compatible provider account and supplies no channels, subscriptions or media.

## Install or update

1. Open the [latest GitHub release](https://github.com/AntoCanza/iptv-receiver/releases/latest) and download **iptv-receiver.apk**.
2. Open the APK on your Android device. If prompted, allow your browser or file manager to install this app, then continue.
3. Install over an existing copy signed with the same certificate to retain local data. Uninstalling or clearing Android app storage removes saved connections, history, progress and parental settings.

The release includes a SHA-256 checksum and identifies its source commit. This is a development build; Google Play publication remains pending. If Android reports a signing conflict, check which build you installed before removing it and its local data. See [release policy](CI_RELEASES.md) for the signing and download channel.

## Connect your provider

Enter the provider's **HTTP or HTTPS base server address**, username and password. Include its custom port or base path when required; do not paste a playlist, stream URL or address containing a username/password query. The connection name is optional. The eye icon reveals or hides the password.

**Remember connection** is optional and off by default. When selected, the connection is encrypted on this device using Android Keystore. Otherwise it remains in memory for the session. An unfinished login survives rotation in memory; process death discards it. Account validation rejects inactive or expired accounts.

HTTP works automatically for account, media and artwork requests. Prefer HTTPS when the provider supports it: HTTP traffic is unencrypted. There is no HTTP compatibility switch. Keep credentials and provider URLs out of screenshots, issues and logs.

## Browse and search

Home opens **Live TV**, **Movies**, **Series**, **TV guide**, **Watch history** and **Downloads**, with a separate **Settings** action.

- At a content section's root, search or choose its all-content option to load titles across the connected account's categories. Inside a category, search stays within that category. Separate accounts are not combined.
- Search uses available title, year and genre metadata. Relevance puts exact titles first unless you explicitly choose another sort. Provider order, title, newest and rating sorts and genre filters use available listing fields; missing values remain unknown.
- Movies open details with a Play action. Series open a season picker and episode list. Descriptions, credits, artwork, runtimes and trailers depend on provider metadata. Trailers open externally after a tap.
- Query, filters and scroll position survive returning from details or playback during the session. Catalogs are held in memory and may need downloading again after reopening the app; there is no durable catalog cache yet.

Large-font or short-window layouts use compact rows and scrolling controls. Long-press utility icons for help. Provider titles and descriptions keep their original language.

## Playback controls

Tap the player to show its controls. Fullscreen uses landscape; Back exits fullscreen before leaving playback. Rotation retains the current playback session. A visible picture-in-picture window keeps playing; closing it or locking the phone pauses it, and leaving the player releases it.

The screen stays awake while media plays. Pausing, stopping, finishing or a playback failure lets Android turn the display off after its normal inactivity timeout. Brief buffering and live reconnects keep it awake for up to one minute; exhausted retries release it sooner. Touching or operating the app resets Android's inactivity timer. No setting or extra permission is needed, and the app does not change your device's screen timeout.

Display settings offer **Fit**, **Zoom** and **Stretch**, separately from **Auto**, **16:9**, **4:3** and **21:9** aspect ratios. Zoom can crop the picture; Stretch can distort it. Choices survive rotation and reconnects within the player session.

The subtitle icon appears when text tracks are available and includes **Off**. Audio selection appears for multiple audio tracks. Stream information shows reported formats and timeline values; unknown values stay unknown. Track availability depends on the actual stream, and a listed subtitle track may not carry visible cues. Display choices do not change source resolution or bitrate.

Live playback prefers provider-advertised HLS, buffers ahead and retries temporary interruptions. This can delay startup and place viewing behind the broadcast. Persistent errors expose Retry; rejected access, certificate errors and unsupported decoding require resolving the underlying issue. See [playback behavior](CACHE_AND_PLAYBACK.md) for exact recovery limits.

## Picture-in-picture

Picture-in-picture (PiP) keeps a small video window above other apps. It uses the current playback session for live TV, movies, episodes and completed downloads. A downloaded video continues reading its local file; PiP does not require a new provider login or network connection for that file.

### Open the floating window

- **While playing:** press Home, use the Home gesture or switch to another app. The video moves into PiP automatically. Buffering or a temporary live reconnect can also continue there while playback is still requested.
- **With the player button:** tap the video to reveal the controls, then choose **Picture-in-picture**. This also works while paused and keeps the video paused until you press Play.

Leaving a paused video with Home does not open a floating window or resume playback. Browsing the catalog without an active player does not open one either. Back keeps its usual behavior: exit fullscreen first, then leave the player.

### Move, resize and control the video

| Action | How it works |
| --- | --- |
| Move | Drag the window. Android snaps it to supported positions near the screen edges; placement follows the system's rules. |
| Resize with two fingers | On Android 12+, place two fingers inside the window and spread or pinch them. Stop at the size you want within Android's minimum and maximum limits. |
| Switch sizes quickly | On Android 12+, double-tap the video to switch between the sizes provided by the system. |
| Show controls | Tap the window once. Android shows the available playback actions, expand and close controls. |
| Pause or play | Use **Pause** or **Play** in that menu. Moving or resizing preserves the current play/pause state. |
| Seek | **Back 10 seconds** and **Forward 10 seconds** appear for seekable non-live video. Seeking is bounded by the beginning and any known end time. |
| Return to the full player | Use Android's expand control. Playback continues at its current position, or stays paused if you paused it. |
| Close | Use Android's close control. Playback pauses and movie/episode progress is saved. |

Android owns the window's size, position, animations and menu. Gesture details and available sizes can differ by Android version and device manufacturer. Resizing changes the size of the window, not the stream's resolution or the video's aspect ratio. To change **Fit/Zoom/Stretch** or the aspect setting, expand and use the app's Display controls. Selected subtitles remain visible in PiP; expand to change subtitles, audio tracks, speed or other options.

### Home, closing and screen lock

| What you do | What happens next |
| --- | --- |
| Go Home while video is playing | The floating video keeps playing when PiP is available. |
| Go Home after pausing | Playback remains paused; no window opens automatically. |
| Pause inside PiP, then expand | The full player remains paused at the same position. |
| Close the floating window, then reopen the app | Playback does not restart automatically. Open the title and choose Play or Resume to continue if the player is no longer open. |
| Lock the phone or turn its screen off | Playback pauses, including when PiP was visible. |
| Wake or unlock the phone | Playback stays paused until you choose Play or Resume. The system may restore the window or require reopening the app. |

Audio does not continue while the phone is locked or after the window is closed. For a movie or episode, the saved bookmark supports later Resume. A live channel has no equivalent durable bookmark: pausing does not record the broadcast, and resuming after its available stream window expires may reconnect nearer the live broadcast. Live channels therefore do not show the ten-second seek buttons.

### Enable PiP and solve common problems

PiP requires a supported Android 8.0+ device and Android's permission for this app. A common settings path is **Android Settings → Apps → Special app access → Picture-in-picture → IPTV Receiver → Allow picture-in-picture**. The names and location vary by device. The app uses this system PiP setting and does not request a separate overlay permission.

| Problem | What to check |
| --- | --- |
| Home pauses the video instead of showing PiP | Check the app's PiP permission and that playback was not already paused, ended or failed. Try the player’s Picture-in-picture button. |
| The PiP button is missing or reports unavailable | Tap the player to reveal its toolbar. Some devices do not support PiP; on supported devices, check Android's app settings. If PiP is unavailable, normal background pausing still applies. |
| The window snaps away from where I release it | Android chooses the allowed edge positions. This is normal system behavior. |
| I cannot resize, or it stops getting larger/smaller | Pinch/double-tap resizing requires Android 12+. Start both fingers inside the video and respect the system's size limits; behavior can vary by manufacturer. |
| Seek buttons are missing | Live streams and non-seekable media do not offer these actions. Expand for the controls supported by that stream. |
| Playback remains paused after unlocking or reopening | This is intentional. Choose Play or Resume when you are ready. |
| Video buffers or fails in the small window | Expand to inspect the playback message and use Retry where offered. PiP uses the same connection, decoder and recovery behavior as the full player. |

See [verification](VERIFICATION.md) for the physical devices and interactions actually checked. Support on one phone does not establish identical behavior on all Android versions, tablets or manufacturer interfaces.

## Progress, watched status and history

Movie details and episode rows show progress, duration/remaining time when known, watched status, **Resume** and **Start over**. Resume becomes available after five seconds of actual playback. Positions save periodically and when seeking, pausing, closing or leaving the player; a sudden process kill can lose the last few seconds.

Playback ending or reaching 95% of a known duration marks a title watched. Rewatching keeps that flag while saving a new position. **Mark watched** and **Mark unwatched** allow manual corrections; marking unwatched clears the current position. Opening details or manually marking a title does not add a history entry.

Series **Continue** selects the last unfinished regular episode, including a brief start before the resume threshold; after completion it selects the next unwatched episode in numeric season/episode order. The continuation season is selected and season counts show watched episodes. Automatic next-episode playback is not implemented.

**Watch history** lists actual playback starts, newest first, loading older entries in pages. Movies and episodes remain for **180 days**; live channels for **10 days**. Watched flags and resume points persist beyond those windows until explicitly cleared. Live and catch-up playback do not create movie/episode bookmarks.

Records stay separate for each provider account and survive logout and cache cleanup. Reconnecting with the same server/account restores them. **Settings → Watch history and progress → Clear for this account** asks for confirmation and clears the connected account's records. There is no cloud sync or data export. Android Clear storage or uninstalling removes all local data.

## Downloads and offline viewing

Choose **Download for offline viewing** on a supported movie or episode. Direct MP4, MKV, TS, M4V, WebM, MOV and supported audio files are eligible; adaptive playlists, live-channel recording and offline DRM licensing are outside this first version. A provider can still return an unsupported playlist/error page behind a video address; that download is rejected instead of being marked complete.

Open **Downloads** from Home to see queued, downloading, paused, ready, failed and missing files, their size and total storage used. Pause/resume transfers, retry failures and play completed files with the normal player. Resume continues at the saved viewing position; **Start over** starts at zero. Select individual or multiple entries to remove them after confirmation. Cancelling an active download also removes its partial file. Media removal preserves watch history/progress.

**Settings → Downloads** chooses private app storage or a local folder through Android's folder picker, including supported SD/USB storage. Some folders are restricted by Android. Cloud document providers are not supported. The setting affects new downloads; existing files keep their location and required folder grants. Files in a chosen shared folder can also be opened by other apps. The default transfer setting allows unmetered connections only; disable it to permit metered connections. Android may delay background jobs.

Completed downloads remain until removed and are excluded from ordinary cache cleanup. The login screen offers **Downloads** for the last connected account, so a cold launch can play saved content without provider authentication. Explicit logout hides that library and removes unfinished request credentials; reconnecting to the same account restores access. Switching accounts does not combine libraries. Current parental rules still apply offline; blocked entries show a generic label and can be removed without revealing their title.

Watch state is saved to the same account-scoped database used for streaming. A moved/deleted file, revoked folder grant or disconnected drive appears as unavailable. Reconnect storage or grant the original folder again before retrying access. Downloaded media is a local copy: it does not repair missing codecs, add subtitle/audio tracks or guarantee provider download eligibility. App-data clearing/uninstallation removes private app media and its index; files in a chosen shared folder can remain and must be removed there if no longer needed.

## TV guide and replay

Open **TV guide** from Home, or use the guide action for the current live channel. Browse/search current, upcoming and past programmes; times use the device's zone and interface language. Overlapping provider entries are flagged as conflicts.

Replay appears only for ended programmes whose channel and programme advertise an eligible archive within the provider's retention window. The provider supplies the schedule and archive media, so availability and programme identity can vary. Opening the guide from a live player closes that player before another stream starts. A simultaneous multi-channel grid and XMLTV import remain planned.

## Settings, parental controls and reminders

Settings contains account details/logout, server information, language, parental controls and viewing-data clearing. Connection counts and server clock are snapshots from sign-in. Language follows the device by default, with English fallback and ten explicit choices; Android 13+ also offers per-app language settings.

Adult content is blocked by default, even before a parental password is created. The protected panel lets you create/change a password and block additional categories. Unlocking the panel does not disable the content restrictions. Classification uses provider metadata and recognizable labels; manually lock categories when necessary.

A forgotten parental password cannot be recovered. Reset removes the password and **all accounts' custom category locks**, restores adult blocking to enabled and requires new password setup. Read the confirmation before continuing. Unreadable parental settings block content until reset. See [security](../SECURITY.md#parental-controls) for limits.

Subscription reminders appear while browsing from two calendar months before the provider's recorded expiry date, with more frequent repeats near expiry. Dismissing a reminder acknowledges it; playback defers popups until browsing resumes. They are in-app prompts, not system notifications. A renewed expiry is picked up through authentication on reopening.

Logout asks for confirmation. Cancel or Back keeps the account connected. Confirming removes the remembered connection and hides its viewing data while retaining that data and parental settings locally.

## Troubleshooting and verification limits

- **Login rejected:** verify the base address, username/password and account status with the provider. A neutral sign-in error can also reflect networking or an unexpected response.
- **Missing titles or artwork:** check category/genre/search choices, parental restrictions and provider metadata. The app cannot repair missing files or incorrectly labeled content.
- **No guide/replay or wrong programme:** the provider may omit archive flags, offer an expired archive or supply an inconsistent schedule. Report it without publishing private URLs or account data.
- **No video, subtitles or audio choice:** formats/tracks vary by provider and device. Retry temporary failures and check the [recorded compatibility limits](VERIFICATION.md); a successful login does not guarantee every stream will decode.
- **Large catalog will not load:** full catalogs use a bounded 128 MiB transfer and stay in memory. Larger catalogs or providers without unfiltered endpoints need further integration work.

Physical checks cover selected streams on a Pixel 9 Pro XL. The latest progress/UI checks also use a fictional catalog and loopback audio fixture. API 26, tablets, visual RTL, broader providers, actual subtitle cues and the minified build on hardware remain outside that evidence. Read [verification](VERIFICATION.md) for the exact versions, checks and limits, and [the roadmap](ROADMAP.md) for remaining work.
