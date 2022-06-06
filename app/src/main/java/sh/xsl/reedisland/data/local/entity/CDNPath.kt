package sh.xsl.reedisland.data.local.entity

import androidx.room.Entity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity
data class CDNPath(
    val url: String,
    val rate: Int,
)