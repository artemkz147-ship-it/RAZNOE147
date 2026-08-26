package ru.vibecut.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

internal object EditorCursorState {
    var clipPositionMs by mutableLongStateOf(0L)
}
