package app.naviamp.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The shared encoder is checked by an independent decoder, across sizes that need every version range. */
class NaviampQrCodeTest {
    @Test fun aConnectLinkDecodesToTheSameText() {
        val link = "naviamp://connect?v=1#n=Living%20Room%20TV&h=192.168.10.223,fd00::1:2:3:4&p=45123&" +
            "f=${"ab".repeat(32)}&o=3f2a7c1e-0000-4000-8000-000000000001&s=AAECAwQFBgcICQoLDA0ODw"
        assertEquals(link, decode(link))
    }

    @Test fun everyVersionRangeDecodes() {
        // Lengths that land in small (1-9), medium (10-26) and large (27-40) versions, with several blocks.
        listOf(1, 17, 60, 150, 300, 700, 1_200, 2_000, 2_331).forEach { length ->
            val text = (0 until length).map { "abcdefghijklmnopqrstuvwxyz0123456789-_"[it % 38] }.joinToString("")
            assertEquals(text, decode(text), "length $length")
        }
    }

    @Test fun aDeviceNameOutsideAsciiSurvives() {
        val text = "naviamp://connect?v=1#n=Salón de Música — 客厅"
        assertEquals(text, decode(text))
    }

    @Test fun textBeyondTheLargestSymbolIsRefused() {
        assertNull(NaviampQrCode.encode("x".repeat(2_332)))
    }

    private fun decode(text: String): String {
        val code = checkNotNull(NaviampQrCode.encode(text))
        val scale = 4
        val quiet = 4
        val side = (code.size + quiet * 2) * scale
        val pixels = ByteArray(side * side) { index ->
            val x = (index % side) / scale - quiet
            val y = (index / side) / scale - quiet
            if (x in 0 until code.size && y in 0 until code.size && code[x, y]) 0 else 255.toByte()
        }
        val source = object : LuminanceSource(side, side) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray = pixels.copyOfRange(y * side, (y + 1) * side)
            override fun getMatrix(): ByteArray = pixels
        }
        val hints = mapOf(DecodeHintType.PURE_BARCODE to true, DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }
}
