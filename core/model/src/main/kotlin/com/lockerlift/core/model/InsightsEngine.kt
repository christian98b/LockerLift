package com.lockerlift.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure-Kotlin analytics engine for the mobile insights dashboard.
 * Operates on plain domain snapshots so it stays Android-free and unit-testable.
 */
object InsightsEngine {

    enum class TrainingVerdict {
        TOO_LITTLE,
        ON_TRACK,
        DO_MORE;

        val isTooLittle: Boolean get() = this == TOO_LITTLE
        val isDoMore: Boolean get() = this == DO_MORE
    }

    data class SessionSnapshot(
        val sessionId: String,
        val startTime: Long,
        val endTime: Long?,
        val machineInstances: List<MachineInstanceSnapshot>
    ) {
        val isCompleted: Boolean get() = endTime != null
        val completedSets: Int
            get() = machineInstances.sumOf { instance ->
                instance.sets.count { it.setType != SetType.WARMUP }
            }
    }

    data class MachineInstanceSnapshot(
        val machineId: String,
        val machineName: String,
        val isSkipped: Boolean,
        val sets: List<SetSnapshot>
    ) {
        val volumeKg: Float
            get() = sets.filter { it.setType != SetType.WARMUP }
                .sumOf { (it.weightKg * it.reps).toDouble() }
                .toFloat()
    }

    data class SetSnapshot(
        val setNumber: Int,
        val reps: Int,
        val weightKg: Float,
        val setType: SetType = SetType.NORMAL
    )

    data class DayActivity(
        val date: LocalDate,
        val sessionCount: Int,
        val totalVolumeKg: Float
    ) {
        val hasTrained: Boolean get() = sessionCount > 0
    }

    data class CalendarMonth(
        val yearMonth: String,
        val weeks: List<List<DayActivity?>>
    )

    data class RegularityResult(
        val weeksInWindow: Int,
        val averageSessionsPerWeek: Float,
        val verdict: TrainingVerdict
    )

    data class VolumePoint(
        val date: LocalDate,
        val volumeKg: Float
    )

    data class ProgressionTrend(
        val hasImproved: Boolean,
        val firstVolumeKg: Float,
        val lastVolumeKg: Float,
        val changePercent: Float
    )

    data class ExerciseProgression(
        val machineId: String,
        val machineName: String,
        val points: List<VolumePoint>,
        val trend: ProgressionTrend?
    )

    data class PersonalRecords(
        val totalSessions: Int,
        val totalVolumeKg: Float,
        val totalSets: Int
    )

    fun trainingDays(sessions: List<SessionSnapshot>, zone: ZoneId): List<DayActivity> {
        val sessionsByDate = sessions
            .filter { it.isCompleted }
            .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        return sessionsByDate.map { (date, daySessions) ->
            DayActivity(
                date = date,
                sessionCount = daySessions.size,
                totalVolumeKg = daySessions.sumOf { s ->
                    s.machineInstances.filter { !it.isSkipped }.sumOf { (it.volumeKg).toDouble() }
                        .toFloat()
                }
            )
        }.sortedBy { it.date }
    }

    fun buildCalendarMonth(
        sessions: List<SessionSnapshot>,
        yearMonth: java.time.YearMonth,
        zone: ZoneId
    ): CalendarMonth {
        val activityByDate = trainingDays(sessions, zone).associateBy { it.date }
        val firstDay = yearMonth.atDay(1)
        val leadingBlanks = when (firstDay.dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        val weeks = mutableListOf<List<DayActivity?>>()
        var currentWeek = MutableList<DayActivity?>(leadingBlanks) { null }
        for (day in 1..yearMonth.lengthOfMonth()) {
            val date = yearMonth.atDay(day)
            currentWeek.add(activityByDate[date] ?: DayActivity(date, 0, 0f))
            if (currentWeek.size == 7) {
                weeks.add(currentWeek.toList())
                currentWeek = mutableListOf()
            }
        }
        if (currentWeek.isNotEmpty()) {
            while (currentWeek.size < 7) currentWeek.add(null)
            weeks.add(currentWeek.toList())
        }
        return CalendarMonth(yearMonth = yearMonth.toString(), weeks = weeks)
    }

    fun averageSessionsPerWeek(sessions: List<SessionSnapshot>, zone: ZoneId): Float {
        val completed = sessions.filter { it.isCompleted }
        if (completed.isEmpty()) return 0f
        val dates = completed.map { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        val earliest = dates.min()
        val latest = dates.max()
        val spanDays = ChronoUnit.DAYS.between(earliest, latest).coerceAtLeast(0L) + 1
        val spanWeeks = (spanDays.toFloat() / 7f).coerceAtLeast(1f)
        return completed.size / spanWeeks
    }

    /**
     * Rates regularity over the trailing window (default 28 days up to and
     * including [upTo]). Fewer than [minSessionsPerWeek] sessions is TOO_LITTLE,
     * more than [maxSessionsPerWeek] is DO_MORE.
     */
    fun rateRegularity(
        sessions: List<SessionSnapshot>,
        upTo: LocalDate,
        zone: ZoneId,
        windowDays: Long = 28,
        minSessionsPerWeek: Float = 1f,
        maxSessionsPerWeek: Float = 4f
    ): RegularityResult {
        val windowStart = upTo.minusDays(windowDays - 1)
        val sessionsInWindow = sessions.filter {
            it.isCompleted && run {
                val d = Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
                !d.isBefore(windowStart) && !d.isAfter(upTo)
            }
        }
        val weeks = (windowDays.toFloat() / 7f)
        val perWeek = sessionsInWindow.size / weeks
        val verdict = when {
            sessionsInWindow.isEmpty() || perWeek < minSessionsPerWeek -> TrainingVerdict.TOO_LITTLE
            perWeek > maxSessionsPerWeek -> TrainingVerdict.DO_MORE
            else -> TrainingVerdict.ON_TRACK
        }
        return RegularityResult(
            weeksInWindow = weeks.toInt(),
            averageSessionsPerWeek = perWeek,
            verdict = verdict
        )
    }

    /**
     * Per-exercise volume progression across the last [maxPoints] completed
     * sessions that contain this machine (skipped instances excluded).
     */
    fun exerciseProgression(
        sessions: List<SessionSnapshot>,
        machineId: String,
        zone: ZoneId,
        maxPoints: Int = 10
    ): ExerciseProgression? {
        val relevant = sessions
            .filter { it.isCompleted }
            .sortedBy { it.startTime }
            .filter { s ->
                s.machineInstances.any { !it.isSkipped && it.machineId == machineId && it.sets.isNotEmpty() }
            }
            .takeLast(maxPoints)
        if (relevant.isEmpty()) return null
        val name = relevant.last().machineInstances.first { it.machineId == machineId }.machineName
        val points = relevant.map { s ->
            val volume = s.machineInstances
                .filter { !it.isSkipped && it.machineId == machineId }
                .sumOf { it.volumeKg.toDouble() }.toFloat()
            val date = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate()
            VolumePoint(date, volume)
        }
        val trend = linearTrend(points.map { it.volumeKg })
        return ExerciseProgression(
            machineId = machineId,
            machineName = name,
            points = points,
            trend = trend
        )
    }

    fun overallVolumeProgression(
        sessions: List<SessionSnapshot>,
        zone: ZoneId,
        maxPoints: Int = 12
    ): List<VolumePoint> {
        return sessions.filter { it.isCompleted }
            .sortedBy { it.startTime }
            .takeLast(maxPoints)
            .map { s ->
                VolumePoint(
                    date = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate(),
                    volumeKg = s.machineInstances.filter { !it.isSkipped }
                        .sumOf { it.volumeKg.toDouble() }.toFloat()
                )
            }
    }

    /**
     * Determines whether volume is improving. Uses a simple least-squares slope
     * so a single outlier cannot dominate the verdict.
     */
    fun linearTrend(values: List<Float>): ProgressionTrend? {
        if (values.size < 2) return null
        val n = values.size
        val meanX = (n - 1) / 2f
        val meanY = values.sum() / n
        var numerator = 0f
        var denominator = 0f
        values.forEachIndexed { i, v ->
            numerator += (i - meanX) * (v - meanY)
            denominator += (i - meanX) * (i - meanX)
        }
        val slope = if (denominator == 0f) 0f else numerator / denominator
        val first = values.first()
        val last = values.last()
        val changePercent = if (first == 0f) {
            if (last > 0f) 100f else 0f
        } else {
            ((last - first) / first) * 100f
        }
        return ProgressionTrend(
            hasImproved = slope > 0f,
            firstVolumeKg = first,
            lastVolumeKg = last,
            changePercent = changePercent
        )
    }

    fun personalRecords(sessions: List<SessionSnapshot>): PersonalRecords {
        val completed = sessions.filter { it.isCompleted }
        return PersonalRecords(
            totalSessions = completed.size,
            totalVolumeKg = completed.sumOf { s ->
                s.machineInstances.filter { !it.isSkipped }
                    .sumOf { it.volumeKg.toDouble() }.toFloat()
            },
            totalSets = completed.sumOf { it.completedSets }
        )
    }

    fun trainedMachines(sessions: List<SessionSnapshot>): List<Pair<String, String>> {
        return sessions.asSequence()
            .filter { it.isCompleted }
            .flatMap { s -> s.machineInstances.asSequence().filter { !it.isSkipped && it.sets.isNotEmpty() } }
            .distinctBy { it.machineId }
            .map { it.machineId to it.machineName }
            .sortedBy { it.second.lowercase() }
            .toList()
    }
}
