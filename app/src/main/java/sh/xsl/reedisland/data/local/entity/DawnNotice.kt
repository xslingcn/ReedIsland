package sh.xsl.reedisland.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
@Entity
data class DawnNotice(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null, // only used to keep track of versions
    @Json(name = "loadingBible")
    val loadingMsgs: List<String>,
    @Json(name = "kaomoji")
    val kaomojiList: List<Emoji>,
    var lastUpdatedAt: LocalDateTime = LocalDateTime.now()
) {

    override fun equals(other: Any?) =
        if (other is DawnNotice) {
            loadingMsgs == other.loadingMsgs
                    && kaomojiList == other.kaomojiList
        } else false

    override fun hashCode(): Int {
        var result = loadingMsgs.hashCode()
        result = 31 * result + kaomojiList.hashCode()
        return result
    }

    fun setUpdatedTimestamp(time: LocalDateTime = LocalDateTime.now()) {
        lastUpdatedAt = time
    }
}