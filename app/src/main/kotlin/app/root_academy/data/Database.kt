package app.root_academy.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One finished mission or puzzle. [points] is what it paid out on first completion. */
@Entity(tableName = "completion")
data class CompletionEntity(@PrimaryKey val activityId: String, val kind: String, val day: Long, val points: Int)

/** Hints taken for an activity (0..4, the 4th is the walkthrough) and the points they cost. */
@Entity(tableName = "hint_use")
data class HintUseEntity(@PrimaryKey val activityId: String, val count: Int, val spent: Int)

@Entity(tableName = "skill_xp")
data class SkillXpEntity(@PrimaryKey val skill: String, val xp: Int)

@Entity(tableName = "codex_unlock")
data class CodexUnlockEntity(@PrimaryKey val lessonId: String, val day: Long)

@Entity(tableName = "active_day")
data class ActiveDayEntity(@PrimaryKey val day: Long)

/** The last activity opened, so Home can offer "continue". Single row, id = 0. */
@Entity(tableName = "last_activity")
data class LastActivityEntity(@PrimaryKey val id: Int = 0, val activityId: String, val kind: String)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM completion") fun completions(): Flow<List<CompletionEntity>>
    @Query("SELECT * FROM hint_use") fun hintUses(): Flow<List<HintUseEntity>>
    @Query("SELECT * FROM skill_xp") fun skillXp(): Flow<List<SkillXpEntity>>
    @Query("SELECT * FROM codex_unlock") fun codex(): Flow<List<CodexUnlockEntity>>
    @Query("SELECT * FROM active_day") fun activeDays(): Flow<List<ActiveDayEntity>>
    @Query("SELECT * FROM active_day") suspend fun daysList(): List<ActiveDayEntity>
    @Query("SELECT * FROM last_activity WHERE id = 0") fun last(): Flow<LastActivityEntity?>

    @Query("SELECT * FROM completion WHERE activityId = :id") suspend fun completion(id: String): CompletionEntity?
    @Query("SELECT * FROM hint_use WHERE activityId = :id") suspend fun hintUse(id: String): HintUseEntity?
    @Query("SELECT * FROM skill_xp WHERE skill = :skill") suspend fun xpOf(skill: String): SkillXpEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCompletion(c: CompletionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHint(h: HintUseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertXp(x: SkillXpEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCodex(c: CodexUnlockEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDay(d: ActiveDayEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setLast(l: LastActivityEntity)

    @Query("DELETE FROM completion") suspend fun clearCompletions()
    @Query("DELETE FROM hint_use") suspend fun clearHints()
    @Query("DELETE FROM skill_xp") suspend fun clearXp()
    @Query("DELETE FROM codex_unlock") suspend fun clearCodex()
    @Query("DELETE FROM active_day") suspend fun clearDays()
    @Query("DELETE FROM last_activity") suspend fun clearLast()
}

@Database(
    entities = [
        CompletionEntity::class, HintUseEntity::class, SkillXpEntity::class,
        CodexUnlockEntity::class, ActiveDayEntity::class, LastActivityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
}
