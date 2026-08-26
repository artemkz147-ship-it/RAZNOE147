package ru.vibecut.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Связывает рабочую панель «Движение» с жестами поверх предпросмотра,
 * не протаскивая редакторские callbacks через весь стек плеера.
 */
internal object PreviewGestureBridge {
    var enabled by mutableStateOf(false)
    var onGestureStart: (() -> Unit)? = null
    var onTransform: ((panX: Float, panY: Float, zoom: Float, rotation: Float) -> Unit)? = null
    var onGestureEnd: (() -> Unit)? = null

    fun clear() {
        enabled = false
        onGestureStart = null
        onTransform = null
        onGestureEnd = null
    }
}
