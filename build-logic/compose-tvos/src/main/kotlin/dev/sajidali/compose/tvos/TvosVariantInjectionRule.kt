package dev.sajidali.compose.tvos

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.Attribute
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * A single successful injection recorded for the end-of-build diagnostics summary (D1,
 * Task 5): the umbrella module that was redirected, the target coordinate its variants
 * point at, and how many variants were mirrored in.
 */
data class InjectionRecord(
    val sourceModule: String,
    val targetCoordinate: String,
    val variantCount: Int
)

/**
 * A redirect-eligible module (artifact/group mapping matched, umbrella-shaped) for which
 * [TvosVariantDiscovery] returned zero variants -- the "silent no-op" case the D1 defect
 * describes. Recorded so the settings plugin's end-of-build summary can warn (or, in
 * `strictMode`, fail) instead of degrading invisibly.
 */
data class EmptyDiscoveryRecord(
    val sourceModule: String,
    val targetCoordinate: String,
    val repositoryUrls: List<String>,
    val offline: Boolean
)

/**
 * Task 10b (Phase 4 blocker fix): a discovered tvOS variant that was deliberately NOT
 * injected because the OFFICIAL module (fetched via the same discovery path, keyed by its
 * own `group:module:version` rather than the redirect target coordinate) already ships a
 * native variant for that target -- e.g. `org.jetbrains.compose.runtime` already publishes
 * `tvosArm64` upstream. Recorded for verbose diagnostics only: deliberately NOT an
 * [EmptyDiscoveryRecord] (discovery succeeded; the module is simply already covered), so it
 * never contributes to the end-of-build WARN/strictMode path.
 *
 * Task 10e: also used for two OTHER "official-first" successes that likewise must never
 * contribute to the WARN/strictMode path: (1) [TvosVariantInjectionRule.execute]'s
 * fork-discovery-EMPTY branch, when the official artifact turns out to already cover the
 * target itself (`skippedNativeTargets` then lists every officially-covered target, since the
 * fork provided nothing to compare per-variant against); and (2)
 * [ComposeTvosRedirectPlugin]'s project-level `dependencySubstitution` skip, recorded directly
 * via [TvosDiagnosticsBookkeeping.recordSkippedAlreadySupported] from that independent code
 * path -- see [TvosDiagnosticsBookkeeping]'s KDoc for why the bookkeeping itself lives there
 * rather than in this rule's companion object.
 */
data class SkippedAlreadySupportedRecord(
    val sourceModule: String,
    val targetCoordinate: String,
    val skippedNativeTargets: List<String>
)

/**
 * D7 (1.4.2, fail-closed official rewrite): a dependency edge of a fork tvOS platform module
 * ([forkModule], e.g. `dev.sajidali.compose.ui:ui-tvosarm64:1.12.0`) onto a fork umbrella
 * ([forkCoordinate]) that [TvosVariantInjectionRule.mapForkDependenciesToOfficial] deliberately
 * left pointing at the fork because the official counterpart ([officialCoordinate]) could not be
 * CONFIRMED usable -- as opposed to being confirmed absent, which is silent. [reason] says which
 * part of the proof failed (existence unknown, offline with nothing cached, or no confirmed tvOS
 * coverage). Reported at end of build as a WARN naming the exact coordinate, or as a build
 * failure in `strictMode`.
 */
data class UnmappedForkEdgeRecord(
    val forkModule: String,
    val forkCoordinate: String,
    val officialCoordinate: String,
    val reason: String,
    val repositoryUrls: List<String>,
    val offline: Boolean
)

/** Immutable point-in-time read of [TvosVariantInjectionRule]'s diagnostics bookkeeping. */
data class DiagnosticsSnapshot(
    val injections: List<InjectionRecord>,
    val emptyDiscoveries: List<EmptyDiscoveryRecord>,
    val skippedAlreadySupported: List<SkippedAlreadySupportedRecord> = emptyList(),
    val unmappedForkEdges: List<UnmappedForkEdgeRecord> = emptyList()
)

/**
 * Single class-based component metadata rule that injects tvOS variants into JetBrains
 * Compose Multiplatform (and configured additional) umbrella modules.
 *
 * Replaces the previous per-mapping closure registrations (one `withModule` rule per
 * artifact-mapping entry, one `all` rule per group-mapping entry) with a single
 * `components.all(TvosVariantInjectionRule::class.java) { it.params(params) }`
 * registration. [Params] is serialized/isolated by Gradle rather than captured by a
 * closure over settings-scope state, which is what makes the rule cacheable
 * ([CacheableRule]) and configuration-cache-friendly.
 *
 * Behavior is intentionally identical to the previous closure-based implementation:
 * artifact-mapping match first, then group-mapping match, an umbrella-module check,
 * version resolution, variant discovery, and `addVariant` with mirrored attributes and a
 * dependency onto the resolved target coordinate.
 *
 * Since 1.4.0 the same rule also fires on the FORK's tvOS platform modules
 * (`dev.sajidali.G:M-tvosarm64` etc., i.e. the targets the injected variants point at) and
 * maps their dependencies on fork umbrellas (`dev.sajidali.G2:M2`) back to the official
 * umbrellas (`org.jetbrains.G2:M2`) -- see [mapForkDependenciesToOfficial] for why.
 */
@CacheableRule
abstract class TvosVariantInjectionRule @Inject constructor(
    private val params: Params
) : ComponentMetadataRule {

    /**
     * Serializable parameter bundle for [TvosVariantInjectionRule]. Every field must stay
     * serializable/isolatable by Gradle: only primitives, strings, and simple
     * collections/pairs of those.
     */
    data class Params(
        val groupMappings: Map<String, String>,
        val artifactMappings: Map<String, Pair<String, String>>,
        val versionMappings: Map<String, String>,
        val targetVersionOverride: String?,
        val repositoryUrls: List<String>,
        val cacheDirPath: String,
        val offline: Boolean,
        val verbose: Boolean
    ) : Serializable

    override fun execute(context: ComponentMetadataContext) {
        val metadata = context.details
        val id = metadata.id
        val group = id.group
        val moduleName = id.module.name
        val artifactKey = "$group:$moduleName"

        if (isForkPlatformModule(group, moduleName)) {
            mapForkDependenciesToOfficial(metadata, "$group:$moduleName:${id.version}")
            return
        }

        val artifactMatch = params.artifactMappings[artifactKey]
        val (targetGroup, targetArtifact) = if (artifactMatch != null) {
            artifactMatch
        } else {
            val mappedGroup = params.groupMappings[group] ?: return
            mappedGroup to moduleName
        }

        if (!TvosArtifactMapping.isUmbrellaModule(moduleName)) return

        val version = ComposeVersions.resolveVersion(
            group, moduleName, id.version, params.versionMappings, params.targetVersionOverride
        )
        val sourceModule = "$group:$moduleName:${id.version}"
        val targetCoordinate = "$targetGroup:$targetArtifact:$version"

        // Task 10b (Phase 4 blocker fix), reordered ahead of the empty-fork-discovery check by
        // Task 10e: check whether the OFFICIAL module -- this component's own
        // group:module:version, not the redirect target -- already ships native tvOS variant(s)
        // itself (e.g. org.jetbrains.compose.runtime already publishes tvosArm64 upstream).
        // Re-uses the exact same discoverVariants/parseModuleMetadata path, repository list, and
        // disk/memory caching as the target lookup below, keyed by the OFFICIAL coordinate --
        // naturally distinct from the target coordinate's own cache key, since the redirect
        // target always carries a different (dev.sajidali-mapped) group. A failed/empty
        // official-metadata fetch (offline, network error, or a parse failure on well-formed
        // metadata) degrades to an empty set here, same as discoverVariants's existing
        // never-throw contract.
        //
        // Task 10e note: this fetch now ALSO runs on the empty-fork-discovery path below (it
        // previously only ran once the fork side was confirmed non-empty) -- one extra
        // getVariants call for that path, deliberately accepted: it is memoized (in-memory +
        // disk, see getVariants's own KDoc), so it costs a genuine network/disk round trip only
        // the FIRST time a given official coordinate is seen, not per-component-metadata-rule
        // invocation.
        //
        // Task 11 (dangling-metadata fix): an official variant's advertised availability is no
        // longer trusted at face value -- verifyOfficialSupport additionally probes each
        // available-at redirect target's existence (one extra cached probe per distinct
        // available-at module, see its own KDoc) before counting a native target as officially
        // supported, so upstream metadata that advertises a tvOS variant whose target module
        // was never actually published no longer causes this rule to skip injection for it.
        val officialVariants = getVariants(group, moduleName, id.version)
        val supportResult = TvosVariantDiscovery.verifyOfficialSupport(
            officialVariants, params.repositoryUrls, File(params.cacheDirPath),
            if (params.verbose) logger else null, params.offline
        )
        val alreadySupportedTargets = supportResult.supportedNativeTargets

        val variants = getVariants(targetGroup, targetArtifact, version)
        if (variants.isEmpty()) {
            // Task 10e: fork discovery came back empty. Before treating this as a genuine gap,
            // check whether the official artifact already covers tvOS itself -- if so, nothing
            // is actually broken (no injection was ever needed for this coordinate) and this
            // must NOT be recorded as an [EmptyDiscoveryRecord] (see
            // TvosDiagnosticsBookkeeping.recordEmptyForkDiscovery for the precedence decision).
            // Finding 2 (review of task-20-report.md): no poisoning here either -- the fork has
            // nothing to inject as a replacement, so de-tuning the dangling official variant's
            // attributes would only trade a recognizable "phantom coordinate" resolution failure
            // for an unhelpful "no matching variant" one, with no replacement to show for it.
            TvosDiagnosticsBookkeeping.recordEmptyForkDiscovery(
                sourceModule, targetCoordinate, alreadySupportedTargets, params.repositoryUrls, params.offline
            )
            if (params.verbose && alreadySupportedTargets.isNotEmpty()) {
                logger.lifecycle(
                    "[ComposeTvosRedirect] Fork discovery found nothing for $sourceModule, but the " +
                        "official artifact already provides tvOS variant(s) for: " +
                        "${alreadySupportedTargets.toList()}; no injection needed."
                )
            }
            return
        }

        // That fallback (empty officialVariants -> nothing already supported -> inject
        // everything discovered, i.e. the previous, unconditional behavior) is deliberately
        // chosen over failing closed: injection is only reachable when the TARGET dev.sajidali
        // metadata was already fetchable, and a wrongly-SKIPPED injection breaks tvOS builds
        // outright, whereas a wrongly-ADDED injection only breaks the (rare)
        // already-officially-supported case -- exactly the case this whole check exists to
        // avoid in the common path.
        val injectable = variants.filter { it.nativeTarget !in alreadySupportedTargets }

        // Task 11 follow-up: a dangling official variant is still present, UNMODIFIED, in this
        // component's own metadata -- merely excluding its native target from
        // alreadySupportedTargets (so injectable above includes it) reproduces the exact
        // "cannot choose between ..." variant-ambiguity Gradle error Task 10b's whole pre-check
        // exists to avoid, since our injected variant would carry an IDENTICAL, mirrored
        // attribute set. See poisonDanglingOfficialVariants's own KDoc for why de-tuning the
        // dangling variant's own attributes -- rather than removing it (no public API for that)
        // or overriding its available-at target (also no public API for that) -- is how this is
        // resolved.
        //
        // Finding 2 (review of task-20-report.md): poisoning must only run for a dangling
        // target that `injectable` will actually replace -- gated here, AFTER `injectable` is
        // computed, rather than unconditionally on every confirmed-dangling target. If the fork
        // has no variant for that same native target either, poisoning the official variant
        // buys nothing (there is no injected replacement to leave as the sole candidate) and
        // only trades the recognizable "phantom org.jetbrains coordinate" resolution failure for
        // an unhelpful "no matching variant" one.
        val replacedDanglingTargets = supportResult.danglingNativeTargets.filterTo(mutableSetOf()) { target ->
            injectable.any { it.nativeTarget == target }
        }
        if (replacedDanglingTargets.isNotEmpty()) {
            poisonDanglingOfficialVariants(metadata, officialVariants, replacedDanglingTargets)
        }

        if (injectable.size < variants.size) {
            val skippedTargets = variants.filter { it.nativeTarget in alreadySupportedTargets }.map { it.nativeTarget }
            TvosDiagnosticsBookkeeping.recordSkippedAlreadySupported(
                SkippedAlreadySupportedRecord(sourceModule, targetCoordinate, skippedTargets)
            )
            if (params.verbose) {
                // Fix 4 (review of task-10b-report.md): skippedTargets carries one entry per
                // skipped VARIANT (api/metadata/sources all share the same nativeTarget), so the
                // raw list logs the same target repeated 2-3x (e.g. "[tvos_arm64, tvos_arm64,
                // tvos_arm64]"). The count in the message still reflects the true variant count;
                // only the displayed list of targets is deduped for readability.
                logger.lifecycle(
                    "[ComposeTvosRedirect] Skipping ${skippedTargets.size} tvOS variant(s) already " +
                        "supported by the official artifact: $group:$moduleName:${id.version} (${skippedTargets.distinct()})"
                )
            }
        }

        if (injectable.isEmpty()) return

        TvosDiagnosticsBookkeeping.recordInjection(InjectionRecord(sourceModule, targetCoordinate, injectable.size))

        if (params.verbose) {
            logger.lifecycle("[ComposeTvosRedirect] Injecting ${injectable.size} tvOS variants into: $group:$moduleName:${id.version}")
        }
        injectTvosVariants(metadata, targetGroup, version, injectable)
    }

    private val officialGroupByForkGroup: Map<String, String> by lazy {
        params.groupMappings.entries.associate { (official, fork) -> fork to official }
    }

    private val officialArtifactByForkArtifact: Map<String, Pair<String, String>> by lazy {
        params.artifactMappings.entries.associate { (official, fork) ->
            "${fork.first}:${fork.second}" to official.split(":").let { it[0] to it[1] }
        }
    }

    private val forkGroups: Set<String> by lazy {
        params.groupMappings.values.toSet() + params.artifactMappings.values.map { it.first }
    }

    /**
     * Distinguishes [officialCoordinateCache] entries produced under different plugin
     * configuration. The companion-object cache is shared across every build that runs in the
     * same Gradle daemon (see [officialCoordinateCache]'s own KDoc), but the DECISION
     * [officialCoordinateFor] makes for a given fork coordinate depends on
     * [Params.versionMappings], [Params.targetVersionOverride], [Params.repositoryUrls] and
     * [Params.offline] -- two projects (or two settings-file edits reusing the same daemon) with
     * different manifest overrides or repository lists must not share a cache entry, or the
     * second one can be served a candidate that was only valid for the first one's
     * configuration: the repository list matters because it changes whether
     * [TvosVariantDiscovery.probeTargetModuleExistence] confirms or rejects a candidate, not just
     * which artifact bytes get downloaded. D7: `offline` is part of the key too, since an
     * `--offline` build in a warm daemon must re-derive (from the disk cache only) which edges it
     * can confirm, and report its own reason for the ones it cannot.
     */
    private val paramsFingerprint: String by lazy {
        val mappings = params.versionMappings.entries
            .sortedBy { it.key }
            .joinToString(",") { (k, v) -> "$k=$v" }
        val repos = params.repositoryUrls.joinToString(",")
        "${params.targetVersionOverride.orEmpty()}|$mappings|$repos|offline=${params.offline}"
    }

    /**
     * A tvOS platform module published by the fork, e.g. `dev.sajidali.compose.ui:ui-tvosarm64`:
     * the module an injected variant's dependency (see [injectTvosVariants]) points at. Official
     * platform modules (`org.jetbrains.compose.runtime:runtime-tvosarm64`) never match because
     * their group is a mapping SOURCE, not a target.
     */
    private fun isForkPlatformModule(group: String, moduleName: String): Boolean =
        group in forkGroups && TvosTargets.matchesTvosSuffix(moduleName)

    /**
     * 1.4.0 (shared-source-set metadata fix): rewrites every dependency of a fork tvOS platform
     * module that points at a fork UMBRELLA (`dev.sajidali.G2:M2:forkVersion`) to the official
     * umbrella it mirrors (`org.jetbrains.G2:M2:officialVersion`), for all of the module's
     * variants. The official umbrella then receives its own injected tvOS variant from
     * [execute] exactly as a directly declared dependency would (or, official-first, resolves
     * to a genuine upstream tvOS variant), so the tvOS platform graph ends up with the same
     * `org.jetbrains.*` node set as every other platform's graph.
     *
     * Why this is needed: Kotlin's granular metadata transformation
     * (`SourceSetVisibilityProvider.getVisibleSourceSets`, KGP 2.4.10) decides which source sets
     * of a dependency a shared source set such as `commonMain` may see by looking the dependency
     * up -- by `group:module` of the SELECTED component -- in the compile classpath of EVERY
     * platform compilation that source set participates in, and returns "nothing visible" as
     * soon as one platform compilation does not contain it at all. Before 1.4.0 the injected
     * variant handed resolution over to the fork's platform module, whose dependencies name the
     * fork's own umbrellas, so `org.jetbrains.compose.ui:ui-graphics` (transitively requested by
     * `ui`) existed in the iOS/JVM/JS graphs but only `dev.sajidali.compose.ui:ui-graphics` existed
     * in the tvOS graphs: the transformation dropped `ui-graphics` (and every other transitive
     * Compose module) from `compileCommonMainKotlinMetadata`'s classpath, while a module
     * declared DIRECTLY in `commonMain` survived only because its official umbrella was itself
     * the node in the tvOS graph. Patching `kotlin-project-structure-metadata.json` would not
     * help: that file is only consulted after the presence check passes.
     *
     * Version handling: the official version is recovered through
     * [ComposeVersions.officialVersionCandidates] (same-version convention first, then exact
     * manifest keys targeting [forkVersion]); a candidate is accepted only if
     * [ComposeVersions.resolveVersion] maps it back to exactly the fork version the edge already
     * requested -- so the rewrite can never change which fork platform module ends up on the
     * classpath -- and only on the CONFIRMED proof described in [resolveOfficialCoordinate]
     * (D7, 1.4.2): the official coordinate's existence was confirmed by a repository, never
     * assumed, and its tvOS coverage was confirmed too. Edges with no acceptable candidate (a
     * fork-only module with no upstream twin, or a mapping that is not invertible) are left
     * untouched, which is exactly the pre-1.4.0 behavior for that one edge; when that happens
     * because the proof was INCONCLUSIVE rather than negative, the edge is additionally recorded
     * as an [UnmappedForkEdgeRecord] so the end-of-build summary names it (WARN, or a failure in
     * `strictMode`).
     *
     * Only fork platform modules are touched -- they are reachable solely through tvOS
     * resolution -- so a consumer without tvOS targets sees byte-identical resolution, and a
     * consumer's own explicitly declared `dev.sajidali.*` coordinates are never rewritten.
     */
    private fun mapForkDependenciesToOfficial(metadata: ComponentMetadataDetails, forkModule: String) {
        metadata.allVariants { variant ->
            variant.withDependencies { dependencies ->
                dependencies.toList().forEach { dependency ->
                    val official = officialCoordinateFor(
                        dependency.group, dependency.name, dependency.versionConstraint, forkModule
                    ) ?: return@forEach
                    dependencies.removeAll { it.group == dependency.group && it.name == dependency.name }
                    dependencies.add(official)
                }
            }
        }
    }

    private fun officialCoordinateFor(
        forkGroup: String,
        forkName: String,
        versionConstraint: VersionConstraint,
        forkModule: String
    ): String? {
        if (TvosTargets.matchesTvosSuffix(forkName)) return null
        val (officialGroup, officialName) = officialArtifactByForkArtifact["$forkGroup:$forkName"]
            ?: ((officialGroupByForkGroup[forkGroup] ?: return null) to forkName)
        val forkVersion = versionConstraint.requiredVersion
            .ifEmpty { versionConstraint.strictVersion }
            .ifEmpty { versionConstraint.preferredVersion }
            .ifEmpty { return null }
        val edgeKey = "$forkGroup:$forkName:$forkVersion"
        val key = "$paramsFingerprint::$edgeKey"
        val decision = officialCoordinateCache.getOrPut(key) {
            resolveOfficialCoordinate(forkGroup, forkName, forkVersion, officialGroup, officialName, edgeKey, forkModule)
        }
        // Recorded on every lookup, not only on the memoizing one: the bookkeeping is reset per
        // build while this cache spans the daemon, and each build must report its own gaps.
        decision.unmapped?.let { TvosDiagnosticsBookkeeping.recordUnmappedForkEdge(it) }
        return decision.official
    }

    /**
     * Memoized outcome of [resolveOfficialCoordinate] for one fork umbrella edge: the official
     * coordinate to rewrite to, or `null` to leave the edge as is; [unmapped] is non-null only
     * when the edge was left as is for an INCONCLUSIVE reason (see [UnmappedForkEdgeRecord]).
     */
    private data class OfficialEdgeDecision(
        val official: String?,
        val unmapped: UnmappedForkEdgeRecord?
    )

    /**
     * D7 (1.4.2): decides whether the fork umbrella edge `forkGroup:forkName:forkVersion` may be
     * rewritten to `officialGroup:officialName:<candidate>`, FAILING CLOSED. Before 1.4.2 the
     * only guard was the lenient [TvosVariantDiscovery.targetModuleExists], which reads an
     * inconclusive probe (timeout, 5xx, 401/403, DNS error) and `--offline` as "exists": one
     * flaky repository was enough to rewrite
     * `dev.sajidali.androidx.navigationevent:navigationevent-compose:1.1.1` to an
     * `org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.1` JetBrains never
     * published, and `@CacheableRule` then froze that rewrite into Gradle's metadata cache.
     *
     * A candidate is accepted only when ALL of the following hold:
     *  1. [ComposeVersions.resolveVersion] maps it back to exactly [forkVersion] (same-version
     *     policy; a rewrite can never change which fork platform module is selected).
     *  2. [TvosVariantDiscovery.probeTargetModuleExistence] returns a confirmed
     *     [TvosVariantDiscovery.ModuleExistence.Exists] for the official umbrella. `Unknown`
     *     (including offline with nothing cached) is NOT accepted -- it is not a confirmed
     *     absence either, so it is recorded as inconclusive.
     *  3. [hasConfirmedTvosCoverage]: the official umbrella is actually usable for tvOS -- either
     *     this rule will inject a fork tvOS variant into it (fork discovery for the SAME
     *     `forkGroup:forkName:forkVersion` is non-empty), or the official umbrella's own tvOS
     *     variants are real (non-empty, and every `available-at` leaf confirmed `Exists`).
     *     Merely existing is not enough: `org.jetbrains.androidx.navigationevent:
     *     navigationevent-compose:1.1.0` exists and advertises `tvos_arm64`, yet its leaf 404s.
     *
     * A confirmed `Absent` on every candidate leaves the edge as is silently (verbose log only),
     * exactly as before. Any other rejection of a version-compatible candidate is inconclusive
     * and yields an [UnmappedForkEdgeRecord] naming the exact coordinate.
     */
    private fun resolveOfficialCoordinate(
        forkGroup: String,
        forkName: String,
        forkVersion: String,
        officialGroup: String,
        officialName: String,
        forkCoordinate: String,
        forkModule: String
    ): OfficialEdgeDecision {
        val candidates = ComposeVersions.officialVersionCandidates(
            officialGroup, officialName, forkVersion, params.versionMappings
        )
        val inconclusive = mutableListOf<Pair<String, String>>()
        for (candidate in candidates) {
            val forward = ComposeVersions.resolveVersion(
                officialGroup, officialName, candidate, params.versionMappings, params.targetVersionOverride
            )
            if (forward != forkVersion) continue
            val official = "$officialGroup:$officialName:$candidate"
            val existence = TvosVariantDiscovery.probeTargetModuleExistence(
                params.repositoryUrls, officialGroup, officialName, candidate, File(params.cacheDirPath),
                if (params.verbose) logger else null, params.offline
            )
            when (existence) {
                TvosVariantDiscovery.ModuleExistence.Absent -> continue
                TvosVariantDiscovery.ModuleExistence.Unknown -> {
                    inconclusive += official to if (params.offline) {
                        "offline and no cached existence verdict for it"
                    } else {
                        "its existence could not be confirmed (a configured repository failed to answer)"
                    }
                    continue
                }
                TvosVariantDiscovery.ModuleExistence.Exists -> Unit
            }
            if (!hasConfirmedTvosCoverage(forkGroup, forkName, forkVersion, officialGroup, officialName, candidate)) {
                inconclusive += official to "it exists but no tvOS coverage could be confirmed for it " +
                    "(fork discovery for $forkCoordinate returned nothing and the official artifact's own " +
                    "tvOS variants are missing or unverified)"
                continue
            }
            if (params.verbose) {
                logger.lifecycle(
                    "[ComposeTvosRedirect] Mapping transitive dependency of $forkModule back to the " +
                        "official umbrella: $forkCoordinate -> $official"
                )
            }
            return OfficialEdgeDecision(official, null)
        }
        if (params.verbose) {
            logger.lifecycle(
                "[ComposeTvosRedirect] No official umbrella found for transitive dependency $forkCoordinate " +
                    "of $forkModule (candidates: $candidates); leaving it as is"
            )
        }
        val unmapped = inconclusive.firstOrNull()?.let { (official, reason) ->
            UnmappedForkEdgeRecord(forkModule, forkCoordinate, official, reason, params.repositoryUrls, params.offline)
        }
        return OfficialEdgeDecision(null, unmapped)
    }

    /**
     * D7 proof step 3 (see [resolveOfficialCoordinate]): is the official umbrella
     * `officialGroup:officialName:officialVersion` genuinely usable from a tvOS graph? True when
     * either
     *  - fork discovery for `forkGroup:forkName:forkVersion` is non-empty: this rule will then
     *    inject those variants into the official umbrella when it fires on it (or skip them
     *    because the official artifact already covers the target), so tvOS resolution through the
     *    official node is guaranteed to have a variant to select; or
     *  - the official umbrella's own tvOS variants are non-empty AND every `available-at` leaf
     *    among them is confirmed [TvosVariantDiscovery.ModuleExistence.Exists] (inline variants
     *    count as confirmed: there is no separate leaf to probe).
     * Every lookup here is the same memoized [getVariants] / existence probe the rule performs
     * anyway when it later fires on the official umbrella, so this adds no network round trip
     * beyond what a healthy build already pays.
     */
    private fun hasConfirmedTvosCoverage(
        forkGroup: String,
        forkName: String,
        forkVersion: String,
        officialGroup: String,
        officialName: String,
        officialVersion: String
    ): Boolean {
        if (getVariants(forkGroup, forkName, forkVersion).isNotEmpty()) return true
        val officialVariants = getVariants(officialGroup, officialName, officialVersion)
        if (officialVariants.isEmpty()) return false
        return officialVariants.all { variant ->
            val leafGroup = variant.availableAtGroup
            val leafVersion = variant.availableAtVersion
            if (leafGroup == null || leafVersion == null) return@all true
            TvosVariantDiscovery.probeTargetModuleExistence(
                params.repositoryUrls, leafGroup, variant.artifactId, leafVersion, File(params.cacheDirPath),
                if (params.verbose) logger else null, params.offline
            ) == TvosVariantDiscovery.ModuleExistence.Exists
        }
    }

    /**
     * Fetches (and memoizes) the tvOS variants for `groupId:artifactId:version`, via
     * [TvosVariantDiscovery.discoverVariants]. Shared by both callers in [execute]: the
     * redirect TARGET coordinate (dev.sajidali-mapped) and, since Task 10b, the OFFICIAL
     * coordinate (this component's own identity) used for the already-supported pre-check --
     * these are always different cache keys, since the mapped target group never equals the
     * original (JetBrains) group.
     */
    private fun getVariants(groupId: String, artifactId: String, version: String): List<TvosVariant> {
        val key = "$groupId:$artifactId:$version"
        return variantCache.getOrPut(key) {
            TvosVariantDiscovery.discoverVariants(
                params.repositoryUrls, groupId, artifactId, version, File(params.cacheDirPath),
                if (params.verbose) logger else null,
                offline = params.offline
            )
        }
    }

    private fun injectTvosVariants(
        metadata: ComponentMetadataDetails,
        targetGroup: String,
        version: String,
        variants: List<TvosVariant>
    ) {
        variants.forEach { variant ->
            metadata.addVariant("${variant.variantName}-injected") { variantMetadata ->
                variantMetadata.attributes { attrs ->
                    val attributesToApply = if (variant.attributes.isNotEmpty()) {
                        variant.attributes
                    } else {
                        // Fallback for cache entries predating attribute capture. This
                        // covers the common kotlin-api request path but does NOT cover
                        // metadata / sources / runtime variant lookups — if a consumer
                        // reports unresolved references in a shared source set (e.g.
                        // `appleMain`) after upgrading, deleting the cache directory
                        // (<gradleUserHome>/compose-tvos-redirect-cache-v4/, `~/.gradle` by
                        // default) forces re-discovery
                        // with the full attribute set.
                        mapOf(
                            "org.gradle.category" to "library",
                            "org.gradle.usage" to "kotlin-api",
                            "org.gradle.jvm.environment" to "non-jvm",
                            "org.jetbrains.kotlin.platform.type" to "native",
                            "org.jetbrains.kotlin.native.target" to variant.nativeTarget
                        )
                    }
                    attributesToApply.forEach { (key, value) ->
                        attrs.attribute(Attribute.of(key, String::class.java), value)
                    }
                }
                variantMetadata.withDependencies { deps ->
                    deps.add("$targetGroup:${variant.artifactId}:$version")
                }
            }
        }
    }

    /**
     * Task 11 (dangling-metadata fix): de-tunes each ORIGINAL official [officialVariants] entry
     * whose native target is confirmed dangling ([danglingNativeTargets]) so it can never again
     * be selected as a candidate for a real consumer request, clearing the way for the freshly
     * [injectTvosVariants]-added variant (same native target, pointing at the fork) to be the
     * SOLE match.
     *
     * Why this exists at all: once a dangling native target is excluded from
     * `alreadySupportedTargets`, [execute] injects a normal variant for it exactly as if the
     * official artifact had never mentioned that target -- but the official artifact's ORIGINAL
     * variant (the one whose `available-at` redirect is broken) is still sitting there,
     * unmodified, in this component's metadata. Since [injectTvosVariants] mirrors the fork
     * variant's attributes 1:1 (the same mechanism Task 10b relies on for the genuinely-official
     * case), the newly injected variant and the original dangling one end up with IDENTICAL
     * attribute sets -- Gradle then cannot choose between them and fails the whole component
     * with a "cannot choose between ..." variant-ambiguity error (confirmed red: the dangling
     * functional-test scenario failed with exactly this error before this fix was added).
     *
     * Why de-tuning attributes rather than something more surgical: [ComponentMetadataDetails]'s
     * public API has no way to remove a variant outright, and [org.gradle.api.artifacts.VariantMetadata]
     * has no way to override an EXISTING variant's `available-at` redirect target (its
     * `withDependencies`/`withFiles` mutate a variant's OWN local dependency/file list, which an
     * available-at variant doesn't have -- its entire identity is delegated to the target
     * module). Overwriting the variant's own `org.jetbrains.kotlin.native.target` attribute value
     * to a value no real consumer request will ever carry is the one lever both APIs do expose:
     * [ComponentMetadataDetails.withVariant] can still target an already-declared (including
     * available-at-backed) variant by name, and [org.gradle.api.artifacts.VariantMetadata.attributes]
     * lets an existing attribute's value be overwritten (not just added to), same as any other
     * `AttributeContainer`. Once that attribute no longer reads `tvos_arm64` (etc.), the variant
     * simply stops matching any real `tvosArm64CompileKlibraries`-shaped request, so Gradle's
     * variant selection is left with exactly one candidate: the injected one.
     *
     * Finding 2 (review of task-20-report.md): [danglingNativeTargets] as passed by [execute] is
     * NOT the raw `supportResult.danglingNativeTargets` set -- it is pre-filtered down to only
     * the targets `injectable` will actually replace. A dangling target the fork has no variant
     * for either must never reach this function: there would be no injected replacement left
     * behind, so de-tuning the official variant would only turn a recognizable
     * "target module not found" resolution failure into an unhelpful "no matching variant" one.
     */
    private fun poisonDanglingOfficialVariants(
        metadata: ComponentMetadataDetails,
        officialVariants: List<TvosVariant>,
        danglingNativeTargets: Set<String>
    ) {
        officialVariants.filter { it.nativeTarget in danglingNativeTargets }.forEach { variant ->
            metadata.withVariant(variant.variantName) { variantMetadata ->
                variantMetadata.attributes { attrs ->
                    attrs.attribute(
                        Attribute.of("org.jetbrains.kotlin.native.target", String::class.java),
                        "${variant.nativeTarget}-dangling-unavailable"
                    )
                }
            }
        }
    }

    companion object {
        private val logger: Logger = Logging.getLogger(TvosVariantInjectionRule::class.java)

        // Rule instances are created per-component by Gradle, so this cache must live at
        // the companion-object level (shared across all instances) rather than as an
        // instance field to actually memoize across components, mirroring the previous
        // settings-plugin-local `variantCache`.
        private val variantCache = ConcurrentHashMap<String, List<TvosVariant>>()

        // 1.4.0: memoizes mapForkDependenciesToOfficial's per-edge decision (fork umbrella
        // coordinate -> official umbrella coordinate, or none, plus the D7 inconclusive record).
        // The same fork umbrella is depended on by dozens of fork platform modules x variants,
        // and the underlying existence probes are already cached, so this only saves repeated
        // candidate evaluation and keeps the verbose log to one line per distinct edge. Keyed by
        // `paramsFingerprint::forkGroup:forkName:forkVersion` (see [paramsFingerprint]), NOT just
        // the edge, because this cache is shared across every build in the same Gradle daemon
        // and the decision depends on that build's version mappings, override and repositories.
        // An inconclusive (D7) decision is memoized too, bounding the re-probe cost of a flaky
        // repository to one attempt per distinct edge per daemon; a daemon restart (or
        // `--stop`) retries it.
        private val officialCoordinateCache = ConcurrentHashMap<String, OfficialEdgeDecision>()

        // Diagnostics bookkeeping (Task 5 / defect D1) moved to [TvosDiagnosticsBookkeeping]
        // in Task 10e -- see that object's KDoc for why: ComposeTvosRedirectPlugin's own
        // "official-first" success path now needs to record into the exact same
        // skippedAlreadySupported map, so the bookkeeping itself no longer lives on this rule's
        // companion object specifically.
    }
}
