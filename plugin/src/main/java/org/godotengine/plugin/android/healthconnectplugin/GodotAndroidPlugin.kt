package org.godotengine.plugin.android.healthconnectplugin

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
import androidx.health.connect.client.records.StepsRecord
import com.example.healthconnectsample.data.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot


class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): Set<SignalInfo?> {
        return setOf(
            SignalInfo("onStepsReceived", Dictionary::class.java),  // signal with one String argument
            SignalInfo("on_ready")                       // signal with no args
        )
    }

    private val changesDataTypes = setOf(
        StepsRecord::class,
    )

    val permissions = changesDataTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    val healthConnectManager by lazy {
        HealthConnectManager(godot.getActivity()!!)
    }

    @UsedByGodot
    fun helloWorld() {
        runOnUiThread {
            Toast.makeText(activity, "Hello World", Toast.LENGTH_LONG).show()
            Log.v(pluginName, "Hello World")
        }
    }

    companion object {
        private const val HEALTH_CONNECT_PERMISSION_REQUEST_CODE = 1001
    }

    @UsedByGodot
    fun requestPermissions() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Check if permissions are already granted
                val allPermissions = permissions + PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
                val granted = healthConnectManager.hasAllPermissions(allPermissions)

                if (granted) {
                    Log.i(TAG, "All permissions already granted")
                    return@launch
                }

                // Launch permission request
                val contract = healthConnectManager.requestPermissionsActivityContract()
                val intent = contract.createIntent(godot.getActivity()!!, allPermissions)

                // Use startActivityForResult instead of startActivity
                godot.getActivity()!!.startActivityForResult(intent, HEALTH_CONNECT_PERMISSION_REQUEST_CODE)

                Log.i(TAG, "Health Connect permission dialog launched with request code $HEALTH_CONNECT_PERMISSION_REQUEST_CODE")

            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(godot.getActivity(), "Error requesting permissions: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Override this method to handle activity results
    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == HEALTH_CONNECT_PERMISSION_REQUEST_CODE) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val contract = healthConnectManager.requestPermissionsActivityContract()
                    val allPermissions = permissions + PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
                    val result = contract.parseResult(resultCode, data)

                    val message = if (result.containsAll(allPermissions)) {
                        "All permissions granted successfully!"
                    } else {
                        "Some permissions were denied. Granted: ${result.size}/${allPermissions.size}"
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(godot.getActivity(), message, Toast.LENGTH_LONG).show()
                        Log.i(TAG, "Permission result: $message")
                        Log.i(TAG, "Granted permissions: $result")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing permission result", e)
                }
            }
        }
}

@UsedByGodot
fun checkPermissions() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val allPermissions = permissions + PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

            Log.i(TAG, "=== Permission Check ===")
            Log.i(TAG, "Generated permissions from data types:")
            permissions.forEach { permission ->
                Log.i(TAG, "  - $permission")
            }
            Log.i(TAG, "Background permission:")
            Log.i(TAG, "  - $PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND")

            val granted = healthConnectManager.hasAllPermissions(allPermissions)

            withContext(Dispatchers.Main) {
                val message = if (granted) "All permissions granted" else "Some permissions missing"
                Toast.makeText(godot.getActivity(), message, Toast.LENGTH_SHORT).show()
                Log.i(TAG, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
        }
    }
}

@UsedByGodot
fun checkCurrentPermissionStatus() {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val allPermissions = permissions + PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

            Log.i(TAG, "=== Current Permission Status ===")

            // Get currently granted permissions directly from Health Connect
            val grantedPermissions = healthConnectManager.healthConnectClient.permissionController.getGrantedPermissions()

            Log.i(TAG, "All granted permissions from Health Connect (${grantedPermissions.size}):")
            if (grantedPermissions.isEmpty()) {
                Log.i(TAG, "  (no permissions granted)")
            } else {
                grantedPermissions.forEach { permission ->
                    Log.i(TAG, "  ✓ $permission")
                }
            }

            Log.i(TAG, "Requested permissions status:")
            allPermissions.forEach { permission ->
                val isGranted = grantedPermissions.contains(permission)
                Log.i(TAG, "  ${if (isGranted) "✓" else "✗"} $permission")
            }

            val allGranted = grantedPermissions.containsAll(allPermissions)

            withContext(Dispatchers.Main) {
                val message = "Permissions: ${grantedPermissions.size} granted total, ${if (allGranted) "all required granted" else "missing required permissions"}"
                Toast.makeText(godot.getActivity(), message, Toast.LENGTH_LONG).show()
                Log.i(TAG, message)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking current permission status", e)
            e.printStackTrace()
        }
    }
}

@UsedByGodot
fun openHealthConnectAppDirectly() {
    healthConnectManager.openHealthConnectPermissionsScreen(godot.getActivity()!!)
}

@UsedByGodot
fun openHealthConnectViaPackageManager() {
    try {
        Log.i(TAG, "=== Opening Health Connect via Package Manager ===")

        val context = godot.getActivity()!!
        val packageManager = context.packageManager

        try {
            // Get the Health Connect app's launch intent
            val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")

            if (launchIntent != null) {
                Log.i(TAG, "✓ Found Health Connect launch intent")
                context.startActivity(launchIntent)

                runOnUiThread {
                    Toast.makeText(activity, "Opened Health Connect app - navigate to App permissions → podo", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "✗ Could not get launch intent for Health Connect")
                runOnUiThread {
                    Toast.makeText(activity, "Health Connect app not found", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to launch Health Connect app", e)
            runOnUiThread {
                Toast.makeText(activity, "Could not launch Health Connect app", Toast.LENGTH_SHORT).show()
            }
        }

    } catch (e: Exception) {
        Log.e(TAG, "Error in package manager approach", e)
    }
}

@UsedByGodot
fun openSystemHealthConnectSettings() {
    try {
        Log.i(TAG, "=== Opening System Health Connect Settings ===")

        val context = godot.getActivity()!!

        // Try opening system settings for Health Connect
        val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)

        try {
            context.startActivity(intent)
            runOnUiThread {
                Toast.makeText(activity, "Navigate to: Privacy → Health Connect → App permissions → podo", Toast.LENGTH_LONG).show()
            }
            Log.i(TAG, "✓ Opened system settings - user should navigate to Health Connect")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to open system settings", e)
            runOnUiThread {
                Toast.makeText(activity, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }

    } catch (e: Exception) {
        Log.e(TAG, "Error opening system settings", e)
    }
}
}