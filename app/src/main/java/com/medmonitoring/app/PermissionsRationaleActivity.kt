package com.medmonitoring.app

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = """
                    Health Connect privacy

                    MedMonitoring reads only the health data types configured for this application.
                    The data is used to create monitoring records and is stored locally on this device.
                    You can revoke access at any time in Health Connect.
                """.trimIndent()
                textSize = 18f
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
        )
    }
}
