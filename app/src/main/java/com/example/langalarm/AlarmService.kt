package com.example.langalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val channelId = "alarm_channel"
    private val notificationId = 123

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_ALARM") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(notificationId, buildNotification())
        playAlarmSound()
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
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

    private fun buildNotification(): android.app.Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelName = "Alarm Clock"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Alarm Notifications"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val quizIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("START_QUIZ", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            quizIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Wake Up!")
            .setContentText("Solve the quiz to stop the alarm.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .build()
    }
}