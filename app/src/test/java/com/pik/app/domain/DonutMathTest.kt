package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DonutMathTest {
    @Test
    fun arcsFollowTheShares() {
        val arcs = DonutMath.arcs(listOf(50, 25, 25), gapDegrees = 0f)
        assertEquals(0f, arcs[0].startDegrees, 0.01f)
        assertEquals(180f, arcs[0].sweepDegrees, 0.01f)
        assertEquals(180f, arcs[1].startDegrees, 0.01f)
        assertEquals(90f, arcs[1].sweepDegrees, 0.01f)
        assertEquals(270f, arcs[2].startDegrees, 0.01f)
    }

    @Test
    fun gapsAreTakenOffButNotForASingleSlice() {
        val two = DonutMath.arcs(listOf(1, 1), gapDegrees = 2f)
        assertEquals(178f, two[0].sweepDegrees, 0.01f)
        assertEquals(180f, two[1].startDegrees, 0.01f)
        val one = DonutMath.arcs(listOf(5), gapDegrees = 2f)
        assertEquals(360f, one[0].sweepDegrees, 0.01f)
    }

    @Test
    fun emptyOrZeroTotalHasNoArea() {
        assertEquals(0f, DonutMath.arcs(listOf(0, 0))[0].sweepDegrees, 0.01f)
        assertNull(DonutMath.indexAt(emptyList(), 10f))
        assertNull(DonutMath.indexAt(listOf(0, 0), 10f))
    }

    @Test
    fun indexAtFindsTheSliceUnderAnAngle() {
        val values = listOf(50L, 25L, 25L)
        assertEquals(0, DonutMath.indexAt(values, 10f))
        assertEquals(1, DonutMath.indexAt(values, 200f))
        assertEquals(2, DonutMath.indexAt(values, 300f))
        assertEquals(0, DonutMath.indexAt(values, 370f))
        assertEquals(2, DonutMath.indexAt(values, -10f))
    }

    @Test
    fun zeroValuesAreSkipped() {
        assertEquals(1, DonutMath.indexAt(listOf(0L, 10L), 5f))
    }
}
