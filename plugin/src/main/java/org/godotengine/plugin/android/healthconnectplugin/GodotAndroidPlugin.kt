package org.godotengine.plugin.android.healthconnectplugin

import android.content.ContentValues.TAG
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.viktormykhailiv.kmp.health.HealthDataType.Steps
import com.viktormykhailiv.kmp.health.HealthManagerFactory
import com.viktormykhailiv.kmp.health.duration
import com.viktormykhailiv.kmp.health.readSteps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import kotlin.time.Duration.Companion.days


class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME


    val health = HealthManagerFactory().createManager()

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

        CoroutineScope(Dispatchers.IO).launch {
            val result = request()

            withContext(Dispatchers.Main) {
                Log.i(TAG, "readPermissions: ")
        }
        }
    }

    suspend fun request()
    {


        health.isAvailable()
            .onSuccess { isAvailable ->
                if (!isAvailable) {
                    println("No Health service is available on the device")
                }
                else
                {
                    Log.i(TAG, "request: Available")
                    health.requestAuthorization(
                        readTypes = listOf(
                            Steps,
                        ),
                        writeTypes = listOf(
                            Steps,
                        ),
                    )
                        .onSuccess { isAuthorized ->
                            if (!isAuthorized) {
                                println("Not authorized")
                            }
                        }
                        .onFailure { error ->
                            println("Failed to authorize $error")
                        }
                }
            }
            .onFailure { error ->
                println("Failed to check if Health service is available $error")
            }
        
       
    }

    @UsedByGodot
    fun readSteps()
    {
        CoroutineScope(Dispatchers.IO).launch {
            health.readSteps(
                startTime = Clock.System.now().minus(1.days),
                endTime = Clock.System.now(),
            ).onSuccess { steps ->
                steps.forEachIndexed { index, record ->
                    println("[$index] ${record.count} steps for ${record.duration}")
                }
                if (steps.isEmpty()) {
                    println("No steps data")
                }
            }.onFailure { error ->
                println("Failed to read steps $error")
            }
        }
    }


}
