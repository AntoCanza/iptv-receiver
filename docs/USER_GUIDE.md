# IPTV Receiver user guide

This guide describes Android **0.1.14 (versionCode 15)** for phones and tablets running Android 8.0 or later. Android TV, Windows, LG webOS and Samsung Tizen apps remain planned. The app requires your own Xtream-compatible provider account and supplies no channels, subscriptions or media.

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

Home opens **Live TV**, **Movies**, **Series**, **TV guide** and **Watch history**, with a separate **Settings** action.

- At a content section's root, search or choose its all-content option to load titles across the connected account's categories. Inside a category, search stays within that category. Separate accounts are not combined.
- Search uses available title, year and genre metadata. Relevance puts exact titles first unless you explicitly choose another sort. Provider order, title, newest and rating sorts and genre filters use available listing fields; missing values remain unknown.
- Movies open details with a Play action. Series open a season picker and episode list. Descriptions, credits, artwork, runtimes and trailers depend on provider metadata. Trailers open externally after a tap.
- Query, filters and scroll position survive returning from details or playback during the session. Catalogs are held in memory and may need downloading again after reopening the app; there is no durable catalog cache yet.

Large-font or short-window layouts use compact rows and scrolling controls. Long-press utility icons for help. Provider titles and descriptions keep their original language.

## Playback controls

Tap the player to show its controls. Fullscreen uses landscape; Back exits fullscreen before leaving playback. Rotation retains the current playback session. Backgrounding pauses it, and leaving the player releases it.

The screen stays awake while media plays. Pausing, stopping, finishing or a playback failure lets Android turn the display off after its normal inactivity timeout. Brief buffering and live reconnects keep it awake for up to one minute; exhausted retries release it sooner. Touching or operating the app resets Android's inactivity timer. No setting or extra permission is needed, and the app does not change your device's screen timeout.

Display settings offer **Fit**, **Zoom** and **Stretch**, separately from **Auto**, **16:9**, **4:3** and **21:9** aspect ratios. Zoom can crop the picture; Stretch can distort it. Choices survive rotation and reconnects within the player session.

The subtitle icon appears when text tracks are available and includes **Off**. Audio selection appears for multiple audio tracks. Stream information shows reported formats and timeline values; unknown values stay unknown. Track availability depends on the actual stream, and a listed subtitle track may not carry visible cues. Display choices do not change source resolution or bitrate.

Live playback prefers provider-advertised HLS, buffers ahead and retries temporary interruptions. This can delay startup and place viewing behind the broadcast. Persistent errors expose Retry; rejected access, certificate errors and unsupported decoding require resolving the underlying issue. See [playback behavior](CACHE_AND_PLAYBACK.md) for exact recovery limits.

## Progress, watched status and history

Movie details and episode rows show progress, duration/remaining time when known, watched status, **Resume** and **Start over**. Resume becomes available after five seconds of actual playback. Positions save periodically and when seeking, pausing, backgrounding or leaving; a sudden process kill can lose the last few seconds.

Playback ending or reaching 95% of a known duration marks a title watched. Rewatching keeps that flag while saving a new position. **Mark watched** and **Mark unwatched** allow manual corrections; marking unwatched clears the current position. Opening details or manually marking a title does not add a history entry.

Series **Continue** selects the last unfinished regular episode, including a brief start before the resume threshold; after completion it selects the next unwatched episode in numeric season/episode order. The continuation season is selected and season counts show watched episodes. Automatic next-episode playback is not implemented.

**Watch history** lists actual playback starts, newest first, loading older entries in pages. Movies and episodes remain for **180 days**; live channels for **10 days**. Watched flags and resume points persist beyond those windows until explicitly cleared. Live and catch-up playback do not create movie/episode bookmarks.

Records stay separate for each provider account and survive logout and cache cleanup. Reconnecting with the same server/account restores them. **Settings → Watch history and progress → Clear for this account** asks for confirmation and clears the connected account's records. There is no cloud sync or data export. Android Clear storage or uninstalling removes all local data.

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
