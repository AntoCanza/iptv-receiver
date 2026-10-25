plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    configurations.configureEach {
        resolutionStrategy.componentSelection.all {
            if (Regex("(?i)(alpha|beta|rc|snapshot|eap|preview|dev|milestone|[.-]m\\d)").containsMatchIn(candidate.version)) {
                reject("Only stable dependency releases are allowed")
            }
        }
    }
}

tasks.register("verifyDependencyPolicy") {
    group = "verification"
    description = "Checks that declared library and plugin versions are exact stable releases."
    doLast {
        val versions = file("gradle/libs.versions.toml").readText().substringBefore("[libraries]")
        val declared = versions.lineSequence().map { it.substringAfter("=", "").trim() }
            .filter { it.startsWith('"') }.map { it.trim('"') }.toList()
        check(declared.isNotEmpty() && declared.all { it.matches(Regex("[0-9]+(\\.[0-9]+)+")) }) {
            "Version catalog must contain pinned stable releases only"
        }
    }
}
