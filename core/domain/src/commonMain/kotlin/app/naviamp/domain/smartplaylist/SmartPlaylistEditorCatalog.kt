package app.naviamp.domain.smartplaylist

fun SmartPlaylistConditionDraft.valueLabel(): String =
    when (operator) {
        SmartPlaylistOperator.InTheLast,
        SmartPlaylistOperator.NotInTheLast -> "Days"
        SmartPlaylistOperator.InPlaylist,
        SmartPlaylistOperator.NotInPlaylist -> "Playlist ID"
        else -> "Value"
    }

fun SmartPlaylistOperator.displayLabel(): String =
    when (this) {
        SmartPlaylistOperator.Is -> "is"
        SmartPlaylistOperator.IsNot -> "is not"
        SmartPlaylistOperator.GreaterThan -> "greater than"
        SmartPlaylistOperator.LessThan -> "less than"
        SmartPlaylistOperator.Contains -> "contains"
        SmartPlaylistOperator.NotContains -> "does not contain"
        SmartPlaylistOperator.StartsWith -> "starts with"
        SmartPlaylistOperator.EndsWith -> "ends with"
        SmartPlaylistOperator.InTheRange -> "in range"
        SmartPlaylistOperator.Before -> "before"
        SmartPlaylistOperator.After -> "after"
        SmartPlaylistOperator.InTheLast -> "in the last"
        SmartPlaylistOperator.NotInTheLast -> "not in the last"
        SmartPlaylistOperator.InPlaylist -> "in playlist"
        SmartPlaylistOperator.NotInPlaylist -> "not in playlist"
    }

fun <T> List<T>.updated(index: Int, value: T): List<T> =
    mapIndexed { currentIndex, currentValue -> if (currentIndex == index) value else currentValue }
