package com.example.tv_controller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvStatusLabel: TextView
    private lateinit var pinInputField: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSavePin: Button
    private lateinit var btnStartServer: Button
    private val REQUEST_MEDIA_PROJECTION = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        pinInputField = findViewById(R.id.pinInputField)
        btnSavePin = findViewById(R.id.btnSavePin)
        btnStartServer = findViewById(R.id.btnStartServer)

        btnSavePin.setOnClickListener {
            val pinText = pinInputField.text?.toString()?.trim().orEmpty()
            if (pinText.length >= 4) {
                val hashedResult = SecurityVault.computeMasterHash(pinText)
                SecurityVault.getPrefs(this).edit().putString("master_hash", hashedResult).apply()
                Toast.makeText(this, "Master Security H7 Key Saved Successfully", Toast.LENGTH_SHORT).show()
                pinInputField.text?.clear()
            } else {
                Toast.makeText(this, "PIN must contain at least 4 digits", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartServer.setOnClickListener {
            if (btnStartServer.text == "START SERVER ENGINE") {
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mpManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
            } else {
                // --- THE KILL BUTTON LOGIC ---
                val serviceIntent = Intent(this, TvMediaProjectionService::class.java)
                stopService(serviceIntent)

                btnStartServer.text = "START SERVER ENGINE"
                btnStartServer.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                tvStatusLabel.text = "Server Status: Offline"
                tvStatusLabel.setTextColor(android.graphics.Color.WHITE)
                Toast.makeText(this, "Server Terminated Safely", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, TvMediaProjectionService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA_INTENT", data)
            }
            startService(serviceIntent)

            // Toggle the button state into a kill switch action receiver
            btnStartServer.text = "KILL HOSTER SERVER"
            btnStartServer.setTextColor(android.graphics.Color.RED)

            tvStatusLabel.text = "Server Status: BROADCASTING"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#39FF14"))
        }
    }
}