package com.example.langalarm.data

import java.util.UUID

/**
 * Represents a single alarm configuration.
 * @param id A unique identifier for the alarm.
 * @param hour The hour of the day (0-23).
 * @param minute The minute of the hour (0-59).
 * @param recurringDays A set of Calendar day constants (e.g., Calendar.MONDAY). An empty set means it's a one-time alarm.
 * @param isEnabled Whether the alarm is active.
 */
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val recurringDays: Set<Int> = emptySet(),
    val isEnabled: Boolean = true
)