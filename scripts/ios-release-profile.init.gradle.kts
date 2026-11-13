// Opt-in Release archive measurements. Uses Gradle's public task-completion service API.
import groovy.json.JsonOutput
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.events.task.TaskSkippedResult
import javax.inject.Inject
import java.util.UUID

abstract class NaviampArchiveTimings : BuildService<NaviampArchiveTimings.Parameters>,
    OperationCompletionListener, AutoCloseable {
    interface Parameters : BuildServiceParameters { val directory: DirectoryProperty }
    private val tasks = mutableListOf<Map<String, Any>>()
    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        val result = event.result
        val outcome = when (result) {
            is TaskSuccessResult -> when {
                result.isFromCache -> "FROM-CACHE"
                result.isUpToDate -> "UP-TO-DATE"
                else -> "EXECUTED"
            }
            is TaskSkippedResult -> "SKIPPED"
            else -> "FAILED"
        }
        tasks.add(mapOf("task" to event.descriptor.taskPath, "startMillis" to result.startTime,
            "endMillis" to result.endTime, "durationMillis" to (result.endTime - result.startTime),
            "outcome" to outcome))
    }
    override fun close() {
        val directory = parameters.directory.get().asFile
        directory.mkdirs()
        directory.resolve("gradle-${UUID.randomUUID()}.json").writeText(JsonOutput.toJson(tasks))
    }
}

abstract class NaviampArchiveTimingPlugin : Plugin<Project> {
    @get:Inject abstract val events: BuildEventsListenerRegistry
    override fun apply(project: Project) {
        val directory = project.providers.environmentVariable("NAVIAMP_IOS_ARCHIVE_REPORT_DIR").orNull ?: return
        val service = project.gradle.sharedServices.registerIfAbsent("naviampArchiveTimings", NaviampArchiveTimings::class.java) {
            parameters.directory.set(project.file("$directory/tasks"))
        }
        events.onTaskCompletion(service)
    }
}
gradle.rootProject { apply<NaviampArchiveTimingPlugin>() }
