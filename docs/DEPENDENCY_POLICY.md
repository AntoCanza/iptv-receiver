# Dependency support policy

Prefer an officially maintained LTS line where the upstream project provides one. Otherwise use the latest compatible stable release. A stable release is not automatically an LTS release. Never describe a release as LTS without an upstream support statement.

- Build and CI run on Java 21 LTS; Android bytecode targets Java 17. Both choices remain deliberate compatibility constraints.
- AndroidX publishes stable, RC, beta and alpha channels rather than a general LTS channel. Select stable releases and upgrade related artifacts together. See [AndroidX release channels](https://developer.android.com/jetpack/androidx/versions).
- Kotlin 2.4 has an official 18-month JVM standard-library security support window ending December 3, 2027. This is a specific security commitment, not an LTS guarantee for every Kotlin tool or library. See [Kotlin support](https://kotlinlang.org/docs/releases.html).
- Pin exact versions in the version catalog, verify the Gradle distribution checksum, and pin GitHub Actions to reviewed commit hashes. Do not use `+`, version ranges, snapshots or previews.
- Gradle rejects prerelease dependency candidates. CI also checks declared versions with `verifyDependencyPolicy`. Dependabot proposes weekly updates for review; it does not merge them automatically.
- For every upgrade, check Android minimum/target support, AGP/Gradle/Kotlin compatibility, migration notes, tests, lint, minified release builds, and affected device flows. Raise an explicit, documented exception if the newest stable release is incompatible.
- Do not force transitive libraries independently to their newest versions. Upgrade the owning library or BOM so its tested dependency set stays aligned.

The [dependency audit](DEPENDENCY_AUDIT.md) records the checked versions and any remaining migration work. Recheck upstream support before every store release.
