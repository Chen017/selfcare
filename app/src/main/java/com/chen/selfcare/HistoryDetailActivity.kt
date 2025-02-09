package com.chen.selfcare

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.heytap.wearable.support.widget.HeyMainTitleBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * HistoryDetailActivity: Displays detailed information for a selected history record.
 * Shows duration, average frequency, max frequency, and cycle count of a past exercise session.
 * Implements swipe-back gesture navigation.
 */
class HistoryDetailActivity : ComponentActivity(), GestureDetector.OnGestureListener {

    private lateinit var gestureDetector: GestureDetector // Detects swipe gestures for navigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        // Initialize HeyMainTitleBar from layout
        val titleBar = findViewById<HeyMainTitleBar>(R.id.history_detail_title_bar)
        titleBar.setTitle(getString(R.string.history_detail_title)) // 从 strings.xml 中获取标题文本

        // Initialize TextViews for displaying details from layout
        val durationTextView = findViewById<TextView>(R.id.detailDurationTextView)
        val avgFrequencyTextView = findViewById<TextView>(R.id.detailAvgFrequencyTextView)
        val maxFrequencyTextView = findViewById<TextView>(R.id.detailMaxFrequencyTextView)
        val cycleCountTextView = findViewById<TextView>(R.id.detailCycleCountTextView)
        // Initialize TextViews for heart rate display
        val detailAvgHeartRateTextView = findViewById<TextView>(R.id.detailAvgHeartRateTextView)
        val detailMaxHeartRateTextView = findViewById<TextView>(R.id.detailMaxHeartRateTextView)

        // Retrieve data from Intent extras, passed from HistoryActivity
        val durationMillis = intent.getLongExtra("DURATION", 0L) // 获取会话时长（毫秒）
        val avgFrequency = intent.getDoubleExtra("AVG_FREQUENCY", 0.0) // 获取平均频率
        val maxFrequency = intent.getDoubleExtra("MAX_FREQUENCY", 0.0) // 获取最大频率
        val cycleCount = intent.getIntExtra("CYCLE_COUNT", 0) // 获取次数
        val durationFormatted = formatDuration(durationMillis) // 将时长格式化为 MM:SS
        // 获取心率数据
        val averageHeartRate = intent.getDoubleExtra("averageHeartRate", 0.0)
        val maxHeartRate = intent.getFloatExtra("maxHeartRate", 0f)

        // 使用资源字符串及占位符设置 TextView 文本，避免直接拼接字符串
        durationTextView.text = getString(R.string.history_duration, durationFormatted)
        avgFrequencyTextView.text = getString(R.string.history_avg_frequency, avgFrequency)
        maxFrequencyTextView.text = getString(R.string.history_max_frequency, maxFrequency)
        cycleCountTextView.text = getString(R.string.history_cycle_count, cycleCount)
        detailAvgHeartRateTextView.text = getString(R.string.history_avg_heart_rate, averageHeartRate)
        detailMaxHeartRateTextView.text = getString(R.string.history_max_heart_rate, maxHeartRate)

        // Initialize GestureDetector for handling swipe gestures
        gestureDetector = GestureDetector(this, this)
    }

    /**
     * Formats duration in milliseconds to MM:SS format.
     * @param durationMillis Duration in milliseconds.
     * @return Formatted duration string in MM:SS format.
     */
    private fun formatDuration(durationMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds) // 使用默认 Locale 格式化
    }

    @Suppress("unused")
    private fun formatTimestamp(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
    }

    /**
     * Overrides onTouchEvent to pass touch events to the GestureDetector for swipe back functionality.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            if (gestureDetector.onTouchEvent(it)) {
                return true // Gesture handled
            }
        }
        return super.onTouchEvent(event)
    }

    // GestureDetector.OnGestureListener implementation for swipe back gesture

    override fun onDown(e: MotionEvent): Boolean = false // Not used

    override fun onShowPress(e: MotionEvent) { /* Not used */ }

    override fun onSingleTapUp(e: MotionEvent): Boolean = false // Not used

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean = false // Not used

    override fun onLongPress(e: MotionEvent) { /* Not used */ }

    /**
     * Handles fling gestures for swipe back functionality.
     * Finishes the activity if a right fling is detected.
     */
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 != null) {
            val diffX = e2.x - e1.x // 水平距离
            val diffY = e2.y - e1.y // 垂直距离
            if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && diffX > 100 && kotlin.math.abs(velocityX) > 100) {
                finish() // 结束当前 Activity
                return true
            }
        }
        return false
    }
}
