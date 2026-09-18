package com.example.dyastie.model

enum class EffectType(val displayName: String, val category: String) {
    ZOOM("Zoom / Scale", "Motion"),
    PUNCH_IN("Punch In", "Gaming"),
    PUNCH_OUT("Punch Out", "Gaming"),
    SHAKE("Screen Shake", "Gaming"),
    IMPACT("Heavy Impact", "Gaming"),
    FLASH("White Flash", "Gaming"),
    MOTION_BLUR("Fast Motion Blur", "Motion"),
    FREEZE_FRAME("Freeze Frame", "Time"),
    VIGNETTE("Cinematic Vignette", "Color"),
    GLITCH("Cyber Glitch", "Gaming"),
    RGB_SPLIT("RGB Split", "Gaming")
}

data class VideoEffect(
    val id: String,
    val type: EffectType,
    val isEnabled: Boolean = true,
    val intensity: Float = 1.0f, // 0.0 to 1.0 or multiplier
    val speed: Float = 1.0f,
    val durationMs: Long = 1000L
)

data class ColorGrading(
    val brightness: Float = 0f,      // -1.0 to 1.0
    val contrast: Float = 1.0f,      // 0.0 to 2.0
    val saturation: Float = 1.0f,    // 0.0 to 2.0
    val temperature: Float = 0f,     // -1.0 to 1.0 (cool to warm)
    val tint: Float = 0f,            // -1.0 to 1.0 (green to magenta)
    val highlights: Float = 0f,      // -1.0 to 1.0
    val shadows: Float = 0f          // -1.0 to 1.0
)

data class VideoTransform(
    val posX: Float = 0f,            // Translation offset X in pixels / percentage
    val posY: Float = 0f,            // Translation offset Y in pixels / percentage
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1.0f,       // 0.0 to 1.0
    val cropLeft: Float = 0f,        // 0.0 to 0.5
    val cropRight: Float = 0f,
    val cropTop: Float = 0f,
    val cropBottom: Float = 0f
)

data class TextOverlay(
    val id: String,
    val text: String,
    val fontSizeSp: Float = 28f,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val textColor: Long = 0xFFFFFFFF,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 3f,
    val shadowColor: Long = 0xAA000000,
    val backgroundColor: Long = 0x00000000,
    val alignment: Int = 1, // 0=left, 1=center, 2=right
    val presetName: String = "CUSTOM"
)
