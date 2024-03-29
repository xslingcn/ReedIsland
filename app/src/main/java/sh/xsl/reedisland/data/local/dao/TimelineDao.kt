package sh.xsl.reedisland.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import sh.xsl.reedisland.data.local.entity.Timeline

@Dao
interface TimelineDao {
    @Query("SELECT * FROM Timeline")
    fun getAll(): LiveData<List<Timeline>>

    @Query("SELECT * FROM Timeline WHERE id=:id")
    suspend fun getTimelineById(id: String): Timeline

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(Timeline: Timeline)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(TimelineList: List<Timeline>)

    @Delete
    suspend fun delete(Timeline: Timeline)

    @Query("DELETE FROM Timeline")
    suspend fun nukeTable()
}