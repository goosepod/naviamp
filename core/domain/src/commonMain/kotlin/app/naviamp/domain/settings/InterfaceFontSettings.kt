package app.naviamp.domain.settings

import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.intOrNull

/** A text-size preference as a percentage of Naviamp's standard size, in 5% steps. */
@Serializable(with = InterfaceFontSizeSerializer::class)
@JvmInline
value class InterfaceFontSize(val percent: Int) {
    val scale: Float get() = normalizedInterfaceFontSizePercent(percent) / 100f

    fun normalized(): InterfaceFontSize = InterfaceFontSize(normalizedInterfaceFontSizePercent(percent))

    companion object {
        const val MinPercent = 80
        const val MaxPercent = 150
        const val StepPercent = 5
        val Small = InterfaceFontSize(90)
        val Standard = InterfaceFontSize(100)
        val Large = InterfaceFontSize(115)
        val Steps: List<InterfaceFontSize> =
            (MinPercent..MaxPercent step StepPercent).map(::InterfaceFontSize)
    }
}

fun normalizedInterfaceFontSizePercent(percent: Int): Int {
    val clamped = percent.coerceIn(InterfaceFontSize.MinPercent, InterfaceFontSize.MaxPercent)
    val step = InterfaceFontSize.StepPercent
    return ((clamped + step / 2) / step) * step
}

@Serializable
enum class InterfaceFontFamily {
    NunitoSans,
    Inter,
    System,
}

/** The font this device renders; the stored choice itself is never changed by a fallback. */
data class InterfaceFontSelection(
    val family: InterfaceFontFamily,
    val installedFamily: String? = null,
    /** The stored installed family when it is not installed, or not usable, on this device. */
    val unavailableInstalledFamily: String? = null,
)

/**
 * [availableInstalledFamilies] holds the usable installed families on this device, or null where
 * none can be listed. A synced family that is missing or unusable here falls back to Nunito Sans
 * and applies again on a device that has it.
 */
fun InterfaceSettings.resolvedFontSelection(availableInstalledFamilies: Collection<String>?): InterfaceFontSelection {
    val requested = normalizedInstalledFontFamily(installedFontFamily)
        ?: return InterfaceFontSelection(fontFamily)
    val match = availableInstalledFamilies?.firstOrNull { it == requested }
        ?: availableInstalledFamilies?.firstOrNull { it.equals(requested, ignoreCase = true) }
    return if (match != null) {
        InterfaceFontSelection(fontFamily, installedFamily = match)
    } else {
        InterfaceFontSelection(InterfaceFontFamily.NunitoSans, unavailableInstalledFamily = requested)
    }
}

fun InterfaceSettings.withBundledFontFamily(family: InterfaceFontFamily): InterfaceSettings =
    copy(fontFamily = family, installedFontFamily = null).normalized()

fun InterfaceSettings.withInstalledFontFamily(name: String): InterfaceSettings =
    copy(installedFontFamily = name).normalized()

internal fun normalizedInstalledFontFamily(name: String?): String? =
    name?.trim()?.takeIf { it.isNotEmpty() }?.take(MaxInstalledFontFamilyLength)

private const val MaxInstalledFontFamilyLength = 256

/**
 * Encodes the percentage as a number but also accepts a stored preset name, so a decoder that
 * bypasses [InterfaceSettingsSerializer] still keeps the size instead of rejecting the section.
 */
internal object InterfaceFontSizeSerializer : KSerializer<InterfaceFontSize> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.naviamp.domain.settings.InterfaceFontSize", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: InterfaceFontSize) = encoder.encodeInt(value.percent)

    override fun deserialize(decoder: Decoder): InterfaceFontSize {
        val json = decoder as? JsonDecoder ?: return InterfaceFontSize(decoder.decodeInt())
        val value = json.decodeJsonElement() as? JsonPrimitive ?: return InterfaceFontSize.Standard
        val percent = if (value.isString) LegacyPresetPercents[value.content] ?: value.content.toIntOrNull() else value.intOrNull
        return InterfaceFontSize(percent ?: InterfaceFontSize.Standard.percent)
    }
}

/**
 * Older Naviamp versions decode each font size as a `Small`/`Standard`/`Large` enum and reject
 * anything else, which would discard the whole interface section. The legacy keys therefore keep
 * the nearest preset name while the exact percentage travels in a companion key they ignore.
 */
internal object InterfaceSettingsSerializer :
    JsonTransformingSerializer<InterfaceSettings>(InterfaceSettings.generatedSerializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val fields = element.toMutableMap()
        for (key in FontSizeKeys) {
            val percent = (fields[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: continue
            fields[key] = JsonPrimitive(legacyPresetName(percent))
            fields[key + PercentSuffix] = JsonPrimitive(percent)
        }
        return JsonObject(fields)
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val fields = element.toMutableMap()
        for (key in FontSizeKeys) {
            val legacy = fields[key] as? JsonPrimitive
            val legacyPercent = when {
                legacy == null -> null
                legacy.isString -> LegacyPresetPercents[legacy.content]
                else -> legacy.intOrNull
            }
            val exact = (fields.remove(key + PercentSuffix) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            // An older writer can change the preset and drop the exact key; a stale exact value
            // must not override a preset chosen there.
            val resolved = exact?.takeIf { legacyPercent == null || legacyPresetName(it) == legacyPresetName(legacyPercent) }
                ?: legacyPercent
            if (resolved == null) fields.remove(key) else fields[key] = JsonPrimitive(resolved)
        }
        return JsonObject(fields)
    }
}

private val FontSizeKeys = listOf("generalFontSize", "nowPlayingFontSize")
private const val PercentSuffix = "Percent"
private val LegacyPresetPercents = mapOf("Small" to 90, "Standard" to 100, "Large" to 115)

private fun legacyPresetName(percent: Int): String = when {
    percent < 95 -> "Small"
    percent <= 105 -> "Standard"
    else -> "Large"
}
