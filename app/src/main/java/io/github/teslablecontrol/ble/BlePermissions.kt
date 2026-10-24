package io.github.teslablecontrol.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** What Android needs before the app can find and talk to the car. */
object BlePermissions {
    /** Runtime permissions for scanning and connecting. Location is required to receive scan results. */
    val required: List<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    /** Connecting to a remembered car needs no location access. */
    val connectOnly: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    val notifications: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context, permissions: List<String> = required): List<String> =
        permissions.filterNot { granted(context, it) }

    fun canScan(context: Context): Boolean = missing(context).isEmpty()

    fun canConnect(context: Context): Boolean = missing(context, connectOnly).isEmpty()

    /**
     * Asks Android to turn Bluetooth on. On Android 12+ the prompt needs the connect permission;
     * without it the Bluetooth settings screen opens instead.
     */
    fun requestEnableBluetooth(context: Context) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (allowed) BluetoothAdapter.ACTION_REQUEST_ENABLE else Settings.ACTION_BLUETOOTH_SETTINGS)
        try {
            context.startActivity(intent)
        } catch (_: SecurityException) {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            // No Bluetooth UI on this device; the status text still says Bluetooth is off.
        }
    }

    fun isBluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    fun isLocationOn(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } == true
}
