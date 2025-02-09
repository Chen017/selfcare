package com.chen.selfcare

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import com.heytap.wearable.support.widget.HeyMainTitleBar
import com.heytap.wearable.support.widget.HeySingleDefaultItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HistoryActivity: Displays a list of exercise history records.
 * Allows users to view past session summaries and navigate to detailed views.
 * Implements swipe-back gesture navigation.
 */
class HistoryActivity : ComponentActivity(), GestureDetector.OnGestureListener {

    private lateinit var historyRecyclerView: com.heytap.wearable.support.recycler.widget.RecyclerView // RecyclerView to display history items
    private lateinit var historyAdapter: HistoryAdapter // Adapter for the RecyclerView
    private lateinit var gestureDetector: GestureDetector // Detects swipe gestures for navigation

    /**
     * Called when the activity is first created. Initializes UI, RecyclerView, adapter, and gesture detection.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Initialize RecyclerView from layout
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        // Set LinearLayoutManager for RecyclerView to display items in a vertical list
        historyRecyclerView.layoutManager = com.heytap.wearable.support.recycler.widget.LinearLayoutManager(this)
        historyRecyclerView.setBottomAlignEnable(false) // Disable bottom alignment effect (optional - set to false as per request)

        // Load history records from SharedPreferences
        val historyRecords = BlackModeActivity.loadHistoryRecords(this)
        // Reverse the history records list to display latest records at the top
        val reversedHistoryRecords = historyRecords.reversed()
        // Initialize HistoryAdapter with reversed history records and item click listener
        historyAdapter = HistoryAdapter(reversedHistoryRecords) { record ->
            navigateToDetailActivity(record) // Navigate to HistoryDetailActivity on item click
        }
        // Set the adapter to the RecyclerView
        historyRecyclerView.adapter = historyAdapter

        // Initialize HeyMainTitleBar from layout
        val titleBar = findViewById<HeyMainTitleBar>(R.id.history_title_bar)
        titleBar.setTitle(getString(R.string.history_title)) // Set title bar text from strings.xml
        // titleBar.setHeyShowClock(true) - Clock display is controlled by XML, no need to set here

        // Initialize GestureDetector for handling swipe gestures
        gestureDetector = GestureDetector(this, this)
    }

    /**
     * Navigates to HistoryDetailActivity to display detailed information for a selected history record.
     * @param record The HistoryRecord object containing data to display in detail.
     */
    private fun navigateToDetailActivity(record: HistoryRecord) {
        val intent = Intent(this, HistoryDetailActivity::class.java)
        // Pass history record data to HistoryDetailActivity via Intent extras
        intent.putExtra("DURATION", record.duration)
        intent.putExtra("AVG_FREQUENCY", record.averageFrequency)
        intent.putExtra("MAX_FREQUENCY", record.maxFrequency)
        intent.putExtra("CYCLE_COUNT", record.cycleCount)
        intent.putExtra("averageHeartRate", record.averageHeartRate) // Pass average heart rate
        intent.putExtra("maxHeartRate", record.maxHeartRate)       // Pass max heart rate
        startActivity(intent) // Start HistoryDetailActivity
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

    override fun onDown(e: MotionEvent): Boolean { return false } // Not used
    override fun onShowPress(e: MotionEvent) {} // Not used
    override fun onSingleTapUp(e: MotionEvent): Boolean { return false } // Not used
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean { return false } // Not used
    override fun onLongPress(e: MotionEvent) {} // Not used

    /**
     * Handles fling gestures for swipe back functionality. Finishes the activity if a right fling is detected.
     */
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 != null && e2 != null) {
            val diffX = e2.x - e1.x // Horizontal distance of fling gesture
            val diffY = e2.y - e1.y // Vertical distance of fling gesture
            // Check if it's a right fling with sufficient distance and velocity for swipe back
            if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && diffX > 100 && kotlin.math.abs(velocityX) > 100) {
                finish() // Finish HistoryActivity, navigating back
                return true // Gesture is handled
            }
        }
        return false // Gesture not handled
    }
}

/**
 * HistoryAdapter: Adapter for RecyclerView to display list of HistoryRecord items.
 * Binds HistoryRecord data to HeySingleDefaultItem views for display in HistoryActivity.
 */
class HistoryAdapter(private val historyList: List<HistoryRecord>, private val onItemClick: (HistoryRecord) -> Unit) :
    com.heytap.wearable.support.recycler.widget.RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    /**
     * ViewHolder class to hold the view for each history item in the RecyclerView.
     */
    class ViewHolder(itemView: View) : com.heytap.wearable.support.recycler.widget.RecyclerView.ViewHolder(itemView) {
        val historyItemView: HeySingleDefaultItem = itemView.findViewById(R.id.history_list_item) // HeySingleDefaultItem for displaying history item
    }

    /**
     * Called when RecyclerView needs a new ViewHolder to represent an item.
     * Creates a new ViewHolder by inflating the item layout.
     */
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        // Inflate the history_list_item_layout.xml layout for each item
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_list_item_layout, parent, false)
        return ViewHolder(itemView) // Return new ViewHolder instance
    }

    /**
     * Called by RecyclerView to display data at the specified position.
     * Binds HistoryRecord data to the HeySingleDefaultItem view in ViewHolder.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val historyRecord = historyList[position] // Get HistoryRecord for the current position
        // Format the start time of the history record to a readable date and time string
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(historyRecord.startTime))
        holder.historyItemView.setTitle(formattedDate) // Set the formatted date as the title of HeySingleDefaultItem
        holder.itemView.setOnClickListener {
            onItemClick(historyRecord) // Set click listener to handle item clicks, invoking the onItemClick lambda
        }
    }

    /**
     * Returns the total number of items in the history list.
     */
    override fun getItemCount() = historyList.size
}