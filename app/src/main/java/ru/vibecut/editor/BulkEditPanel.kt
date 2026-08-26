package ru.vibecut.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun BulkEditPanel(
    clips: MutableList<VideoClip>,
    selected: VideoClip,
    onSnapshot: () -> Unit,
    onMessage: (String) -> Unit,
) {
    fun applyVisuals() {
        onSnapshot()
        clips.indices.forEach { i ->
            val c = clips[i]
            clips[i] = c.copy(
                brightness = selected.brightness,
                contrast = selected.contrast,
                saturation = selected.saturation,
                hue = selected.hue,
                lightness = selected.lightness,
                crop = selected.crop,
                colorEffect = selected.colorEffect,
                redScale = selected.redScale,
                greenScale = selected.greenScale,
                blueScale = selected.blueScale,
                maskType = selected.maskType,
                maskSize = selected.maskSize,
                vignette = selected.vignette,
                motion = selected.motion,
                motionStrength = selected.motionStrength,
            )
        }
        onMessage("Цвет, эффекты, маска и анимация применены ко всем клипам")
    }

    fun applyTransitions() {
        onSnapshot()
        clips.indices.forEach { i ->
            clips[i] = clips[i].copy(
                transitionOut = if (i == clips.lastIndex) TransitionType.NONE else selected.transitionOut,
                transitionDurationMs = selected.transitionDurationMs,
            )
        }
        onMessage("Переход применён ко всему проекту")
    }

    fun setMuted(muted: Boolean) {
        onSnapshot()
        clips.indices.forEach { i -> clips[i] = clips[i].copy(muted = muted) }
        onMessage(if (muted) "Звук всех клипов выключен" else "Звук всех клипов включён")
    }

    fun resetVisuals() {
        onSnapshot()
        clips.indices.forEach { i ->
            val c = clips[i]
            clips[i] = c.copy(
                brightness = 0f,
                contrast = 0f,
                saturation = 0f,
                hue = 0f,
                lightness = 0f,
                crop = 0f,
                colorEffect = ColorEffect.NONE,
                redScale = 1f,
                greenScale = 1f,
                blueScale = 1f,
                maskType = MaskType.NONE,
                maskSize = 0.82f,
                vignette = 0f,
                motion = ClipMotion.NONE,
                motionStrength = 0.14f,
                transitionOut = TransitionType.NONE,
            )
        }
        onMessage("Визуальные эффекты всех клипов сброшены")
    }

    SectionCard("Массовое редактирование") {
        Text(
            "Применить настройки выбранного клипа сразу ко всему проекту.",
            color = Color(0xFF9A9AA8),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToolButton("Цвет и эффекты → всем", ::applyVisuals, enabled = clips.size > 1)
            ToolButton("Переход → всем", ::applyTransitions, enabled = clips.size > 1)
            ToolButton("Выключить звук у всех", { setMuted(true) }, enabled = clips.isNotEmpty())
            ToolButton("Включить звук у всех", { setMuted(false) }, enabled = clips.isNotEmpty())
            ToolButton("Сбросить эффекты у всех", ::resetVisuals, enabled = clips.isNotEmpty())
        }
    }
}
