package com.example.tv_controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TvAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TvAccessibilityService"
        private var instance: TvAccessibilityService? = null

        // Handler tied permanently to the Main UI Thread loop
        private val uiHandler = Handler(Looper.getMainLooper())

        fun injectTouch(x: Float, y: Float) {
            val service = instance
            if (service != null) {
                // --- THE CRITICAL PATCH ---
                // Forces execution onto the Main UI Thread to pass Android Security
                uiHandler.post {
                    service.executeTouchGesture(x, y)
                }
            } else {
                Log.w(TAG, "Cannot inject touch! TvAccessibilityService instance is NULL. Ensure service is enabled in Android Settings.")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TvAccessibilityService Connected and Registered.")
    }

    private fun executeTouchGesture(percentX: Float, percentY: Float) {
        try {
            val displayMetrics = resources.displayMetrics
            val targetX = percentX * displayMetrics.widthPixels
            val targetY = percentY * displayMetrics.heightPixels

            Log.d(TAG, "Processing touch injection path mapping: Percent($percentX, $percentY) -> Pixels($targetX, $targetY)")

            val path = Path().apply { moveTo(targetX, targetY) }

            // 80ms duration matches standard organic user human latency profiles
            val stroke = GestureDescription.StrokeDescription(path, 0, 80)
            val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

            dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Gesture dispatch COMPLETED successfully at destination coordinates.")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Gesture dispatch CANCELLED by the OS window manager layer.")
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered during gesture dispatch execution: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}