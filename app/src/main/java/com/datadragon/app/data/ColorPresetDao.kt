package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Data access for user-saved [ColorPreset]s. */
@Dao
interface ColorPresetDao {

    @Insert
    suspend fun insert(preset: ColorPreset): Long

    /** All saved presets, name-sorted, observed so the dropdown updates live. */
    @Query("SELECT * FROM color_presets ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<ColorPreset>>

    /** One-shot snapshot of every saved preset. */
    @Query("SELECT * FROM color_presets ORDER BY name COLLATE NOCASE ASC, id ASC")
    suspend fun getAllOnce(): List<ColorPreset>
}
