package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeSongId: String,
    val updatedAt: Long
)

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val bpm: Int,
    val beatsPerBar: Int,
    val totalBars: Int,
    val selectedMetronomeSound: String,
    val songDataJson: String
)
