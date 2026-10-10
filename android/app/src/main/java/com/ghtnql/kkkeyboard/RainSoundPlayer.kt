package com.ghtnql.kkkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class RainSoundPlayer(context: Context) {
    private data class Note(val frequency: Double, val durationMs: Int, val gain: Double)

    private val preferences = context.getSharedPreferences("rain_audio", Context.MODE_PRIVATE)
    private val tracks = mutableMapOf<String, AudioTrack>()
    var isMuted: Boolean = preferences.getBoolean("muted", false)
        private set

    init {
        createTone("start", listOf(Note(587.33, 75, 0.18), Note(783.99, 110, 0.2)))
        createTone("hit", listOf(Note(880.0, 55, 0.2), Note(1174.66, 100, 0.18)))
        createTone("combo", listOf(Note(1046.5, 55, 0.2), Note(1396.91, 110, 0.2)))
        createTone("error", listOf(Note(196.0, 130, 0.17)))
        createTone("splash", listOf(Note(130.81, 80, 0.12), Note(98.0, 130, 0.1)))
        createTone("finish", listOf(Note(659.25, 85, 0.18), Note(783.99, 85, 0.18), Note(1046.5, 150, 0.2)))
    }

    fun toggleMuted(): Boolean {
        isMuted = !isMuted
        preferences.edit().putBoolean("muted", isMuted).apply()
        if (isMuted) pauseAll()
        return isMuted
    }

    fun playStart() = play("start")
    fun playHit(combo: Int) = play(if (combo >= 5) "combo" else "hit")
    fun playError() = play("error")
    fun playSplash() = play("splash")
    fun playFinish() = play("finish")

    fun pauseAll() {
        tracks.values.forEach { track -> runCatching { track.pause() } }
    }

    fun release() {
        tracks.values.forEach { track -> runCatching { track.release() } }
        tracks.clear()
    }

    private fun play(name: String) {
        if (isMuted) return
        val track = tracks[name] ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        }
    }

    private fun createTone(name: String, notes: List<Note>) {
        val sampleRate = 22_050
        val sampleCount = notes.sumOf { it.durationMs * sampleRate / 1000 }
        val samples = ShortArray(sampleCount)
        var offset = 0
        notes.forEach { note ->
            val noteSamples = note.durationMs * sampleRate / 1000
            for (index in 0 until noteSamples) {
                val progress = index.toDouble() / noteSamples.coerceAtLeast(1)
                val envelope = minOf(progress / 0.08, (1.0 - progress) / 0.28, 1.0).coerceAtLeast(0.0)
                val wave = sin(2.0 * PI * note.frequency * index / sampleRate)
                samples[offset + index] = (wave * envelope * note.gain * Short.MAX_VALUE).toInt().toShort()
            }
            offset += noteSamples
        }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
                .also { it.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) }
        }.getOrNull()
        if (track != null) tracks[name] = track
    }
}
