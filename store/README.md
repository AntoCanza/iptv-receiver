# Google Play preparation

This package prepares IPTV Receiver for a future phone/tablet listing. Nothing has been uploaded or published. Publisher identity, support contact, privacy hosting, production signing and Console declarations still require the owner's details and review.

## Included assets

- `assets/play-icon-512.png`: square 512 × 512 PNG, original receiver/play symbol. Android adaptive and monochrome variants are installed in the application resources.
- `assets/feature-graphic-1024x500.png`: opaque 1024 × 500 PNG.
- `screenshots/`: 1080 × 1920 English phone screenshots captured from the real debug UI with an offline fictional catalog. Captioned exports retain the app's proportions. No provider content, private account or third-party branding is used.
- `listings/`: English and Italian copy plus localized short descriptions for all ten interface languages.
- `privacy-policy-draft.html`, `DATA_SAFETY.md`, `APP_ACCESS.md`, `RELEASE_CHECKLIST.md`: review documents. Draft placeholders must be completed before submission.

An unsigned release Android App Bundle is supplied separately with the delivery package. It cannot be submitted until signed with the owner's upload key and configured for Play App Signing.

## Reproduce visuals

Install Node.js LTS and Sharp 0.35.5 (`npm install --no-save sharp@0.35.5` in an isolated tools directory), make it available to Node, then run `node store/tools/export-assets.cjs`. This exports the editable SVG sources, PNGs, native launcher resources and debug-only poster fixtures. Run `node store/tools/export-screenshots.cjs <private-capture-directory>` after capturing the listed screens. Never commit captures from a real account.

For screenshot capture only, start the debug build with `adb shell am start --activity-single-top -n io.github.antocanza.iptvreceiver/.MainActivity --ez store_preview true`. Navigate normally. The fixture is fully offline, stores its fake connection in memory, and cannot play media. Start with `--ez store_preview false` afterward. Release builds ignore this option and exclude the catalog and poster assets.

Asset requirements checked October 25, 2026 against [Google Play preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en-GB) and [icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications). Recheck at submission.
