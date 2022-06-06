package sh.xsl.reedisland.data.local.entity

import androidx.room.Entity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity
data class Config(
    @Json(name="cdnPathV1")
    val cdnPath: List<CDNPath>,
    val emoticonList: List<String>,
    @Json(name="forumListV1")
    val communities: List<Community>,
    val globalRule: String,
    @Json(name="siteNotify")
    val notify: String,
    @Json(name="sitePrivacyPolicy")
    val privacyPolicy: String,
    @Json(name="siteServiceAgreement")
    val serviceAgreement: String
    ) {
}