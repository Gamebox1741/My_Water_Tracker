package com.example.mywatertracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class WaterTrackingService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "water_tracker_channel"
        const val ACTION_ADD_WATER = "ADD_WATER"
        const val EXTRA_WATER_AMOUNT = "WATER_AMOUNT"
        const val GLASS_OF_WATER_ML = 250
    }

    private val binder = WaterTrackerBinder()
    private var waterLevel = 0.0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val waterDecreaseRunnable = object : Runnable {
        override fun run() {
            decreaseWaterLevel()
            handler.postDelayed(this, 5000) // Run every 5 seconds
        }
    }

    inner class WaterTrackerBinder : Binder() {
        fun getService(): WaterTrackingService = this@WaterTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle add water action
        if (intent?.action == ACTION_ADD_WATER) {
            val amount = intent.getDoubleExtra(EXTRA_WATER_AMOUNT, GLASS_OF_WATER_ML.toDouble())
            addWater(amount)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Water Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your water consumption"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            if (!isRunning) {
                isRunning = true
                handler.post(waterDecreaseRunnable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Run without foreground if needed
            if (!isRunning) {
                isRunning = true
                handler.post(waterDecreaseRunnable)
            }
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("My Water Tracker")
            .setContentText("Water Level: ${String.format("%.1f", waterLevel)} ml")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(null)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun decreaseWaterLevel() {
        waterLevel -= 0.144 // Decrease by 0.144 ml every 5 seconds
        if (waterLevel < 0) waterLevel = 0.0
        updateNotification()
    }

    fun addWater(amount: Double) {
        waterLevel += amount
        updateNotification()
    }

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentWaterLevel(): Double = waterLevel

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(waterDecreaseRunnable)
        stopForeground(true)
    }
}