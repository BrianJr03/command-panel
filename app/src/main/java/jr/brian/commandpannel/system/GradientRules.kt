package jr.brian.commandpannel.system

/** Limits for [GradientSpec]. Pure, so it is unit-tested. */
object GradientRules {
    const val MIN_STOPS = 2
    const val MAX_STOPS = 6
    const val MIN_SPEED = 0f
    const val MAX_SPEED = 4f
    const val DEFAULT_SPEED = 1f
    const val MAX_NAME = 40

    /** [color] with its alpha forced opaque. */
    fun opaque(color: Int): Int = color or (0xFF shl 24)

    /** [degrees] wrapped into 0 until 360 (NaN or infinite becomes 0). */
    fun wrapAngle(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        val w = degrees % 360f
        return if (w < 0f) w + 360f else w
    }

    /** [speed] clamped to [MIN_SPEED]..[MAX_SPEED] (NaN becomes [DEFAULT_SPEED]). */
    fun clampSpeed(speed: Float): Float = if (speed.isNaN()) DEFAULT_SPEED else speed.coerceIn(MIN_SPEED, MAX_SPEED)

    /**
     * Stops made opaque, positions clamped into 0..1 (NaN ones dropped), at most [MAX_STOPS] (the first ones given),
     * sorted by position; `null` if fewer than [MIN_STOPS] remain.
     */
    fun normalizeStops(stops: List<GradientStop>): List<GradientStop>? {
        val kept = stops.filter { !it.position.isNaN() }.take(MAX_STOPS)
            .map { GradientStop(opaque(it.color), it.position.coerceIn(0f, 1f)) }
            .sortedBy { it.position }
        return kept.takeIf { it.size >= MIN_STOPS }
    }

    /** [spec] with every field in range, or `null` if it can't be a gradient (blank id, too few stops). */
    fun normalize(spec: GradientSpec): GradientSpec? {
        if (spec.id.isBlank()) return null
        val stops = normalizeStops(spec.stops) ?: return null
        return spec.copy(
            name = spec.name.trim().take(MAX_NAME),
            stops = stops,
            angle = wrapAngle(spec.angle),
            speed = clampSpeed(spec.speed),
        )
    }

    /** Whether [spec] moves: Animated backgrounds on and a speed above 0. */
    fun moves(spec: GradientSpec, animationsOn: Boolean): Boolean = animationsOn && spec.speed > 0f
}
