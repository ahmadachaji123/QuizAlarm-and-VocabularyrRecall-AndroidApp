package com.example.langalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        
        // Handle boot completed to reschedule alarms
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.rescheduleAllAlarms(context)
            return
        }

        // Handle rescheduling for repeating alarms or disabling one-time alarms
        val alarmId = intent?.getLongExtra("ALARM_ID", -1L) ?: -1L
        if (alarmId != -1L) {
            val alarm = AlarmRepository.getAlarmById(context, alarmId)
            if (alarm != null) {
                if (alarm.isRepeating()) {
                    AlarmScheduler.schedule(context, alarm)
                } else {
                    val updatedAlarm = alarm.copy(isEnabled = false)
                    AlarmRepository.updateAlarm(context, updatedAlarm)
                }
            }
        }

        // Acquire a WakeLock to ensure the device is awake when we start the service
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LangAlarm:WakeLock"
        )
        // Increase hold time to ensure service has enough time to start and acquire its own lock
        wakeLock.acquire(10 * 1000L) // Hold for 10 seconds

        // Start the Alarm Service to play sound and show notification
        val serviceIntent = Intent(context, AlarmService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        // We do NOT release the lock immediately here. 
        // The timeout (10s) will handle it, or we rely on the Service to pick up.
        // Releasing it immediately might kill the process before Service.onCreate runs.
    }
}
