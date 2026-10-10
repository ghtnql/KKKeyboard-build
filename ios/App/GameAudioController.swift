import AVFAudio
import UIKit
import SharedUI

/// App-only game audio (BGM + SFX). Never used from an extension or
/// RequestsOpenAccess change; lives in the main app target only.
final class GameAudioController: NSObject, AVAudioPlayerDelegate {
    private static let musicEnabledKey = "game_audio.music_enabled"
    private static let effectsEnabledKey = "game_audio.effects_enabled"
    private static let musicVolume: Float = 0.18
    private static let effectsVolume: Float = 0.45

    private let defaults = UserDefaults.standard
    private let session = AVAudioSession.sharedInstance()

    private var musicPlayer: AVAudioPlayer?
    private var effectPlayers: [String: AVAudioPlayer] = [:]

    /// Track requested via start(track:) while a game is active.
    /// Retained across background/interruption so foreground return can resume.
    /// Cleared only by stop() / dispose().
    private var requestedTrack: SharedUI.GameMusicTrack?
    private var isGameActive = false
    private var isForeground = UIApplication.shared.applicationState == .active
    private var isInterrupted = false
    /// Set when the output route was unplugged (oldDeviceUnavailable).
    /// Blocks auto-resume until the user explicitly starts/toggles music.
    private var needsUserResume = false
    private var disposed = false

    private var notificationTokens: [NSObjectProtocol] = []

    override init() {
        super.init()
        defaults.register(defaults: [
            Self.musicEnabledKey: true,
            Self.effectsEnabledKey: true,
        ])
        let center = NotificationCenter.default
        notificationTokens.append(center.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in self?.handleResignActive() })
        notificationTokens.append(center.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in self?.handleBecomeActive() })
        notificationTokens.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: nil,
            queue: .main
        ) { [weak self] note in self?.handleInterruption(note) })
        notificationTokens.append(center.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] note in self?.handleRouteChange(note) })
    }

    // MARK: - Settings contract

    func readSettings() -> SharedUI.GameAudioSettings {
        SharedUI.GameAudioSettings(
            musicEnabled: isMusicEnabled,
            effectsEnabled: isEffectsEnabled
        )
    }

    func setMusicEnabled(enabled: Bool) {
        runOnMain { [self] in
            guard !disposed else { return }
            defaults.set(enabled, forKey: Self.musicEnabledKey)
            needsUserResume = false
            if enabled {
                resumeMusicIfAllowed()
            } else {
                stopMusic()
                relaxSessionIfIdle()
            }
        }
    }

    func setEffectsEnabled(enabled: Bool) {
        runOnMain { [self] in
            guard !disposed else { return }
            defaults.set(enabled, forKey: Self.effectsEnabledKey)
            if !enabled {
                stopAllEffects()
                relaxSessionIfIdle()
            }
        }
    }

    // MARK: - Playback contract

    func start(track: SharedUI.GameMusicTrack) {
        runOnMain { [self] in
            guard !disposed else { return }
            isGameActive = true
            requestedTrack = track
            needsUserResume = false
            startMusicTrack(track)
        }
    }

    func finish() {
        runOnMain { [self] in
            guard !disposed else { return }
            let allowCue = isGameActive && isForeground && !isInterrupted && !needsUserResume && isEffectsEnabled
            stopMusic()
            stopAllEffects()
            if allowCue {
                play(effect: SharedUI.GameSoundEffect.finish)
            }
            isGameActive = false
            requestedTrack = nil
            relaxSessionIfIdle()
        }
    }

    func stop() {
        runOnMain { [self] in
            guard !disposed else { return }
            isGameActive = false
            requestedTrack = nil
            needsUserResume = false
            stopMusic()
            stopAllEffects()
            relaxSessionIfIdle()
        }
    }

    func play(effect: SharedUI.GameSoundEffect) {
        runOnMain { [self] in
            guard !disposed else { return }
            guard isGameActive else { return }
            guard isForeground else { return }
            guard !isInterrupted else { return }
            guard !needsUserResume else { return }
            guard isEffectsEnabled else { return }
            guard let player = effectPlayer(for: effect.assetName) else { return }
            guard ensureSessionForPlayback() else { return }
            // Restart so rapid repeats retrigger instead of overlapping.
            player.currentTime = 0
            player.play()
        }
    }

    func dispose() {
        runOnMain { [self] in
            guard !disposed else { return }
            disposed = true
            isGameActive = false
            requestedTrack = nil
            stopMusic()
            stopAllEffects()
            removeObservers()
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    deinit {
        removeObservers()
    }

    // MARK: - AVAudioPlayerDelegate

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        guard !disposed else { return }
        relaxSessionIfIdle()
    }

    // MARK: - Settings helpers

    private var isMusicEnabled: Bool {
        defaults.object(forKey: Self.musicEnabledKey) as? Bool ?? true
    }

    private var isEffectsEnabled: Bool {
        defaults.object(forKey: Self.effectsEnabledKey) as? Bool ?? true
    }

    // MARK: - Music

    private func startMusicTrack(_ track: SharedUI.GameMusicTrack) {
        // Idempotent: same track already looping stays untouched (no overlap).
        if let current = musicPlayer, current.isPlaying,
           requestedTrackForPlayer == track.assetName {
            return
        }
        guard isForeground else { return }
        guard !isInterrupted else { return }
        guard !needsUserResume else { return }
        guard isMusicEnabled else { return }
        guard let url = resourceURL(for: track.assetName) else { return }
        // Stop the previous track before creating its replacement so the
        // cached player never overlaps the new one.
        stopMusic()
        guard let player = try? AVAudioPlayer(contentsOf: url) else { return }
        requestedTrackForPlayer = track.assetName
        player.delegate = self
        player.numberOfLoops = -1
        player.volume = Self.musicVolume
        guard ensureSessionForPlayback() else { return }
        musicPlayer = player
        player.play()
    }

    private var requestedTrackForPlayer: String?

    private func resumeMusicIfAllowed() {
        guard isGameActive else { return }
        guard let track = requestedTrack else { return }
        guard isForeground else { return }
        guard !isInterrupted else { return }
        guard !needsUserResume else { return }
        guard isMusicEnabled else { return }
        // Already looping: keep it (idempotent resume).
        if let current = musicPlayer, current.isPlaying { return }
        if musicPlayer != nil, requestedTrackForPlayer == track.assetName {
            guard ensureSessionForPlayback() else { return }
            musicPlayer?.play()
            return
        }
        startMusicTrack(track)
    }

    private func stopMusic() {
        musicPlayer?.stop()
        // Keep musicPlayer cached so a same-track resume restarts instantly.
    }

    // MARK: - Effects

    private func effectPlayer(for assetName: String) -> AVAudioPlayer? {
        if let cached = effectPlayers[assetName] { return cached }
        guard let url = resourceURL(for: assetName) else { return nil }
        guard let player = try? AVAudioPlayer(contentsOf: url) else { return nil }
        player.delegate = self
        player.numberOfLoops = 0
        player.volume = Self.effectsVolume
        player.prepareToPlay()
        effectPlayers[assetName] = player
        return player
    }

    private func stopAllEffects() {
        for player in effectPlayers.values where player.isPlaying {
            player.stop()
            player.currentTime = 0
        }
    }

    // MARK: - Resources

    private func resourceURL(for assetName: String) -> URL? {
        let base = (assetName as NSString).deletingPathExtension
        // Manager later configures the folder resource; WAV files live under audio/.
        return Bundle.main.url(forResource: base, withExtension: "wav", subdirectory: "audio")
    }

    // MARK: - Audio session

    private func ensureSessionForPlayback() -> Bool {
        do {
            // Ambient: mixes with other apps and respects the Silent switch.
            try session.setCategory(.ambient, options: [.mixWithOthers])
            try session.setActive(true)
            return true
        } catch {
            return false
        }
    }

    private func relaxSessionIfIdle() {
        let musicPlaying = musicPlayer?.isPlaying == true
        let effectPlaying = effectPlayers.values.contains(where: \.isPlaying)
        guard !musicPlaying, !effectPlaying else { return }
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: - Foreground / interruption / route

    private func handleResignActive() {
        guard !disposed else { return }
        isForeground = false
        // BGM stays paused for resume; SFX stop/reset since effects never auto-resume.
        musicPlayer?.pause()
        stopAllEffects()
        relaxSessionIfIdle()
    }

    private func handleBecomeActive() {
        guard !disposed else { return }
        isForeground = true
        guard isGameActive else { return }
        guard !isInterrupted else { return }
        guard !needsUserResume else { return }
        resumeMusicIfAllowed()
        // Effects do not auto-resume mid-sample; gameplay retriggers them.
    }

    private func handleInterruption(_ note: Notification) {
        guard !disposed else { return }
        guard let info = note.userInfo,
              let raw = info[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        switch type {
        case .began:
            isInterrupted = true
            musicPlayer?.pause()
            stopAllEffects()
        case .ended:
            let optionsRaw = info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsRaw)
            isInterrupted = false
            guard options.contains(.shouldResume) else { return }
            guard isForeground else { return }
            guard isGameActive else { return }
            guard isMusicEnabled else { return }
            guard !needsUserResume else { return }
            resumeMusicIfAllowed()
        @unknown default:
            break
        }
    }

    private func handleRouteChange(_ note: Notification) {
        guard !disposed else { return }
        guard let info = note.userInfo,
              let raw = info[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: raw) else { return }
        guard reason == .oldDeviceUnavailable else { return }
        // Headphones unplugged: stop audio so it never blasts from the speaker.
        // Do not auto-resume; wait for an explicit start/music toggle.
        needsUserResume = true
        musicPlayer?.pause()
        stopAllEffects()
        relaxSessionIfIdle()
    }

    private func removeObservers() {
        let center = NotificationCenter.default
        for token in notificationTokens {
            center.removeObserver(token)
        }
        notificationTokens.removeAll()
    }

    private func runOnMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread {
            work()
        } else {
            DispatchQueue.main.async(execute: work)
        }
    }
}
