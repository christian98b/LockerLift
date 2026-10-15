package com.lockerlift.core.model

import kotlinx.serialization.Serializable

/**
 * Unit in which a machine's weight stack scale is labeled (Issue #37).
 * All weights are stored internally in kilograms; this unit only governs
 * what the user reads on the scale and types into the app.
 */
@Serializable
enum class WeightUnit {
    KG,
    LBS
}

/**
 * Exact conversion between lbs and kg (1 lbs = 0.45359237 kg, Issue #37).
 * Calculations are exact; rounding happens only at display time.
 */
object WeightConversion {
    const val LBS_TO_KG_FACTOR = 0.45359237f

    fun lbsToKg(lbs: Float): Float = lbs * LBS_TO_KG_FACTOR

    fun kgToLbs(kg: Float): Float = kg / LBS_TO_KG_FACTOR

    fun toKg(value: Float, unit: WeightUnit): Float = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LBS -> lbsToKg(value)
    }

    fun fromKg(kg: Float, unit: WeightUnit): Float = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LBS -> kgToLbs(kg)
    }

    fun unitSuffix(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "kg"
        WeightUnit.LBS -> "lbs"
    }

    /**
     * Formats a kg value for display in the given unit. The conversion is
     * exact; only the rendered number is rounded (2 decimals, trimmed) so
     * scale values like "40" lbs reappear as "40" and not "40.000004".
     */
    fun formatWeightForUnit(weightKg: Float, unit: WeightUnit): String {
        val value = fromKg(weightKg, unit)
        val rounded = if (unit == WeightUnit.LBS) {
            kotlin.math.round(value * 100f) / 100f
        } else {
            value
        }
        return WorkoutLogic.formatWeight(rounded)
    }
}
