# Security policy

Do not post credentials, playlist URLs, provider responses or stream URLs in public issues, pull requests, screenshots, recordings or crash reports. Contact the repository owner through a private channel for a security report; use GitHub private vulnerability reporting if enabled.

## Credential handling

- Users enter their provider connection at runtime. No subscription is bundled.
- Remembering a connection is optional and off by default.
- Saved connections are encrypted with AES-GCM using a random IV and a key in Android Keystore.
- App backup is disabled. Passwords do not enter saved-state bundles. The unfinished login form stays in an activity ViewModel across configuration changes and is cleared after successful sign-in; process death discards it.
- Password entry is hidden initially. A trailing eye button reveals/hides it in memory; visibility is not persisted across activity recreation.
- Sign out removes encrypted saved connection data and clears in-memory session selections.
- Account-scoped watch history remains in app-private files for a rolling 10 days, including after sign out or cache cleanup. It stores content/episode identifiers, display metadata and last-watch timestamps, without playback/artwork URLs or account credentials. Android Clear storage removes it.
- Model `toString` methods redact credential-bearing values.
- HTTP logs and analytics integrations are absent. Do not add raw network logging.
- Account/catalog redirects are rejected; platform TLS validation remains enabled for HTTPS.
- Account/catalog requests accept HTTP and HTTPS base addresses. Embedded account details, query parameters and fragments in the base address are rejected.
- HTTP and HTTPS video and artwork work automatically, including cross-protocol delivery redirects. There is no per-connection HTTP option; legacy saved options are ignored when loading a connection.
- Android's manifest permits cleartext transport for arbitrary provider hosts. A guarded Media3 data source accepts only HTTP and HTTPS before every manifest/segment request. Artwork tries HTTPS first for an HTTP image, then keeps the supplied HTTP URL as a fallback.
- HTTP requests are unencrypted and can expose credentials, catalog queries, artwork and stream URLs to the network. Prefer HTTPS when available. HTTP support never relaxes TLS certificate verification.

Runtime memory still contains credentials while a connection is active. Keystore protects the stored key; it cannot hide credentials from a fully compromised device. Avoid making stronger security claims.

Movie/episode progress and expiry acknowledgements use separate versioned, atomically replaced app-private files outside cache directories. Account filenames are hashed from normalized server/account identifiers. Progress stores content/episode IDs, position, optional duration and completion only; acknowledgement stores the provider expiry and last dismissal time. Neither contains credentials or playback URLs. Logout hides these records without deleting them; app-data clearing/uninstallation removes them. Existing parental checks still run before offering or resolving resumed playback.

## Parental controls

- Adult blocking defaults to enabled, including before a parental password is created and after its reset. Provider adult flags, recognizable adult labels and category descendants are filtered from browsing, full-catalog search, details, history and stream resolution. Parents can block additional categories for each provider account and content type. Missing category membership fails closed when categories are blocked.
- The protected Settings panel creates a password at first access, asks for confirmation and explicitly warns that it cannot be recovered. Leaving the panel or backgrounding the app clears access and password fields. Unlocking the panel does not itself remove content restrictions.
- A random 16-byte salt and a 32-byte PBKDF2-HMAC-SHA256 verifier with 600,000 iterations are stored in an atomic, versioned app-private file outside caches. Hashing and writes run off the UI thread. Plaintext passwords and access grants never enter saved-state bundles. Five incorrect checks cause a 30-second in-process delay. Parameters follow the [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2); Android supports this algorithm from the minimum API 26.
- Reset is available without the forgotten password only together with resetting **all** protected options: enable adult blocking, remove every account's manual category blocks and require a new password. A confirmation explains these consequences. Password changes preserve options. Logout and cache cleanup preserve parental settings.
- Old history is checked against current provider identifiers before display or playback, because its durable records do not carry category/adult metadata. Provider lookup failure hides history rather than exposing previously allowed entries. Corrupt settings block content until an explicit reset restores safe defaults; they are not silently overwritten.

Automatic detection depends on provider metadata and recognizable naming; it is not independent age classification. Incorrectly labeled content may require a manual category lock. App storage clearing, uninstalling or device compromise can remove local settings. Reset deliberately removes custom category blocks; it preserves the default adult filter, as described in the reset confirmation.

## Public source and CI

- `.gitignore` excludes local settings, secrets, signing material and build outputs.
- Automated tests use synthetic data and require no provider account.
- CI has read-only repository permissions and does not run `pull_request_target` code from contributors.
- GitHub Actions are pinned to commit SHAs; Dependabot proposes updates for review.
- The Gradle distribution is checksum-pinned and the official Wrapper JAR is committed.
- Never embed release signing keys, personal tokens or private provider configuration.
- Before pushing, review the staged diff and scan the complete Git history for accidental sensitive values.

Public visibility and an ignore rule do not remove a secret already committed. If a secret is accidentally published, revoke/rotate it and coordinate history cleanup with the owner.
