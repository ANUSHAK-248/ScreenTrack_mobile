package com.example.tv_controller

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import android.hardware.display.VirtualDisplay.Callback
import kotlin.concurrent.thread

class TvMediaProjectionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRunning = false

    private var tvSocketServer: TvSocketServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupForegroundNotification()

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val dataData = intent?.getParcelableExtra<Intent>("DATA_INTENT")

        if (dataData != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, dataData)

            startCoreHosterEngine()
        }
        return START_STICKY
    }

//    called in onStartCommand
    private fun startCoreHosterEngine() {
        isRunning = true

        val metrics = resources.displayMetrics

        // --- THE ABSOLUTE MARGIN DESTRUCTION FIX ---
        // Instead of forcing a 1280x720 landscape box, we query the exact
        // physical dimensions of the phone dynamically.
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        android.util.Log.d("MediaProjection", "Native Portrait Capture Initiated: ${width}x${height}")

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                onDestroy()
            }
        }, Handler(Looper.getMainLooper()))

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TvScreenCapture", width, height, metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader?.surface, null, null
        )

        tvSocketServer = TvSocketServer(this) { percentX, percentY ->
            TvAccessibilityService.injectTouch(percentX, percentY)
        }
        tvSocketServer?.startServer()

        startImageProcessingLoop()
    }

//    called in startCoreHosterEngine
    private fun startImageProcessingLoop() {
        thread {
            try {
                while (isRunning) {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * image.width

                        val bitmap = Bitmap.createBitmap(
                            image.width + rowPadding / pixelStride,
                            image.height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        val byteStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, byteStream)
                        val frameBytes = byteStream.toByteArray()

                        // --- THE CRITICAL FIX APPLIED HERE ---
                        // Transmits frame payload via TvSocketServer's active authenticated socket pipeline
                        tvSocketServer?.sendFrameToClient(frameBytes)
                    }
                    // Approx 15 FPS match profile cadence to reduce overhead spikes
                    Thread.sleep(66)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

//    called in onStartCommand
    private fun setupForegroundNotification() {
        val channelId = "tv_track_channel"
        val channelName = "TvTrack Background Sync"
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TvTrack Server Engine Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(101, notification)
    }

//    called in startCoreHosterEngine
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        virtualDisplay?.release()
        mediaProjection?.stop()
        tvSocketServer?.stopServer()
    }
}