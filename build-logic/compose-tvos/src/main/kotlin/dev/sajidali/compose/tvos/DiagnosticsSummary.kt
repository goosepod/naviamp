package dev.sajidali.compose.tvos

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Formats and emits the Task 5 (defect D1) end-of-build diagnostics summary from a
 * [DiagnosticsSnapshot]:
 *  - injections happened -> a single lifecycle line (always; independent of the tvOS-target
 *    guard below -- an actual injection is real signal, never spurious noise).
 *  - empty-discovery cases exist AND the consuming project declared a tvOS target
 *    ([tvosTargetsDetected]) -> a single WARN block (capped at [MAX_LISTED_MODULES], "+K more"),
 *    or, in `strictMode`, a [GradleException] listing every one of them.
 *  - nothing eligible seen -> silent, regardless of verbosity (zero noise for non-tvOS
 *    projects is the whole point of the tvOS-target guard).
 *  - D7 (1.4.2): unmapped fork edges ([DiagnosticsSnapshot.unmappedForkEdges]) -> a WARN block
 *    naming every coordinate, or a [GradleException] in `strictMode`. Not gated on
 *    [tvosTargetsDetected]: such a record can only ever be produced while resolving a fork tvOS
 *    platform module, which is itself only reachable from a tvOS graph.
 *
 * Task 10d (Phase 4 closeout): before either the WARN block or the `strictMode` failure list is
 * built, [filterConflictLosers] drops empty-discovery entries for `group:module@version`
 * coordinates where that SAME `group:module` has, at some OTHER version, either a successful
 * injection or an already-officially-supported record. `TvosVariantInjectionRule`'s
 * `ComponentMetadataRule` fires on every version node Gradle's resolution engine touches while
 * building the graph, not just the one version conflict resolution eventually keeps -- so a real
 * consumer graph routinely produces one empty-discovery record per LOSING candidate version of a
 * module that resolves perfectly fine at its winning version (see task-10c-report.md gate (e):
 * 11 such entries on a healthy demo build with zero actual resolution failures). Suppressing
 * those here is purely a summary-time read of the existing bookkeeping -- `TvosVariantInjectionRule`
 * itself, and every map it maintains, is unchanged; a module with NO working version anywhere
 * still warns (the genuine "would have injected but found nothing" signal this whole feature
 * exists for).
 *
 * Kept as a pure function of its inputs (no Gradle service/task state) so it is unit-testable
 * without GradleTestKit.
 */
internal object DiagnosticsSummary {
    private const val MAX_LISTED_MODULES = 5

    /** Result of [filterConflictLosers]: the two disjoint partitions of a snapshot's empty discoveries. */
    internal data class FilteredEmptyDiscoveries(
        val kept: List<EmptyDiscoveryRecord>,
        val suppressed: List<EmptyDiscoveryRecord>
    )

    fun report(
        snapshot: DiagnosticsSnapshot,
        tvosTargetsDetected: Boolean,
        strictMode: Boolean,
        verbose: Boolean,
        logger: Logger,
        selectedGraphOnly: Boolean = false
    ) {
        if (snapshot.injections.isNotEmpty()) {
            logger.lifecycle(lifecycleLine(snapshot.injections.size, snapshot.emptyDiscoveries.size))
        }

        // Once scoped to selected tvOS coordinates, none of these entries is a losing candidate.
        val filtered = if (selectedGraphOnly) FilteredEmptyDiscoveries(snapshot.emptyDiscoveries, emptyList())
                       else filterConflictLosers(snapshot)

        // Task 10e (fix of a 10d concern): this must fire at Gradle's `lifecycle` level, not
        // `info`, so it is visible to a consumer who only set `composeTvos.verbose=true` --
        // matching the level every OTHER verbose-only message in this plugin already uses
        // (TvosVariantInjectionRule's injection/skip lines, ComposeTvosRedirectPlugin's
        // redirect/skip-substitution lines). `logger.info` would have silently required the
        // consumer to ALSO pass Gradle's own `--info`/`--debug` flag, which this plugin's
        // `verbose` flag was never meant to depend on.
        //
        // Task 10e (review fix): also gated on [tvosTargetsDetected], same as the WARN/strict
        // path below -- this is tvOS-specific chatter (conflict losers among tvOS redirect
        // candidates), so it must stay silent for a non-tvOS-target project even with
        // `verbose=true`, matching this class's "zero noise for non-tvOS projects" invariant.
        if (verbose && tvosTargetsDetected && filtered.suppressed.isNotEmpty()) {
            logger.lifecycle(suppressedLine(filtered.suppressed))
        }

        val keptGaps = if (tvosTargetsDetected) filtered.kept else emptyList()
        val unmapped = snapshot.unmappedForkEdges
        if (keptGaps.isEmpty() && unmapped.isEmpty()) return

        if (strictMode) {
            throw GradleException(
                listOfNotNull(
                    keptGaps.takeIf { it.isNotEmpty() }?.let(::strictFailureMessage),
                    unmapped.takeIf { it.isNotEmpty() }?.let(::unmappedStrictFailureMessage)
                ).joinToString("\n")
            )
        }
        if (keptGaps.isNotEmpty()) logger.warn(warnBlock(keptGaps))
        if (unmapped.isNotEmpty()) logger.warn(unmappedWarnBlock(unmapped))
    }

    /**
     * Partitions [DiagnosticsSnapshot.emptyDiscoveries] into `kept` (the module has no working
     * version anywhere -- a genuine gap) and `suppressed` (conflict-resolution losers: the same
     * `group:module` succeeded, at some other version, either via injection or as
     * already-officially-supported). An empty-discovery record's own `sourceModule` version can
     * never itself also appear in [DiagnosticsSnapshot.injections] or
     * [DiagnosticsSnapshot.skippedAlreadySupported] -- `TvosVariantInjectionRule.execute` records
     * a coordinate into `emptyDiscoveries` and returns immediately, mutually exclusive with the
     * later injection/skip bookkeeping for that exact same coordinate -- so matching on
     * `group:module` alone (ignoring the version suffix) is sufficient to mean "some OTHER
     * version," with no extra same-version exclusion needed.
     */
    internal fun filterConflictLosers(snapshot: DiagnosticsSnapshot): FilteredEmptyDiscoveries {
        val workingModuleKeys = (
            snapshot.injections.asSequence().map { moduleKey(it.sourceModule) } +
                snapshot.skippedAlreadySupported.asSequence().map { moduleKey(it.sourceModule) }
            ).toSet()
        val (suppressed, kept) = snapshot.emptyDiscoveries.partition { moduleKey(it.sourceModule) in workingModuleKeys }
        return FilteredEmptyDiscoveries(kept = kept, suppressed = suppressed)
    }

    /** Strips the trailing `:version` off a `group:module:version` sourceModule string. */
    private fun moduleKey(sourceModule: String): String = sourceModule.substringBeforeLast(':')

    internal fun lifecycleLine(injectedCount: Int, skippedCount: Int): String =
        "[ComposeTvos] Injected tvOS variants into $injectedCount module(s) ($skippedCount skipped). " +
            "Run with composeTvos.verbose=true for detail."

    internal fun warnBlock(emptyDiscoveries: List<EmptyDiscoveryRecord>): String {
        val listed = emptyDiscoveries.take(MAX_LISTED_MODULES).joinToString("\n") {
            "  - ${it.sourceModule} -> ${it.targetCoordinate}"
        }
        val remaining = emptyDiscoveries.size - MAX_LISTED_MODULES
        val moreLine = if (remaining > 0) "\n  ... +$remaining more" else ""
        return "[ComposeTvos] WARNING: tvOS variant discovery found nothing for " +
            "${emptyDiscoveries.size} redirect-eligible module(s); tvOS builds depending on " +
            "them will fail to resolve:\n$listed$moreLine\n" +
            "Check that dev.sajidali artifacts exist for the requested version, set " +
            "composeTvos.versionMappings to point at a version that does, or see: " +
            "https://github.com/sajidalidev/compose-tvos#readme"
    }

    internal fun strictFailureMessage(emptyDiscoveries: List<EmptyDiscoveryRecord>): String {
        val listed = emptyDiscoveries.joinToString("\n") { "  - ${it.sourceModule} -> ${it.targetCoordinate}" }
        return "[ComposeTvos] strictMode: tvOS variant discovery found nothing for " +
            "${emptyDiscoveries.size} redirect-eligible module(s):\n$listed"
    }

    private fun unmappedEntry(record: UnmappedForkEdgeRecord): String =
        "  - ${record.forkCoordinate} (dependency of ${record.forkModule}) -> ${record.officialCoordinate}: ${record.reason}"

    internal fun unmappedWarnBlock(unmapped: List<UnmappedForkEdgeRecord>): String {
        val listed = unmapped.take(MAX_LISTED_MODULES).joinToString("\n", transform = ::unmappedEntry)
        val remaining = unmapped.size - MAX_LISTED_MODULES
        val moreLine = if (remaining > 0) "\n  ... +$remaining more" else ""
        val repositories = unmapped.first().repositoryUrls.joinToString(", ")
        return "[ComposeTvos] WARNING: ${unmapped.size} transitive dependency edge(s) of fork tvOS platform " +
            "module(s) were left pointing at the fork umbrella because the official counterpart could not " +
            "be confirmed (not rewritten to the official coordinate; shared source-set metadata compilation " +
            "may not see these modules):\n$listed$moreLine\n" +
            "Re-run with --refresh-dependencies once every configured repository is reachable " +
            "(repositories: $repositories), or set composeTvos.strictMode=true to fail the build instead."
    }

    internal fun unmappedStrictFailureMessage(unmapped: List<UnmappedForkEdgeRecord>): String {
        val listed = unmapped.joinToString("\n", transform = ::unmappedEntry)
        return "[ComposeTvos] strictMode: the official counterpart of ${unmapped.size} transitive dependency " +
            "edge(s) of fork tvOS platform module(s) could not be confirmed:\n$listed"
    }

    internal fun suppressedLine(suppressed: List<EmptyDiscoveryRecord>): String =
        "[ComposeTvos] ${suppressed.size} version-candidate entries suppressed " +
            "(conflict-resolution losers): ${suppressed.joinToString(", ") { it.sourceModule }}"
}
