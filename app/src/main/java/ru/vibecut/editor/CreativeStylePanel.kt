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

private data class CreativePreset(
    val title: String,
    val apply: (VideoClip) -> VideoClip,
)

private val creativePresets = listOf(
    CreativePreset("Кино") { it.copy(colorEffect=ColorEffect.NONE,brightness=-.02f,contrast=.20f,saturation=-12f,hue=0f,lightness=-4f,redScale=1.04f,greenScale=.98f,blueScale=.92f,vignette=.22f,motion=ClipMotion.ZOOM_IN,motionStrength=.07f) },
    CreativePreset("Тёплая плёнка") { it.copy(colorEffect=ColorEffect.VINTAGE,brightness=.025f,contrast=.08f,saturation=5f,hue=5f,lightness=2f,redScale=1.10f,greenScale=1.01f,blueScale=.88f,vignette=.28f) },
    CreativePreset("Холодное кино") { it.copy(colorEffect=ColorEffect.COLD,brightness=-.02f,contrast=.17f,saturation=-8f,hue=-5f,lightness=-3f,redScale=.91f,greenScale=1.02f,blueScale=1.12f,vignette=.20f) },
    CreativePreset("Контраст Ч/Б") { it.copy(colorEffect=ColorEffect.GRAYSCALE,brightness=-.01f,contrast=.32f,saturation=-100f,lightness=-5f,vignette=.30f) },
    CreativePreset("Мягкий портрет") { it.copy(colorEffect=ColorEffect.WARM,brightness=.06f,contrast=-.08f,saturation=-5f,hue=3f,lightness=8f,redScale=1.06f,greenScale=1.02f,blueScale=.96f,vignette=.10f) },
    CreativePreset("Закат") { it.copy(colorEffect=ColorEffect.WARM,brightness=.02f,contrast=.13f,saturation=24f,hue=7f,lightness=2f,redScale=1.18f,greenScale=1.03f,blueScale=.82f,vignette=.18f) },
    CreativePreset("Неон") { it.copy(colorEffect=ColorEffect.CYAN,brightness=-.03f,contrast=.28f,saturation=34f,hue=-8f,lightness=-4f,redScale=1.05f,greenScale=1.10f,blueScale=1.24f,vignette=.32f,motion=ClipMotion.ZOOM_IN,motionStrength=.12f) },
    CreativePreset("Розовый неон") { it.copy(colorEffect=ColorEffect.PINK,brightness=-.01f,contrast=.24f,saturation=32f,hue=8f,lightness=-3f,redScale=1.23f,greenScale=.88f,blueScale=1.14f,vignette=.30f) },
    CreativePreset("Ночной город") { it.copy(colorEffect=ColorEffect.NIGHT,brightness=-.10f,contrast=.28f,saturation=14f,hue=-5f,lightness=-10f,redScale=.76f,greenScale=.94f,blueScale=1.22f,vignette=.38f) },
    CreativePreset("Путешествие") { it.copy(colorEffect=ColorEffect.NONE,brightness=.035f,contrast=.12f,saturation=18f,hue=3f,lightness=3f,redScale=1.05f,greenScale=1.03f,blueScale=.98f,vignette=.08f,motion=ClipMotion.PAN_RIGHT,motionStrength=.10f) },
    CreativePreset("Еда") { it.copy(colorEffect=ColorEffect.WARM,brightness=.045f,contrast=.16f,saturation=25f,hue=4f,lightness=3f,redScale=1.10f,greenScale=1.04f,blueScale=.93f,vignette=.08f) },
    CreativePreset("Природа") { it.copy(colorEffect=ColorEffect.NONE,brightness=.02f,contrast=.11f,saturation=20f,hue=-2f,lightness=2f,redScale=.98f,greenScale=1.10f,blueScale=1.01f,vignette=.05f) },
    CreativePreset("Море") { it.copy(colorEffect=ColorEffect.CYAN,brightness=.035f,contrast=.10f,saturation=18f,hue=-8f,lightness=4f,redScale=.91f,greenScale=1.08f,blueScale=1.13f,vignette=.04f) },
    CreativePreset("Ретро 90-х") { it.copy(colorEffect=ColorEffect.VINTAGE,brightness=.03f,contrast=-.05f,saturation=-15f,hue=4f,lightness=7f,redScale=1.08f,greenScale=.99f,blueScale=.84f,vignette=.35f) },
    CreativePreset("Старая камера") { it.copy(colorEffect=ColorEffect.SEPIA,brightness=-.03f,contrast=.10f,saturation=-28f,hue=2f,lightness=-4f,redScale=1.02f,greenScale=.93f,blueScale=.74f,vignette=.55f) },
    CreativePreset("Документальный") { it.copy(colorEffect=ColorEffect.NONE,brightness=-.01f,contrast=.22f,saturation=-25f,hue=0f,lightness=-3f,redScale=1f,greenScale=1f,blueScale=.98f,vignette=.15f) },
    CreativePreset("Игровой") { it.copy(colorEffect=ColorEffect.NONE,brightness=.01f,contrast=.25f,saturation=30f,hue=-3f,lightness=0f,redScale=1.04f,greenScale=1.06f,blueScale=1.08f,vignette=.18f,motion=ClipMotion.ZOOM_IN,motionStrength=.10f) },
    CreativePreset("Короткий ролик") { it.copy(colorEffect=ColorEffect.NONE,brightness=.035f,contrast=.19f,saturation=26f,hue=1f,lightness=2f,vignette=.10f,motion=ClipMotion.ZOOM_IN,motionStrength=.16f) },
    CreativePreset("Сон") { it.copy(colorEffect=ColorEffect.PINK,brightness=.07f,contrast=-.14f,saturation=-5f,hue=4f,lightness=10f,redScale=1.08f,greenScale=1.01f,blueScale=1.09f,vignette=.06f,motion=ClipMotion.ZOOM_IN,motionStrength=.055f) },
    CreativePreset("Драма") { it.copy(colorEffect=ColorEffect.NONE,brightness=-.08f,contrast=.36f,saturation=-35f,hue=0f,lightness=-9f,redScale=1.10f,greenScale=.95f,blueScale=.91f,vignette=.45f) },
    CreativePreset("Лёд") { it.copy(colorEffect=ColorEffect.COLD,brightness=.02f,contrast=.18f,saturation=-10f,hue=-10f,lightness=3f,redScale=.84f,greenScale=1.02f,blueScale=1.22f,vignette=.16f) },
    CreativePreset("Золото") { it.copy(colorEffect=ColorEffect.WARM,brightness=.025f,contrast=.16f,saturation=16f,hue=9f,lightness=1f,redScale=1.18f,greenScale=1.07f,blueScale=.75f,vignette=.22f) },
    CreativePreset("Высокая энергия") { it.copy(colorEffect=ColorEffect.NONE,brightness=.02f,contrast=.34f,saturation=38f,hue=0f,lightness=0f,vignette=.14f,motion=ClipMotion.ZOOM_IN,motionStrength=.22f) },
    CreativePreset("Чистый свет") { it.copy(colorEffect=ColorEffect.NONE,brightness=.08f,contrast=-.03f,saturation=5f,hue=0f,lightness=9f,redScale=1.02f,greenScale=1.02f,blueScale=1.02f,vignette=0f) },
)

@Composable
internal fun CreativeStylePanel(
    clip: VideoClip,
    onSnapshot: () -> Unit,
    onUpdate: (VideoClip) -> Unit,
) {
    SectionCard("Стили видео и фото") {
        Text(
            "Стиль меняет сразу цвет, тон, движение и виньетку. После применения каждый параметр остаётся редактируемым.",
            color = Color(0xFF9A9AA8),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            creativePresets.forEach { preset ->
                ToolButton(preset.title, {
                    onSnapshot()
                    onUpdate(preset.apply(clip))
                })
            }
            ToolButton("Сбросить стиль", {
                onSnapshot()
                onUpdate(clip.copy(
                    colorEffect=ColorEffect.NONE,brightness=0f,contrast=0f,saturation=0f,hue=0f,lightness=0f,
                    redScale=1f,greenScale=1f,blueScale=1f,vignette=0f,motion=ClipMotion.NONE,motionStrength=.14f,
                ))
            })
        }
    }
}
