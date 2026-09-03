// Top-level build file.
// The project intentionally uses NO third-party runtime libraries (no AndroidX,
// no Room/Compose artifacts) so that every part of the app can be compiled and
// assembled fully offline with the bundled scripts under tools/offline-build/,
// while still building normally inside Android Studio with this Gradle setup.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
}
