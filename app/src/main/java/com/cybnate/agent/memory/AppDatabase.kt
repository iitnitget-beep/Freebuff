package com.cybnate.agent.memory

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectPath: String,
    val title: String,
    val updatedAt: Long,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long,
)

@Entity(tableName = "project_memory")
data class ProjectMemoryEntity(
    @PrimaryKey val projectPath: String,
    val instructions: String,
    val notes: String,
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun conversations(): Flow<List<ConversationEntity>>

    @Insert suspend fun insertConversation(c: ConversationEntity): Long
    @Insert suspend fun insertMessage(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    fun messages(id: Long): Flow<List<MessageEntity>>

    @Insert suspend fun upsertProjectMemory(m: ProjectMemoryEntity)

    @Query("SELECT * FROM project_memory WHERE projectPath = :path")
    suspend fun projectMemory(path: String): ProjectMemoryEntity?
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ProjectMemoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
