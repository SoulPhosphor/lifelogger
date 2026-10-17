plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val ciBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER")
    .orNull
    ?.toIntOrNull()

android {
    namespace = "com.datadragon.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.datadragon.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuildNumber?.coerceAtLeast(3) ?: 3
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A stable, checked-in key so every build (especially the CI debug APKs that
    // ship as GitHub Releases) is signed identically. Without this, AGP's
    // auto-generated debug keystore differs on each CI runner, so a new build
    // can't install over a previously installed one (signature mismatch). A
    // debug keystore is not a secret, so committing it is safe.
    signingConfigs {
        create("stable") {
            storeFile = file("datadragon-debug.keystore")
            storePassword = "datadragon"
            keyAlias = "datadragon"
            keyPassword = "datadragon"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged Android resources/manifest available to
            // JVM unit tests (used by the Room database + migration tests).
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    // Committed under app/schemas; CI regenerates it and fails if it changed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.reorderable)
    implementation(libs.colorpicker.compose)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}

// Preserve failure details in CI logs when Android tests cannot run locally.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

// Fails the build if a dependency silently pulls in a different version of a
// library that decides how saved data and backups are stored (Room,
// kotlinx-serialization), or of the Kotlin standard library, which must match
// the Kotlin compiler. See docs/DEPENDENCY_MAINTENANCE.md.
val verifyPinnedDependencies by tasks.registering {
    group = "verification"
    description = "Checks that data-critical libraries resolve to the catalog versions."
    val pinned = mapOf(
        "androidx.room:room-runtime" to libs.versions.room.get(),
        "org.jetbrains.kotlinx:kotlinx-serialization-json" to libs.versions.kotlinxSerialization.get(),
        "org.jetbrains.kotlin:kotlin-stdlib" to libs.versions.kotlin.get(),
    )
    val resolved = configurations.named("releaseRuntimeClasspath").map { classpath ->
        classpath.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .associate { "${it.group}:${it.module}" to it.version }
    }
    doLast {
        val actual = resolved.get()
        val mismatches = pinned.mapNotNull { (module, expected) ->
            val found = actual[module]
            if (found == expected) null else "$module: catalog $expected, resolved ${found ?: "nothing"}"
        }
        check(mismatches.isEmpty()) { "Pinned dependency versions changed:\n" + mismatches.joinToString("\n") }
        pinned.forEach { (module, version) -> logger.lifecycle("$module $version") }
    }
}
