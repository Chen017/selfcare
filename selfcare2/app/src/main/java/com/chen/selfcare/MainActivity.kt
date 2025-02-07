package com.chen.selfcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.chen.selfcare.ui.theme.SelfcareTheme
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import com.heytap.wearable.support.widget.HeyDialog

/**
 * MainActivity: The main entry point of the Selfcare application.
 * Sets up the main UI using Jetpack Compose and loads the main layout from XML.
 * Handles navigation to different features like Black Mode and History.
 */
class MainActivity : ComponentActivity() {
    /**
     * Called when the activity is first created. Initializes the UI and sets up the content.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enable edge-to-edge display

        showHeyDialog() // Display a welcome dialog on app start

        setContent { // Set the content of the activity using Jetpack Compose
            SelfcareTheme { // Apply the SelfcareTheme to the composable content
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding -> // Use Scaffold to provide basic screen layout structure
                    AndroidView( // Embed an Android View (from XML layout) within Compose UI
                        factory = { context -> // Factory lambda to create the Android View
                            LayoutInflater.from(context).inflate(R.layout.activity_main_layout, null) // Inflate activity_main_layout.xml
                        },
                        modifier = Modifier
                            .padding(innerPadding) // Apply inner padding from Scaffold
                            .fillMaxSize(), // Make the AndroidView fill the maximum available size
                        update = { view -> // Update lambda to interact with the inflated view
                            val singleDefaultItemNormalMode =
                                view.findViewById<com.heytap.wearable.support.widget.HeySingleDefaultItem>(R.id.list_item_1) // Find 'Normal Mode' list item
                            singleDefaultItemNormalMode.setOnClickListener { // Set click listener for 'Normal Mode' item
                                val intent = android.content.Intent(this@MainActivity, BlackModeActivity::class.java) // Create intent to start BlackModeActivity
                                startActivity(intent) // Start BlackModeActivity
                            }

                            val singleDefaultItemHistory =
                                view.findViewById<com.heytap.wearable.support.widget.HeySingleDefaultItem>(R.id.list_item_history) // Find 'History' list item
                            singleDefaultItemHistory.setOnClickListener { // Set click listener for 'History' item
                                val intent = android.content.Intent(this@MainActivity, HistoryActivity::class.java) // Create intent to start HistoryActivity
                                startActivity(intent) // Start HistoryActivity
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Displays a HeyDialog as a welcome message when the app starts.
     */
    private fun showHeyDialog() {
        val builder = HeyDialog.HeyBuilder(this) // Use HeyDialog.HeyBuilder to create a new dialog
        builder.setContentViewStyle(HeyDialog.STYLE_TITLE_CONTENT) // Set dialog style to include title and content
        builder.setTitle("欢迎来到Selfcare") // Set the title of the dialog (Chinese: Welcome to Selfcare)
        builder.setMessage("    开启快乐的旅程吧!\n              OvO") // Set the message of the dialog (Chinese: Start a happy journey! OvO)
        builder.setPositiveButton("确定", null) // Set a positive button with text '确定' (Chinese: OK), action is null so it just dismisses the dialog
        val dialog = builder.create() // Create the HeyDialog instance
        dialog.show() // Show the dialog
    }
}