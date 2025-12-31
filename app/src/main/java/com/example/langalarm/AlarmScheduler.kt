package com.example.langalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast

object AlarmScheduler {

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(context, alarm)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
        }
        
        // Use the alarm ID's hash code as the request code to ensure uniqueness
        val requestCode = alarm.id.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = alarm.getNextAlarmTime()

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "Permission denied to set alarm", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = alarm.id.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAllAlarms(context: Context) {
        val alarms = AlarmRepository.loadAlarms(context)
        for (alarm in alarms) {
            if (alarm.isEnabled) {
                schedule(context, alarm)
            }
        }
    }
}
