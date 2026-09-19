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
) {
    companion object {
        fun interpolate(
            keyframes: List<ClipKeyframe>,
            property: KeyframeProperty,
            timeOffsetMs: Long,
            defaultValue: Float
        ): Float {
            val propKfs = keyframes.filter { it.property == property }.sortedBy { it.timeOffsetMs }
            if (propKfs.isEmpty()) return defaultValue
            if (propKfs.size == 1) return propKfs[0].value
            if (timeOffsetMs <= propKfs.first().timeOffsetMs) return propKfs.first().value
            if (timeOffsetMs >= propKfs.last().timeOffsetMs) return propKfs.last().value

            // Find surrounding keyframes
            for (i in 0 until propKfs.size - 1) {
                val k1 = propKfs[i]
                val k2 = propKfs[i + 1]
                if (timeOffsetMs in k1.timeOffsetMs..k2.timeOffsetMs) {
                    val duration = (k2.timeOffsetMs - k1.timeOffsetMs).coerceAtLeast(1L)
                    val t = (timeOffsetMs - k1.timeOffsetMs).toFloat() / duration
                    return k1.value + t * (k2.value - k1.value)
                }
            }
            return defaultValue
        }
    }
}
