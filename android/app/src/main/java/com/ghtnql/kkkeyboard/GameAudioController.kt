package com.ghtnql.kkkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.ghtnql.kkkeyboard.sharedui.GameAudioSettings
import com.ghtnql.kkkeyboard.sharedui.GameMusicTrack
import com.ghtnql.kkkeyboard.sharedui.GameSoundEffect

internal interface GameAudioEngine {
    fun startMusic(track: GameMusicTrack)
    fun stopMusic()
    fun play(effect: GameSoundEffect)
    fun stopEffects()
    fun release()
    fun activate() {}
}

internal class GameAudioController(
    context: Context,
    private val engine: GameAudioEngine = AndroidGameAudioEngine(context),
) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun migrationDefault(): Boolean {
        return try {
            val old =
                appContext.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
            !old.getBoolean(OLD_KEY_MUTED, false)
        } catch (_: Exception) {
            true
        }
    }

    private var requestedTrack: GameMusicTrack? = null
    private var foreground = true
    private var released = false
    private var musicStarted = false

    fun readSettings(): GameAudioSettings {
        val d = migrationDefault()
        return GameAudioSettings(
            musicEnabled = prefs.getBoolean(KEY_MUSIC, d),
            effectsEnabled = prefs.getBoolean(KEY_EFFECTS, d),
        )
    }

    fun setMusicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC, enabled).apply()
        if (released) return
        if (!enabled) {
            musicStarted = false
            try {
                engine.stopMusic()
            } catch (_: Exception) {
            }
        } else {
            val track = requestedTrack
            if (foreground && track != null) {
                try {
                    engine.startMusic(track)
                    musicStarted = true
                } catch (_: Exception) {
                }
            }
        }
    }

    fun setEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EFFECTS, enabled).apply()
        if (released) return
        if (!enabled) {
            try {
                engine.stopEffects()
            } catch (_: Exception) {
            }
        }
    }

    fun start(track: GameMusicTrack) {
        if (released) return
        val settings = readSettings()
        if (foreground && requestedTrack == track && musicStarted && settings.musicEnabled) return
        requestedTrack = track
        if (!foreground) return
        try {
            engine.activate()
        } catch (_: Exception) {
        }
        if (!settings.musicEnabled) return
        try {
            engine.startMusic(track)
            musicStarted = true
        } catch (_: Exception) {
        }
    }

    fun stop() {
        requestedTrack = null
        musicStarted = false
        if (released) return
        try {
            engine.stopMusic()
        } catch (_: Exception) {
        }
        try {
            engine.stopEffects()
        } catch (_: Exception) {
        }
    }

    fun play(effect: GameSoundEffect) {
        if (released) return
        if (!foreground) return
        if (requestedTrack == null) return
        if (!readSettings().effectsEnabled) return
        try {
            engine.play(effect)
        } catch (_: Exception) {
        }
    }

    fun onPause() {
        foreground = false
        musicStarted = false
        if (released) return
        try {
            engine.stopMusic()
        } catch (_: Exception) {
        }
        try {
            engine.stopEffects()
        } catch (_: Exception) {
        }
    }

    fun onResume() {
        foreground = true
        if (released) return
        try {
            engine.activate()
        } catch (_: Exception) {
        }
        val track = requestedTrack ?: return
        if (!readSettings().musicEnabled) return
        try {
            engine.startMusic(track)
            musicStarted = true
        } catch (_: Exception) {
        }
    }

    fun finish() {
        if (released) return
        val allowed = (requestedTrack != null && foreground && readSettings().effectsEnabled)
        requestedTrack = null
        musicStarted = false
        try {
            engine.stopMusic()
        } catch (_: Exception) {
        }
        try {
            engine.stopEffects()
        } catch (_: Exception) {
        }
        if (allowed) {
            try {
                engine.play(GameSoundEffect.FINISH)
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        requestedTrack = null
        musicStarted = false
        try {
            engine.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        internal const val PREFS_NAME = "game_audio"
        internal const val KEY_MUSIC = "music_enabled"
        internal const val KEY_EFFECTS = "effects_enabled"
        internal const val OLD_PREFS_NAME = "rain_audio"
        internal const val OLD_KEY_MUTED = "muted"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal class AndroidGameAudioEngine(context: Context) : GameAudioEngine,
    AudioManager.OnAudioFocusChangeListener {
    private val appContext = context.applicationContext
    private val lock = Any()

    private var player: MediaPlayer? = null
    private var pool: SoundPool? = null
    private var manager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var nativeTrack: GameMusicTrack? = null
    private var focusBlocked = false
    private var generation = 0
    private var effectFocusGeneration = 0
    private var released = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val soundIds = mutableMapOf<GameSoundEffect, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private val activeStreams = ArrayDeque<Int>()

    init {
        synchronized(lock) {
            try {
                ensurePoolLocked()
            } catch (_: Exception) {
            }
        }
    }

    private fun audioManager(): AudioManager? {
        if (manager == null) {
            try {
                manager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            } catch (_: Exception) {
                manager = null
            }
        }
        return manager
    }

    private fun requestFocus(): Boolean {
        if (released) return false
        return try {
            val mgr = audioManager() ?: return false
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this, Handler(Looper.getMainLooper()))
                .build()
            focusRequest = req
            mgr.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun abandonFocus() {
        try {
            val mgr = manager ?: return
            val req = focusRequest ?: return
            mgr.abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        } finally {
            focusRequest = null
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        synchronized(lock) {
            if (released) return
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusBlocked = false
                    val track = nativeTrack
                    if (track != null) {
                        try {
                            player?.let {
                                if (!it.isPlaying) it.start()
                            } ?: startMusicLocked(track)
                        } catch (_: Exception) {
                        }
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    focusBlocked = true
                    try {
                        player?.let { if (it.isPlaying) it.pause() }
                    } catch (_: Exception) {
                    }
                    try {
                        stopEffectsLocked()
                    } catch (_: Exception) {
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    focusBlocked = true
                    nativeTrack = null
                    generation++
                    try {
                        player?.let {
                            try {
                                it.stop()
                            } catch (_: Exception) {
                            }
                            try {
                                it.reset()
                                it.release()
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                    }
                    player = null
                    try {
                        stopEffectsLocked()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun activate() {
        synchronized(lock) {
            if (released) return
            focusBlocked = false
        }
    }

    override fun startMusic(track: GameMusicTrack) {
        synchronized(lock) {
            if (released) return
            focusBlocked = false
            nativeTrack = track
            if (!requestFocus()) {
                focusBlocked = true
                try {
                    player?.let {
                        try {
                            it.stop()
                        } catch (_: Exception) {
                        }
                        try {
                            it.reset()
                            it.release()
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
                player = null
                return
            }
            focusBlocked = false
            startMusicLocked(track)
        }
    }

    private fun startMusicLocked(track: GameMusicTrack) {
        generation++
        val gen = generation
        try {
            player?.let {
                try {
                    it.stop()
                } catch (_: Exception) {
                }
                try {
                    it.reset()
                    it.release()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        player = null
        if (released || gen != generation) return
        val mp: MediaPlayer
        try {
            mp = MediaPlayer()
        } catch (_: Exception) {
            return
        }
        player = mp
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            mp.setAudioAttributes(attrs)
            mp.isLooping = true
            mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            val fd = appContext.assets.openFd(AUDIO_DIR + track.assetName)
            try {
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            } finally {
                try {
                    fd.close()
                } catch (_: Exception) {
                }
            }
            mp.prepare()
            if (released || gen != generation || focusBlocked) {
                try {
                    mp.release()
                } catch (_: Exception) {
                }
                if (player === mp) player = null
                return
            }
            mp.start()
        } catch (_: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {
            }
            if (player === mp) player = null
        }
    }

    override fun stopMusic() {
        synchronized(lock) {
            nativeTrack = null
            abandonFocus()
            generation++
            effectFocusGeneration++
            try {
                player?.let {
                    try {
                        it.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        it.reset()
                        it.release()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            player = null
        }
    }

    private fun ensurePoolLocked() {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attrs)
            .build()
        val callbackPool = sp
        sp.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(lock) {
                if (released) return@synchronized
                if (pool !== callbackPool) return@synchronized
                if (status == 0) loadedIds.add(sampleId)
            }
        }
        pool = sp
        for (effect in GameSoundEffect.values()) {
            try {
                val fd = appContext.assets.openFd(AUDIO_DIR + effect.assetName)
                try {
                    soundIds[effect] = sp.load(fd, 1)
                } finally {
                    try {
                        fd.close()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun play(effect: GameSoundEffect) {
        synchronized(lock) {
            if (released) return
            if (focusBlocked) return
            try {
                ensurePoolLocked()
                val sp = pool ?: return
                val id = soundIds[effect] ?: return
                if (!loadedIds.contains(id)) return
                if (nativeTrack == null && !requestFocus()) { focusBlocked = true; return }
                val streamId = sp.play(id, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1.0f)
                if (streamId != 0) {
                    activeStreams.addLast(streamId)
                    while (activeStreams.size > MAX_ACTIVE_TRACKED) activeStreams.removeFirst()
                    if (nativeTrack == null) {
                        effectFocusGeneration++
                        val token = effectFocusGeneration
                        mainHandler.postDelayed({
                            synchronized(lock) {
                                if (token == effectFocusGeneration && nativeTrack == null && !released) {
                                    abandonFocus()
                                }
                            }
                        }, 950L)
                    }
                } else if (nativeTrack == null) {
                    abandonFocus()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun stopEffectsLocked() {
        try {
            val sp = pool ?: return
            for (sid in activeStreams) {
                try {
                    sp.stop(sid)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        } finally {
            activeStreams.clear()
        }
    }

    override fun stopEffects() {
        synchronized(lock) {
            if (released) return
            effectFocusGeneration++
            stopEffectsLocked()
            if (nativeTrack == null) abandonFocus()
        }
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            nativeTrack = null
            generation++
            effectFocusGeneration++
            try {
                player?.let {
                    try {
                        it.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        it.release()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            player = null
            try {
                pool?.let {
                    try {
                        it.release()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            pool = null
            soundIds.clear()
            loadedIds.clear()
            activeStreams.clear()
        }
        abandonFocus()
        manager = null
    }

    companion object {
        internal const val AUDIO_DIR = "audio/"
        internal const val MUSIC_VOLUME = 0.18f
        internal const val EFFECT_VOLUME = 0.45f
        internal const val MAX_STREAMS = 4
        internal const val MAX_ACTIVE_TRACKED = 8
    }
}
