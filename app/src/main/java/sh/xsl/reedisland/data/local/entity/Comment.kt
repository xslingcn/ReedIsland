/*
 *  Copyright 2020 Fishballzzz
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package sh.xsl.reedisland.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import sh.xsl.reedisland.DawnApp
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
@Entity
data class Comment(
    @PrimaryKey val id: String,
    val userid: String,
    val name: String?,
    val sage: String = "0",
    val admin: String = "0",
    val status: String = "n",
    val title: String?,
    val email: String?,
    val now: String,
    var content: String?,
    val img: String?,
    val ext: String?,
    var page: Int = 1,
    @Json(name = "parent")
    var parentId: String = "",
    val domain: String = DawnApp.currentDomain,
    var lastUpdatedAt: LocalDateTime = LocalDateTime.now()
) {
    // used for reply filtering
    @Ignore
    var visible: Boolean = true

    // only compares by server fields
    override fun equals(other: Any?): Boolean =
        if (other is Comment)
            id == other.id && parentId == other.parentId && page == other.page && img == other.img
                    && ext == other.ext && now == other.now
                    && userid == other.userid && name == other.name
                    && email == other.email && title == other.title
                    && content == other.content && sage == other.sage
                    && admin == other.admin && status == other.status
                    && domain == other.domain
        else false


    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + parentId.hashCode()
        result = 31 * result + page.hashCode()
        result = 31 * result + img.hashCode()
        result = 31 * result + ext.hashCode()
        result = 31 * result + now.hashCode()
        result = 31 * result + userid.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + sage.hashCode()
        result = 31 * result + admin.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + domain.hashCode()
        return result
    }

    fun getSimplifiedTitle(): String =
        if (isAd()) "广告"
        else if (title != null && title != "无标题") "标题：$title"
        else ""

    fun getSimplifiedName(): String = if (name != null && name != "无名氏") "作者：$name" else ""

    fun getImgUrl(): String = img?.let { img + ext } ?: ""
    fun isNotAd(): Boolean = (id != "9999999")
    fun isAd(): Boolean = !isNotAd()

    fun equalsWithServerData(target: Comment?): Boolean =
        if (target == null) false
        else id == target.id && userid == target.userid
                && name == target.name && sage == target.sage
                && admin == target.admin && status == target.status
                && title == target.title && email == target.email
                && now == target.now && content == target.content
                && img == target.img && ext == target.ext

    fun setUpdatedTimestamp(time: LocalDateTime = LocalDateTime.now()) {
        lastUpdatedAt = time
    }

    fun getPostId(): String = if (parentId == "0") id else parentId
}
