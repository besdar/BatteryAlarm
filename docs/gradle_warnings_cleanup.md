# Gradle Modernization

The project now uses Gradle 9.6.1, Android Gradle Plugin 9.2.1, and Kotlin 2.4.0.

AGP 9.2 uses built-in Kotlin, so the legacy `org.jetbrains.kotlin.android` plugin,
`android.builtInKotlin=false`, `android.newDsl=false`, and the obsolete `kotlinOptions`
block were removed. The Compose compiler plugin remains applied.

The Gradle daemon toolchain is pinned to JDK 17 in
`gradle/gradle-daemon-jvm.properties`, and the app compiles and targets API 37. Release
builds enable both code shrinking and resource shrinking.
