# Dependency audit and stable upgrades

Checked October 25, 2026. At that audit, all 14 directly managed library/plugin version families matched their latest stable upstream release. Gradle and the four GitHub Actions then in use were also upgraded. Java 21 LTS remains the CI build runtime, with Java 17 bytecode and Android 8.0 minimum support. The tables record that dated metadata check, not a fresh upstream audit on every documentation update.

## Direct libraries and plugins

| Family | Previous | Now pinned | Latest stable checked | Primary source |
| --- | --- | --- | --- | --- |
| agp | 9.1.0 | 9.4.1 | 9.4.1 | [metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) |
| kotlin | 2.2.10 | 2.4.21 | 2.4.21 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml) |
| compose-bom | 2026.03.00 | 2026.09.00 | 2026.09.00 | [metadata](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml) |
| activity | 1.12.4 | 1.13.0 | 1.13.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml) |
| appcompat | 1.7.1 | 1.8.0 | 1.8.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat/maven-metadata.xml) |
| lifecycle | 2.10.0 | 2.11.0 | 2.11.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose/maven-metadata.xml) |
| navigation | 2.9.7 | 2.10.2 | 2.10.2 | [metadata](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml) |
| media3 | 1.9.3 | 1.11.1 | 1.11.1 | [metadata](https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml) |
| coroutines | 1.10.2 | 1.11.0 | 1.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml) |
| serialization | 1.9.0 | 1.12.0 | 1.12.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) |
| okhttp | 4.12.0 | 5.5.1 | 5.5.1 | [metadata](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml) |
| coil | 2.7.0 | 3.6.3 | 3.6.3 | [metadata](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-compose/maven-metadata.xml) |
| junit | 4.13.2 | 4.13.2 | 4.13.2 | [metadata](https://repo.maven.apache.org/maven2/junit/junit/maven-metadata.xml) |
| work | 2.12.0 | 2.12.0 | 2.12.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/maven-metadata.xml) |

Coil moved from the old io.coil-kt coordinates to io.coil-kt.coil3. The migration added explicit OkHttp networking, a Cache-Control strategy and bounded caches. Subsequent automatic HTTP/HTTPS support uses one loader/cache; see [current artwork behavior](CACHE_AND_PLAYBACK.md#artwork-requests). See the [official migration guide](https://coil-kt.github.io/coil/upgrading_to_coil3/) for the library migration. OkHttp 5's non-null response body API was applied to the provider and tests.

## Build and asset tools

Gradle is now 9.8.2, with the official distribution SHA-256 pinned and wrapper scripts/JAR regenerated. AGP 9.4.1 needs Gradle 9.6 or newer; see the [AGP compatibility table](https://developer.android.com/build/releases/about-agp). New stable AARs require API 37 to compile, so compilation uses the stable SDK Platform 37.2. Target API remains 36 and minSdk remains 26. Build Tools stay on AGP's documented default 36.0.0; see [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

Sharp 0.35.5 is the current stable asset-export dependency, pinned with an npm lockfile. Its [official package metadata](https://registry.npmjs.org/sharp/latest) was checked. The scripts use Node.js LTS and no runtime component is added to Android.

| GitHub Action | Current release, commit pinned in workflow | Primary source |
| --- | --- | --- |
| actions/checkout | v7.0.1 | [release](https://github.com/actions/checkout/releases/tag/v7.0.1) |
| actions/setup-java | v6.1.0 | [release](https://github.com/actions/setup-java/releases/tag/v6.1.0) |
| gradle/actions | v6.4.0 | [release](https://github.com/gradle/actions/releases/tag/v6.4.0) |
| actions/upload-artifact | v7.0.2 | [release](https://github.com/actions/upload-artifact/releases/tag/v7.0.2) |

The later rolling-APK workflow also pins `actions/download-artifact` v8.0.2 at `9000827ccba6bdab643e8b6fd33ac0654aef8333`. This records the [current workflow configuration](../.github/workflows/android.yml); it was not part of the October 25 upstream-version audit. Its release/signing boundary is described in [CI and releases](CI_RELEASES.md).

## Complete resolved inventory

The audit inspected 438 scoped dependency entries across the app debug runtime and unit-test runtime plus both core test runtimes, producing **212 unique artifact/version records**, each verified against its official Google Maven or Maven Central metadata. [dependency-inventory.csv](dependency-inventory.csv) includes every record, resolved and latest stable versions, scopes and direct source links. Platform-specific and compatibility artifacts may intentionally resolve below their latest independent release; do not override the dependency owner's tested graph merely to make every transitive version numerically newest.

This is a version/support audit, not a vulnerability scan or a guarantee that upstream code has no defects. Build-tool internals are controlled by AGP/Gradle; they are not manually forced. No prerelease artifact is intentionally selected. The [support policy](DEPENDENCY_POLICY.md) gives LTS preference when upstream offers it and latest compatible stable otherwise.

## Reproduce

Run from the repository root with its SDK configured:

~~~sh
./gradlew --init-script tools/dependency-inventory.gradle exportDependencyInventory
node tools/audit-dependencies.cjs
./gradlew verifyDependencyPolicy
~~~

The inventory writes temporary JSON under build/dependency-audit and the audit refreshes the public CSV. Online metadata checks have 20-second timeouts and report unverified entries explicitly. Review new versions and migration notes before changing the catalog; Dependabot proposes weekly updates without automatic merging.
