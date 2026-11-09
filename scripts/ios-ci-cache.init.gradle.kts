// Compile/link artifacts may be reused. Every test body still executes against this runner.
allprojects {
    tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
        outputs.doNotCacheIf("iOS CI must execute tests against fresh external state") { true }
        outputs.upToDateWhen { false }
    }
}
