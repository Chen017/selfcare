package com.chen.selfcare

data class HistoryRecord(
    val duration: Long, // Duration in milliseconds
    val averageFrequency: Double,
    val maxFrequency: Double,
    val cycleCount: Int,
    val startTime: Long, // Timestamp in milliseconds
    val endTime: Long, // Timestamp in milliseconds
    val averageHeartRate: Double, // Average heart rate
    val maxHeartRate: Float // Max heart rate
)