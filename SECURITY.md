# Security policy

Do not post credentials, playlist URLs, provider responses or stream URLs in public issues, pull requests, screenshots, recordings or crash reports. Contact the repository owner through a private channel for a security report; use GitHub private vulnerability reporting if enabled.

## Credential handling

- Users enter their provider connection at runtime. No subscription is bundled.
- Remembering a connection is optional and off by default.
- Saved connections are encrypted with AES-GCM using a random IV and a key in Android Keystore.
- App backup is disabled. Passwords do not enter saved-state bundles.
- Sign out removes encrypted saved connection data and clears in-memory session selections.
- Account-scoped watch history remains in app-private files for a rolling 10 days, including after sign out or cache cleanup. It stores content/episode identifiers, display metadata and last-watch timestamps, without playback/artwork URLs or account credentials. Android Clear storage removes it.
- Model `toString` methods redact credential-bearing values.
- HTTP logs and analytics integrations are absent. Do not add raw network logging.
- Account/catalog HTTPS redirects are rejected; platform TLS validation remains enabled.
- Account/catalog requests always require HTTPS, regardless of playback options.
- HTTP playback requires the separate **Allow HTTP streams** option for that connection; it defaults to off. Its explanation appears beside the login option.
- Android's manifest must permit cleartext transport for arbitrary provider delivery hosts. A guarded Media3 data source enforces the per-connection policy before every manifest/segment request, and cross-protocol redirects are disabled unless the connection opted in. Artwork is restricted to HTTPS.
- HTTP video delivery is unencrypted and can expose stream URLs to the network. The playback option does not make HTTP secure and never relaxes TLS certificate verification.

Runtime memory still contains credentials while a connection is active. Keystore protects the stored key; it cannot hide credentials from a fully compromised device. Avoid making stronger security claims.

## Public source and CI

- `.gitignore` excludes local settings, secrets, signing material and build outputs.
- Automated tests use synthetic data and require no provider account.
- CI has read-only repository permissions and does not run `pull_request_target` code from contributors.
- GitHub Actions are pinned to commit SHAs; Dependabot proposes updates for review.
- The Gradle distribution is checksum-pinned and the official Wrapper JAR is committed.
- Never embed release signing keys, personal tokens or private provider configuration.
- Before pushing, review the staged diff and scan the complete Git history for accidental sensitive values.

Public visibility and an ignore rule do not remove a secret already committed. If a secret is accidentally published, revoke/rotate it and coordinate history cleanup with the owner.
