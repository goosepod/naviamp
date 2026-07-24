plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        iosMain.dependencies {
            api(project(":core:app"))
            api(project(":core:domain"))
            api(project(":core:presentation"))
            api(project(":core:storage"))
            api(project(":core:ui"))
            api(project(":providers:navidrome"))
        }
    }
}
