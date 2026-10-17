package com.lockerlift.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the weight stack scale helpers (Issue #48).
 */
class WeightStackTest {

    private val kgScale = listOf(5f, 12f, 20f, 28f, 36f)

    @Test
    fun testParseScaleValues_commaSeparated() {
        assertEquals(kgScale, WeightStack.parseScaleValues("5, 12, 20, 28, 36"))
    }

    @Test
    fun testParseScaleValues_mixedSeparatorsAndInvalidTokens() {
        assertEquals(listOf(5f, 12f, 20f), WeightStack.parseScaleValues("5 12;foo,20,x"))
    }

    @Test
    fun testParseScaleValues_deduplicatesAndSorts() {
        assertEquals(listOf(5f, 12f, 20f), WeightStack.parseScaleValues("20, 5, 12, 5"))
    }

    @Test
    fun testParseScaleValues_emptyAndNegativeOnly() {
        assertTrue(WeightStack.parseScaleValues("").isEmpty())
        assertTrue(WeightStack.parseScaleValues("-5, -12").isEmpty())
    }

    @Test
    fun testScaleValuesUnitConversion_lbs() {
        val lbsValues = listOf(10f, 25f, 40f)
        val kgValues = WeightStack.scaleValuesToKg(lbsValues, WeightUnit.LBS)
        assertEquals(lbsValues, WeightStack.scaleValuesFromKg(kgValues, WeightUnit.LBS))
        // 10 lbs ≈ 4.536 kg
        assertEquals(4.535924f, kgValues[0], 0.0001f)
    }

    @Test
    fun testNextScaleValue_upAndDown() {
        assertEquals(12f, WeightStack.nextScaleValue(5f, kgScale, up = true))
        assertEquals(20f, WeightStack.nextScaleValue(5f, kgScale, up = true, steps = 2))
        assertNull(WeightStack.nextScaleValue(36f, kgScale, up = true))
        assertEquals(28f, WeightStack.nextScaleValue(36f, kgScale, up = false))
        assertNull(WeightStack.nextScaleValue(5f, kgScale, up = false))
    }

    @Test
    fun testNextScaleValue_betweenPositionsUsesNextHigher() {
        assertEquals(20f, WeightStack.nextScaleValue(15f, kgScale, up = true))
        assertEquals(12f, WeightStack.nextScaleValue(15f, kgScale, up = false))
    }

    @Test
    fun testSnapToScale_closestValue() {
        assertEquals(20f, WeightStack.snapToScale(19.2f, kgScale))
        assertEquals(5f, WeightStack.snapToScale(1f, kgScale))
        assertNull(WeightStack.snapToScale(1f, emptyList()))
    }

    @Test
    fun testIsOnScale() {
        assertTrue(WeightStack.isOnScale(20f, kgScale))
        assertFalse(WeightStack.isOnScale(19.5f, kgScale))
    }

    @Test
    fun testNextProgressionWeight_normalAndInverse() {
        assertEquals(28f, WeightStack.nextProgressionWeight(20f, kgScale, WeightDirection.NORMAL))
        // INVERSE (assisted): progression means going DOWN the scale
        assertEquals(12f, WeightStack.nextProgressionWeight(20f, kgScale, WeightDirection.INVERSE))
        assertNull(WeightStack.nextProgressionWeight(36f, kgScale, WeightDirection.NORMAL))
    }

    @Test
    fun testJsonRoundTrip() {
        val json = WeightStack.scaleValuesToJson(kgScale)
        assertEquals(kgScale, WeightStack.scaleValuesFromJson(json))
    }

    @Test
    fun testJsonFromInvalid_returnsEmpty() {
        assertTrue(WeightStack.scaleValuesFromJson("not json").isEmpty())
    }

    @Test
    fun testFormatScaleValues() {
        assertEquals("5, 12, 20", WeightStack.formatScaleValues(listOf(5f, 12f, 20f)))
    }
}
