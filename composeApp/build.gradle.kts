plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
                implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation("org.jetbrains.compose.ui:ui:1.12.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:${libs.versions.activityCompose.get()}")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.kurocanvas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kurocanvas"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

compose.desktop {
    application {
        mainClass = "com.kurocanvas.MainKt"
    }
}
