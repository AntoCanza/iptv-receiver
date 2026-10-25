# Data safety worksheet

Draft for owner review. This is evidence for answering the Console form, not a submitted declaration.

| Flow | Current implementation | Declaration decision to review |
| --- | --- | --- |
| Connection name, server, username and password | Runtime input; optionally encrypted on-device with Android Keystore; removed locally on sign out; backup disabled | Authentication data leaves the device to the user-selected provider. Evaluate the applicable collection, sharing, user-initiated transfer and service-provider definitions; do not assume “no collection”. |
| Provider API requests | HTTPS; Xtream credentials are query parameters sent to that provider | Explain authentication purpose and identify the recipient categories. Provider server retention is controlled by the provider. |
| Video and artwork requests | Device IP and normal network headers go to provider/CDN/artwork hosts. Stream URLs may carry credentials. HTTP delivery requires a separate default-off opt-in | Optional HTTP means a blanket “all user data encrypted in transit” answer is unsafe. Redirects may use other hosts; disclose these flows. |
| Catalog and artwork | Catalog in memory; private image caches limited to 100 MB disk and 24 MB memory across two policies | Cache persists temporarily on-device. Daily maintenance prunes old app temp files and clears image disk caches approximately weekly while backgrounded. |
| Viewing activity | Current playback/session held in memory; no durable watch history or analytics implemented | Verify final release and provider logging. Provider request logs can reveal selections even though the app has no analytics backend. |
| Advertising, analytics, crash-upload SDKs | None configured | Recheck resolved dependencies and final release. Adding any SDK requires this worksheet and policy to be updated. |

The developer currently operates no account or streaming backend for this app. The app does not create IPTV accounts; provider account deletion must be requested from the provider. Signing out removes the remembered connection locally, not the provider's account. Clearing Android app storage removes the app's local data and cache.

Before answering Console, review its current [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), finalize the publisher's practices, and consider every off-device transfer. The app does not request storage/media-library, camera, microphone, location or contacts permissions. Its declared runtime functionality uses Internet access; WorkManager may merge background scheduling/wake-lock permissions.
