// Resolves the JVM, Desktop and Android dependency graphs without building, so the main-branch
// Gradle cache workflow can seed plugin and dependency caches for Verify. Apple, native, JS and
// Wasm graphs are skipped: the iOS job keeps its own cache, and no Verify job needs the others.
// Resolution is lenient; a graph that cannot resolve in isolation is left to the real build.
val skippedTargets = listOf("ios", "tvos", "watchos", "macosarm", "macosx64", "linuxx64", "linuxarm", "mingw", "native", "wasm")

fun isSkipped(name: String): Boolean =
    name.lowercase().let { lower -> skippedTargets.any { it in lower } } || name.startsWith("js") || "Js" in name

allprojects {
    val graphs = configurations.matching { it.isCanBeResolved && !isSkipped(it.name) }
    tasks.register("ciResolveDependencies") {
        description = "Resolves cacheable dependency graphs for the CI Gradle cache."
        doLast {
            // Resolving can register further configurations, so iterate over a snapshot.
            graphs.toList().forEach { graph ->
                try {
                    graph.incoming.artifactView { lenient(true) }.files.files
                } catch (failure: Exception) {
                    logger.lifecycle("Skipped ${graph.name}: ${failure.message?.lineSequence()?.firstOrNull()}")
                }
            }
        }
    }
}
