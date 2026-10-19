package dev.fabik.bluetoothhid.utils

/**
 * Remembers which codes were seen recently, so a code that briefly drops out of
 * detection (motion blur, leaving the scan area) is not treated as new when it
 * reappears. A code is forgotten once it has not been seen for [graceMs].
 */
class RecentCodes {
    private val lastSeen = mutableMapOf<String, Long>()

    fun markSeen(values: Collection<String>, now: Long, graceMs: Long): Set<String> {
        expire(now, graceMs)
        val unseen = values.filterTo(mutableSetOf()) { it !in lastSeen }
        values.forEach { lastSeen[it] = now }
        return unseen
    }

    fun expire(now: Long, graceMs: Long) {
        lastSeen.values.removeAll { now - it > graceMs }
    }

    fun clear() = lastSeen.clear()
}
