package com.example.model

enum class ChannelType {
    BACKING,
    METRONOME,
    VOICE_CUE,
    COUNT_IN,
    MIDI,
    ADDITIONAL
}

enum class MetronomeSoundType(val displayName: String) {
    WOODBLOCK("Woodblock"),
    DIGITAL_SINE("Digital Click"),
    COWBELL("Cowbell"),
    HI_HAT("Crisp Hi-Hat"),
    RIMSHOT("Studio Rimshot")
}

enum class SongSectionType(val displayName: String, val shortCode: String) {
    INTRO("Intro", "INT"),
    VERSE("Verse", "VRS"),
    CHORUS("Chorus", "CHR"),
    BRIDGE("Bridge", "BRG"),
    ENDING("Ending", "END")
}

enum class AutomationCurveType(val displayName: String) {
    LINEAR("Linear Sweep"),
    STEP("Step Jump")
}

enum class OutputRouteOption(
    val displayName: String,
    val shortLabel: String,
    val requiresMultiChannel: Boolean = false
) {
    STEREO_1_2("Output 1-2 (Stereo L/R)", "Out 1-2", false),
    OUT_1("Output 1 (Left Mono)", "Out 1", false),
    OUT_2("Output 2 (Right Mono)", "Out 2", false),
    OUT_3("Output 3 (Dedicated Hardware)", "Out 3", true),
    OUT_4("Output 4 (Dedicated Hardware)", "Out 4", true),
    OUT_3_4("Output 3-4 (Hardware Aux)", "Out 3-4", true)
}

data class MixerChannelState(
    val id: String,
    val name: String,
    val type: ChannelType,
    val volumeGain: Float = 0.85f,
    val pan: Float = 0f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val outputRoute: OutputRouteOption = OutputRouteOption.STEREO_1_2,
    val trimDb: Float = 0f,
    val customAudioName: String? = null,
    val customAudioUri: String? = null
)

data class VoiceCueSoundProfile(
    val id: String,
    val displayName: String,
    val pan: Float = 0f,
    val customWavUri: String? = null
)

data class VoiceCueItem(
    val id: String,
    val label: String,
    val spokenPhrase: String,
    val section: SongSectionType,
    val soundProfileId: String,
    val bar: Int,
    val beat: Int = 1,
    val pan: Float = 0f,
    val gain: Float = 1.0f
)

data class SongSection(
    val id: String,
    val name: String,
    val type: SongSectionType,
    val startBar: Int,
    val lengthBars: Int,
    val defaultCuePan: Float = 0f
)

data class PanAutomationPoint(
    val id: String,
    val targetId: String,
    val bar: Int,
    val beat: Float = 1.0f,
    val panValue: Float = 0f,
    val curveType: AutomationCurveType = AutomationCurveType.LINEAR,
    val linkedCueId: String? = null
)

data class SongConfig(
    val id: String,
    val projectId: String,
    val name: String,
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val totalBars: Int = 24,
    val selectedMetronomeSound: MetronomeSoundType = MetronomeSoundType.WOODBLOCK,
    val metronomePanBySound: Map<MetronomeSoundType, Float> = defaultMetronomePanMap(),
    val perCuePanEnabled: Boolean = true,
    val automationEnabled: Boolean = true,
    val cueSoundProfiles: List<VoiceCueSoundProfile> = defaultCueSoundProfiles(),
    val channels: List<MixerChannelState> = defaultChannels(),
    val sections: List<SongSection> = defaultSections(),
    val cues: List<VoiceCueItem> = defaultCues(),
    val automationPoints: List<PanAutomationPoint> = defaultAutomationPoints()
)

data class ProjectConfig(
    val id: String,
    val name: String,
    val activeSongId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class LiveStereoPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val backingPan: Float,
    val metronomePan: Float,
    val voiceCuePan: Float,
    val countInPan: Float,
    val midiPan: Float,
    val enablePerCuePan: Boolean = false
)

data class AudioHardwareCapability(
    val activeDeviceName: String = "Built-in Speaker",
    val activeDeviceType: String = "Internal Stereo",
    val maxOutputChannels: Int = 2,
    val supportsMultiChannelRouting: Boolean = false,
    val isUsbAudioInterface: Boolean = false,
    val connectedDevices: List<String> = emptyList()
)

data class MeterLevels(
    val channelPeaks: Map<String, Pair<Float, Float>> = emptyMap(),
    val masterLeft: Float = 0f,
    val masterRight: Float = 0f,
    val isClipping: Boolean = false
)

data class TransportState(
    val isPlaying: Boolean = false,
    val isCountInActive: Boolean = false,
    val currentBar: Int = 1,
    val currentBeat: Int = 1,
    val barProgressFraction: Float = 0f,
    val activeSection: SongSectionType = SongSectionType.INTRO,
    val lastTriggeredCueLabel: String = "Ready",
    val activeMetronomePan: Float = -100f,
    val activeVoiceCuePan: Float = 100f
)

fun defaultMetronomePanMap(): Map<MetronomeSoundType, Float> = mapOf(
    MetronomeSoundType.WOODBLOCK to -100f,
    MetronomeSoundType.DIGITAL_SINE to -70f,
    MetronomeSoundType.COWBELL to -100f,
    MetronomeSoundType.HI_HAT to 0f,
    MetronomeSoundType.RIMSHOT to 100f
)

fun defaultCueSoundProfiles(): List<VoiceCueSoundProfile> = listOf(
    VoiceCueSoundProfile("voice_studio", "Studio Female Cue", pan = 0f),
    VoiceCueSoundProfile("voice_stage", "Stage Director Cue", pan = 15f),
    VoiceCueSoundProfile("chime_cue", "Harmonic Bell + Voice", pan = -15f)
)

fun defaultChannels(): List<MixerChannelState> = listOf(
    MixerChannelState(
        id = "backing",
        name = "Backing Track",
        type = ChannelType.BACKING,
        volumeGain = 0.85f,
        pan = 0f,
        outputRoute = OutputRouteOption.STEREO_1_2
    ),
    MixerChannelState(
        id = "metronome",
        name = "Metronome Click",
        type = ChannelType.METRONOME,
        volumeGain = 0.90f,
        pan = -100f,
        outputRoute = OutputRouteOption.STEREO_1_2
    ),
    MixerChannelState(
        id = "voice_cue",
        name = "Voice Cue",
        type = ChannelType.VOICE_CUE,
        volumeGain = 0.92f,
        pan = 100f,
        outputRoute = OutputRouteOption.STEREO_1_2
    ),
    MixerChannelState(
        id = "count_in",
        name = "Count-in Voice",
        type = ChannelType.COUNT_IN,
        volumeGain = 0.88f,
        pan = -70f,
        outputRoute = OutputRouteOption.STEREO_1_2
    ),
    MixerChannelState(
        id = "midi",
        name = "MIDI Synth",
        type = ChannelType.MIDI,
        volumeGain = 0.72f,
        pan = 0f,
        outputRoute = OutputRouteOption.STEREO_1_2
    ),
    MixerChannelState(
        id = "extra_1",
        name = "Percussion Loop",
        type = ChannelType.ADDITIONAL,
        volumeGain = 0.68f,
        pan = 25f,
        outputRoute = OutputRouteOption.STEREO_1_2
    )
)

fun defaultSections(): List<SongSection> = listOf(
    SongSection("sec_intro", "Intro", SongSectionType.INTRO, startBar = 1, lengthBars = 4, defaultCuePan = -100f),
    SongSection("sec_verse", "Verse", SongSectionType.VERSE, startBar = 5, lengthBars = 4, defaultCuePan = -75f),
    SongSection("sec_chorus", "Chorus", SongSectionType.CHORUS, startBar = 9, lengthBars = 8, defaultCuePan = 100f),
    SongSection("sec_bridge", "Bridge", SongSectionType.BRIDGE, startBar = 17, lengthBars = 4, defaultCuePan = 75f),
    SongSection("sec_ending", "Ending", SongSectionType.ENDING, startBar = 21, lengthBars = 4, defaultCuePan = 0f)
)

fun defaultCues(): List<VoiceCueItem> = listOf(
    VoiceCueItem("cue_intro", "Intro Cue", "Intro", SongSectionType.INTRO, "voice_studio", bar = 1, beat = 1, pan = -100f),
    VoiceCueItem("cue_verse", "Verse Cue", "Verse", SongSectionType.VERSE, "voice_studio", bar = 5, beat = 1, pan = -100f),
    VoiceCueItem("cue_chorus", "Chorus Cue", "Chorus", SongSectionType.CHORUS, "voice_stage", bar = 9, beat = 1, pan = 100f),
    VoiceCueItem("cue_bridge", "Bridge Cue", "Bridge", SongSectionType.BRIDGE, "voice_stage", bar = 17, beat = 1, pan = 100f),
    VoiceCueItem("cue_ending", "Ending Cue", "Ending", SongSectionType.ENDING, "chime_cue", bar = 21, beat = 1, pan = 0f)
)

fun defaultAutomationPoints(): List<PanAutomationPoint> = listOf(
    PanAutomationPoint("auto_1", "voice_cue", bar = 1, beat = 1f, panValue = -100f, curveType = AutomationCurveType.STEP, linkedCueId = "cue_intro"),
    PanAutomationPoint("auto_2", "voice_cue", bar = 5, beat = 1f, panValue = -100f, curveType = AutomationCurveType.LINEAR, linkedCueId = "cue_verse"),
    PanAutomationPoint("auto_3", "voice_cue", bar = 9, beat = 1f, panValue = 100f, curveType = AutomationCurveType.STEP, linkedCueId = "cue_chorus"),
    PanAutomationPoint("auto_4", "voice_cue", bar = 17, beat = 1f, panValue = 100f, curveType = AutomationCurveType.LINEAR, linkedCueId = "cue_bridge"),
    PanAutomationPoint("auto_5", "voice_cue", bar = 21, beat = 1f, panValue = 0f, curveType = AutomationCurveType.LINEAR, linkedCueId = "cue_ending"),
    PanAutomationPoint("auto_6", "midi", bar = 1, beat = 1f, panValue = -60f, curveType = AutomationCurveType.LINEAR),
    PanAutomationPoint("auto_7", "midi", bar = 9, beat = 1f, panValue = 60f, curveType = AutomationCurveType.LINEAR),
    PanAutomationPoint("auto_8", "midi", bar = 17, beat = 1f, panValue = 0f, curveType = AutomationCurveType.LINEAR)
)

val builtInLiveStereoPresets = listOf(
    LiveStereoPreset(
        id = "preset_iem_1",
        name = "Config 1: Click L / Cue R",
        subtitle = "Backing Stereo • Metronome Full Left (-100) • Voice Cue Full Right (+100)",
        backingPan = 0f,
        metronomePan = -100f,
        voiceCuePan = 100f,
        countInPan = -100f,
        midiPan = 0f,
        enablePerCuePan = false
    ),
    LiveStereoPreset(
        id = "preset_iem_2",
        name = "Config 2: Click R / Cue C",
        subtitle = "Backing Stereo • Metronome Full Right (+100) • Voice Cue Center (0)",
        backingPan = 0f,
        metronomePan = 100f,
        voiceCuePan = 0f,
        countInPan = 100f,
        midiPan = 0f,
        enablePerCuePan = false
    ),
    LiveStereoPreset(
        id = "preset_iem_3",
        name = "Config 3: 70% L / 70% R",
        subtitle = "Backing Stereo • Metronome 70% Left (-70) • Voice Cue 70% Right (+70)",
        backingPan = 0f,
        metronomePan = -70f,
        voiceCuePan = 70f,
        countInPan = -70f,
        midiPan = 0f,
        enablePerCuePan = false
    )
)
