package app.notch.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A child on the door frame. */
@Entity(tableName = "children")
data class Child(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [LocalDate.toEpochDay] of the birthday. */
    val born: Long,
    /** "GIRL", "BOY" or empty: only for the grammar of a few sentences. */
    val gender: String = "",
    /** Which crayon is theirs, an index into the palette. */
    val crayon: Int = 0,
    /** A moment whose picture is their face in lists, or 0 for the latest. */
    val face: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val birthday: LocalDate get() = LocalDate.ofEpochDay(born)
    val girl: Boolean get() = gender == "GIRL"
    val boy: Boolean get() = gender == "BOY"
}

/** What a moment is. */
enum class Kind {
    PHOTO, VIDEO,
    /** A clip from a "second a day" session; a second of it goes into the year's film. */
    SECOND,
    /** An answer in the birthday interview. */
    INTERVIEW,
    /** A hand traced on a sheet of paper. */
    HAND,
}

/**
 * A photo or a clip, of one child or a few. [kids] is the ids as ",1,3," so a moment can be of
 * brother and sister at once.
 */
@Entity(tableName = "moments", indices = [Index("takenAt")])
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kids: String,
    /** The picture or clip, a file in the vault. */
    val file: String,
    /** [Kind] name. */
    val kind: String = Kind.PHOTO.name,
    val takenAt: Long,
    val durationMs: Long = 0,
    /** Written under it. */
    val note: String = "",
    /** The "it will soon be gone" item it answers ([app.notch.domain.Fading] key), or empty. */
    val fading: String = "",
    /** The interview question it answers, 1-based, or 0. */
    val question: Int = 0,
    /** The same spot, the same pose: a series of these, or 0. */
    val seriesId: Long = 0,
    /** The gallery item it came from, so the same picture is never taken in twice. */
    val source: String = "",
    val favorite: Boolean = false,
    /** A traced hand: its outline as "x,y;x,y;…" in millimetres, wrist at the origin, fingers up (negative y). */
    val shape: String = "",
    /** A traced hand: wrist to fingertip, in tenths of a millimetre. */
    val measure: Int = 0,
) {
    val kindOf: Kind get() = runCatching { Kind.valueOf(kind) }.getOrDefault(Kind.PHOTO)
    val video: Boolean get() = kindOf.let { it == Kind.VIDEO || it == Kind.SECOND || it == Kind.INTERVIEW }
    val kidIds: List<Long> get() = kids.split(',').mapNotNull { it.toLongOrNull() }
    fun of(child: Long) = ",$child," in kids

    companion object {
        fun kidsOf(ids: Collection<Long>) = ids.distinct().sorted().joinToString(",", ",", ",")
    }
}

/** A word the way the child says it, and what they mean. */
@Entity(tableName = "words", indices = [Index("childId")])
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    /** "ябуко". */
    val said: String,
    /** "яблоко"; empty for a phrase that needs no translation. */
    val means: String = "",
    /** The recording, a file in the vault, or empty. */
    val audio: String = "",
    val at: Long,
    /** A whole sentence rather than one word. */
    val phrase: Boolean = false,
    /** The "it will soon be gone" item it answers, or empty. */
    val fading: String = "",
)

/** A pencil line on the door frame: how tall, and when. */
@Entity(tableName = "marks", indices = [Index("childId")])
data class Mark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val cm: Float,
    val at: Long,
)

/** The same spot photographed again and again: by the door, on the sofa, with the bear. */
@Entity(tableName = "series")
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val createdAt: Long,
)

@Dao
abstract class NotchDao {
    @Query("SELECT * FROM children ORDER BY born ASC") abstract fun observeChildren(): Flow<List<Child>>
    @Query("SELECT * FROM moments ORDER BY takenAt DESC") abstract fun observeMoments(): Flow<List<Moment>>
    @Query("SELECT * FROM words ORDER BY at DESC") abstract fun observeWords(): Flow<List<Word>>
    @Query("SELECT * FROM marks ORDER BY at ASC") abstract fun observeMarks(): Flow<List<Mark>>
    @Query("SELECT * FROM series ORDER BY createdAt DESC") abstract fun observeSeries(): Flow<List<Series>>

    @Query("SELECT * FROM children ORDER BY born ASC") abstract suspend fun children(): List<Child>
    @Query("SELECT * FROM moments ORDER BY takenAt DESC") abstract suspend fun moments(): List<Moment>
    @Query("SELECT * FROM words ORDER BY at DESC") abstract suspend fun words(): List<Word>
    @Query("SELECT * FROM marks ORDER BY at ASC") abstract suspend fun marks(): List<Mark>
    @Query("SELECT * FROM series ORDER BY createdAt DESC") abstract suspend fun series(): List<Series>

    @Query("SELECT * FROM moments WHERE id = :id") abstract suspend fun moment(id: Long): Moment?
    @Query("SELECT source FROM moments WHERE source != ''") abstract suspend fun sources(): List<String>

    @Insert abstract suspend fun insert(c: Child): Long
    @Update abstract suspend fun update(c: Child)
    @Delete abstract suspend fun delete(c: Child)

    @Insert abstract suspend fun insert(m: Moment): Long
    @Update abstract suspend fun update(m: Moment)
    @Delete abstract suspend fun delete(m: Moment)

    @Insert abstract suspend fun insert(w: Word): Long
    @Update abstract suspend fun update(w: Word)
    @Delete abstract suspend fun delete(w: Word)

    @Insert abstract suspend fun insert(m: Mark): Long
    @Delete abstract suspend fun delete(m: Mark)

    @Insert abstract suspend fun insert(s: Series): Long
    @Update abstract suspend fun update(s: Series)
    @Delete abstract suspend fun delete(s: Series)

    @Query("UPDATE moments SET seriesId = 0 WHERE seriesId = :id") abstract suspend fun leaveSeries(id: Long)
    @Query("DELETE FROM words WHERE childId = :id") abstract suspend fun deleteWordsOf(id: Long)
    @Query("DELETE FROM marks WHERE childId = :id") abstract suspend fun deleteMarksOf(id: Long)

    @Query("DELETE FROM children") protected abstract suspend fun clearChildren()
    @Query("DELETE FROM moments") protected abstract suspend fun clearMoments()
    @Query("DELETE FROM words") protected abstract suspend fun clearWords()
    @Query("DELETE FROM marks") protected abstract suspend fun clearMarks()
    @Query("DELETE FROM series") protected abstract suspend fun clearSeries()
    @Insert protected abstract suspend fun insertChildren(l: List<Child>)
    @Insert protected abstract suspend fun insertMoments(l: List<Moment>)
    @Insert protected abstract suspend fun insertWords(l: List<Word>)
    @Insert protected abstract suspend fun insertMarks(l: List<Mark>)
    @Insert protected abstract suspend fun insertSeries(l: List<Series>)

    @Transaction
    open suspend fun replaceAll(all: Everything) {
        clearChildren(); clearMoments(); clearWords(); clearMarks(); clearSeries()
        insertChildren(all.children); insertMoments(all.moments); insertWords(all.words); insertMarks(all.marks); insertSeries(all.series)
    }
}

/** The whole house, for backups. */
class Everything(val children: List<Child>, val moments: List<Moment>, val words: List<Word>, val marks: List<Mark>, val series: List<Series>)

@Database(entities = [Child::class, Moment::class, Word::class, Mark::class, Series::class], version = 1, exportSchema = true)
abstract class NotchDatabase : RoomDatabase() {
    abstract fun dao(): NotchDao

    companion object {
        fun create(context: Context): NotchDatabase =
            Room.databaseBuilder(context.applicationContext, NotchDatabase::class.java, "notch.db").build()
    }
}
