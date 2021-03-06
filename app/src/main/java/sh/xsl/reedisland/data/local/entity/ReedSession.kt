package sh.xsl.reedisland.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import sh.xsl.reedisland.DawnApp
import java.time.LocalDateTime

@Entity
data class ReedSession(
    @PrimaryKey val cookie: String,
    var lastUpdatedAt: LocalDateTime = LocalDateTime.now(),
    val domain: String = DawnApp.currentDomain
)