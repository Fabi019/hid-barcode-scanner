package dev.fabik.bluetoothhid.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LatencyTrace(bufferSize: Int) {
    private var buffer = BoundedList(bufferSize)

    private var lastTimestamp = 0L
    private var lastFPSmeasurment = 0L

    private var currentFps = 0
    private var fpsCount = 0

    private val _currentState = MutableStateFlow<State?>(null)
    val state = _currentState.asStateFlow()

    fun trigger() {
        val now = System.currentTimeMillis()
        val latency = now - lastTimestamp
        lastTimestamp = now

        buffer.addLast(latency.toFloat())

        if (now - lastFPSmeasurment > 1000) {
            lastFPSmeasurment = now
            currentFps = fpsCount
            fpsCount = 0
        } else {
            fpsCount++
        }

        _currentState.update {
            State(currentFps, latency, buffer)
        }
    }

    data class State(val currentFps: Int, val currentLatency: Long, val history: BoundedList)

    inner class BoundedList(val maxSize: Int) : Iterable<Float> {
        var internalArray = FloatArray(maxSize) { Float.NaN }
            private set
        private var tail = 0
        private var head: Int? = null

        fun addLast(element: Float) {
            if (head == null) {
                head = tail
            } else if (head == tail) {
                head = (head!! + 1) % maxSize
            }
            internalArray[tail] = element
            tail = (tail + 1) % maxSize
        }

        override fun iterator() = object : Iterator<Float> {
            private val current = head ?: 0
            private var count = 0 // Keeps track of iterated elements

            override fun hasNext() =
                count < maxSize && !internalArray[(current + count) % maxSize].isNaN()

            override fun next(): Float = internalArray[(current + count++) % maxSize]
        }
    }
}

