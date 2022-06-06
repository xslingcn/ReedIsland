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
import androidx.room.PrimaryKey
import sh.xsl.reedisland.DawnApp
import java.time.LocalDateTime

@Entity
data class ReadingPage(
    @PrimaryKey val id: String,
    var page: Int,
    val domain: String = DawnApp.currentDomain,
    var lastUpdatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun setUpdatedTimestamp(time: LocalDateTime = LocalDateTime.now()) {
        lastUpdatedAt = time
    }
}