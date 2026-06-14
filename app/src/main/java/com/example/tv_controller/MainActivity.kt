package com.example.tv_controller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : Activity() {

    private lateinit var tvStatusLabel: TextView
    private lateinit var pinInputField: EditText
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
            val pinText = pinInputField.text.toString().trim()
            if (pinText.length >= 4) {
                val hashedResult = SecurityVault.computeMasterHash(pinText)
                SecurityVault.getPrefs(this).edit().putString("master_hash", hashedResult).apply()
                Toast.makeText(this, "Master Security H7 Key Saved Successfully", Toast.LENGTH_SHORT).show()
                pinInputField.text.clear()
            } else {
                Toast.makeText(this, "PIN must contain at least 4 digits", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartServer.setOnClickListener {
            // Trigger standard system projection safety acceptance warnings on the Sansui viewport screen
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
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
            tvStatusLabel.text = "Server Status: ACTIVE & BROADCASTING"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#39FF14"))
        }
    }
}