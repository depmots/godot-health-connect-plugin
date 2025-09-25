package org.godotengine.plugin.android.healthconnectplugin


import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class HealthPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = """
                This app uses Health Connect data (heart rate, steps, etc.)
                to provide gameplay features and tracking.
                We do not share or sell your health data.
            """.trimIndent()
            setPadding(32, 32, 32, 32)
            textSize = 18f
        }

        setContentView(tv)
    }
}
