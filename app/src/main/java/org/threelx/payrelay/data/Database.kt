package org.threelx.payrelay.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Where a queued message stands. Stored by name. */
object Status {
    const val WAITING = "WAITING"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
}

/** One wallet message that is being, or was, passed on to the server. */
@Entity(tableName = "messages", indices = [Index(value = ["key"], unique = true), Index("receivedAt")])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Hash of sender and text: the same message is never queued twice. */
    val key: String,
    val sender: String,
    val wallet: String,
    val body: String,
    val amount: Double?,
    val payer: String?,
    val trxId: String?,
    val receivedAt: Long,
    @ColumnInfo(defaultValue = Status.WAITING) val status: String = Status.WAITING,
    val attempts: Int = 0,
    /** What the server said: verified, no_order, duplicate, ignored… */
    val serverResult: String? = null,
    val error: String? = null,
    val sentAt: Long? = null,
)

data class DayTotals(val count: Int, val amount: Double?)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE status = 'WAITING' ORDER BY receivedAt, id LIMIT :limit")
    suspend fun waiting(limit: Int = 50): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'WAITING'")
    fun waitingCount(): Flow<Int>

    @Query("SELECT COUNT(*) AS count, SUM(amount) AS amount FROM messages WHERE receivedAt >= :since")
    fun totalsSince(since: Long): Flow<DayTotals>

    @Query("UPDATE messages SET status = 'SENT', serverResult = :result, error = NULL, sentAt = :at, attempts = attempts + 1 WHERE id = :id")
    suspend fun markSent(id: Long, result: String, at: Long)

    @Query("UPDATE messages SET status = :status, error = :error, attempts = attempts + 1 WHERE id = :id")
    suspend fun markProblem(id: Long, status: String, error: String)

    /** Puts failed messages back in the queue (one, or all with a null id). */
    @Query("UPDATE messages SET status = 'WAITING', error = NULL WHERE status = 'FAILED' AND (:id IS NULL OR id = :id)")
    suspend fun retry(id: Long? = null)

    @Query("DELETE FROM messages WHERE status = 'SENT' AND receivedAt < :before")
    suspend fun prune(before: Long)

    @Query("DELETE FROM messages WHERE status != 'WAITING'")
    suspend fun clearHistory()
}

@Database(entities = [Message::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "payrelay.db").build()
    }
}
