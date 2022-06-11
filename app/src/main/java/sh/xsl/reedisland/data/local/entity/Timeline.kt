package sh.xsl.reedisland.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity
data class Timeline(
    @PrimaryKey
    val id: String,
    val name: String,
    @Json(name = "showName")
    val display_name: String,
    @Json(name = "msg")
    val notice: String,
    val max_page: Int = 1,
)