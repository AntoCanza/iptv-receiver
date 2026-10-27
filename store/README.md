# Google Play preparation

This package prepares IPTV Receiver for a future phone/tablet listing. Nothing has been uploaded or published. Publisher identity, support contact, privacy hosting, production signing and Console declarations still require the owner's details and review.

The Android source baseline is 0.1.14 (versionCode 15). A separate [signed GitHub APK channel](../docs/CI_RELEASES.md) is available; that does not constitute Google Play publication or upload-key readiness. Use the [user guide](../docs/USER_GUIDE.md) and [verification record](../docs/VERIFICATION.md) when reviewing listing claims.

## Included assets

- `assets/play-icon-512.png`: square 512 × 512 PNG, original receiver/play symbol. Android adaptive and monochrome variants are installed in the application resources.
- `assets/feature-graphic-1024x500.png`: opaque 1024 × 500 PNG.
- `screenshots/`: 1080 × 1920 English phone screenshots captured from an earlier debug UI with an offline fictional catalog. Captioned exports retain the app's proportions. No provider content, private account or third-party branding is used. Refresh these before submission to show the current navigation, progress and controls.
- `listings/listings.json`: titles, short descriptions and full description drafts for all ten interface languages, updated for search, progress, channel guide and parental controls.
- `privacy-policy-draft.html`, `DATA_SAFETY.md`, `APP_ACCESS.md`, `RELEASE_CHECKLIST.md`: review documents. Draft placeholders must be completed before submission.

Generate the unsigned release Android App Bundle with `gradlew.bat :app:bundleRelease` on Windows or `./gradlew :app:bundleRelease` on Linux/macOS. It is written under `app/build/outputs/bundle/release/`. It cannot be submitted until signed with the owner's upload key and configured for Play App Signing. The rolling GitHub release distributes an APK rather than a Play-ready AAB.

## Reproduce visuals

The original artwork, fictional-catalog previews, listing text and export scripts in this package are covered by the project's [MIT License](../LICENSE). Keep the copyright and license notice when reusing them. Third-party tool dependencies retain their own licenses; this permission does not cover real provider content or third-party branding.

Use Node.js LTS satisfying `store/tools/package.json` (Node 22 or later). From the repository root, run `npm ci --prefix store/tools`, then `node store/tools/export-assets.cjs`; the lockfile pins Sharp 0.35.5. This exports the editable SVG sources, PNGs, native launcher resources and debug-only poster fixtures. Run `node store/tools/export-screenshots.cjs <private-capture-directory>` after capturing the listed screens. Never commit captures from a real account.

For screenshot capture only, start the debug build with `adb shell am start --activity-single-top -n io.github.antocanza.iptvreceiver/.MainActivity --ez store_preview true`. Navigate normally. The fixture is fully offline and stores its fake connection in memory. This capture mode supplies no playable media; the separate progress-device runner can attach a synthetic loopback audio fixture. Start with `--ez store_preview false` afterward. Release builds ignore this option and exclude the catalog and poster assets. Preserve/restore real viewing data and preferences if using a device that also holds a personal account.

Asset requirements checked October 25, 2026 against [Google Play preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en-GB) and [icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications). Recheck at submission.
