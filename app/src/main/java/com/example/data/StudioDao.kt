package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM songs WHERE projectId = :projectId ORDER BY name ASC")
    fun observeSongsForProject(projectId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    fun observeAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun projectCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)
}
