package com.example.data

import com.example.model.AutomationCurveType
import com.example.model.MetronomeSoundType
import com.example.model.PanAutomationPoint
import com.example.model.ProjectConfig
import com.example.model.SongConfig
import com.example.model.defaultAutomationPoints
import com.example.model.defaultChannels
import com.example.model.defaultCues
import com.example.model.defaultMetronomePanMap
import com.example.model.defaultSections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class StudioRepository(private val dao: StudioDao) {

    val projects: Flow<List<ProjectConfig>> = dao.observeProjects().map { list ->
        list.map {
            ProjectConfig(
                id = it.id,
                name = it.name,
                activeSongId = it.activeSongId,
                updatedAt = it.updatedAt
            )
        }
    }

    val allSongs: Flow<List<SongConfig>> = dao.observeAllSongs().map { list ->
        list.map { SongJsonSerializer.fromEntity(it) }
    }

    suspend fun ensureSeeded() {
        if (dao.projectCount() > 0) return

        val p1Id = "proj_live_set"
        val s1Id = "song_horizon"
        val s2Id = "song_midnight"
        val p2Id = "proj_acoustic_rig"
        val s3Id = "song_open_road"

        val now = System.currentTimeMillis()
        dao.upsertProject(ProjectEntity(p1Id, "Main Stage Live Set", s1Id, now))
        dao.upsertProject(ProjectEntity(p2Id, "Acoustic Tour IEM", s3Id, now - 10000L))

        val song1 = SongConfig(
            id = s1Id,
            projectId = p1Id,
            name = "Horizon Anthem",
            bpm = 124,
            beatsPerBar = 4,
            totalBars = 24,
            selectedMetronomeSound = MetronomeSoundType.WOODBLOCK,
            metronomePanBySound = defaultMetronomePanMap(),
            channels = defaultChannels(),
            sections = defaultSections(),
            cues = defaultCues(),
            automationPoints = defaultAutomationPoints()
        )

        val song2Channels = defaultChannels().map { ch ->
            when (ch.id) {
                "metronome" -> ch.copy(pan = 100f)
                "voice_cue" -> ch.copy(pan = 0f)
                "midi" -> ch.copy(pan = -40f)
                else -> ch
            }
        }
        val song2 = SongConfig(
            id = s2Id,
            projectId = p1Id,
            name = "Midnight Pulse",
            bpm = 108,
            beatsPerBar = 4,
            totalBars = 24,
            selectedMetronomeSound = MetronomeSoundType.DIGITAL_SINE,
            metronomePanBySound = defaultMetronomePanMap().toMutableMap().apply {
                put(MetronomeSoundType.DIGITAL_SINE, 100f)
            },
            perCuePanEnabled = false,
            channels = song2Channels,
            sections = defaultSections(),
            cues = defaultCues(),
            automationPoints = listOf(
                PanAutomationPoint("s2_a1", "backing", 1, 1f, 0f, AutomationCurveType.LINEAR),
                PanAutomationPoint("s2_a2", "midi", 1, 1f, -80f, AutomationCurveType.LINEAR),
                PanAutomationPoint("s2_a3", "midi", 9, 1f, 80f, AutomationCurveType.LINEAR)
            )
        )

        val song3Channels = defaultChannels().map { ch ->
            when (ch.id) {
                "metronome" -> ch.copy(pan = -70f)
                "voice_cue" -> ch.copy(pan = 70f)
                else -> ch
            }
        }
        val song3 = SongConfig(
            id = s3Id,
            projectId = p2Id,
            name = "Open Road",
            bpm = 96,
            beatsPerBar = 4,
            totalBars = 24,
            selectedMetronomeSound = MetronomeSoundType.RIMSHOT,
            metronomePanBySound = defaultMetronomePanMap().toMutableMap().apply {
                put(MetronomeSoundType.RIMSHOT, -70f)
            },
            channels = song3Channels,
            sections = defaultSections(),
            cues = defaultCues(),
            automationPoints = defaultAutomationPoints()
        )

        dao.upsertSong(SongJsonSerializer.toEntity(song1))
        dao.upsertSong(SongJsonSerializer.toEntity(song2))
        dao.upsertSong(SongJsonSerializer.toEntity(song3))
    }

    suspend fun saveProject(project: ProjectConfig) {
        dao.upsertProject(
            ProjectEntity(
                id = project.id,
                name = project.name,
                activeSongId = project.activeSongId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveSong(song: SongConfig) {
        dao.upsertSong(SongJsonSerializer.toEntity(song))
    }

    suspend fun createProject(name: String): Pair<ProjectConfig, SongConfig> {
        val projectId = "proj_${UUID.randomUUID().toString().take(8)}"
        val songId = "song_${UUID.randomUUID().toString().take(8)}"
        val song = SongConfig(
            id = songId,
            projectId = projectId,
            name = "Song 1",
            bpm = 120
        )
        val project = ProjectConfig(
            id = projectId,
            name = name.ifBlank { "New Setlist" },
            activeSongId = songId
        )
        saveProject(project)
        saveSong(song)
        return project to song
    }

    suspend fun createSongInProject(projectId: String, name: String, bpm: Int): SongConfig {
        val song = SongConfig(
            id = "song_${UUID.randomUUID().toString().take(8)}",
            projectId = projectId,
            name = name.ifBlank { "New Track" },
            bpm = bpm.coerceIn(40, 240)
        )
        saveSong(song)
        return song
    }
}
