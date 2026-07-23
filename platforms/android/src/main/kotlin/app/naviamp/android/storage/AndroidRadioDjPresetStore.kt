package app.naviamp.android

import app.naviamp.domain.radio.RadioArtistRunMode
import app.naviamp.domain.radio.RadioArtistSpread
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.radio.RadioFamiliarity
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.storage.NaviampStorageQueries

class AndroidRadioDjPresetStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
) : RadioDjPresetRepository {
    override fun radioDjPresets(): List<RadioDjPreset> =
        queries.radioDjPresets(::rowToPreset).executeAsList()

    override fun replaceRadioDjPresets(presets: List<RadioDjPreset>) {
        queries.transaction {
            queries.clearRadioDjPresets()
            presets.map { it.normalized() }.forEachIndexed { index, preset ->
                upsertPreset(preset, sortOrder = index.toLong())
            }
        }
    }

    override fun upsertRadioDjPreset(preset: RadioDjPreset) {
        val existing = radioDjPresets()
        val sortOrder = existing.indexOfFirst { it.id == preset.id }
            .takeIf { it >= 0 }
            ?.toLong()
            ?: existing.size.toLong()
        upsertPreset(preset.normalized(), sortOrder)
    }

    override fun deleteRadioDjPreset(id: String) {
        queries.deleteRadioDjPreset(id)
    }

    private fun upsertPreset(preset: RadioDjPreset, sortOrder: Long) {
        val now = nowMillis()
        queries.upsertRadioDjPreset(
            id = preset.id,
            name = preset.name,
            familiarity = preset.tuning.familiarity.name,
            artist_spread = preset.tuning.artistSpread.name,
            same_decade_only = if (preset.tuning.sameDecadeOnly) 1L else 0L,
            artist_run_mode = preset.tuning.artistRunMode.name,
            same_artist_run_length = preset.tuning.sameArtistRunLength.toLong(),
            other_artist_run_length = preset.tuning.otherArtistRunLength.toLong(),
            sort_order = sortOrder,
            created_at_epoch_millis = now,
            updated_at_epoch_millis = now,
        )
    }
}

private fun rowToPreset(
    id: String,
    name: String,
    familiarity: String,
    artistSpread: String,
    sameDecadeOnly: Long,
    artistRunMode: String,
    sameArtistRunLength: Long,
    otherArtistRunLength: Long,
    sortOrder: Long,
    createdAtEpochMillis: Long,
    updatedAtEpochMillis: Long,
): RadioDjPreset =
    RadioDjPreset(
        id = id,
        name = name,
        tuning = RadioTuningSettings(
            familiarity = enumValueOrDefault(familiarity, RadioFamiliarity.Balanced),
            artistSpread = enumValueOrDefault(artistSpread, RadioArtistSpread.Balanced),
            sameDecadeOnly = sameDecadeOnly != 0L,
            artistRunMode = enumValueOrDefault(artistRunMode, RadioArtistRunMode.Mixed),
            sameArtistRunLength = sameArtistRunLength.toInt(),
            otherArtistRunLength = otherArtistRunLength.toInt(),
        ),
    ).normalized()

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
