package com.example.tv_controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class TvAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: TvAccessibilityService? = null

        fun injectTouch(x: Float, y: Float) {
            instance?.executeTouchGesture(x, y)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    private fun executeTouchGesture(percentX: Float, percentY: Float) {
        // Fetch current screen boundaries automatically
        val displayMetrics = resources.displayMetrics
        val targetX = percentX * displayMetrics.widthPixels
        val targetY = percentY * displayMetrics.heightPixels

        val path = Path().apply { moveTo(targetX, targetY) }

        // Construct a click event stroke duration matching human interaction latency profiles (80ms click)
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}