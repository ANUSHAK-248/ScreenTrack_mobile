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

//        called in TvMPs . startCoreHosterEngine
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

//  self call
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TvAccessibilityService Connected and Registered.")
    }

//  called in injectTouch
    private fun executeTouchGesture(percentX: Float, percentY: Float) {
        try {
            val displayMetrics = resources.displayMetrics

            // Pure universal mapping: Percentages translate directly to destination physical screen pixels
            val targetX = percentX * displayMetrics.widthPixels
            val targetY = percentY * displayMetrics.heightPixels

            Log.d(TAG, "Universal Injection -> Pixels: X=$targetX, Y=$targetY")

            val path = Path().apply { moveTo(targetX, targetY) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

            dispatchGesture(gestureBuilder.build(), null, null)

        } catch (e: Exception) {
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