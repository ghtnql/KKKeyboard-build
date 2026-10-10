package com.ghtnql.kkkeyboard

import android.content.Context

data class LearningProgress(
    val totalSessions: Int,
    val bestAccuracy: Int,
    val rainHighScore: Int,
    val totalXp: Int,
)

object LearningProgressStore {
    private const val PREFS = "learning_progress"

    fun read(context: Context): LearningProgress {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LearningProgress(
            totalSessions = prefs.getInt("total_sessions", 0),
            bestAccuracy = prefs.getInt("best_accuracy", 0),
            rainHighScore = prefs.getInt("rain_high_score", 0),
            totalXp = prefs.getInt("total_xp", 0),
        )
    }

    fun recordPractice(context: Context, result: TypingSessionResult) {
        val current = read(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("total_sessions", current.totalSessions + 1)
            .putInt("best_accuracy", maxOf(current.bestAccuracy, result.accuracy))
            .putInt("total_xp", current.totalXp + result.score / 10 + if (result.completed) 20 else 0)
            .apply()
    }

    fun recordRain(context: Context, result: TypingSessionResult) {
        val current = read(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("total_sessions", current.totalSessions + 1)
            .putInt("best_accuracy", maxOf(current.bestAccuracy, result.accuracy))
            .putInt("rain_high_score", maxOf(current.rainHighScore, result.score))
            .putInt("total_xp", current.totalXp + result.score / 10)
            .apply()
    }
}
