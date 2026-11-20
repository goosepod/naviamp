plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    `java-gradle-plugin`
}
group = "app.naviamp.build"
version = "1.4.2-naviamp590.1"
repositories { mavenCentral(); gradlePluginPortal() }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
gradlePlugin {
    plugins.create("composeTvosSettings") {
        id = "dev.sajidali.compose-tvos"
        implementationClass = "dev.sajidali.compose.tvos.ComposeTvosRedirectSettingsPlugin"
    }
}
tasks.test { useJUnitPlatform() }
