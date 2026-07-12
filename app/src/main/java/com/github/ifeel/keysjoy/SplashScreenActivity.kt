package com.github.ifeel.keysjoy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log

class SplashScreenActivity: Activity() {

    companion object {
        private const val REQUEST_CODE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("Kontroller", "SplashScreen started")
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.e("Kontroller", "Requesting permissions: $permissions")
            requestPermissions(permissions.toTypedArray(), REQUEST_CODE)
        } else {
            Log.e("Kontroller", "Permissions OK, launching main")
            launchMainActivity()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (allGranted(grantResults)) {
                launchMainActivity()
            } else {
                showPermissionDeniedDialog()
            }
        }
    }

    private fun allGranted(grantResults: IntArray) =
        grantResults.all { it == PackageManager.PERMISSION_GRANTED }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth Permission Required")
            .setMessage("Please grant Bluetooth and Location permissions and reopen")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, SelectDeviceActivity::class.java))
        finish()
    }
}
