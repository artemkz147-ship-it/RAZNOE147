package ru.vibecut.editor

import androidx.media3.common.Effect

// Старый внутренний Preview больше не используется основным редактором, но остаётся
// в исходниках. Этот overload сохраняет его бинарную совместимость с новым API.
fun buildVideoEffects(clip: VideoClip): List<Effect> = emptyList()
