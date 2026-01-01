package com.example.langalarm

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    // Changed channel ID again to ensure fresh settings
    private val channelId = "alarm_channel_v4"
    private val notificationId = 123
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // Acquire a partial wake lock to keep the CPU running while the alarm is ringing
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LangAlarm:AlarmServiceWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_ALARM") {
            stopSelf()
            return START_NOT_STICKY
        }

        // Check if device is locked
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        val quizIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("START_QUIZ", true)
        }

        // Pass isLocked to buildNotification to conditionally apply FullScreenIntent
        val notification = buildNotification(quizIntent, isLocked)

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Only launch activity explicitly if the device is locked.
        // If unlocked, we rely on the notification (HUN).
        if (isLocked) {
             try {
                startActivity(quizIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        playAlarmSound()
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun playAlarmSound() {
        if (mediaPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val soundUriString = SoundRepository.getActiveSoundUri(this)
            
            mediaPlayer = if (soundUriString == SoundRepository.DEFAULT_SOUND) {
                MediaPlayer.create(this, R.raw.alarm, audioAttributes, 0)
            } else {
                try {
                    val soundUri = Uri.parse(soundUriString)
                    MediaPlayer().apply {
                        setDataSource(this@AlarmService, soundUri)
                        setAudioAttributes(audioAttributes)
                        prepare()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to default if URI is invalid
                    MediaPlayer.create(this, R.raw.alarm, audioAttributes, 0)
                }
            }

            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }
    }

    private fun buildNotification(quizIntent: Intent, isLocked: Boolean): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelName = "Alarm Clock Priority"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical Alarm Notifications"
                setSound(null, null) 
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true) // Attempt to bypass DND
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            quizIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("QuizAlarm")
            .setContentText("Time to wake up! Solve the quiz.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setLocalOnly(true)
        
        // Only set FullScreenIntent if the device is locked.
        // This ensures that if the screen is ON (unlocked), we only get a Heads-Up Notification,
        // preventing the activity from intrusively taking over the screen.
        if (isLocked) {
            builder.setFullScreenIntent(pendingIntent, true)
        } else {
            // When unlocked, we still want the notification to be actionable, 
            // but we rely on the user tapping it (content intent).
            builder.setContentIntent(pendingIntent)
        }
        
        // Ensure immediate display if possible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val notification = builder.build()
        // Explicitly add NO_CLEAR flag
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        return notification
    }
}
