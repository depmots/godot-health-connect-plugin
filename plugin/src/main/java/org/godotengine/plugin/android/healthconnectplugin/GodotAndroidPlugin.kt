package org.godotengine.plugin.android.healthconnectplugin

import android.content.ContentValues.TAG
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import java.time.Instant
import java.time.ZoneId

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    val healthConnectManager by lazy {
        activity?.let { HealthConnectManager(it) }
    }

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.
     */
    @UsedByGodot
    fun helloWorld() {
        runOnUiThread {
            Toast.makeText(activity, "Hello World", Toast.LENGTH_LONG).show()
            Log.v(pluginName, "Hello World")
        }
    }

    @UsedByGodot
    fun requestPermissions() {

            val result = healthConnectManager?.requestPermissionsDirectly(activity)

    }

    @RequiresApi(Build.VERSION_CODES.O)
    @UsedByGodot
    fun readStepsForTheDay() {
        val now = Instant.now()
        val startOfDay = now.atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

        CoroutineScope(Dispatchers.IO).launch {
            val result = healthConnectManager?.readExerciseSessions(startOfDay, now)

            withContext(Dispatchers.Main) {
                Log.i(TAG, "requestHealthConnectPermission: ")
            }
        }
    }
}
