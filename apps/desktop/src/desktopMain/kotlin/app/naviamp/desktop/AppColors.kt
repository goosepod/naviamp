package app.naviamp.desktop

import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val border: Color,
    val accent: Color,
    val albumArtPlaceholder: Color,
) {
    companion object {
        val Dark = AppColors(
            background = Color(0xFF101114),
            primaryText = Color.White,
            secondaryText = Color(0xFFB9BDC7),
            mutedText = Color(0xFF8F96A3),
            border = Color(0xFF59606D),
            accent = Color(0xFF8EA7D8),
            albumArtPlaceholder = Color(0xFF43536B),
        )

        val Light = AppColors(
            background = Color(0xFFF8F9FB),
            primaryText = Color(0xFF171A21),
            secondaryText = Color(0xFF4F5663),
            mutedText = Color(0xFF727A86),
            border = Color(0xFFBAC1CC),
            accent = Color(0xFF315D9E),
            albumArtPlaceholder = Color(0xFFD3DBE8),
        )
    }
}
