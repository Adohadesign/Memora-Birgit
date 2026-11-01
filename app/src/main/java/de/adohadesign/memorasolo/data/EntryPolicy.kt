package de.adohadesign.memorasolo.data

/** Business rules from the user-approved concept. Kept separate for JVM tests. */
object EntryPolicy {
    const val INTERRUPTION_LIMIT_MS: Long = 10 * 60 * 1000L
    fun shouldClose(backgroundSinceMs: Long, nowMs: Long): Boolean =
        backgroundSinceMs == 0L || nowMs < backgroundSinceMs || nowMs - backgroundSinceMs >= INTERRUPTION_LIMIT_MS

    /** A started entry keeps its original date even if capture ends after midnight. */
    fun assignedDate(startDate: String, completionDate: String): String = startDate
}
