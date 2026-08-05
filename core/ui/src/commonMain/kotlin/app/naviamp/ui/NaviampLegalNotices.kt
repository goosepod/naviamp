package app.naviamp.ui

internal data class NaviampLegalNotice(
    val title: String,
    val body: String,
)

internal val NaviampLegalNotices = listOf(
    NaviampLegalNotice(
        title = "Naviamp",
        body = """
            Naviamp is free software licensed under the GNU General Public License, version 3.

            The Naviamp copyright holders grant an additional permission under GPLv3 section 7 to link or combine Naviamp with the BASS audio library and BASS add-ons and to convey the resulting work. BASS and its add-ons remain subject to their own licenses; the exception does not grant permission to redistribute or use them beyond those terms.

            See LICENSE and BASS-LINKING-EXCEPTION.md in the source distribution, or visit https://www.gnu.org/licenses/gpl-3.0.html.

            Naviamp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
        """.trimIndent(),
    ),
    NaviampLegalNotice(
        title = "BASS audio library",
        body = """
            BASS and its official add-ons are copyright Un4seen Developments Ltd. Naviamp uses them under Un4seen's free non-commercial terms. Commercial use requires the appropriate per-platform BASS license.

            BASS is provided without warranty and is used entirely at the user's own risk. Current terms and commercial license information are available at https://www.un4seen.com/bass.html.
        """.trimIndent(),
    ),
    NaviampLegalNotice(
        title = "BASS decoder acknowledgements",
        body = """
            BASS and its add-ons incorporate or use minimp3; libogg/vorbis (Xiph.org Foundation); libFLAC (Josh Coalson and Xiph.Org Foundation); libOpus (Xiph.Org and contributors); nestegg (Mozilla Foundation); WavPack (David Bryant); and the Monkey's Audio SDK (Matthew T. Ashland).

            Complete upstream notices are retained in THIRD_PARTY_NOTICES.md and platforms/ios/vendor/bass/licenses in the source distribution.
        """.trimIndent(),
    ),
    NaviampLegalNotice(
        title = "BASS_SSL and OpenSSL",
        body = """
            BASS_SSL is based on OpenSSL. This product includes software developed by the OpenSSL Project for use in the OpenSSL Toolkit and cryptographic software written by Eric Young.

            The OpenSSL 1.1.1 license text is available at https://www.openssl.org/source/license-openssl-ssleay.txt.
        """.trimIndent(),
    ),
    NaviampLegalNotice(
        title = "BASS_MPC",
        body = """
            BASS_MPC is copyright 2002-2012 Sebastian Andersson. Portions are copyright 2006 The Musepack Development Team. The upstream package permits use and distribution and provides the library without warranty.
        """.trimIndent(),
    ),
)
