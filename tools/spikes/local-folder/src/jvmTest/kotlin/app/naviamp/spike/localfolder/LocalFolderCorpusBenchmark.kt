package app.naviamp.spike.localfolder

import app.naviamp.domain.Track
import app.naviamp.spike.localfolder.tags.AudioTags
import app.naviamp.spike.localfolder.tags.readAudioTags
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Runs only with -Dnaviamp.spike.corpus=<generate_corpus.py output>. Verifies every file against the
 * manifest written by the generator and prints enumeration, tag-read and memory measurements.
 */
class LocalFolderCorpusBenchmark {
    private data class Expected(
        val format: String, val title: String, val artist: String, val albumArtist: String, val album: String,
        val track: Int, val disc: Int, val year: Int, val genre: String, val artwork: Boolean, val gain: Double?,
    )

    private data class Read(val file: ScannedFile, val tags: AudioTags, val nativeReads: Int, val bytes: Long)

    @Test
    fun corpus() = runBlocking {
        val corpus = System.getProperty("naviamp.spike.corpus")?.let(::File) ?: return@runBlocking
        val threads = System.getProperty("naviamp.spike.threads")?.toInt() ?: 4
        val manifest = File(corpus, "manifest.tsv").readLines().filter(String::isNotBlank).associate { line ->
            val c = line.split('\t')
            c[0] to Expected(c[1], c[2], c[3], c[4], c[5], c[6].toInt(), c[7].toInt(), c[8].toInt(), c[9], c[10] == "1", c[11].toDoubleOrNull())
        }
        val grant = FolderGrant(File(corpus, "library").absolutePath, "library")
        val report = StringBuilder()
        fun line(text: String) { println(text); report.appendLine(text) }
        line("Corpus: ${manifest.size} files, JVM ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} CPUs, -Xmx${Runtime.getRuntime().maxMemory() / 1_048_576} MiB")

        val baseline = settledHeap()
        var access = JvmFolderTreeAccess()
        resetPeaks()
        var started = System.nanoTime()
        val scan = access.scan(grant)
        val coldScanMs = (System.nanoTime() - started) / 1e6
        val scanPeak = peakHeap()
        val scanRetained = settledHeap() - baseline
        line("Enumeration (first run): ${scan.files.size} audio files, ${scan.directories} directories, ${access.listingCalls} listing calls, %.0f ms, retained %.1f MiB, peak heap %.1f MiB"
            .format(coldScanMs, scanRetained / 1_048_576.0, scanPeak / 1_048_576.0))
        assertEquals(manifest.size, scan.files.size)
        assertTrue(scan.complete)

        access = JvmFolderTreeAccess()
        started = System.nanoTime()
        val rescan = access.scan(grant)
        val previous = scan.files.map { IndexedFile(it.relativePath, it.fingerprint) }
        val plan = planRescan(previous, rescan.files.map(ScannedFile::fingerprint))
        line("Manual rescan with no changes (walk plus diff): %.0f ms, files to re-read: ${plan.filesToRead}".format((System.nanoTime() - started) / 1e6))
        assertEquals(0, plan.filesToRead)

        for (pass in listOf(threads, 1, threads)) {
            resetPeaks()
            val pool = Executors.newFixedThreadPool(pass)
            val perFileNanos = AtomicLong()
            started = System.nanoTime()
            val reads = pool.asCoroutineDispatcher().use { dispatcher ->
                scan.files.chunked(256).map { batch ->
                    async(dispatcher) {
                        batch.map { file ->
                            val begin = System.nanoTime()
                            access.open(grant, file.entry).use { source ->
                                val result = readAudioTags(source)
                                perFileNanos.addAndGet(System.nanoTime() - begin)
                                Read(file, result.tags, result.nativeReads, result.bytesRead)
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            val wallMs = (System.nanoTime() - started) / 1e6
            line("Tag read, $pass thread(s): %.0f ms wall, %.1f files/s, mean %.3f ms per file, peak heap %.1f MiB, mean %.1f native reads and %.1f KiB per file"
                .format(wallMs, reads.size / (wallMs / 1000), perFileNanos.get() / 1e6 / reads.size, peakHeap() / 1_048_576.0,
                    reads.map(Read::nativeReads).average(), reads.map(Read::bytes).average() / 1024))
            if (pass == threads) verify(reads, manifest, ::line)
        }

        // Baseline: the existing Core parser behind AudioTagReader reads a fixed 2 MiB prefix per file.
        var prefixBytes = 0L
        var longPrefixBytes = 0L
        var longFiles = 0
        val coveredByFormat = mutableMapOf<String, Int>()
        started = System.nanoTime()
        scan.files.forEach { file ->
            val prefix = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(grant.token, file.entry.id)).use {
                it.readNBytes(app.naviamp.domain.audio.MaximumAudioTagProbeBytes)
            }
            prefixBytes += prefix.size
            if (file.entry.sizeBytes!! > 400_000) { longPrefixBytes += prefix.size; longFiles++ }
            if (app.naviamp.domain.audio.audioTagsFromAudioBytes(prefix).isNotEmpty()) {
                coveredByFormat.merge(manifest.getValue(file.relativePath).format, 1, Int::plus)
            }
        }
        line("Existing Core AudioTagParser (2 MiB prefix), 1 thread: %.0f ms, mean %.1f KiB read per file (%.0f KiB per 180 s file), files with any tag by format: ${coveredByFormat.toSortedMap()}"
            .format((System.nanoTime() - started) / 1e6, prefixBytes / 1024.0 / scan.files.size, longPrefixBytes / 1024.0 / maxOf(1, longFiles)))

        // Playback input: drain the largest files through the exact BASS FILEPROCS cursor used by every host.
        val largest = scan.files.sortedByDescending { it.entry.sizeBytes }.take(20)
        var drained = 0L
        var callbacksCalls = 0
        started = System.nanoTime()
        largest.forEach { file ->
            val source = LocalFolderRandomAccessSource(access.open(grant, file.entry))
            val callbacks = app.naviamp.domain.playback.BassFileCallbacks(source)
            callbacks.seek(callbacks.length() / 2)
            callbacks.seek(0)
            while (true) {
                val chunk = callbacks.read(65_536)
                callbacksCalls++
                if (chunk.isEmpty()) break
                drained += chunk.size
            }
            callbacks.close()
            source.close()
        }
        val drainSeconds = (System.nanoTime() - started) / 1e9
        line("BassFileCallbacks over LocalByteSource: ${largest.size} files, %.1f MiB in %.0f ms (%.0f MiB/s), %.1f us per 64 KiB callback"
            .format(drained / 1_048_576.0, drainSeconds * 1000, drained / 1_048_576.0 / drainSeconds, drainSeconds * 1e6 / callbacksCalls))

        val reads = scan.files.map { file -> access.open(grant, file.entry).use { Read(file, readAudioTags(it).tags, 0, 0) } }
        val beforeTracks = settledHeap()
        val tracks: List<Track> = reads.map { localFolderTrack("source_spike", it.file, it.tags) }
        line("Catalog mapping: ${tracks.size} Track models, ${tracks.mapNotNull(Track::albumId).distinct().size} albums, ${tracks.mapNotNull(Track::artistId).distinct().size} artists, retained %.1f MiB"
            .format((settledHeap() - beforeTracks) / 1_048_576.0))
        assertEquals(tracks.size, tracks.map(Track::id).distinct().size)
        File(corpus, "report.txt").writeText(report.toString())
    }

    private fun verify(reads: List<Read>, manifest: Map<String, Expected>, line: (String) -> Unit) {
        val failures = mutableMapOf<String, MutableList<String>>()
        reads.forEach { read ->
            val expected = manifest.getValue(read.file.relativePath)
            val tags = read.tags
            fun check(field: String, ok: Boolean) { if (!ok) failures.getOrPut(expected.format) { mutableListOf() } += "${read.file.relativePath}: $field = $tags" }
            check("title", tags.title == expected.title)
            check("artist", tags.artists == listOf(expected.artist))
            check("albumArtist", tags.albumArtist == expected.albumArtist)
            check("album", tags.album == expected.album)
            check("track", tags.trackNumber == expected.track && tags.trackTotal == 10)
            check("disc", tags.discNumber == expected.disc)
            check("year", tags.year == expected.year)
            check("genre", tags.genres == listOf(expected.genre))
            check("artwork", (tags.artwork != null) == expected.artwork)
            check("gain", expected.gain == null || tags.replayGain.trackGainDb?.let { abs(it - expected.gain) < 0.005 } == true)
            val seconds = if (read.file.entry.sizeBytes!! > 400_000) 180_000 else 2_000
            check("duration", tags.durationMillis?.let { abs(it - seconds) <= 120 } == true)
            check("sampleRate", tags.sampleRateHz != null && tags.sampleRateHz > 0)
        }
        val byFormat = reads.groupBy { manifest.getValue(it.file.relativePath).format }
        byFormat.toSortedMap().forEach { (format, items) ->
            line("  $format: ${items.size} files, ${failures[format]?.size ?: 0} field mismatches, mean ${"%.1f".format(items.map(Read::bytes).average() / 1024)} KiB read")
        }
        reads.filter { it.file.entry.sizeBytes!! > 400_000 }.takeIf { it.isNotEmpty() }?.let { long ->
            line("  180 s files: ${long.size}, mean size %.0f KiB, mean %.1f KiB read, max %.1f KiB read, mean %.1f native reads"
                .format(long.map { it.file.entry.sizeBytes!! }.average() / 1024, long.map(Read::bytes).average() / 1024,
                    long.maxOf(Read::bytes) / 1024.0, long.map(Read::nativeReads).average()))
        }
        failures.values.flatten().take(10).forEach { line("    $it") }
        assertTrue(failures.isEmpty(), "tag mismatches: ${failures.mapValues { it.value.size }}")
    }

    private fun settledHeap(): Long {
        repeat(3) { System.gc(); Thread.sleep(50) }
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun resetPeaks() {
        System.gc()
        ManagementFactory.getMemoryPoolMXBeans().forEach { it.resetPeakUsage() }
    }

    private fun peakHeap(): Long = ManagementFactory.getMemoryPoolMXBeans()
        .filter { it.type == MemoryType.HEAP }.sumOf { it.peakUsage.used }
}
