package com.example.dyastie.model

enum class KeyframeProperty {
    POSITION_X,
    POSITION_Y,
    SCALE,
    ROTATION,
    OPACITY,
    VOLUME
}

data class ClipKeyframe(
    val id: String,
    val timeOffsetMs: Long,
    val property: KeyframeProperty,
    val value: Float,
    val easing: String = "LINEAR" // LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT
)
