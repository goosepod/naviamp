plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("app.naviamp.tools.genreontology.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("auditLibraries") {
    group = "verification"
    description = "Audits one or more exported server genre inventories against an ontology JSON snapshot."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.naviamp.tools.genreontology.LibraryAuditKt")
    workingDir = rootProject.projectDir
}

tasks.test {
    useJUnitPlatform()
}
