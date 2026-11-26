package com.example.data

import com.example.model.AutomationCurveType
import com.example.model.ChannelType
import com.example.model.MetronomeSoundType
import com.example.model.MixerChannelState
import com.example.model.OutputRouteOption
import com.example.model.PanAutomationPoint
import com.example.model.SongConfig
import com.example.model.SongSection
import com.example.model.SongSectionType
import com.example.model.VoiceCueItem
import com.example.model.VoiceCueSoundProfile
import com.example.model.defaultAutomationPoints
import com.example.model.defaultChannels
import com.example.model.defaultCueSoundProfiles
import com.example.model.defaultCues
import com.example.model.defaultMetronomePanMap
import com.example.model.defaultSections
import org.json.JSONArray
import org.json.JSONObject

object SongJsonSerializer {

    fun toJson(song: SongConfig): String {
        val root = JSONObject()
        root.put("perCuePanEnabled", song.perCuePanEnabled)
        root.put("automationEnabled", song.automationEnabled)

        val metroMapObj = JSONObject()
        song.metronomePanBySound.forEach { (k, v) ->
            metroMapObj.put(k.name, v.toDouble())
        }
        root.put("metronomePanBySound", metroMapObj)

        val profilesArr = JSONArray()
        song.cueSoundProfiles.forEach { p ->
            profilesArr.put(JSONObject().apply {
                put("id", p.id)
                put("displayName", p.displayName)
                put("pan", p.pan.toDouble())
                put("customWavUri", p.customWavUri ?: "")
            })
        }
        root.put("cueSoundProfiles", profilesArr)

        val channelsArr = JSONArray()
        song.channels.forEach { ch ->
            channelsArr.put(JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("type", ch.type.name)
                put("volumeGain", ch.volumeGain.toDouble())
                put("pan", ch.pan.toDouble())
                put("isMuted", ch.isMuted)
                put("isSolo", ch.isSolo)
                put("outputRoute", ch.outputRoute.name)
                put("trimDb", ch.trimDb.toDouble())
                put("customAudioName", ch.customAudioName ?: "")
                put("customAudioUri", ch.customAudioUri ?: "")
            })
        }
        root.put("channels", channelsArr)

        val sectionsArr = JSONArray()
        song.sections.forEach { s ->
            sectionsArr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("type", s.type.name)
                put("startBar", s.startBar)
                put("lengthBars", s.lengthBars)
                put("defaultCuePan", s.defaultCuePan.toDouble())
            })
        }
        root.put("sections", sectionsArr)

        val cuesArr = JSONArray()
        song.cues.forEach { c ->
            cuesArr.put(JSONObject().apply {
                put("id", c.id)
                put("label", c.label)
                put("spokenPhrase", c.spokenPhrase)
                put("section", c.section.name)
                put("soundProfileId", c.soundProfileId)
                put("bar", c.bar)
                put("beat", c.beat)
                put("pan", c.pan.toDouble())
                put("gain", c.gain.toDouble())
            })
        }
        root.put("cues", cuesArr)

        val autoArr = JSONArray()
        song.automationPoints.forEach { a ->
            autoArr.put(JSONObject().apply {
                put("id", a.id)
                put("targetId", a.targetId)
                put("bar", a.bar)
                put("beat", a.beat.toDouble())
                put("panValue", a.panValue.toDouble())
                put("curveType", a.curveType.name)
                put("linkedCueId", a.linkedCueId ?: "")
            })
        }
        root.put("automationPoints", autoArr)

        return root.toString()
    }

    fun fromEntity(entity: SongEntity): SongConfig {
        return try {
            val root = JSONObject(entity.songDataJson)
            val perCuePanEnabled = root.optBoolean("perCuePanEnabled", true)
            val automationEnabled = root.optBoolean("automationEnabled", true)

            val metroMap = mutableMapOf<MetronomeSoundType, Float>()
            val metroObj = root.optJSONObject("metronomePanBySound")
            if (metroObj != null) {
                MetronomeSoundType.entries.forEach { type ->
                    if (metroObj.has(type.name)) {
                        metroMap[type] = metroObj.optDouble(type.name, 0.0).toFloat()
                    }
                }
            }
            val finalMetroMap = defaultMetronomePanMap().toMutableMap().apply { putAll(metroMap) }

            val profiles = mutableListOf<VoiceCueSoundProfile>()
            val profilesArr = root.optJSONArray("cueSoundProfiles")
            if (profilesArr != null) {
                for (i in 0 until profilesArr.length()) {
                    val o = profilesArr.getJSONObject(i)
                    profiles.add(
                        VoiceCueSoundProfile(
                            id = o.getString("id"),
                            displayName = o.getString("displayName"),
                            pan = o.optDouble("pan", 0.0).toFloat(),
                            customWavUri = o.optString("customWavUri").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val channels = mutableListOf<MixerChannelState>()
            val channelsArr = root.optJSONArray("channels")
            if (channelsArr != null) {
                for (i in 0 until channelsArr.length()) {
                    val o = channelsArr.getJSONObject(i)
                    channels.add(
                        MixerChannelState(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            type = ChannelType.valueOf(o.getString("type")),
                            volumeGain = o.optDouble("volumeGain", 0.85).toFloat(),
                            pan = o.optDouble("pan", 0.0).toFloat(),
                            isMuted = o.optBoolean("isMuted", false),
                            isSolo = o.optBoolean("isSolo", false),
                            outputRoute = OutputRouteOption.entries.firstOrNull {
                                it.name == o.optString("outputRoute")
                            } ?: OutputRouteOption.STEREO_1_2,
                            trimDb = o.optDouble("trimDb", 0.0).toFloat(),
                            customAudioName = o.optString("customAudioName").takeIf { it.isNotBlank() },
                            customAudioUri = o.optString("customAudioUri").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val sections = mutableListOf<SongSection>()
            val sectionsArr = root.optJSONArray("sections")
            if (sectionsArr != null) {
                for (i in 0 until sectionsArr.length()) {
                    val o = sectionsArr.getJSONObject(i)
                    sections.add(
                        SongSection(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            type = SongSectionType.valueOf(o.getString("type")),
                            startBar = o.getInt("startBar"),
                            lengthBars = o.getInt("lengthBars"),
                            defaultCuePan = o.optDouble("defaultCuePan", 0.0).toFloat()
                        )
                    )
                }
            }

            val cues = mutableListOf<VoiceCueItem>()
            val cuesArr = root.optJSONArray("cues")
            if (cuesArr != null) {
                for (i in 0 until cuesArr.length()) {
                    val o = cuesArr.getJSONObject(i)
                    cues.add(
                        VoiceCueItem(
                            id = o.getString("id"),
                            label = o.getString("label"),
                            spokenPhrase = o.getString("spokenPhrase"),
                            section = SongSectionType.valueOf(o.getString("section")),
                            soundProfileId = o.getString("soundProfileId"),
                            bar = o.getInt("bar"),
                            beat = o.optInt("beat", 1),
                            pan = o.optDouble("pan", 0.0).toFloat(),
                            gain = o.optDouble("gain", 1.0).toFloat()
                        )
                    )
                }
            }

            val autoPoints = mutableListOf<PanAutomationPoint>()
            val autoArr = root.optJSONArray("automationPoints")
            if (autoArr != null) {
                for (i in 0 until autoArr.length()) {
                    val o = autoArr.getJSONObject(i)
                    autoPoints.add(
                        PanAutomationPoint(
                            id = o.getString("id"),
                            targetId = o.getString("targetId"),
                            bar = o.getInt("bar"),
                            beat = o.optDouble("beat", 1.0).toFloat(),
                            panValue = o.optDouble("panValue", 0.0).toFloat(),
                            curveType = AutomationCurveType.entries.firstOrNull {
                                it.name == o.optString("curveType")
                            } ?: AutomationCurveType.LINEAR,
                            linkedCueId = o.optString("linkedCueId").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            SongConfig(
                id = entity.id,
                projectId = entity.projectId,
                name = entity.name,
                bpm = entity.bpm,
                beatsPerBar = entity.beatsPerBar,
                totalBars = entity.totalBars,
                selectedMetronomeSound = MetronomeSoundType.entries.firstOrNull {
                    it.name == entity.selectedMetronomeSound
                } ?: MetronomeSoundType.WOODBLOCK,
                metronomePanBySound = finalMetroMap,
                perCuePanEnabled = perCuePanEnabled,
                automationEnabled = automationEnabled,
                cueSoundProfiles = profiles.ifEmpty { defaultCueSoundProfiles() },
                channels = channels.ifEmpty { defaultChannels() },
                sections = sections.ifEmpty { defaultSections() },
                cues = cues.ifEmpty { defaultCues() },
                automationPoints = autoPoints
            )
        } catch (_: Exception) {
            SongConfig(
                id = entity.id,
                projectId = entity.projectId,
                name = entity.name,
                bpm = entity.bpm,
                beatsPerBar = entity.beatsPerBar,
                totalBars = entity.totalBars,
                automationPoints = defaultAutomationPoints()
            )
        }
    }

    fun toEntity(song: SongConfig): SongEntity {
        return SongEntity(
            id = song.id,
            projectId = song.projectId,
            name = song.name,
            bpm = song.bpm,
            beatsPerBar = song.beatsPerBar,
            totalBars = song.totalBars,
            selectedMetronomeSound = song.selectedMetronomeSound.name,
            songDataJson = toJson(song)
        )
    }
}
