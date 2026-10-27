# Store release checklist

Status: preparation only. Do not submit the current development package.

## Owner details and declarations

- [ ] Supply the legal publisher/developer name, country and required verified developer-account details.
- [ ] Supply a working support email, privacy contact and public support/privacy URLs.
- [ ] Complete the privacy draft, host it on a public HTTPS HTML page, and add its link or complete policy text inside the app. The current build does not yet expose a final privacy policy.
- [ ] Complete the Data safety worksheet using the actual provider/third-party data flows and optional HTTP behavior. Do not automatically select “no data collected” or “all data encrypted in transit”.
- [ ] Provide a dedicated, licensed reviewer account privately in Play Console using APP_ACCESS.md. Never put it in Git or marketing files.
- [ ] Complete ads (currently none), target audience, content rating, app access and all applicable App content declarations. Select an appropriate video-player category and accurate tags. Do not claim Android TV support yet.
- [ ] Review applicable content rights and provider terms. The app includes no channels, subscriptions or media. Confirm rights for every marketing asset and reviewer stream.
- [ ] Choose a source-code license before redistribution/external contributions. Public GitHub visibility alone is not a license.

## Build and quality

- [ ] Keep application ID `io.github.antocanza.iptvreceiver` only if the owner confirms it before the first release; it becomes a lasting store identity.
- [ ] Create/store the upload key securely outside Git, enroll in Play App Signing, and build/sign the release AAB. Preserve recovery details and increase versionCode for each upload.
- [ ] Distinguish the existing signed GitHub APK channel from the Play upload/app-signing setup; verify the intended certificate and update path before distributing the store build.
- [ ] Run `verifyDependencyPolicy`, unit tests, debug/release lint, minified APK and AAB builds. Review [dependency policy](../docs/DEPENDENCY_POLICY.md) and current audit.
- [ ] Current target API 36 meets the new-app requirement effective August 31, 2026; recheck the [target API policy](https://developer.android.com/google/play/requirements/target-sdk) at submission.
- [ ] Verify 16 KB page-size compatibility of any packaged native libraries; inspect the final bundle and Play's checks.
- [ ] Test the signed/minified build on API 26 and current Android, tablets, rotation/fullscreen, interruptions, poor networks, large catalogs, subtitles/audio tracks, Arabic layout and CJK text.
- [ ] Validate background cache maintenance through WorkManager test tools and a multi-day soak. Android schedules it opportunistically; exact execution times are not promised.
- [ ] Review all translations with native speakers and all localized listing copy. Supply tablet screenshots after tablet validation.
- [ ] Upload to internal testing, inspect Play's pre-launch report and fix findings. Complete any account-specific closed-testing requirements shown by Console before production access.

## Listing package

- [ ] Review title (30-character limit), short description (80) and full description (4000), localization and claims.
- [ ] Inspect the 512px icon, 1024×500 feature graphic and at least two compliant actual-UI screenshots. Included four 1080×1920 phone shots use an illustrative offline catalog. Replace them if the UI changes.
- [ ] Refresh the older preview screenshots and review all ten listing drafts against 0.1.14: root catalog search, durable progress/watched status, channel guide/eligible replay and parental controls. Keep Android TV/Windows/webOS/Tizen and automatic next-episode playback described as planned.
- [ ] Keep promotional video optional until an original/cleared video exists. No TV banner is required for this phone/tablet-only release.
- [ ] Review the final Console changes and obtain explicit owner authorization before any store publication.

Sources: [listing setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [review preparation](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en), [user-data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). Requirements can change; Console is the final account-specific checklist.
