package com.pik.app.domain

/** Angles of the donut chart. Angle 0 is the top (12 o'clock), growing clockwise, in degrees. */
object DonutMath {

    data class Arc(val startDegrees: Float, val sweepDegrees: Float)

    /**
     * One arc per value, sized by its share of the total. [gapDegrees] is taken off the end of each
     * arc (not for a single slice) so neighbours do not touch. Values that are zero get a zero sweep.
     */
    fun arcs(values: List<Long>, gapDegrees: Float = 2f): List<Arc> {
        val total = values.sum()
        if (total <= 0L) return values.map { Arc(0f, 0f) }
        val gap = if (values.count { it > 0 } > 1) gapDegrees else 0f
        var start = 0f
        return values.map { v ->
            val full = 360f * v.toFloat() / total.toFloat()
            val arc = Arc(start, (full - gap).coerceAtLeast(0f))
            start += full
            arc
        }
    }

    /**
     * The index of the value under [angleFromTopDegrees] (0 = top, clockwise), ignoring gaps, or
     * null when there is nothing to hit. The angle is wrapped into 0..360.
     */
    fun indexAt(values: List<Long>, angleFromTopDegrees: Float): Int? {
        val total = values.sum()
        if (total <= 0L) return null
        val angle = ((angleFromTopDegrees % 360f) + 360f) % 360f
        var end = 0f
        values.forEachIndexed { i, v ->
            end += 360f * v.toFloat() / total.toFloat()
            if (v > 0L && angle < end) return i
        }
        return values.indexOfLast { it > 0L }.takeIf { it >= 0 }
    }
}
