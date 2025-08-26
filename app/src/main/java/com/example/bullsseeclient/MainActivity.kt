package com.example.bullssee

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS
    )
    private lateinit var statusText: TextView // Reference to layout TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Loads activity_main.xml
        statusText = findViewById(R.id.statusText) // Assuming a status TextView is added
        showConsentDialog()
    }

    private fun showConsentDialog() {
        AlertDialog.Builder(this)
            .setTitle("Consent Required")
            .setMessage("BullsSeeClient will monitor your location, calls, and SMS. Do you consent? This is required for legal compliance.")
            .setPositiveButton("Agree") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Decline") { _, _ ->
                Toast.makeText(this, "App requires consent to proceed. Closing...", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissions() {
        val notGranted = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startServices()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startServices()
            } else {
                Toast.makeText(this, "Permissions denied. App will close.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startServices() {
        try {
            val serviceIntent = Intent(this, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            statusText.text = "Status: Services Started" // Update layout with status
            Toast.makeText(this, "Services started. Monitoring in background.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "Status: Error Starting Services"
            Toast.makeText(this, "Failed to start services: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, LocationService::class.java)) // Clean up service
    }
}