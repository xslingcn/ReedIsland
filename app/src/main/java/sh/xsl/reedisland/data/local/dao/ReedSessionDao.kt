package sh.xsl.reedisland.data.local.dao

import androidx.room.*
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.data.local.entity.ReedSession


@Dao
interface ReedSessionDao {
    @Query("SELECT * FROM ReedSession WHERE domain=:domain ORDER BY lastUpdatedAt DESC LIMIT 1")
    suspend fun get(domain: String = DawnApp.currentDomain): ReedSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reedSession: ReedSession)

    @Delete
    suspend fun delete(reedSession: ReedSession)

    @Query("DELETE FROM ReedSession")
    fun nukeTable()
}