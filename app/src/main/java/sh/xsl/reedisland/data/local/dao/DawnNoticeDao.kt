package sh.xsl.reedisland.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import sh.xsl.reedisland.data.local.entity.DawnNotice

@Dao
interface DawnNoticeDao {
    @Query("SELECT * From DawnNotice ORDER BY id DESC LIMIT 1")
    suspend fun getLatestDawnNotice(): DawnNotice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(dawnNotice: DawnNotice)

    suspend fun insertNoticeWithTimestamp(dawnNotice: DawnNotice) {
        dawnNotice.setUpdatedTimestamp()
        insertNotice(dawnNotice)
    }

    @Query("DELETE FROM DawnNotice")
    suspend fun nukeTable()
}