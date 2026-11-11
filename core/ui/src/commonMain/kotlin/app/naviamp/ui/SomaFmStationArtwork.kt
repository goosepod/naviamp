package app.naviamp.ui

import app.naviamp.domain.InternetRadioStation

/** Official channel identities and artwork paths from https://somafm.com/channels.json,
 * verified 2026-11-11. These are lookup keys, not displayed station names.
 * Keep exact filenames: some channels use JPEG, hyphens or digits in their IDs.
 */
private data class SomaFmArtwork(val id: String, val titleKey: String, val file: String)

private val somaFmArtwork = listOf(
    SomaFmArtwork("beatblender", "beatblender", "beatblender512.png"),
    SomaFmArtwork("brfm", "blackrockfm", "brfm512.jpg"),
    SomaFmArtwork("bootliquor", "bootliquor", "bootliquor512.png"),
    SomaFmArtwork("bossa", "bossabeyond", "bossa-512.jpg"),
    SomaFmArtwork("chillits", "chillitsradio", "chillits512.png"),
    SomaFmArtwork("christmas", "christmaslounge", "christmas512.png"),
    SomaFmArtwork("xmasrocks", "christmasrocks", "xmasrocks512.png"),
    SomaFmArtwork("cliqhop", "cliqhopidm", "cliqhop512.png"),
    SomaFmArtwork("covers", "covers", "covers512.png"),
    SomaFmArtwork("darkzone", "thedarkzone", "darkzone512.jpg"),
    SomaFmArtwork("deepspaceone", "deepspaceone", "deepspaceone512.png"),
    SomaFmArtwork("defcon", "defconradio", "defcon512.png"),
    SomaFmArtwork("deptstore", "departmentstorechristmas", "deptstore512.png"),
    SomaFmArtwork("digitalis", "digitalis", "digitalis512.png"),
    SomaFmArtwork("doomed", "doomed", "doomed512.png"),
    SomaFmArtwork("dronezone", "dronezone", "dronezone512.png"),
    SomaFmArtwork("dz2", "dronezone2", "dz2512.png"),
    SomaFmArtwork("dubstep", "dubstepbeyond", "dubstep512.png"),
    SomaFmArtwork("fluid", "fluid", "fluid512.jpg"),
    SomaFmArtwork("folkfwd", "folkforward", "folkfwd512.png"),
    SomaFmArtwork("groovesalad", "groovesalad", "groovesalad512.png"),
    SomaFmArtwork("groovesalad2", "groovesalad2", "groovesalad2512.png"),
    SomaFmArtwork("gsclassic", "groovesaladclassic", "gsclassic512.jpg"),
    SomaFmArtwork("reggae", "heavyweightreggae", "reggae512.png"),
    SomaFmArtwork("illstreet", "illinoisstreetlounge", "illstreet512.png"),
    SomaFmArtwork("insound", "theinsound", "insound-512.jpg"),
    SomaFmArtwork("indiepop", "indiepoprocks", "indiepop512.png"),
    SomaFmArtwork("jollysoul", "jollyolsoul", "jollysoul512.png"),
    SomaFmArtwork("seventies", "leftcoast70s", "seventies512.jpg"),
    SomaFmArtwork("lush", "lush", "lush512.png"),
    SomaFmArtwork("metal", "metaldetector", "metal512.png"),
    SomaFmArtwork("missioncontrol", "missioncontrol", "missioncontrol512.jpg"),
    SomaFmArtwork("n5md", "n5mdradio", "n5md512.png"),
    SomaFmArtwork("poptron", "poptron", "poptron512.png"),
    SomaFmArtwork("secretagent", "secretagent", "secretagent512.png"),
    SomaFmArtwork("7soul", "seveninchsoul", "7soul512.png"),
    SomaFmArtwork("sf1033", "sf1033", "sf1033512.png"),
    SomaFmArtwork("sfinsf", "sfinsf", "sfinsf512.jpg"),
    SomaFmArtwork("scanner", "sfpolicescanner", "scanner512.png"),
    SomaFmArtwork("live", "somafmlive", "live512.png"),
    SomaFmArtwork("specials", "somafmspecials", "specials512.png"),
    SomaFmArtwork("sonicuniverse", "sonicuniverse", "sonicuniverse512.png"),
    SomaFmArtwork("spacestation", "spacestationsoma", "spacestation512.png"),
    SomaFmArtwork("suburbsofgoa", "suburbsofgoa", "suburbsofgoa512.png"),
    SomaFmArtwork("synphaera", "synphaeraradio", "synphaera512.jpg"),
    SomaFmArtwork("thistle", "thistleradio", "thistle512.jpg"),
    SomaFmArtwork("tikitime", "tikitime", "tikitime512.jpg"),
    SomaFmArtwork("thetrip", "thetrip", "thetrip512.jpg"),
    SomaFmArtwork("u80s", "underground80s", "u80s512.png"),
    SomaFmArtwork("vaporwaves", "vaporwaves", "vaporwaves512.png"),
    SomaFmArtwork("xmasinfrisko", "xmasinfrisko", "xmasinfrisko512.jpg"),
)

private val somaFmArtworkByIdLength = somaFmArtwork.sortedByDescending { it.id.length }
private val somaFmStreamSuffix = Regex("""(?:\d+|[-].*)""")

internal fun somaFmStationArtworkUrl(station: InternetRadioStation): String? {
    val urls = listOfNotNull(station.streamUrl, station.homePageUrl)
    val paths = urls.mapNotNull(::somaFmChannelPath)
    if (paths.isEmpty() && "somafm" !in station.name.lowercase() &&
        "soma.fm" !in station.name.lowercase()) return null
    val channel = paths.firstNotNullOfOrNull { path ->
        somaFmArtwork.firstOrNull { it.id == path }
            ?: somaFmArtworkByIdLength.firstOrNull {
                path.startsWith(it.id) && path.removePrefix(it.id).matches(somaFmStreamSuffix)
            }
    } ?: station.name.lowercase().filter(Char::isLetterOrDigit).removePrefix("somafm").let { name ->
        somaFmArtwork.firstOrNull { it.id == name || it.titleKey == name }
    }
    return channel?.let { "https://api.somafm.com/logos/512/${it.file}" }
}

private fun somaFmChannelPath(url: String): String? {
    val address = url.lowercase().substringAfter("://", "")
    val host = address.substringBefore('/').substringBefore(':')
    if (host != "somafm.com" && !host.endsWith(".somafm.com")) return null
    return address.substringAfter('/', "").substringBefore('?').substringBefore('#')
        .trim('/').substringBefore('/').removeSuffix(".pls").removeSuffix(".m3u").removeSuffix(".html")
}
