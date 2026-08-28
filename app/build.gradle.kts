plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
    id("com.mikepenz.aboutlibraries.plugin")
    id("androidx.room")
}

android {
    namespace = "ua.acclorite.book_story"
    compileSdk = 36

    // Default configuration
    defaultConfig {
        applicationId = "ua.acclorite.book_story"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Shared debug signing: pin the debug build to a committed keystore so every
    // build — local, CI, any contributor — produces an identically-signed debug
    // APK. Without this each machine uses its own auto-generated
    // ~/.android/debug.keystore, and swapping between such builds fails with
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. A debug keystore is not a secret
    // (fixed password "android"); it only signs throwaway debug builds.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Build types configuration
    buildTypes {
        // BOOK_TIMING logs how long opening a book takes, phase by phase, under
        // the "BookTiming" tag. Off in release; on in release-debug as well as
        // debug, because release-debug is the variant performance is measured on —
        // a debug build runs several times slower, so its timings are only ever
        // comparable with each other.
        // DB_EXPORT puts a "Copy database" row in General settings. The reading
        // app is release-debug, which is not debuggable on purpose, so `run-as`
        // cannot reach its database and no question about real reading data can
        // be answered without it. Off in release: nothing ships a one-tap "put
        // my reading history where another process can read it" button.
        getByName("debug") {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "BOOK_TIMING", "true")
            buildConfigField("boolean", "DB_EXPORT", "true")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = false
            buildConfigField("boolean", "BOOK_TIMING", "false")
            buildConfigField("boolean", "DB_EXPORT", "false")

            proguardFiles("proguard-rules.pro")
        }

        create("release-debug") {
            initWith(getByName("release"))
            applicationIdSuffix = ".release.debug"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "BOOK_TIMING", "true")
            buildConfigField("boolean", "DB_EXPORT", "true")
        }
    }

    // Kotlin configuration
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Room configuration
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    lint {
        // Missing/partial translations arrive via Weblate, not from our code —
        // they are the only lint *errors* in the project, so ignoring them lets
        // lint stay green (and thus be a blocking CI gate) while still failing
        // on any real code-level error. Same reason for StringFormatCount
        // (a translated string dropping a format placeholder).
        disable += setOf("MissingTranslation", "StringFormatCount")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/gradle/incremental.annotation.processors"
        }
    }
}

// About Libraries configuration
aboutLibraries {
    registerAndroidTasks = false
    prettyPrint = true

    filterVariants = arrayOf("debug", "release", "release-debug")
    excludeFields = arrayOf("generated", "funding", "description")
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")

    // Compose BOM libraries
    // Compose BOM was eliminated - it is recognized as Closed Source in AboutLibraries..
    // although it is not.
    implementation("androidx.compose.foundation:foundation:1.9.3")
    implementation("androidx.compose.animation:animation:1.9.3")
    implementation("androidx.compose.animation:animation-android:1.9.3")
    implementation("androidx.compose.foundation:foundation-layout:1.9.3")
    implementation("androidx.compose.ui:ui:1.9.3")
    implementation("androidx.compose.ui:ui-graphics:1.9.3")
    implementation("androidx.compose.ui:ui-android:1.9.3")
    implementation("androidx.compose.material3:material3:1.5.0-alpha06")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material:material:1.9.3")

    // All dependencies
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")

    // Dagger - Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")
    implementation("com.google.dagger:hilt-compiler:2.57.2")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:2.7.2")

    // Datastore (Settings)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Splash Screen API
    implementation("androidx.core:core-splashscreen:1.0.1")

    // SAF
    implementation("com.anggrayudi:storage:2.2.0")

    // PDF parser
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // EPUB parser
    implementation("org.jsoup:jsoup:1.21.2")

    // Language Switcher
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.appcompat:appcompat-resources:1.7.1")

    // Coil for loading images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Open source libraries
    implementation("com.mikepenz:aboutlibraries-core:11.4.0")
    implementation("com.mikepenz:aboutlibraries-compose-m3:11.4.0")

    // Drag & Drop
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Scrollbar
    implementation("com.github.nanihadesuka:LazyColumnScrollbar:2.2.0")

    // Markdown
    implementation("org.commonmark:commonmark:0.26.0")

    // Json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // JVM unit tests (app/src/test) — the suite CI runs. Robolectric supplies
    // the handful of real Android APIs the parser reaches (Log, Base64,
    // BitmapFactory); deliberately no `unitTests.isReturnDefaultValues`, so an
    // Android call nothing has thought about still fails loudly instead of
    // quietly returning null. The level Robolectric runs on is pinned in
    // app/src/test/resources/robolectric.properties.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")

    // Instrumented tests (app/src/androidTest) — only what needs a device: real
    // app storage and a real Context. Run by hand, not in CI.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}