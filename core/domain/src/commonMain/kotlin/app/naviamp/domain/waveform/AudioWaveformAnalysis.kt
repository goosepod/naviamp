package app.naviamp.domain.waveform

data class AudioWaveformAnalysisSource(
    val cacheKey: String,
    val streamUrl: String,
)

interface AudioWaveformAnalyzer {
    suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform?
}
