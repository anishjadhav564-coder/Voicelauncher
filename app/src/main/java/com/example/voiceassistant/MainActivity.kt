package com.example.voiceassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val wakeInput = findViewById<EditText>(R.id.wakeWordInput)
        val status = findViewById<TextView>(R.id.statusText)

        wakeInput.setText(prefs.getString("wake_word", "hey assistant"))

        findViewById<Button>(R.id.startButton).setOnClickListener {
            val wake = wakeInput.text.toString().trim().lowercase()
            if (wake.isEmpty()) {
                Toast.makeText(this, "Enter a wake word first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("wake_word", wake).apply()

            if (!hasPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                // Needed so the service can open the assistant while the app is in the background.
                Toast.makeText(
                    this,
                    "Turn on \"Display over other apps\" for this app, then come back and tap Start",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
            status.text = "Running. Say \"$wake\" to open your assistant."
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            status.text = "Stopped"
        }
    }

    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            Toast.makeText(this, "Permission granted. Tap Start again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }
}
