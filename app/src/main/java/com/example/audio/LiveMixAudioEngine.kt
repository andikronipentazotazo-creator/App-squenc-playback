package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import com.example.model.ChannelType
import com.example.model.MeterLevels
import com.example.model.MixerChannelState
import com.example.model.OutputRouteOption
import com.example.model.SongConfig
import com.example.model.SongSectionType
import com.example.model.TransportState
import com.example.model.VoiceCueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sample-accurate real-time DSP audio mixer & panning engine.
 *
 * All channels (Backing Track, Metronome Click, Voice Cue, Count-In, MIDI Synth,
 * Additional Tracks) share one master sampleFrameCounter so changing pan, volume,
 * mute, solo, or live presets in real time never causes sync drift or audio glitches.
 */
class LiveMixAudioEngine(
    context: Context,
    private val deviceRouter: AudioDeviceRouter
) {
    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        .coerceIn(44100, 48000)

    val voiceCueSynthesizer = VoiceCueSynthesizer(context, sampleRate)
    private val metronomeBank = SoundSynthesizer.buildMetronomeBank(sampleRate)

    private val _songConfigRef = AtomicReference<SongConfig?>(null)
    private val customTrackAudio = ConcurrentHashMap<String, DecodedTrackPcm>()

    private val _transportState = MutableStateFlow(TransportState())
    val transportState: StateFlow<TransportState> = _transportState.asStateFlow()

    private val _meterLevels = MutableStateFlow(MeterLevels())
    val meterLevels: StateFlow<MeterLevels> = _meterLevels.asStateFlow()

    private data class ActiveOneShot(
        val samples: FloatArray,
        var cursor: Int = 0,
        val channelId: String,
        val fixedPanOverride: Float? = null,
        val gain: Float = 1.0f,
        val isCueVoice: Boolean = false
    )

    private val activeOneShots = CopyOnWriteArrayList<ActiveOneShot>()

    @Volatile
    private var isEngineRunning = false

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isCountInActive = false

    @Volatile
    private var countInBeatsRemaining = 0

    @Volatile
    private var sampleFrameCounter: Long = 0L

    @Volatile
    private var countInSampleCounter: Long = 0L

    @Volatile
    private var activeOneShotCuePan: Float? = null

    @Volatile
    private var lastTriggeredCueLabel: String = "Ready"

    // Smooth per-channel DSP parameter state (no zipper noise when sliding pan/volume)
    private val smoothedPan = ConcurrentHashMap<String, Float>()
    private val smoothedGain = ConcurrentHashMap<String, Float>()

    // Oscillator phases for continuous Backing Track & MIDI Synth
    private var bassPhase = 0.0
    private var padPhase1 = 0.0
    private var padPhase2 = 0.0
    private var padPhase3 = 0.0
    private var midiPhase = 0.0
    private var lastPlayedBeatIndex: Long = -1L
    private var lastPlayedCountInBeat: Long = -1L
    private var lastPlayedSubdivision: Long = -1L
    private var clipHoldFrames = 0

    private var audioThread: Thread? = null

    fun updateSongConfig(config: SongConfig) {
        _songConfigRef.set(config)
    }

    fun setCustomTrackAudio(channelId: String, audio: DecodedTrackPcm) {
        customTrackAudio[channelId] = audio
    }

    fun clearCustomTrackAudio(channelId: String) {
        customTrackAudio.remove(channelId)
    }

    fun play(withCountIn: Boolean = false) {
        val cfg = _songConfigRef.get() ?: return
        if (withCountIn) {
            isCountInActive = true
            countInBeatsRemaining = cfg.beatsPerBar
            countInSampleCounter = 0L
            lastPlayedCountInBeat = -1L
        }
        isPlaying = true
        ensureAudioThreadStarted()
    }

    fun pause() {
        isPlaying = false
        isCountInActive = false
        emitTransportState()
    }

    fun stop() {
        isPlaying = false
        isCountInActive = false
        sampleFrameCounter = 0L
        countInSampleCounter = 0L
        lastPlayedBeatIndex = -1L
        lastPlayedCountInBeat = -1L
        lastPlayedSubdivision = -1L
        activeOneShots.clear()
        activeOneShotCuePan = null
        lastTriggeredCueLabel = "Ready"
        emitTransportState()
        _meterLevels.value = MeterLevels()
    }

    fun seekToBar(bar: Int) {
        val cfg = _songConfigRef.get() ?: return
        val clampedBar = bar.coerceIn(1, cfg.totalBars)
        val samplesPerBeat = (sampleRate * 60.0) / cfg.bpm.coerceIn(40, 240)
        val targetBeat = (clampedBar - 1).toLong() * cfg.beatsPerBar.toLong()
        sampleFrameCounter = (targetBeat * samplesPerBeat).toLong()
        lastPlayedBeatIndex = targetBeat - 1
        lastPlayedSubdivision = -1L
        emitTransportState()
    }

    fun auditionMetronomeClick(accent: Boolean = true) {
        val cfg = _songConfigRef.get() ?: return
        val pair = metronomeBank[cfg.selectedMetronomeSound] ?: return
        val samples = if (accent) pair.first else pair.second
        activeOneShots.add(
            ActiveOneShot(
                samples = samples,
                channelId = "metronome",
                gain = 1.0f
            )
        )
        ensureAudioThreadStarted()
    }

    fun auditionVoiceCue(cue: VoiceCueItem) {
        val cfg = _songConfigRef.get() ?: return
        val profile = cfg.cueSoundProfiles.firstOrNull { it.id == cue.soundProfileId }
        val customClip = voiceCueSynthesizer.getCustomProfileClip(cue.soundProfileId)
        val clip = customClip ?: voiceCueSynthesizer.getClip(cue.spokenPhrase, cue.soundProfileId)

        val cueChannel = cfg.channels.firstOrNull { it.id == "voice_cue" }
        val resolvedPan = if (cfg.perCuePanEnabled) {
            (cue.pan + (profile?.pan ?: 0f)).coerceIn(-100f, 100f)
        } else {
            cueChannel?.pan ?: cue.pan
        }
        activeOneShotCuePan = resolvedPan
        lastTriggeredCueLabel = "${cue.label} (${PannerDsp.formatPanLabel(resolvedPan)})"

        activeOneShots.add(
            ActiveOneShot(
                samples = clip,
                channelId = "voice_cue",
                fixedPanOverride = resolvedPan,
                gain = cue.gain,
                isCueVoice = true
            )
        )
        ensureAudioThreadStarted()
        emitTransportState()
    }

    fun triggerCountInAudition() {
        val countPhrases = listOf("One", "Two", "Three", "Four")
        val cfg = _songConfigRef.get() ?: return
        val clip = voiceCueSynthesizer.getClip(countPhrases.first(), "voice_studio")
        val ch = cfg.channels.firstOrNull { it.id == "count_in" }
        activeOneShots.add(
            ActiveOneShot(
                samples = clip,
                channelId = "count_in",
                fixedPanOverride = ch?.pan ?: 0f,
                gain = 1.0f
            )
        )
        ensureAudioThreadStarted()
    }

    private fun ensureAudioThreadStarted() {
        if (isEngineRunning && audioThread?.isAlive == true) return
        isEngineRunning = true
        audioThread = Thread(::runAudioLoop, "CuePanDspThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun runAudioLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (_: Exception) {
        }

        val hardwareCap = deviceRouter.capability.value
        val useQuadOutput = hardwareCap.supportsMultiChannelRouting && hardwareCap.maxOutputChannels >= 4
        val channelMask = if (useQuadOutput) {
            AudioFormat.CHANNEL_OUT_QUAD
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val outChannels = if (useQuadOutput) 4 else 2

        val bufferFrames = 512
        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(bufferFrames * outChannels * 4)

        val audioTrack = buildAudioTrack(channelMask, minBufBytes) ?: run {
            isEngineRunning = false
            return
        }

        deviceRouter.preferredDeviceInfo?.let { dev ->
            try {
                audioTrack.preferredDevice = dev
            } catch (_: Exception) {
            }
        }

        try {
            audioTrack.play()
        } catch (_: Exception) {
            isEngineRunning = false
            audioTrack.release()
            return
        }

        val floatBuffer = FloatArray(bufferFrames * outChannels)
        val smoothingCoeff = 0.008f // ~2.5ms zipper-free smoothing
        var meterEmitCounter = 0

        val peakL = ConcurrentHashMap<String, Float>()
        val peakR = ConcurrentHashMap<String, Float>()

        while (isEngineRunning) {
            val cfg = _songConfigRef.get()
            if (cfg == null) {
                Thread.sleep(20)
                continue
            }

            // If neither playing nor auditioning one-shots, output silence gently or sleep
            if (!isPlaying && activeOneShots.isEmpty()) {
                Thread.sleep(16)
                continue
            }

            floatBuffer.fill(0f)
            val channels = cfg.channels
            val anySolo = channels.any { it.isSolo }

            val samplesPerBeat = (sampleRate * 60.0) / cfg.bpm.coerceIn(40, 240)
            val samplesPerSubdivision = samplesPerBeat / 2.0
            val totalSongBeats = cfg.totalBars * cfg.beatsPerBar
            val totalSongSamples = (totalSongBeats * samplesPerBeat).toLong().coerceAtLeast(1L)

            var masterPeakL = 0f
            var masterPeakR = 0f
            var clippedThisBlock = false

            for (frame in 0 until bufferFrames) {
                var mixL = 0f
                var mixR = 0f
                var mixOut3 = 0f
                var mixOut4 = 0f

                // Advance musical clock when playing
                var currentBarFloat = 1f
                if (isPlaying) {
                    if (isCountInActive) {
                        val beatIdx = (countInSampleCounter / samplesPerBeat).toLong()
                        if (beatIdx != lastPlayedCountInBeat) {
                            lastPlayedCountInBeat = beatIdx
                            val countWords = listOf("One", "Two", "Three", "Four")
                            val phrase = countWords.getOrElse(beatIdx.toInt()) { "${beatIdx + 1}" }
                            val countClip = voiceCueSynthesizer.getClip(phrase, "voice_studio")
                            activeOneShots.add(
                                ActiveOneShot(
                                    samples = countClip,
                                    channelId = "count_in",
                                    gain = 1.0f
                                )
                            )
                            val metroPair = metronomeBank[cfg.selectedMetronomeSound]
                            if (metroPair != null) {
                                activeOneShots.add(
                                    ActiveOneShot(
                                        samples = if (beatIdx == 0L) metroPair.first else metroPair.second,
                                        channelId = "metronome",
                                        gain = 0.85f
                                    )
                                )
                            }
                        }
                        countInSampleCounter++
                        if (countInSampleCounter >= (cfg.beatsPerBar * samplesPerBeat).toLong()) {
                            isCountInActive = false
                            sampleFrameCounter = 0L
                            lastPlayedBeatIndex = -1L
                            lastPlayedSubdivision = -1L
                        }
                    } else {
                        val beatIdx = (sampleFrameCounter / samplesPerBeat).toLong()
                        val subIdx = (sampleFrameCounter / samplesPerSubdivision).toLong()
                        val barIndex = (beatIdx / cfg.beatsPerBar).toInt() + 1
                        val beatInBar = (beatIdx % cfg.beatsPerBar).toInt() + 1
                        currentBarFloat = 1f + (sampleFrameCounter / (samplesPerBeat * cfg.beatsPerBar)).toFloat()

                        if (beatIdx != lastPlayedBeatIndex) {
                            lastPlayedBeatIndex = beatIdx
                            // Trigger Metronome Click
                            val metroPair = metronomeBank[cfg.selectedMetronomeSound]
                            if (metroPair != null) {
                                activeOneShots.add(
                                    ActiveOneShot(
                                        samples = if (beatInBar == 1) metroPair.first else metroPair.second,
                                        channelId = "metronome",
                                        gain = 1.0f
                                    )
                                )
                            }

                            // Trigger Voice Cues matching this Bar & Beat
                            cfg.cues.filter { it.bar == barIndex && it.beat == beatInBar }.forEach { cue ->
                                val profile = cfg.cueSoundProfiles.firstOrNull { it.id == cue.soundProfileId }
                                val clip = voiceCueSynthesizer.getCustomProfileClip(cue.soundProfileId)
                                    ?: voiceCueSynthesizer.getClip(cue.spokenPhrase, cue.soundProfileId)
                                val cueCh = channels.firstOrNull { it.id == "voice_cue" }
                                val resolvedPan = if (cfg.perCuePanEnabled) {
                                    (cue.pan + (profile?.pan ?: 0f)).coerceIn(-100f, 100f)
                                } else {
                                    cueCh?.pan ?: cue.pan
                                }
                                activeOneShotCuePan = resolvedPan
                                lastTriggeredCueLabel = "${cue.label} (${PannerDsp.formatPanLabel(resolvedPan)})"
                                activeOneShots.add(
                                    ActiveOneShot(
                                        samples = clip,
                                        channelId = "voice_cue",
                                        fixedPanOverride = resolvedPan,
                                        gain = cue.gain,
                                        isCueVoice = true
                                    )
                                )
                            }
                        }

                        if (subIdx != lastPlayedSubdivision) {
                            lastPlayedSubdivision = subIdx
                        }

                        sampleFrameCounter++
                        if (sampleFrameCounter >= totalSongSamples) {
                            // Loop cleanly at bar 1
                            sampleFrameCounter = 0L
                            lastPlayedBeatIndex = -1L
                            lastPlayedSubdivision = -1L
                        }
                    }
                }

                // Synthesize / read continuous tracks when playing (and not in count-in)
                val beatProgress = if (isPlaying && !isCountInActive) {
                    (sampleFrameCounter % samplesPerBeat.toLong()).toFloat() / samplesPerBeat.toFloat()
                } else 0f
                val barIndex = if (isPlaying && !isCountInActive) {
                    ((sampleFrameCounter / samplesPerBeat).toInt() / cfg.beatsPerBar) + 1
                } else 1
                val beatInBar = if (isPlaying && !isCountInActive) {
                    ((sampleFrameCounter / samplesPerBeat).toInt() % cfg.beatsPerBar) + 1
                } else 1

                val currentSection = cfg.sections.firstOrNull {
                    barIndex >= it.startBar && barIndex < it.startBar + it.lengthBars
                }?.type ?: SongSectionType.INTRO

                for (ch in channels) {
                    val isAudible = !ch.isMuted && (!anySolo || ch.isSolo)
                    val targetGain = if (isAudible) {
                        PannerDsp.computeEffectiveGain(ch.volumeGain, ch.trimDb)
                    } else 0f

                    val prevGain = smoothedGain[ch.id] ?: targetGain
                    val curGain = prevGain + (targetGain - prevGain) * smoothingCoeff
                    smoothedGain[ch.id] = curGain

                    // Resolve pan (Automation -> Per-Cue / Sound -> Channel Pan)
                    var targetPan = when (ch.type) {
                        ChannelType.METRONOME -> ch.pan
                        ChannelType.VOICE_CUE -> {
                            if (cfg.perCuePanEnabled && activeOneShotCuePan != null) {
                                activeOneShotCuePan!!
                            } else {
                                ch.pan
                            }
                        }
                        else -> ch.pan
                    }

                    if (cfg.automationEnabled && isPlaying && !isCountInActive) {
                        val hasLane = cfg.automationPoints.any { it.targetId == ch.id }
                        if (hasLane) {
                            targetPan = PannerDsp.evaluateAutomationPan(
                                points = cfg.automationPoints,
                                targetId = ch.id,
                                currentBarPosition = currentBarFloat,
                                beatsPerBar = cfg.beatsPerBar,
                                fallbackPan = targetPan
                            )
                        }
                    }

                    val prevPan = smoothedPan[ch.id] ?: targetPan
                    val curPan = prevPan + (targetPan - prevPan) * smoothingCoeff
                    smoothedPan[ch.id] = curPan

                    var chL = 0f
                    var chR = 0f

                    if (curGain > 0.0005f) {
                        // Continuous generators for Backing, MIDI, Additional
                        if (isPlaying && !isCountInActive) {
                            when (ch.type) {
                                ChannelType.BACKING -> {
                                    val custom = customTrackAudio[ch.id]
                                    if (custom != null && custom.left.isNotEmpty()) {
                                        val idx = (sampleFrameCounter % custom.left.size).toInt()
                                        val inL = custom.left[idx] / 32768f
                                        val inR = custom.right[idx] / 32768f
                                        val (pL, pR) = PannerDsp.applyStereoPan(inL, inR, curPan)
                                        chL += pL * curGain
                                        chR += pR * curGain
                                    } else {
                                        val (rawL, rawR) = synthesizeBackingFrame(currentSection, beatInBar, beatProgress)
                                        val (pL, pR) = PannerDsp.applyStereoPan(rawL, rawR, curPan)
                                        chL += pL * curGain
                                        chR += pR * curGain
                                    }
                                }
                                ChannelType.MIDI -> {
                                    val (rawL, rawR) = synthesizeMidiFrame(currentSection, beatInBar, beatProgress)
                                    val (pL, pR) = PannerDsp.applyStereoPan(rawL, rawR, curPan)
                                    chL += pL * curGain
                                    chR += pR * curGain
                                }
                                ChannelType.ADDITIONAL -> {
                                    val custom = customTrackAudio[ch.id]
                                    if (custom != null && custom.left.isNotEmpty()) {
                                        val idx = (sampleFrameCounter % custom.left.size).toInt()
                                        val inL = custom.left[idx] / 32768f
                                        val inR = custom.right[idx] / 32768f
                                        val (pL, pR) = PannerDsp.applyStereoPan(inL, inR, curPan)
                                        chL += pL * curGain
                                        chR += pR * curGain
                                    } else {
                                        val raw = synthesizePercussionFrame(beatProgress)
                                        val (gL, gR) = PannerDsp.computeMonoPanGains(curPan)
                                        chL += raw * gL * curGain
                                        chR += raw * gR * curGain
                                    }
                                }
                                else -> Unit
                            }
                        }

                        // Mix any active one-shot triggers belonging to this channel
                        for (shot in activeOneShots) {
                            if (shot.channelId == ch.id && shot.cursor < shot.samples.size) {
                                val mono = shot.samples[shot.cursor] * shot.gain
                                shot.cursor++
                                val panToUse = if (shot.isCueVoice && cfg.automationEnabled &&
                                    cfg.automationPoints.any { it.targetId == "voice_cue" } && isPlaying
                                ) {
                                    curPan
                                } else {
                                    shot.fixedPanOverride ?: curPan
                                }
                                val (gL, gR) = PannerDsp.computeMonoPanGains(panToUse)
                                chL += mono * gL * curGain
                                chR += mono * gR * curGain
                            }
                        }
                    }

                    // Track per-channel post-fader / post-pan peaks
                    val pL = peakL[ch.id] ?: 0f
                    val pR = peakR[ch.id] ?: 0f
                    if (abs(chL) > pL) peakL[ch.id] = abs(chL).coerceAtMost(1f)
                    if (abs(chR) > pR) peakR[ch.id] = abs(chR).coerceAtMost(1f)

                    // Route to physical outputs
                    when (ch.outputRoute) {
                        OutputRouteOption.STEREO_1_2 -> {
                            mixL += chL
                            mixR += chR
                        }
                        OutputRouteOption.OUT_1 -> {
                            mixL += 0.5f * (chL + chR)
                        }
                        OutputRouteOption.OUT_2 -> {
                            mixR += 0.5f * (chL + chR)
                        }
                        OutputRouteOption.OUT_3 -> {
                            if (useQuadOutput) mixOut3 += 0.5f * (chL + chR)
                            else {
                                mixL += chL
                                mixR += chR
                            }
                        }
                        OutputRouteOption.OUT_4 -> {
                            if (useQuadOutput) mixOut4 += 0.5f * (chL + chR)
                            else {
                                mixL += chL
                                mixR += chR
                            }
                        }
                        OutputRouteOption.OUT_3_4 -> {
                            if (useQuadOutput) {
                                mixOut3 += chL
                                mixOut4 += chR
                            } else {
                                mixL += chL
                                mixR += chR
                            }
                        }
                    }
                }

                // Clean finished one-shots
                if (activeOneShots.isNotEmpty()) {
                    activeOneShots.removeAll { it.cursor >= it.samples.size }
                }

                // Check soft limiter & clip status
                if (abs(mixL) > 0.96f || abs(mixR) > 0.96f) {
                    clippedThisBlock = true
                    clipHoldFrames = 24
                }
                val outL = PannerDsp.softLimit(mixL)
                val outR = PannerDsp.softLimit(mixR)

                if (abs(outL) > masterPeakL) masterPeakL = abs(outL)
                if (abs(outR) > masterPeakR) masterPeakR = abs(outR)

                val baseIdx = frame * outChannels
                floatBuffer[baseIdx] = outL
                floatBuffer[baseIdx + 1] = outR
                if (useQuadOutput) {
                    floatBuffer[baseIdx + 2] = PannerDsp.softLimit(mixOut3)
                    floatBuffer[baseIdx + 3] = PannerDsp.softLimit(mixOut4)
                }
            }

            audioTrack.write(floatBuffer, 0, floatBuffer.size, AudioTrack.WRITE_BLOCKING)

            meterEmitCounter++
            if (meterEmitCounter >= 3) { // ~28 Hz responsive UI meters & playhead
                meterEmitCounter = 0
                if (clipHoldFrames > 0) clipHoldFrames--
                val snapshotPeaks = cfg.channels.associate { ch ->
                    val l = peakL[ch.id] ?: 0f
                    val r = peakR[ch.id] ?: 0f
                    peakL[ch.id] = l * 0.72f
                    peakR[ch.id] = r * 0.72f
                    ch.id to (l to r)
                }
                _meterLevels.value = MeterLevels(
                    channelPeaks = snapshotPeaks,
                    masterLeft = masterPeakL.coerceIn(0f, 1f),
                    masterRight = masterPeakR.coerceIn(0f, 1f),
                    isClipping = clippedThisBlock || clipHoldFrames > 0
                )
                emitTransportState(
                    activeMetronomePan = smoothedPan["metronome"],
                    activeVoiceCuePan = smoothedPan["voice_cue"]
                )
            }
        }

        try {
            audioTrack.stop()
        } catch (_: Exception) {
        }
        audioTrack.release()
    }

    private fun synthesizeBackingFrame(
        section: SongSectionType,
        beatInBar: Int,
        beatProgress: Float
    ): Pair<Float, Float> {
        val rootFreq = when (section) {
            SongSectionType.INTRO -> 130.81 // C3
            SongSectionType.VERSE -> 110.00 // A2
            SongSectionType.CHORUS -> 174.61 // F3
            SongSectionType.BRIDGE -> 196.00 // G3
            SongSectionType.ENDING -> 130.81 // C3
        }
        val kick = if (beatInBar == 1 || beatInBar == 3) {
            val t = beatProgress * 0.45f
            val pitch = 48.0 + 90.0 * exp(-t * 36.0)
            bassPhase += 2.0 * PI * pitch / sampleRate
            (sin(bassPhase) * exp(-t * 14.0) * 0.42).toFloat()
        } else {
            bassPhase += 2.0 * PI * rootFreq / sampleRate
            (sin(bassPhase) * 0.16).toFloat()
        }

        val chord1 = rootFreq * 2.0
        val chord2 = rootFreq * 2.5
        val chord3 = rootFreq * 3.0
        padPhase1 += 2.0 * PI * chord1 / sampleRate
        padPhase2 += 2.0 * PI * chord2 / sampleRate
        padPhase3 += 2.0 * PI * chord3 / sampleRate

        val padL = (sin(padPhase1) * 0.12 + sin(padPhase2) * 0.09).toFloat()
        val padR = (sin(padPhase1) * 0.10 + sin(padPhase3) * 0.11).toFloat()

        return (kick * 0.7f + padL) to (kick * 0.7f + padR)
    }

    private fun synthesizeMidiFrame(
        section: SongSectionType,
        beatInBar: Int,
        beatProgress: Float
    ): Pair<Float, Float> {
        val baseNote = when (section) {
            SongSectionType.INTRO -> 261.63
            SongSectionType.VERSE -> 220.00
            SongSectionType.CHORUS -> 349.23
            SongSectionType.BRIDGE -> 392.00
            SongSectionType.ENDING -> 261.63
        }
        val stepMultiplier = when (((beatProgress * 2f).toInt() + beatInBar) % 4) {
            0 -> 1.0
            1 -> 1.25
            2 -> 1.5
            else -> 2.0
        }
        val freq = baseNote * stepMultiplier
        midiPhase += 2.0 * PI * freq / sampleRate
        val localBeat = (beatProgress * 2f) % 1f
        val env = exp(-localBeat * 5.5f) * 0.22f
        val sig = (sin(midiPhase) * 0.7 + sin(midiPhase * 2.0) * 0.3).toFloat() * env
        return sig * 0.95f to sig * 1.05f
    }

    private fun synthesizePercussionFrame(beatProgress: Float): Float {
        val sub = (beatProgress * 2f) % 1f
        val env = exp(-sub * 28f) * 0.18f
        val pulse = sin(sub * 80f)
        return pulse * env
    }

    private fun emitTransportState(
        activeMetronomePan: Float? = null,
        activeVoiceCuePan: Float? = null
    ) {
        val cfg = _songConfigRef.get() ?: return
        val samplesPerBeat = (sampleRate * 60.0) / cfg.bpm.coerceIn(40, 240)
        val totalBeats = (sampleFrameCounter / samplesPerBeat).toInt()
        val bar = (totalBeats / cfg.beatsPerBar) + 1
        val beat = (totalBeats % cfg.beatsPerBar) + 1
        val barProgress = ((sampleFrameCounter % (samplesPerBeat * cfg.beatsPerBar)) /
            (samplesPerBeat * cfg.beatsPerBar)).toFloat().coerceIn(0f, 1f)

        val section = cfg.sections.firstOrNull {
            bar >= it.startBar && bar < it.startBar + it.lengthBars
        }?.type ?: SongSectionType.INTRO

        val metroPan = activeMetronomePan
            ?: cfg.channels.firstOrNull { it.id == "metronome" }?.pan ?: 0f
        val cuePan = activeVoiceCuePan
            ?: _transportState.value.activeVoiceCuePan

        _transportState.value = TransportState(
            isPlaying = isPlaying,
            isCountInActive = isCountInActive,
            currentBar = bar.coerceIn(1, cfg.totalBars),
            currentBeat = beat,
            barProgressFraction = barProgress,
            activeSection = section,
            lastTriggeredCueLabel = lastTriggeredCueLabel,
            activeMetronomePan = metroPan,
            activeVoiceCuePan = cuePan
        )
    }

    private fun buildAudioTrack(channelMask: Int, bufferBytes: Int): AudioTrack? {
        return try {
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    fun release() {
        isEngineRunning = false
        isPlaying = false
        try {
            audioThread?.join(300)
        } catch (_: Exception) {
        }
        voiceCueSynthesizer.release()
    }
}
