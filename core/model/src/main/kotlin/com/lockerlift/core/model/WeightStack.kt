package com.lockerlift.core.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Weight stack scale of a machine (Issue #48): the cumulative pin positions
 * printed on the plates (e.g. "5, 12, 20, 28, 36"), entered by the user
 * exactly as printed. Values are in the machine's unit (kg or lbs as
 * configured); a stack's values are stored internally converted to kg so
 * that all existing weight math stays unit-agnostic.
 *
 * Without a configured scale everything behaves as before (increment-based
 * deltas); with a scale, steppers and progression jump to the printed
 * values instead of doing kg delta math.
 */
object WeightStack {

    /**
     * Parses a user-entered scale string ("5, 12, 20, 28, 36") into a list
     * of raw values in the machine's unit. Invalid tokens are skipped;
     * duplicates and unsorted values are normalized (strictly ascending).
     */
    fun parseScaleValues(input: String): List<Float> {
        return input
            .split(',', ';', ' ')
            .mapNotNull { token -> token.trim().toFloatOrNull() }
            .filter { it > 0f }
            .distinct()
            .sorted()
    }

    /**
     * Formats raw scale values (machine unit) back to an editable string.
     */
    fun formatScaleValues(values: List<Float>): String =
        values.joinToString(", ") { WorkoutLogic.formatWeight(it) }

    /**
     * Converts raw scale values from the machine unit to kg (internal storage).
     */
    fun scaleValuesToKg(values: List<Float>, unit: WeightUnit): List<Float> =
        values.map { WeightConversion.toKg(it, unit) }

    /**
     * Converts stored kg scale values back to the machine unit for display.
     */
    fun scaleValuesFromKg(valuesKg: List<Float>, unit: WeightUnit): List<Float> =
        valuesKg.map { WeightConversion.fromKg(it, unit) }

    /**
     * Finds the stored kg scale value closest to a reference weight and
     * snaps to it. Returns null when the scale is empty.
     */
    fun snapToScale(weightKg: Float, scaleValuesKg: List<Float>): Float? {
        if (scaleValuesKg.isEmpty()) return null
        return scaleValuesKg.minBy { kotlin.math.abs(it - weightKg) }
    }

    /**
     * Returns the next stored kg scale value strictly above (or below) the
     * given weight, optionally skipping [steps] positions. Returns null if
     * no such value exists on the scale.
     */
    fun nextScaleValue(
        weightKg: Float,
        scaleValuesKg: List<Float>,
        up: Boolean = true,
        steps: Int = 1
    ): Float? {
        if (scaleValuesKg.isEmpty()) return null
        val sorted = scaleValuesKg.sorted()
        return if (up) {
            val higher = sorted.filter { it > weightKg + EPSILON }
            higher.getOrNull(steps - 1)
        } else {
            val lower = sorted.filter { it < weightKg - EPSILON }
            lower.getOrNull(lower.size - steps)
        }
    }

    /**
     * True when the given weight (kg) sits on a configured scale position.
     */
    fun isOnScale(weightKg: Float, scaleValuesKg: List<Float>): Boolean {
        return scaleValuesKg.any { kotlin.math.abs(it - weightKg) < EPSILON }
    }

    private const val EPSILON = 0.01f

    /**
     * Progression target on a configured scale (Issue #48): the next
     * printed pin position above (NORMAL) or below (INVERSE) the current
     * weight. Falls back to null when the scale offers no such position.
     */
    fun nextProgressionWeight(
        currentWeightKg: Float,
        scaleValuesKg: List<Float>,
        weightDirection: WeightDirection
    ): Float? {
        return nextScaleValue(
            weightKg = currentWeightKg,
            scaleValuesKg = scaleValuesKg,
            up = weightDirection == WeightDirection.NORMAL
        )
    }

    /**
     * Serializes stored kg scale values to a JSON array string for Room.
     */
    fun scaleValuesToJson(valuesKg: List<Float>): String {
        return Json.encodeToString(ListSerializer(Float.serializer()), valuesKg)
    }

    /**
     * Deserializes a JSON array of kg scale values; malformed JSON yields
     * an empty scale (increment-based fallback).
     */
    fun scaleValuesFromJson(json: String): List<Float> {
        return runCatching {
            Json.decodeFromString(ListSerializer(Float.serializer()), json)
        }.getOrDefault(emptyList())
    }
}
