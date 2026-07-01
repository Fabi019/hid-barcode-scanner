plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

val gitHash = providers.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD")
}.standardOutput.asText.get().trim()

val processedAssetsDir = layout.buildDirectory.dir("processed-assets")

val isBuildingBundle = gradle.startParameter.taskNames.any {
    it.lowercase().contains("bundle")
}

tasks.register<Copy>("processAssets") {
    filteringCharset = "UTF-8"
    from("src/main/assets") { exclude("**/*.layout") }
    from("src/main/assets") {
        include("**/*.layout")
        filter { line: String ->
            line.takeUnless { it.startsWith("##") || it.isBlank() }
        }
    }
    into(processedAssetsDir)
}

tasks.matching {
    val n = it.name.lowercase()
    (n.startsWith("merge") && n.endsWith("assets")) || n.contains("lint")
}.configureEach {
    dependsOn("processAssets")
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "dev.fabik.bluetoothhid"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.fabik.bluetoothhid"
        minSdk = 28
        targetSdk = 37
        versionCode = 57
        versionName = "2.2.1"

        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitHash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            // split abi not supported for bundles
            isEnable = !isBuildingBundle

            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
        }
    }

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf(processedAssetsDir))
        }
    }
}

configurations.all {
    exclude(group = "androidx.appcompat", module = "appcompat")
}

// Version variables from root project or literals
val compose_ui_version = rootProject.extra["compose_ui_version"] as String
val compose_mat_version = rootProject.extra["compose_mat_version"] as String
val camerax_version = rootProject.extra["camerax_version"] as String
val accomp_version = rootProject.extra["accomp_version"] as String

dependencies {
    implementation("androidx.javascriptengine:javascriptengine:1.1.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.ui:ui-android:$compose_ui_version")
    implementation("androidx.compose.material:material-icons-extended:$compose_mat_version")
    implementation("androidx.compose.runtime:runtime-livedata:$compose_ui_version")
    implementation("androidx.compose.material3:material3:1.4.0")

    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-compose:$camerax_version")

    implementation("io.github.zxing-cpp:android:3.0.2")

    implementation("com.google.accompanist:accompanist-permissions:$accomp_version")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("org.robolectric:robolectric:4.16.1")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_ui_version")

    implementation("androidx.compose.ui:ui-tooling-preview:$compose_ui_version")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose_ui_version")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$compose_ui_version")
}
