package org.godotengine.plugin.android.healthconnectplugin

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

import android.util.Log
import android.widget.Toast
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
import androidx.health.connect.client.records.StepsRecord
import com.example.healthconnectsample.data.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.time.temporal.ChronoUnit
import java.time.Instant


class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot), SensorEventListener {

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): Set<SignalInfo?> {
        return setOf(
            SignalInfo("onStepsReceived", Dictionary::class.java),  // signal with one String argument
            SignalInfo("onPermissionStatusReceived", Integer::class.java),  // Fixed
            SignalInfo("onPermissionResultReceived", Integer::class.java),
            SignalInfo("onStepCountUpdated", Integer::class.java),
            SignalInfo("onStepDetected", Integer::class.java),
        )
    }




    @UsedByGodot
    fun helloWorld() {
        runOnUiThread {
            Toast.makeText(activity, "Hello World", Toast.LENGTH_LONG).show()
            Log.v(pluginName, "Hello World")
        }
    }


    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var initialStepCount: Int = 0
    private var isStepCounterInitialized = false

    @UsedByGodot
    fun initPedometer() {
        try {
            sensorManager = godot.getActivity()!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // TYPE_STEP_COUNTER gives total steps since last reboot
            stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            // TYPE_STEP_DETECTOR triggers an event for each step
            stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            if (stepCounterSensor == null && stepDetectorSensor == null) {
                Log.e(TAG, "No step sensors available on this device")
                runOnUiThread {
                    Toast.makeText(activity, "Device does not have a step counter", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.i(TAG, "Step sensors initialized successfully")
                Log.i(TAG, "Step Counter: ${stepCounterSensor != null}")
                Log.i(TAG, "Step Detector: ${stepDetectorSensor != null}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing pedometer", e)
        }
    }

    @UsedByGodot
    fun startStepCounting() {
        try {
            isStepCounterInitialized = false

            stepCounterSensor?.let { sensor ->
                sensorManager?.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                Log.i(TAG, "Started step counter")
            }

            stepDetectorSensor?.let { sensor ->
                sensorManager?.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                Log.i(TAG, "Started step detector")
            }

            runOnUiThread {
                Toast.makeText(activity, "Step counting started", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting step counting", e)
        }
    }

    @UsedByGodot
    fun stopStepCounting() {
        try {
            sensorManager?.unregisterListener(this)
            Log.i(TAG, "Stopped step counting")

            runOnUiThread {
                Toast.makeText(activity, "Step counting stopped", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping step counting", e)
        }
    }

    @UsedByGodot
    fun isPedometerAvailable(): Boolean {
        return stepCounterSensor != null || stepDetectorSensor != null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: $accuracy")
    }

    // Call this in your plugin's cleanup/destroy
    override fun onMainDestroy() {
        super.onMainDestroy()
        stopStepCounting()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    // Returns total steps since last reboot
                    val totalSteps = it.values[0].toInt()

                    if (!isStepCounterInitialized) {
                        initialStepCount = totalSteps
                        isStepCounterInitialized = true
                        Log.i(TAG, "Initial step count: $initialStepCount")
                    }

                    val stepsSinceStart = totalSteps - initialStepCount
                    Log.d(TAG, "Steps since start: $stepsSinceStart (Total: $totalSteps)")

                    // Emit signal with steps since we started counting
                    emitSignal("onStepCountUpdated", Integer.valueOf(stepsSinceStart))
                }

                Sensor.TYPE_STEP_DETECTOR -> {
                    // Triggers once per step detected
                    Log.d(TAG, "Step detected!")
                    emitSignal("onStepDetected", Integer.valueOf(1))
                }
            }
        }
    }

    



    private val changesDataTypes = setOf(
        StepsRecord::class,
    )

    val permissions = changesDataTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    val healthConnectManager by lazy {
        HealthConnectManager(godot.getActivity()!!)
    }


    companion object {
        private const val HEALTH_CONNECT_PERMISSION_REQUEST_CODE = 1001
        private const val ACTIVITY_RECOGNITION_REQUEST_CODE = 1002

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
                    openHealthConnectSettings()
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

                        emitSignal("onPermissionResultReceived", Integer.valueOf(if (result.containsAll(allPermissions)) 1 else 0))

//
//                        Toast.makeText(godot.getActivity(), message, Toast.LENGTH_LONG).show()
//                        Log.i(TAG, "Permission result: $message")
//                        Log.i(TAG, "Granted permissions: $result")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing permission result", e)
                }
            }
        }
    }


    @UsedByGodot
    fun openHealthConnectSettings() {
        try {
            Log.i(TAG, "=== Opening Health Connect Settings Directly ===")

            val context = godot.getActivity()!!
            val packageName = context.packageName

            // Try different Health Connect settings intents
            val intents = listOf(
                // Direct app permissions for this app
                android.content.Intent("androidx.health.action.HEALTH_PERMISSIONS_SETTINGS").apply {
                    setPackage("com.google.android.apps.healthdata")
                    putExtra("package_name", packageName)
                },
                // General Health Connect settings
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", "com.google.android.apps.healthdata", null)
                },
                // Health Connect main settings
                android.content.Intent("android.settings.HEALTH_CONNECT_SETTINGS")
            )

            for ((index, intent) in intents.withIndex()) {
                Log.i(TAG, "Trying intent ${index + 1}: ${intent.action}")

                try {
                    val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolveInfo != null) {
                        Log.i(TAG, "✓ Intent ${index + 1} can be resolved, launching...")
                        context.startActivity(intent)

                        runOnUiThread {
                            Toast.makeText(activity, "Opening Health Connect settings", Toast.LENGTH_SHORT).show()
                        }
                        return
                    } else {
                        Log.w(TAG, "✗ Intent ${index + 1} cannot be resolved")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "✗ Intent ${index + 1} failed: ${e.message}")
                }
            }

            Log.e(TAG, "All Health Connect settings intents failed")
            runOnUiThread {
                Toast.makeText(activity, "Could not open Health Connect settings", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error opening Health Connect settings", e)
            e.printStackTrace()
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
                    emitSignal("onPermissionStatusReceived", Integer.valueOf(if (granted) 1 else 0))


//                    val message = if (granted) "All permissions granted" else "Some permissions missing"
//                    Toast.makeText(godot.getActivity(), message, Toast.LENGTH_SHORT).show()
//                    Log.i(TAG, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking permissions", e)
            }
        }
    }

    @UsedByGodot
    fun getStepsSince(p_startTime: Long, p_endTime: Long): Long? {
        val now = Instant.ofEpochMilli(p_endTime)
        val startTime = Instant.ofEpochMilli(p_startTime)

        return try {
            runBlocking {
                healthConnectManager.aggregateSteps(
                    healthConnectManager.healthConnectClient,
                    startTime,
                    now
                )
            }
        } catch (e: Exception) {
            null
        }
    }









    fun StepsRecord.toDictionary(): Dictionary {
        val dict = Dictionary()
        dict["count"] = count
        dict["end_time"] = endTime.toEpochMilli()
        dict["start_time"] = startTime.toEpochMilli()
        dict["id"] = metadata.id
        metadata.device?.let { dict["device"] = it.model }
        dict["method"] = metadata.recordingMethod.toString()
        return dict
    }





}