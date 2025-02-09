package com.chen.selfcare

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.heytap.wearable.support.widget.HeyShapeButton
import java.util.Locale
import kotlin.math.abs

@SuppressLint("SetTextI18n") // 抑制直接使用字符串拼接设置文本的警告
class BlackModeActivity : ComponentActivity(), SensorEventListener, GestureDetector.OnGestureListener {

    // UI elements for displaying data
    private lateinit var timeTextView: TextView // Displays elapsed time
    private lateinit var countTextView: TextView // Displays cycle count
    private lateinit var frequencyTextView: TextView // Displays frequency of cycles
    private lateinit var heartRateTextView: TextView // Displays real-time heart rate

    private lateinit var stopButton: HeyShapeButton // Button to stop the recording session
    private lateinit var sensorManager: SensorManager // System sensor manager
    private var gyroscopeSensor: Sensor? = null // Gyroscope sensor
    private var heartRateSensor: Sensor? = null // Heart rate sensor

    private var startTime: Long = 0 // Start time of the recording session
    private var cycleCount: Int = 0 // Number of exercise cycles counted
    private var lastCycleTime: Long = 0 // Time of the last cycle detection to prevent overcounting
    private var cycleCountsInLastSecond: Int = 0 // Number of cycles counted in the last second for frequency calculation
    private val handler = Handler(Looper.getMainLooper()) // Handler to update UI from sensor callbacks

    private var isRunning: Boolean = true // Flag to indicate if the recording session is running
    private lateinit var gestureDetector: GestureDetector // GestureDetector for swipe back functionality

    // Global variable to record maximum frequency during the entire session
    private var sessionMaxFrequency = 0.0

    // List to store heart rate records as Pair<采集时间, 心率值>，只记录非 0 的心率数据
    private val heartRateRecords = mutableListOf<Pair<Long, Float>>()
    // 会话结束时间，用于计算最后一条记录的时长
    private var sessionEndTime: Long = 0L

    private val heartRatePermissionRequestCode = 101 // Request code for body sensor permission

    // Runnables for periodic updates using Handler
    private val timeRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateTime()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val frequencyRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateFrequency()
                resetCycleCountsInLastSecond()
                handler.postDelayed(this, 1000)
            }
        }
    }

    /**
     * Called when the activity is first created. Initializes UI, sensors, and starts data recording.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_black_mode)

        // Keep screen on during the activity to prevent screen from dimming and locking
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize TextViews from layout
        timeTextView = findViewById(R.id.timeTextView)
        countTextView = findViewById(R.id.countTextView)
        frequencyTextView = findViewById(R.id.frequencyTextView)
        heartRateTextView = findViewById(R.id.heartRateTextView)

        // Initialize the stop button
        stopButton = findViewById(R.id.stopButton)

        // Get the sensor manager service
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Get the gyroscope and heart rate sensors from the sensor manager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        // Record the start time of the session
        startTime = System.currentTimeMillis()
        // Start the periodic update runnables
        handler.post(timeRunnable)
        handler.post(frequencyRunnable)

        // Set click listener for the stop button to end the recording session
        stopButton.setOnClickListener {
            stopRecording()
        }
        // Initialize GestureDetector for handling swipe gestures
        gestureDetector = GestureDetector(this, this)

        // Check for body sensor permission and request if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), heartRatePermissionRequestCode)
        } else {
            registerHeartRateListener() // Register heart rate listener if permission is already granted
        }
    }

    /**
     * Callback for the result from requesting permissions. Registers heart rate listener if permission is granted.
     */
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == heartRatePermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerHeartRateListener() // Register heart rate listener if permission is granted
            } else {
                // Handle permission denial, display a message in the heart rate TextView
                heartRateTextView.text = "心率: Permission Denied"
            }
        }
    }

    /**
     * Registers the heart rate sensor listener to start receiving heart rate data.
     */
    private fun registerHeartRateListener() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Called when the activity is resumed. Registers sensor listeners if the session is running.
     */
    override fun onResume() {
        super.onResume()
        if (isRunning) {
            gyroscopeSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            // Re-register heart rate listener on resume if permission is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                registerHeartRateListener()
            }
        }
    }

    /**
     * Called when the activity is paused. Unregisters sensor listeners to save battery.
     */
    override fun onPause() {
        super.onPause()
        if (isRunning) {
            sensorManager.unregisterListener(this, gyroscopeSensor)
            sensorManager.unregisterListener(this, heartRateSensor) // Unregister heart rate sensor
        }
    }

    /**
     * Called when sensor values have changed. Handles gyroscope and heart rate sensor events.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRunning) return // Do not process sensor data if recording is not running

        when (event?.sensor?.type) {
            Sensor.TYPE_GYROSCOPE -> {
                // Get gyroscope sensor values for x, y, and z axes
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val threshold = 0.85f // Threshold for detecting significant movement
                // Detect cycle if any axis's rotation speed exceeds the threshold
                if (abs(x) > threshold || abs(y) > threshold || abs(z) > threshold) {
                    val currentTime = System.currentTimeMillis()
                    // Prevent counting cycles too rapidly (debounce time of 200ms)
                    if (currentTime - lastCycleTime > 200) {
                        cycleCount++ // Increment cycle count
                        countTextView.text = "次数: $cycleCount" // Update cycle count TextView
                        lastCycleTime = currentTime // Update last cycle time
                        cycleCountsInLastSecond++ // Increment cycle count for frequency calculation
                    }
                }
            }
            Sensor.TYPE_HEART_RATE -> {
                // Get heart rate value from the sensor event
                val heartRate = event.values[0]
                heartRateTextView.text = String.format(Locale.getDefault(), "心率: %.0f bpm", heartRate) // Update heart rate TextView in real-time
                // 只有心率不为 0 时才记录采集时间和心率值
                if (heartRate != 0f) {
                    val currentTime = System.currentTimeMillis()
                    heartRateRecords.add(Pair(currentTime, heartRate))
                }
            }
        }
    }

    /**
     * Called when sensor accuracy changes. Not used in this activity.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    /**
     * Updates the time TextView with the elapsed time since the session started.
     */
    private fun updateTime() {
        val elapsedTime = System.currentTimeMillis() - startTime
        val seconds = (elapsedTime / 1000).toInt()
        val minutes = seconds / 60
        val displaySeconds = seconds % 60
        val timeString = String.format(Locale.getDefault(), "时间: %02d:%02d", minutes, displaySeconds) // Format time as MM:SS

        handler.post {
            timeTextView.text = timeString // Post UI update to the main thread
        }
    }

    /**
     * Updates the frequency TextView with the cycles per second calculated from cycleCountsInLastSecond.
     * Also updates the global sessionMaxFrequency if current frequency is higher.
     */
    private fun updateFrequency() {
        val frequency = cycleCountsInLastSecond.toDouble() // Frequency is cycles in the last second
        // Update global session maximum frequency
        if (frequency > sessionMaxFrequency) {
            sessionMaxFrequency = frequency
        }
        handler.post {
            frequencyTextView.text = String.format(Locale.getDefault(), "频率: %.1f 次/秒", frequency) // Update frequency TextView
        }
    }

    /**
     * Resets the cycle count for the last second to prepare for the next frequency calculation.
     */
    private fun resetCycleCountsInLastSecond() {
        cycleCountsInLastSecond = 0 // Reset counter
    }

    /**
     * Stops the recording session, unregisters sensor listeners, calculates session statistics, and saves history.
     */
    private fun stopRecording() {
        isRunning = false // Set running flag to false
        handler.removeCallbacks(timeRunnable)
        handler.removeCallbacks(frequencyRunnable)
        sensorManager.unregisterListener(this, gyroscopeSensor) // Unregister gyroscope sensor
        sensorManager.unregisterListener(this, heartRateSensor) // Unregister heart rate sensor

        stopButton.isEnabled = false // Disable stop button after session ends
        stopButton.text = "已结束" // Change button text to indicate session end

        val endTime = System.currentTimeMillis() // Record end time
        sessionEndTime = endTime // 记录会话结束时间，用于心率时长计算
        val durationMillis = endTime - startTime // Calculate session duration in milliseconds
        val durationSeconds = durationMillis / 1000.0
        val averageFrequency = if (durationSeconds > 0) cycleCount.toDouble() / durationSeconds else 0.0 // Calculate average frequency
        val maxFrequency = calculateMaxFrequency() // Calculate max frequency over the session
        val averageHeartRate = calculateAverageHeartRate() // Calculate average heart rate
        val maxHeartRate = calculateMaxHeartRate() // Calculate max heart rate

        // Create a HistoryRecord object to store session data
        val historyRecord = HistoryRecord(
            duration = durationMillis,
            averageFrequency = averageFrequency,
            maxFrequency = maxFrequency,
            cycleCount = cycleCount,
            startTime = startTime,
            endTime = endTime,
            averageHeartRate = averageHeartRate,
            maxHeartRate = maxHeartRate
        )

        saveHistoryRecord(this, historyRecord) // Save the history record to SharedPreferences
    }

    /**
     * Calculates the maximum frequency recorded during the entire session.
     */
    private fun calculateMaxFrequency(): Double {
        return sessionMaxFrequency
    }

    /**
     * Calculates the average heart rate over the session duration.
     * 平均心率计算公式为不为0的心率值之和除以心率值不为0的时间长度
     */
    private fun calculateAverageHeartRate(): Double {
        if (heartRateRecords.isEmpty()) return 0.0

        var totalWeightedHR = 0.0  // 累计“心率值×持续时长”的和
        var totalDuration = 0L     // 有效非0心率的总时长（毫秒）

        // 遍历除最后一条记录外的所有记录
        for (i in 0 until heartRateRecords.size - 1) {
            val (time, hr) = heartRateRecords[i]
            val nextTime = heartRateRecords[i + 1].first
            val duration = nextTime - time
            totalWeightedHR += hr * duration
            totalDuration += duration
        }

        // 处理最后一条记录，使用会话结束时间与最后一次记录时间的差值作为持续时长
        val lastRecord = heartRateRecords.last()
        val lastDuration = sessionEndTime - lastRecord.first
        if (lastDuration > 0) {
            totalWeightedHR += lastRecord.second * lastDuration
            totalDuration += lastDuration
        }

        return if (totalDuration > 0) totalWeightedHR / totalDuration else 0.0
    }

    /**
     * Calculates the maximum heart rate recorded during the session.
     */
    private fun calculateMaxHeartRate(): Float {
        return heartRateRecords.maxOfOrNull { it.second } ?: 0f
    }

    /**
     * Overrides onTouchEvent to pass touch events to the GestureDetector for swipe back functionality.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            if (gestureDetector.onTouchEvent(it)) {
                return true // Processed by gesture detector
            }
        }
        return super.onTouchEvent(event) // Default touch event handling
    }

    // GestureDetector.OnGestureListener implementation for swipe back gesture

    /**
     * Not used in this implementation of OnGestureListener.
     */
    override fun onDown(e: MotionEvent): Boolean {
        return false // Not handling 'down' gestures
    }

    /**
     * Not used in this implementation of OnGestureListener.
     */
    override fun onShowPress(e: MotionEvent) {
        // Not used
    }

    /**
     * Not used in this implementation of OnGestureListener.
     */
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false // Not handling 'single tap up' gestures
    }

    /**
     * Not used in this implementation of OnGestureListener.
     */
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false // Not handling 'scroll' gestures
    }

    /**
     * Not used in this implementation of OnGestureListener.
     */
    override fun onLongPress(e: MotionEvent) {
        // Not used
    }

    /**
     * Handles fling gestures for swipe back functionality. Finishes the activity if a right fling is detected.
     */
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 != null) {
            val diffX = e2.x - e1.x // Difference in X for fling gesture
            val diffY = e2.y - e1.y // Difference in Y for fling gesture

            // Check if it's a right fling with sufficient distance and velocity
            if (abs(diffX) > abs(diffY) && diffX > 100 && abs(velocityX) > 100) {
                finish() // Finish activity for swipe back
                return true // Gesture is handled
            }
        }
        return false // Gesture not handled
    }

    /**
     * Companion object for BlackModeActivity. Contains constants and shared utility functions for history management.
     */
    companion object {
        private const val PREFS_NAME = "HistoryPrefs" // Name of the SharedPreferences file for history
        private const val HISTORY_RECORDS_KEY = "history_records" // Key for storing history records in SharedPreferences
        private val gson = Gson() // Gson instance for JSON serialization/deserialization

        /**
         * Saves a HistoryRecord object to SharedPreferences.
         * @param context The context to access SharedPreferences.
         * @param record The HistoryRecord object to save.
         */
        fun saveHistoryRecord(context: Context, record: HistoryRecord) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) // Get SharedPreferences instance
            val historyList = loadHistoryRecords(context).toMutableList() // Load existing history records
            historyList.add(record) // Add the new record to the list
            val historyJson = gson.toJson(historyList) // Convert the list of history records to JSON string
            prefs.edit().putString(HISTORY_RECORDS_KEY, historyJson).apply() // Save the JSON string to SharedPreferences
        }

        /**
         * Loads history records from SharedPreferences.
         * @param context The context to access SharedPreferences.
         * @return A list of HistoryRecord objects loaded from SharedPreferences, or an empty list if no history is found.
         */
        fun loadHistoryRecords(context: Context): List<HistoryRecord> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) // Get SharedPreferences instance
            val historyJson = prefs.getString(HISTORY_RECORDS_KEY, null) // Get the JSON string of history records, null if not exists
            if (historyJson.isNullOrEmpty()) {
                return emptyList() // Return empty list if no history data is found
            } else {
                val type = object : TypeToken<List<HistoryRecord>>() {}.type // Define the list type for Gson deserialization
                return gson.fromJson(historyJson, type) ?: emptyList() // Deserialize JSON to list of HistoryRecord, or empty list if deserialization fails
            }
        }
    }
}
