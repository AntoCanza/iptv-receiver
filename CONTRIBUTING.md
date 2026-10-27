# Contributing

The repository publishes a signed Android development baseline; its source license has not been selected. Coordinate with the owner before contributing code intended for redistribution.

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

## Documentation and public pages

Keep the [README](README.md), [user guide](docs/USER_GUIDE.md), [public website](docs/index.html) and relevant technical guides aligned with implemented behavior. Check build values against Gradle and release behavior against `.github/workflows/android.yml`. Separate remaining implementation work from hardware/release validation in the roadmap. Dated audits and older verification sections describe their recorded versions; preserve that history and add new evidence explicitly.

For documentation/site-only changes, run:

```sh
python tools/check_site.py
git diff --check
```

Check relative Markdown links and listing JSON when those files change. Review the rendered public page at phone and desktop widths. Keep privacy/listing drafts marked as drafts, and use fictional examples and screenshots. GitHub Pages serves `main:/docs`; merging changes to `main` publishes the website and also triggers the existing Android verification/rolling-release workflow.

For changes to CI, signing or publication, also run:

```sh
python -m unittest discover -s tools/tests -v
actionlint .github/workflows/android.yml
```
