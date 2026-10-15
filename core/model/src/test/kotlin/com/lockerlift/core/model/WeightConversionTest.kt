package com.lockerlift.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightConversionTest {

    @Test
    fun `lbs to kg uses standard factor`() {
        val kg = WeightConversion.lbsToKg(1f)
        assertEquals(0.45359237f, kg, 0.0000001f)
    }

    @Test
    fun `kg to lbs and back is lossless within float precision`() {
        val kg = 18.1437f
        val roundTrip = WeightConversion.lbsToKg(WeightConversion.kgToLbs(kg))
        assertEquals(kg, roundTrip, 0.0001f)
    }

    @Test
    fun `scale value 40 lbs converts to expected kg`() {
        val kg = WeightConversion.lbsToKg(40f)
        assertEquals(18.143695f, kg, 0.001f)
    }

    @Test
    fun `formatWeightForUnit renders scale values exactly for lbs`() {
        val kg = WeightConversion.lbsToKg(40f)
        assertEquals("40", WeightConversion.formatWeightForUnit(kg, WeightUnit.LBS))
    }

    @Test
    fun `formatWeightForUnit rounds only for display and keeps kg exact`() {
        val kg = WeightConversion.lbsToKg(19f)
        assertEquals("19", WeightConversion.formatWeightForUnit(kg, WeightUnit.LBS))
        assertEquals("8.618262", WeightConversion.formatWeightForUnit(kg, WeightUnit.KG))
    }

    @Test
    fun `formatWeightForUnit in kg omits decimal zero`() {
        assertEquals("80", WeightConversion.formatWeightForUnit(80f, WeightUnit.KG))
        assertEquals("82.5", WeightConversion.formatWeightForUnit(82.5f, WeightUnit.KG))
    }

    @Test
    fun `unit suffix maps to display symbols`() {
        assertEquals("kg", WeightConversion.unitSuffix(WeightUnit.KG))
        assertEquals("lbs", WeightConversion.unitSuffix(WeightUnit.LBS))
    }

    @Test
    fun `toKg is identity for kg machines`() {
        assertEquals(42.5f, WeightConversion.toKg(42.5f, WeightUnit.KG), 0f)
        assertEquals(42.5f, WeightConversion.fromKg(42.5f, WeightUnit.KG), 0f)
    }

    @Test
    fun `machine default unit is kg for backwards compatibility`() {
        val machine = Machine(name = "Press", targetMuscleGroup = "Chest")
        assertEquals(WeightUnit.KG, machine.weightUnit)
    }
}
