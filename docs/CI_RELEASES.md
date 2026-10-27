# Latest Android APK

The Android workflow in [android.yml](../.github/workflows/android.yml) verifies pull requests and builds `main`. A successful push to `main`, or a manual run selecting `main`, publishes the optimized, signed APK to one rolling **Latest Android APK** release.

**[Download the latest APK](https://github.com/AntoCanza/iptv-receiver/releases/latest/download/iptv-receiver.apk)** · [Release page](https://github.com/AntoCanza/iptv-receiver/releases/latest) · [Build runs](https://github.com/AntoCanza/iptv-receiver/actions/workflows/android.yml)

The APK supports Android 8.0 or later. It contains the application's development baseline; publication on GitHub does not constitute a Google Play release or broaden the device/provider verification in [VERIFICATION.md](VERIFICATION.md).

## Install and identify a build

Download `iptv-receiver.apk` and open it on an Android phone or tablet. Android may ask you to allow installation from the browser/file manager. An update signed with the same certificate preserves local app data; uninstalling first removes local records. See the [user guide](USER_GUIDE.md#install-or-update).

The release notes identify the app version, versionCode and exact source commit. Because `latest` moves, use that commit to identify a build; the fixed download URL always serves the currently published APK. Download `iptv-receiver.apk.sha256` alongside it and compare the file's digest before installation:

```powershell
Get-FileHash .\iptv-receiver.apk -Algorithm SHA256
Get-Content .\iptv-receiver.apk.sha256
```

On Linux/macOS, use `sha256sum -c iptv-receiver.apk.sha256` where that command is available. The checksum detects a mismatched/corrupt download; the APK certificate determines update compatibility.

## Build and publication

1. The verification job runs dependency policy, model/provider/app unit tests, debug/release lint, debug/release builds and the release-policy tests.
2. On `main`, the unsigned optimized APK is transferred to the publishing job as a temporary artifact.
3. The publishing job aligns the APK for 16 KiB pages, signs it using private Actions secrets and verifies its certificate against the checked-in public fingerprint.
4. The publisher checks that the verified commit is still the head of `main`. It uploads the APK and its SHA-256 checksum under temporary names and verifies GitHub's size/digest metadata before replacing the public files.
5. The `latest` tag points to that source commit. The release notes identify the application version, Android versionCode and source commit. The APK filename and download URL stay the same.
6. Previous release records/assets and old `development-apk`/`unsigned-apk` Actions artifacts are removed after successful publication. Code remains available through Git commit history. Verification reports retain their seven-day limit; temporary APK transfer artifacts have a one-day fallback retention and are removed by the publishing job even if signing fails.

A failed verification/signing/upload keeps the previous working download. Activation failures attempt to restore the previous filenames and tag. Main runs are serialized so publishing is not cancelled mid-replacement. A run whose commit has been superseded skips publication. The rolling release must remain mutable; repository release immutability is currently disabled.

The first successful main run creates the release automatically. Subsequent runs replace it. Manual runs from other branches perform verification without publishing.

Documentation-only pushes to `main` also run this workflow and can replace the rolling APK after verification. The separate website-check workflow validates HTML on documentation changes, while GitHub Pages publishes `main:/docs` through its branch source.

## Signing and permissions

The existing development installation's signing key is used for this APK channel so it can update installed copies while retaining their local data. The optimized release build is signed after compilation; signing configuration is not embedded in Gradle or committed files. This key is not a claim of Google Play production signing readiness.

The repository has these encrypted Actions secrets:

| Secret | Purpose |
| --- | --- |
| `ANDROID_SIGNING_KEYSTORE_BASE64` | Base64-encoded private signing keystore |
| `ANDROID_SIGNING_STORE_PASSWORD` | Keystore password |
| `ANDROID_SIGNING_KEY_PASSWORD` | Private-key password |
| `ANDROID_SIGNING_KEY_ALIAS` | Signing-key alias |

The public SHA-256 certificate fingerprint is in [android-signing-certificate.sha256](../.github/android-signing-certificate.sha256). It must match the certificate already used by installed APKs. Keep a private backup of the corresponding keystore outside Git; the GitHub secrets API cannot return its plaintext value. Signing passwords and key bytes are never printed. Temporary keystores are deleted after signing.

Pull-request verification has read-only repository access and receives no signing secrets. Only the main publishing job gets `contents: write` for release/tag management and `actions: write` for obsolete APK-artifact cleanup. It uses GitHub's short-lived `GITHUB_TOKEN`; no personal access token is stored in the workflow. Actions are pinned to reviewed commit hashes.

Application versions remain controlled by `versionName` and `versionCode` in [app/build.gradle.kts](../app/build.gradle.kts). Increment versionCode for each new application version; rebuilding the same version updates the rolling download and records its exact source commit.

## Verification

The release scripts use the Python standard library. Their synthetic tests exercise successful replacement, upload/digest/activation failures, stale main commits, rollback, first-release cleanup, immutable-release rejection and the pull-request publication boundary:

```sh
python3 -m unittest discover -s tools/tests -v
actionlint .github/workflows/android.yml
```

APK signature verification uses Android SDK Build Tools 36.0.0. No provider account, provider credential or playback URL belongs in CI or its artifacts.
