package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDeviceRouter
import com.example.audio.AudioFileDecoder
import com.example.audio.DecodedPcmAudio
import com.example.audio.LiveMixAudioEngine
import com.example.data.StudioDatabase
import com.example.data.StudioRepository
import com.example.model.AudioHardwareCapability
import com.example.model.AutomationCurveType
import com.example.model.ChannelType
import com.example.model.LiveStereoPreset
import com.example.model.MeterLevels
import com.example.model.MetronomeSoundType
import com.example.model.MixerChannelState
import com.example.model.OutputRouteOption
import com.example.model.PanAutomationPoint
import com.example.model.ProjectConfig
import com.example.model.SongConfig
import com.example.model.SongSectionType
import com.example.model.TransportState
import com.example.model.VoiceCueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class StudioUiState(
    val isLoading: Boolean = true,
    val isImportingAudio: Boolean = false,
    val projects: List<ProjectConfig> = emptyList(),
    val songsInActiveProject: List<SongConfig> = emptyList(),
    val activeProject: ProjectConfig? = null,
    val activeSong: SongConfig? = null,
    val statusBanner: String? = null
)

class MixerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = StudioDatabase.getInstance(application).studioDao()
    private val repository = StudioRepository(dao)
    val deviceRouter = AudioDeviceRouter(application)
    val audioEngine = LiveMixAudioEngine(application, deviceRouter)

    private val _activeProjectId = MutableStateFlow<String?>(null)
    private val _activeSongId = MutableStateFlow<String?>(null)
    private val _liveSongOverride = MutableStateFlow<SongConfig?>(null)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _isImportingAudio = MutableStateFlow(false)
    private var lastLoadedSongId: String? = null

    private var persistJob: Job? = null

    val transportState: StateFlow<TransportState> = audioEngine.transportState
    val meterLevels: StateFlow<MeterLevels> = audioEngine.meterLevels
    val hardwareCapability: StateFlow<AudioHardwareCapability> = deviceRouter.capability

    val uiState: StateFlow<StudioUiState> = combine(
        repository.projects,
        repository.allSongs,
        _activeProjectId,
        _activeSongId,
        _liveSongOverride,
        _statusBanner,
        _isImportingAudio
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val projects = values[0] as List<ProjectConfig>
        @Suppress("UNCHECKED_CAST")
        val allSongs = values[1] as List<SongConfig>
        val selectedProjId = values[2] as String?
        val selectedSongId = values[3] as String?
        val liveOverride = values[4] as SongConfig?
        val banner = values[5] as String?
        val importing = values[6] as Boolean

        if (projects.isEmpty()) {
            StudioUiState(isLoading = true)
        } else {
            val activeProject = projects.firstOrNull { it.id == selectedProjId } ?: projects.first()
            val projectSongs = allSongs.filter { it.projectId == activeProject.id }
            val resolvedSongId = selectedSongId?.takeIf { id -> projectSongs.any { it.id == id } }
                ?: activeProject.activeSongId.takeIf { id -> projectSongs.any { it.id == id } }
                ?: projectSongs.firstOrNull()?.id

            val dbSong = projectSongs.firstOrNull { it.id == resolvedSongId }
            val effectiveSong = if (liveOverride != null && liveOverride.id == dbSong?.id) {
                liveOverride
            } else {
                dbSong
            }

            if (effectiveSong != null) {
                audioEngine.updateSongConfig(effectiveSong)
                if (effectiveSong.id != lastLoadedSongId) {
                    lastLoadedSongId = effectiveSong.id
                    reloadSavedAudioForSong(effectiveSong)
                }
            }

            StudioUiState(
                isLoading = false,
                isImportingAudio = importing,
                projects = projects,
                songsInActiveProject = projectSongs,
                activeProject = activeProject,
                activeSong = effectiveSong,
                statusBanner = banner
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun togglePlayPause() {
        if (transportState.value.isPlaying) {
            audioEngine.pause()
        } else {
            audioEngine.play(withCountIn = false)
        }
    }

    fun playWithCountIn() {
        audioEngine.play(withCountIn = true)
    }

    fun stopPlayback() {
        audioEngine.stop()
    }

    fun seekToBar(bar: Int) {
        audioEngine.seekToBar(bar)
    }

    fun auditionMetronome(accent: Boolean = true) {
        audioEngine.auditionMetronomeClick(accent)
    }

    fun auditionVoiceCue(cue: VoiceCueItem) {
        audioEngine.auditionVoiceCue(cue)
    }

    fun triggerCountInAudition() {
        audioEngine.triggerCountInAudition()
    }

    fun refreshAudioDevices() {
        deviceRouter.refreshDevices()
        _statusBanner.value = "Audio devices rescanned: ${deviceRouter.capability.value.activeDeviceName}"
    }

    fun selectProject(projectId: String) {
        audioEngine.stop()
        _liveSongOverride.value = null
        _activeProjectId.value = projectId
        _activeSongId.value = null
    }

    fun selectSong(songId: String) {
        audioEngine.stop()
        _liveSongOverride.value = null
        _activeSongId.value = songId
        val project = uiState.value.activeProject ?: return
        viewModelScope.launch {
            repository.saveProject(project.copy(activeSongId = songId))
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val (project, song) = repository.createProject(name)
            _liveSongOverride.value = null
            _activeProjectId.value = project.id
            _activeSongId.value = song.id
            _statusBanner.value = "Project '${project.name}' created"
        }
    }

    fun createSong(name: String, bpm: Int) {
        val project = uiState.value.activeProject ?: return
        viewModelScope.launch {
            val song = repository.createSongInProject(project.id, name, bpm)
            repository.saveProject(project.copy(activeSongId = song.id))
            _liveSongOverride.value = null
            _activeSongId.value = song.id
            _statusBanner.value = "Song '${song.name}' added to ${project.name}"
        }
    }

    fun setBpm(newBpm: Int) {
        mutateActiveSong { it.copy(bpm = newBpm.coerceIn(40, 240)) }
    }

    fun updateTotalBars(totalBars: Int) {
        mutateActiveSong { song ->
            song.copy(totalBars = totalBars.coerceIn(8, 64))
        }
    }

    fun updateSectionRange(sectionId: String, startBar: Int, lengthBars: Int) {
        mutateActiveSong { song ->
            song.copy(
                sections = song.sections.map { sec ->
                    if (sec.id == sectionId) {
                        sec.copy(
                            startBar = startBar.coerceIn(1, song.totalBars),
                            lengthBars = lengthBars.coerceIn(1, song.totalBars)
                        )
                    } else sec
                }.sortedBy { it.startBar }
            )
        }
    }

    fun updateChannelVolume(channelId: String, volume: Float) {
        mutateActiveSong { song ->
            song.copy(
                channels = song.channels.map { ch ->
                    if (ch.id == channelId) ch.copy(volumeGain = volume.coerceIn(0f, 1.45f)) else ch
                }
            )
        }
    }

    fun updateChannelPan(channelId: String, pan: Float) {
        val clamped = pan.coerceIn(-100f, 100f)
        mutateActiveSong { song ->
            val updatedChannels = song.channels.map { ch ->
                if (ch.id == channelId) ch.copy(pan = clamped) else ch
            }
            val updatedMetroMap = if (channelId == "metronome") {
                song.metronomePanBySound.toMutableMap().apply {
                    put(song.selectedMetronomeSound, clamped)
                }
            } else {
                song.metronomePanBySound
            }
            song.copy(
                channels = updatedChannels,
                metronomePanBySound = updatedMetroMap
            )
        }
    }

    fun updateChannelTrimDb(channelId: String, trimDb: Float) {
        mutateActiveSong { song ->
            song.copy(
                channels = song.channels.map { ch ->
                    if (ch.id == channelId) ch.copy(trimDb = trimDb.coerceIn(-12f, 12f)) else ch
                }
            )
        }
    }

    fun toggleChannelMute(channelId: String) {
        mutateActiveSong { song ->
            song.copy(
                channels = song.channels.map { ch ->
                    if (ch.id == channelId) ch.copy(isMuted = !ch.isMuted) else ch
                }
            )
        }
    }

    fun toggleChannelSolo(channelId: String) {
        mutateActiveSong { song ->
            song.copy(
                channels = song.channels.map { ch ->
                    if (ch.id == channelId) ch.copy(isSolo = !ch.isSolo) else ch
                }
            )
        }
    }

    fun updateChannelOutputRoute(channelId: String, route: OutputRouteOption) {
        mutateActiveSong { song ->
            song.copy(
                channels = song.channels.map { ch ->
                    if (ch.id == channelId) ch.copy(outputRoute = route) else ch
                }
            )
        }
    }

    fun addAdditionalTrack(trackName: String) {
        mutateActiveSong { song ->
            val index = song.channels.count { it.type == ChannelType.ADDITIONAL } + 1
            val newTrack = MixerChannelState(
                id = "extra_${UUID.randomUUID().toString().take(6)}",
                name = trackName.ifBlank { "Extra Track $index" },
                type = ChannelType.ADDITIONAL,
                volumeGain = 0.78f,
                pan = 0f,
                outputRoute = OutputRouteOption.STEREO_1_2
            )
            song.copy(channels = song.channels + newTrack)
        }
    }

    fun removeAdditionalTrack(channelId: String) {
        audioEngine.clearCustomTrackAudio(channelId)
        mutateActiveSong { song ->
            song.copy(channels = song.channels.filterNot { it.id == channelId && it.type == ChannelType.ADDITIONAL })
        }
    }

    fun selectMetronomeSound(soundType: MetronomeSoundType) {
        mutateActiveSong { song ->
            val savedPanForSound = song.metronomePanBySound[soundType] ?: 0f
            song.copy(
                selectedMetronomeSound = soundType,
                channels = song.channels.map { ch ->
                    if (ch.id == "metronome") ch.copy(pan = savedPanForSound) else ch
                }
            )
        }
    }

    fun updateMetronomePanForSound(soundType: MetronomeSoundType, pan: Float) {
        val clamped = pan.coerceIn(-100f, 100f)
        mutateActiveSong { song ->
            val newMap = song.metronomePanBySound.toMutableMap().apply {
                put(soundType, clamped)
            }
            val updatedChannels = if (song.selectedMetronomeSound == soundType) {
                song.channels.map { ch ->
                    if (ch.id == "metronome") ch.copy(pan = clamped) else ch
                }
            } else {
                song.channels
            }
            song.copy(metronomePanBySound = newMap, channels = updatedChannels)
        }
    }

    fun togglePerCuePanEnabled(enabled: Boolean) {
        mutateActiveSong { it.copy(perCuePanEnabled = enabled) }
    }

    fun updateCuePan(cueId: String, pan: Float) {
        val clamped = pan.coerceIn(-100f, 100f)
        mutateActiveSong { song ->
            song.copy(
                cues = song.cues.map { cue ->
                    if (cue.id == cueId) cue.copy(pan = clamped) else cue
                }
            )
        }
    }

    fun updateCueSoundProfile(cueId: String, profileId: String) {
        mutateActiveSong { song ->
            song.copy(
                cues = song.cues.map { cue ->
                    if (cue.id == cueId) cue.copy(soundProfileId = profileId) else cue
                }
            )
        }
    }

    fun updateCueSoundProfilePan(profileId: String, pan: Float) {
        val clamped = pan.coerceIn(-100f, 100f)
        mutateActiveSong { song ->
            song.copy(
                cueSoundProfiles = song.cueSoundProfiles.map { p ->
                    if (p.id == profileId) p.copy(pan = clamped) else p
                }
            )
        }
    }

    fun updateSectionCuePan(sectionType: SongSectionType, pan: Float) {
        val clamped = pan.coerceIn(-100f, 100f)
        mutateActiveSong { song ->
            song.copy(
                sections = song.sections.map { sec ->
                    if (sec.type == sectionType) sec.copy(defaultCuePan = clamped) else sec
                },
                cues = song.cues.map { cue ->
                    if (cue.section == sectionType) cue.copy(pan = clamped) else cue
                }
            )
        }
    }

    fun addVoiceCue(
        label: String,
        phrase: String,
        section: SongSectionType,
        bar: Int,
        beat: Int,
        pan: Float
    ) {
        mutateActiveSong { song ->
            val newCue = VoiceCueItem(
                id = "cue_${UUID.randomUUID().toString().take(6)}",
                label = label.ifBlank { "${section.displayName} Cue" },
                spokenPhrase = phrase.ifBlank { section.displayName },
                section = section,
                soundProfileId = song.cueSoundProfiles.firstOrNull()?.id ?: "voice_studio",
                bar = bar.coerceIn(1, song.totalBars),
                beat = beat.coerceIn(1, song.beatsPerBar),
                pan = pan.coerceIn(-100f, 100f)
            )
            song.copy(cues = (song.cues + newCue).sortedWith(compareBy({ it.bar }, { it.beat })))
        }
    }

    fun deleteVoiceCue(cueId: String) {
        mutateActiveSong { song ->
            song.copy(cues = song.cues.filterNot { it.id == cueId })
        }
    }

    fun applyLiveStereoPreset(preset: LiveStereoPreset) {
        mutateActiveSong { song ->
            val updatedMetroMap = song.metronomePanBySound.toMutableMap().apply {
                put(song.selectedMetronomeSound, preset.metronomePan)
            }
            val updatedChannels = song.channels.map { ch ->
                when (ch.id) {
                    "backing" -> ch.copy(pan = preset.backingPan)
                    "metronome" -> ch.copy(pan = preset.metronomePan)
                    "voice_cue" -> ch.copy(pan = preset.voiceCuePan)
                    "count_in" -> ch.copy(pan = preset.countInPan)
                    "midi" -> ch.copy(pan = preset.midiPan)
                    else -> ch
                }
            }
            song.copy(
                perCuePanEnabled = preset.enablePerCuePan,
                metronomePanBySound = updatedMetroMap,
                channels = updatedChannels
            )
        }
        _statusBanner.value = "Applied ${preset.name}"
    }

    fun toggleAutomationEnabled(enabled: Boolean) {
        mutateActiveSong { it.copy(automationEnabled = enabled) }
    }

    fun addAutomationPoint(
        targetId: String,
        bar: Int,
        beat: Float,
        panValue: Float,
        curveType: AutomationCurveType,
        linkedCueId: String? = null
    ) {
        mutateActiveSong { song ->
            val point = PanAutomationPoint(
                id = "auto_${UUID.randomUUID().toString().take(6)}",
                targetId = targetId,
                bar = bar.coerceIn(1, song.totalBars),
                beat = beat.coerceIn(1f, song.beatsPerBar.toFloat()),
                panValue = panValue.coerceIn(-100f, 100f),
                curveType = curveType,
                linkedCueId = linkedCueId
            )
            song.copy(automationPoints = song.automationPoints + point)
        }
    }

    fun updateAutomationPoint(updated: PanAutomationPoint) {
        mutateActiveSong { song ->
            song.copy(
                automationPoints = song.automationPoints.map { pt ->
                    if (pt.id == updated.id) updated else pt
                }
            )
        }
    }

    fun deleteAutomationPoint(pointId: String) {
        mutateActiveSong { song ->
            song.copy(automationPoints = song.automationPoints.filterNot { it.id == pointId })
        }
    }

    fun applySweepAutomationPreset(targetId: String, startPan: Float, endPan: Float) {
        mutateActiveSong { song ->
            val remaining = song.automationPoints.filterNot { it.targetId == targetId }
            val p1 = PanAutomationPoint(
                id = "auto_${UUID.randomUUID().toString().take(6)}",
                targetId = targetId,
                bar = 1,
                beat = 1f,
                panValue = startPan,
                curveType = AutomationCurveType.LINEAR
            )
            val p2 = PanAutomationPoint(
                id = "auto_${UUID.randomUUID().toString().take(6)}",
                targetId = targetId,
                bar = song.totalBars,
                beat = 1f,
                panValue = endPan,
                curveType = AutomationCurveType.LINEAR
            )
            song.copy(
                automationEnabled = true,
                automationPoints = remaining + p1 + p2
            )
        }
        _statusBanner.value = "Created Left-to-Right sweep automation"
    }

    fun applyChorusJumpAutomation(targetId: String, versePan: Float, chorusPan: Float) {
        mutateActiveSong { song ->
            val chorusBar = song.sections.firstOrNull { it.type == SongSectionType.CHORUS }?.startBar ?: 9
            val bridgeBar = song.sections.firstOrNull { it.type == SongSectionType.BRIDGE }?.startBar ?: 17
            val remaining = song.automationPoints.filterNot { it.targetId == targetId }
            val pts = listOf(
                PanAutomationPoint("auto_${UUID.randomUUID().toString().take(6)}", targetId, 1, 1f, versePan, AutomationCurveType.STEP),
                PanAutomationPoint("auto_${UUID.randomUUID().toString().take(6)}", targetId, chorusBar, 1f, chorusPan, AutomationCurveType.STEP),
                PanAutomationPoint("auto_${UUID.randomUUID().toString().take(6)}", targetId, bridgeBar, 1f, 0f, AutomationCurveType.LINEAR)
            )
            song.copy(automationEnabled = true, automationPoints = remaining + pts)
        }
        _statusBanner.value = "Chorus automatic pan jump applied"
    }

    fun resolveFileDisplayName(context: Context, uri: Uri): String? = resolveDisplayName(context, uri)

    fun importSongFromUri(
        context: Context,
        uri: Uri,
        songTitle: String,
        bpm: Int,
        createNewSong: Boolean
    ) {
        viewModelScope.launch {
            _isImportingAudio.value = true
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                val fileName = resolveDisplayName(context, uri) ?: "Imported Audio"
                val decoded = withContext(Dispatchers.IO) {
                    AudioFileDecoder.decodeAudioUri(context, uri, audioEngine.sampleRate)
                }
                if (decoded == null) {
                    _statusBanner.value = "Gagal membaca file audio. Gunakan MP3, WAV, M4A, AAC, OGG, atau FLAC."
                    return@launch
                }

                if (createNewSong) {
                    val project = uiState.value.activeProject ?: return@launch
                    val cleanTitle = songTitle.ifBlank { fileName.substringBeforeLast('.') }
                    val newSong = repository.createSongInProject(project.id, cleanTitle, bpm)
                    val updatedSong = newSong.copy(
                        channels = newSong.channels.map { ch ->
                            if (ch.id == "backing") {
                                ch.copy(customAudioName = fileName, customAudioUri = uri.toString())
                            } else ch
                        }
                    )
                    repository.saveSong(updatedSong)
                    repository.saveProject(project.copy(activeSongId = updatedSong.id))
                    audioEngine.stop()
                    audioEngine.setCustomTrackAudio("backing", decoded)
                    lastLoadedSongId = updatedSong.id
                    _liveSongOverride.value = updatedSong
                    _activeSongId.value = updatedSong.id
                    _statusBanner.value = "Lagu '$cleanTitle' berhasil diimpor ke Backing Track!"
                } else {
                    audioEngine.setCustomTrackAudio("backing", decoded)
                    mutateActiveSong { song ->
                        song.copy(
                            name = songTitle.ifBlank { song.name },
                            bpm = bpm.coerceIn(40, 240),
                            channels = song.channels.map { ch ->
                                if (ch.id == "backing") {
                                    ch.copy(customAudioName = fileName, customAudioUri = uri.toString())
                                } else ch
                            }
                        )
                    }
                    _statusBanner.value = "'$fileName' dimuat ke Main Backing Track!"
                }
            } finally {
                _isImportingAudio.value = false
            }
        }
    }

    fun importChannelWav(context: Context, channelId: String, uri: Uri) {
        viewModelScope.launch {
            _isImportingAudio.value = true
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                val fileName = resolveDisplayName(context, uri) ?: "Imported Audio"
                val decoded = withContext(Dispatchers.IO) {
                    AudioFileDecoder.decodeAudioUri(context, uri, audioEngine.sampleRate)
                }
                if (decoded == null) {
                    _statusBanner.value = "Gagal membaca audio. Coba file MP3, WAV, M4A, atau FLAC."
                    return@launch
                }
                audioEngine.setCustomTrackAudio(channelId, decoded)
                mutateActiveSong { song ->
                    song.copy(
                        channels = song.channels.map { ch ->
                            if (ch.id == channelId) {
                                ch.copy(customAudioName = fileName, customAudioUri = uri.toString())
                            } else ch
                        }
                    )
                }
                _statusBanner.value = "Berhasil memuat '$fileName'"
            } finally {
                _isImportingAudio.value = false
            }
        }
    }

    fun importCueProfileWav(context: Context, profileId: String, uri: Uri) {
        viewModelScope.launch {
            _isImportingAudio.value = true
            try {
                val fileName = resolveDisplayName(context, uri) ?: "Cue Audio"
                val decodedTrack = withContext(Dispatchers.IO) {
                    AudioFileDecoder.decodeAudioUri(context, uri, audioEngine.sampleRate)
                }
                if (decodedTrack == null) {
                    _statusBanner.value = "Gagal membaca audio cue. Pilih MP3, WAV, atau M4A."
                    return@launch
                }
                val decodedFloat = DecodedPcmAudio(
                    left = FloatArray(decodedTrack.left.size) { i -> decodedTrack.left[i] / 32768f },
                    right = FloatArray(decodedTrack.right.size) { i -> decodedTrack.right[i] / 32768f },
                    isStereo = decodedTrack.isStereo
                )
                audioEngine.voiceCueSynthesizer.registerCustomCueClip(profileId, decodedFloat)
                mutateActiveSong { song ->
                    song.copy(
                        cueSoundProfiles = song.cueSoundProfiles.map { p ->
                            if (p.id == profileId) p.copy(displayName = fileName, customWavUri = uri.toString()) else p
                        }
                    )
                }
                _statusBanner.value = "Suara cue '$fileName' dimuat"
            } finally {
                _isImportingAudio.value = false
            }
        }
    }

    private fun reloadSavedAudioForSong(song: SongConfig) {
        val appContext = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            song.channels.forEach { ch ->
                val uriStr = ch.customAudioUri
                if (!uriStr.isNullOrBlank()) {
                    val decoded = AudioFileDecoder.decodeAudioUri(
                        appContext,
                        Uri.parse(uriStr),
                        audioEngine.sampleRate
                    )
                    if (decoded != null) {
                        audioEngine.setCustomTrackAudio(ch.id, decoded)
                    } else {
                        audioEngine.clearCustomTrackAudio(ch.id)
                    }
                } else {
                    audioEngine.clearCustomTrackAudio(ch.id)
                }
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mutateActiveSong(transform: (SongConfig) -> SongConfig) {
        val current = _liveSongOverride.value?.takeIf { it.id == uiState.value.activeSong?.id }
            ?: uiState.value.activeSong
            ?: return
        val updated = transform(current)
        _liveSongOverride.value = updated
        audioEngine.updateSongConfig(updated)

        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(250)
            repository.saveSong(updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        deviceRouter.release()
    }
}
