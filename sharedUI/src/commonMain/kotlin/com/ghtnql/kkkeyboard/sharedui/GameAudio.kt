package com.ghtnql.kkkeyboard.sharedui

/** Game audio contract shared by Android/iOS hosts and common gameplay UI. */
enum class GameMusicTrack(val assetName: String) {
    PRACTICE("practice_loop.wav"),
    RAIN("rain_loop.wav"),
    CAFE("cafe_loop.wav"),
}

enum class GameSoundEffect(val assetName: String) {
    START("start.wav"),
    HIT("hit.wav"),
    ERROR("error.wav"),
    COMBO("combo.wav"),
    MISS("miss.wav"),
    FINISH("finish.wav"),
}

data class GameAudioSettings(val musicEnabled: Boolean = true, val effectsEnabled: Boolean = true)
