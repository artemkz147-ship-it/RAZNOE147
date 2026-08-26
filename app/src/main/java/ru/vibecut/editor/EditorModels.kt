package ru.vibecut.editor

enum class ClipMotion(val title: String) {
    NONE("Без анимации"), ZOOM_IN("Приближение"), ZOOM_OUT("Отдаление"), PAN_LEFT("Панорама влево"), PAN_RIGHT("Панорама вправо"), PAN_UP("Панорама вверх"), PAN_DOWN("Панорама вниз")
}

enum class TransitionType(val title: String) {
    NONE("Без перехода"), FADE("Затемнение"), SLIDE_LEFT("Сдвиг влево"), SLIDE_RIGHT("Сдвиг вправо"), SLIDE_UP("Сдвиг вверх"), SLIDE_DOWN("Сдвиг вниз"), ZOOM("Масштаб"), SPIN("Вращение"), ROTATE_LEFT("Поворот влево"), ROTATE_RIGHT("Поворот вправо"), PULSE("Пульс"), SHAKE("Встряска"), FLASH("Вспышка")
}

enum class ColorEffect(val title: String) {
    NONE("Без эффекта"), GRAYSCALE("Чёрно-белый"), INVERT("Инверсия"), SEPIA("Сепия"), WARM("Тёплый"), COLD("Холодный"), VINTAGE("Винтаж"), NIGHT("Ночной"), CYAN("Циан"), PINK("Розовый")
}

enum class SpecialEffect(val title: String) {
    NONE("Без спецэффекта"), VHS("VHS"), CRT("CRT / телевизор"), FILM_GRAIN("Плёночное зерно"), OLD_FILM("Старая плёнка"), SCRATCHES("Царапины"), GLITCH("Глитч"), RGB_PULSE("RGB-пульсация"), STROBE("Строб"), FLICKER("Мерцание"), LIGHT_LEAK("Засветка плёнки"), FLASHES("Вспышки"), CAMERA_SHAKE("Дрожание камеры"), ZOOM_PULSE("Зум-пульс"), DREAM("Сон / сияние"), NIGHT_VISION("Ночное видение"), SECURITY_CAM("Камера наблюдения")
}

enum class MaskType(val title: String) { NONE("Без маски"), CIRCLE("Круг"), ROUNDED_RECT("Скруглённая"), CINEMA("Кино-кадр") }
enum class KeyframeEasing(val title: String) { LINEAR("Линейно"), EASE_IN("Разгон"), EASE_OUT("Торможение"), EASE_IN_OUT("Плавно"), OVERSHOOT("С перелётом"), BOUNCE("Пружина") }
data class TransitionSpec(val type: TransitionType, val durationMs: Long)
data class TransformKeyframe(val id:String,val timeMs:Long,val x:Float=0f,val y:Float=0f,val scale:Float=1f,val rotation:Float=0f,val easing:KeyframeEasing=KeyframeEasing.EASE_IN_OUT)

data class TrackingPoint(
    val timeMs:Long,
    val x:Float,
    val y:Float,
    val objectScale:Float=1f,
    val width:Float=.30f,
    val height:Float=.30f,
)

enum class TrackedOverlayStyle(val title:String) {
    BLACK_BOX("Чёрная плашка"),
    MOSAIC("Мозаика"),
    FRAME("Рамка"),
    HIGHLIGHT("Подсветка"),
}

data class TrackedObjectOverlay(
    val id:String,
    val style:TrackedOverlayStyle,
    val trackingPath:List<TrackingPoint>,
    val padding:Float=.12f,
    val alpha:Float=.90f,
    val color:Int=0xFFFFD54F.toInt(),
)

data class StickerLayer(
    val id:String,val uri:String,val name:String="Изображение",val x:Float=0f,val y:Float=0f,val scale:Float=.35f,val rotation:Float=0f,val alpha:Float=1f,
    val startMs:Long=0L,val endMs:Long=Long.MAX_VALUE,val keyframes:List<TransformKeyframe> = emptyList(),val trackingPath:List<TrackingPoint> = emptyList()
)

enum class AnimatedStickerKind(val title:String){HEART("Сердце"),STAR("Звезда"),SPARKLE("Искры"),ARROW("Стрелка"),RING("Кольцо"),LIGHTNING("Молния"),CONFETTI("Конфетти"),FIRE("Огонь"),CHECK("Галочка"),QUESTION("Вопрос"),WOW("WOW"),TARGET("Прицел")}
data class AnimatedStickerLayer(
    val id:String,val kind:AnimatedStickerKind,val x:Float=0f,val y:Float=0f,val scale:Float=.35f,val rotation:Float=0f,val alpha:Float=1f,val startMs:Long=0L,val endMs:Long=Long.MAX_VALUE,
    val speed:Float=1f,val loop:Boolean=true,val keyframes:List<TransformKeyframe> = emptyList(),val trackingPath:List<TrackingPoint> = emptyList()
)

data class GifStickerLayer(
    val id:String,val uri:String,val name:String="GIF",val x:Float=0f,val y:Float=0f,val scale:Float=.45f,val rotation:Float=0f,val alpha:Float=1f,
    val startMs:Long=0L,val endMs:Long=Long.MAX_VALUE,val speed:Float=1f,val loop:Boolean=true,
    val keyframes:List<TransformKeyframe> = emptyList(),val trackingPath:List<TrackingPoint> = emptyList()
)

data class VideoClip(
    val id:String,val uri:String,val name:String,val sourceDurationMs:Long,val trimStartMs:Long=0L,val trimEndMs:Long=sourceDurationMs,
    val muted:Boolean=false,val audioVolume:Float=1f,val audioFadeInMs:Long=0L,val audioFadeOutMs:Long=0L,
    val rotationDegrees:Int=0,val speed:Float=1f,val brightness:Float=0f,val contrast:Float=0f,val saturation:Float=0f,val hue:Float=0f,val lightness:Float=0f,val crop:Float=0f,
    val flipHorizontal:Boolean=false,val flipVertical:Boolean=false,val motion:ClipMotion=ClipMotion.NONE,val motionStrength:Float=.14f,
    val colorEffect:ColorEffect=ColorEffect.NONE,val specialEffect:SpecialEffect=SpecialEffect.NONE,val specialEffectStrength:Float=.65f,
    val redScale:Float=1f,val greenScale:Float=1f,val blueScale:Float=1f,val maskType:MaskType=MaskType.NONE,val maskSize:Float=.82f,val vignette:Float=0f,
    val transitionOut:TransitionType=TransitionType.NONE,val transitionDurationMs:Long=650L,val keyframes:List<TransformKeyframe> = emptyList(),
    val stickers:List<StickerLayer> = emptyList(),val animatedStickers:List<AnimatedStickerLayer> = emptyList(),val gifStickers:List<GifStickerLayer> = emptyList(),
    val trackedOverlays:List<TrackedObjectOverlay> = emptyList(),
    val overlayText:String="",val textX:Float=0f,val textY:Float=-.72f,val textScale:Float=.72f,val textRotation:Float=0f,val textColor:Int=-1,val textBackground:Boolean=true,val textBold:Boolean=true,val textItalic:Boolean=false
){val sourceSliceDurationMs:Long get()=(trimEndMs-trimStartMs).coerceAtLeast(1L);val durationMs:Long get()=(sourceSliceDurationMs/speed.coerceAtLeast(.05f)).toLong().coerceAtLeast(1L)}

data class AudioTrack(val uri:String,val name:String,val volume:Float=.65f)
data class PositionedAudioTrack(val id:String,val uri:String,val name:String,val sourceDurationMs:Long,val startAtMs:Long=0L,val volume:Float=.85f)
data class SubtitleCue(val id:String,val startMs:Long,val endMs:Long,val text:String)
enum class SubtitleAnimation(val title:String){NONE("Без анимации"),FADE("Плавное появление"),POP("Акцентное появление"),TYPEWRITER("Печатная машинка"),WORD_BY_WORD("По словам"),KARAOKE("Караоке"),BOUNCE("Прыжок"),SLIDE_UP("Снизу вверх")}
data class SubtitleStyle(
    val fontScale:Float=1f,val textColor:Int=-1,val backgroundColor:Int=0xB3000000.toInt(),val backgroundEnabled:Boolean=true,val verticalPosition:Float=.84f,
    val fontKey:String="sans-serif",val fontDisplayName:String="Системный",val fontFilePath:String="",val outlineColor:Int=0xFF000000.toInt(),val outlineWidth:Float=1.6f,
    val shadowColor:Int=0xB3000000.toInt(),val shadowRadius:Float=1.2f,val bold:Boolean=true,val italic:Boolean=false,val uppercase:Boolean=false,val letterSpacing:Float=0f,
    val accentColor:Int=0xFFFFD54F.toInt(),val animation:SubtitleAnimation=SubtitleAnimation.NONE
)
enum class VideoCodec(val title:String,val mimeType:String){H264("H.264","video/avc"),H265("H.265","video/hevc")}
data class ExportSettings(val height:Int=1080,val maxFrameRate:Int=30,val aspectRatio:Float?=null,val cropToFill:Boolean=false,val videoCodec:VideoCodec=VideoCodec.H264)
data class EditorSnapshot(val clips:List<VideoClip>,val selectedId:String?)
data class SavedProject(val id:String,val name:String="Новый проект",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),val clips:List<VideoClip> = emptyList(),val selectedId:String?=null,val backgroundAudio:AudioTrack?=null,val positionedAudioTracks:List<PositionedAudioTrack> = emptyList(),val subtitles:List<SubtitleCue> = emptyList(),val subtitleStyle:SubtitleStyle=SubtitleStyle(),val exportSettings:ExportSettings=ExportSettings())
data class ProjectSummary(val id:String,val name:String,val updatedAt:Long,val clipCount:Int,val durationMs:Long)
enum class ExportState{IDLE,EXPORTING,DONE,ERROR}
