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

            // --- THE PORTRAIT ALIGNMENT FIX ---
            // When held in portrait, the vertical height matches the largest pixel scale layout boundary
            val targetX = percentX * displayMetrics.widthPixels
            val targetY = percentY * displayMetrics.heightPixels

            Log.d(TAG, "Executing Portrait Injection -> Pixels: X=$targetX, Y=$targetY [Screen Bounds: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}]")

            val path = Path().apply { moveTo(targetX, targetY) }

            val stroke = GestureDescription.StrokeDescription(path, 0, 60) // Reduced to 60ms for Snappier interaction
            val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

            dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Touch successfully injected at target location.")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Touch blocked or cancelled by system window constraints.")
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during gesture injection handling: ${e.message}")
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