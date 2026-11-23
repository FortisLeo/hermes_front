package com.rishitgoklani.hermes_front_mobile.data.avatar

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "avatars")
data class AvatarRow(
    @PrimaryKey val id: String,
    val name: String, val source: String, val license: String, val author: String,
    val originalBytes: Long, val storedBytes: Long, val textureCap: Int, val estTexBytes: Long,
    val note: String?, val thumbPath: String?, val addedAt: Long, val lastUsedAt: Long, val status: String,
)

@Dao
interface AvatarDao {
    @Query("SELECT * FROM avatars ORDER BY addedAt ASC, id ASC") fun observe(): Flow<List<AvatarRow>>
    @Query("SELECT * FROM avatars ORDER BY addedAt ASC, id ASC") suspend fun all(): List<AvatarRow>
    @Query("SELECT * FROM avatars WHERE id = :id") suspend fun get(id: String): AvatarRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: AvatarRow)
    @Query("DELETE FROM avatars WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE avatars SET name = :name WHERE id = :id") suspend fun rename(id: String, name: String)
    @Query("UPDATE avatars SET status = :status WHERE id = :id") suspend fun setStatus(id: String, status: String)
    @Query("UPDATE avatars SET lastUsedAt = :at WHERE id = :id") suspend fun touch(id: String, at: Long)
    @Query("UPDATE avatars SET thumbPath = :path WHERE id = :id") suspend fun setThumb(id: String, path: String)
}

@Database(entities = [AvatarRow::class], version = 1, exportSchema = true)
abstract class DeskbotDatabase : RoomDatabase() { abstract fun avatars(): AvatarDao }

internal fun AvatarRow.toEntry() = AvatarEntry(
    id, name, runCatching { AvatarSource.valueOf(source) }.getOrDefault(AvatarSource.UPLOAD), license, author, originalBytes, storedBytes, textureCap, estTexBytes, note, thumbPath, addedAt, lastUsedAt,
    runCatching { AvatarStatus.valueOf(status) }.getOrDefault(AvatarStatus.BROKEN),
)
internal fun AvatarEntry.toRow() = AvatarRow(id, name, source.name, license, author, originalBytes, storedBytes, textureCap, estTexBytes, note, thumbPath, addedAt, lastUsedAt, status.name)

object DeskbotDatabases {
    /** The app database (file `deskbot.db`). One instance per process. */
    fun create(context: android.content.Context): DeskbotDatabase = androidx.room.Room.databaseBuilder(context.applicationContext, DeskbotDatabase::class.java, "deskbot.db").build()
}
