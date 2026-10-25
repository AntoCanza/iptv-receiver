# Contributing

The repository is at an early development stage and its source license has not been selected. Coordinate with the owner before contributing code intended for redistribution.

Use short-lived feature branches and pull requests. Keep each change focused on one user-visible behavior or one architectural improvement. Include the relevant validation and any device-testing limitations.

## Development rules

- Follow Kotlin style and the repository `.editorconfig`.
- Keep UI state in ViewModels and provider HTTP/JSON behavior behind `CatalogRepository`.
- Keep core modules independent of Android.
- Add meaningful tests for parsing, URL construction, error handling and state transitions.
- Use fakes and synthetic fixtures instead of real provider accounts or raw catalogs.
- Propagate coroutine cancellation. Do not hide cancellation in broad exception handlers.
- Never log complete request URLs, passwords or provider response bodies.
- Add dependencies through `gradle/libs.versions.toml`; update the Wrapper and checksum together.
- Do not commit machine paths, local credentials, build artifacts or signing material.

Run the verification command in the README before requesting review. For playback changes, also describe the physical devices, formats and interactions checked. Passing a JVM test or APK build does not prove device playback compatibility.
