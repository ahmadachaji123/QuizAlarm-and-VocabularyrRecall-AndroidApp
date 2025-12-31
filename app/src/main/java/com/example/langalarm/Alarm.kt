package com.example.langalarm

import java.util.Calendar

data class Alarm(
    val id: Long = System.currentTimeMillis(),
    var hour: Int,
    var minute: Int,
    var isEnabled: Boolean = true,
    var days: Set<Int> = emptySet() // Calendar.SUNDAY (1) through Calendar.SATURDAY (7)
) {
    fun isRepeating(): Boolean = days.isNotEmpty()

    fun getNextAlarmTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val now = System.currentTimeMillis()

        if (days.isEmpty()) {
            // One-time alarm
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            // Repeating alarm
            // Find the next day in the set that matches
            while (calendar.timeInMillis <= now || !days.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }
}
