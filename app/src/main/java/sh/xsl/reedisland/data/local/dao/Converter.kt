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

package sh.xsl.reedisland.data.local.dao

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import sh.xsl.reedisland.data.local.entity.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class Converter {
    private val moshi = Moshi.Builder().build()

    @TypeConverter
    fun jsonToForumList(value: String): List<Forum> {
        return moshi.adapter<List<Forum>>(
            Types.newParameterizedType(
                List::class.java,
                Forum::class.java
            )
        ).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun forumListToJson(list: List<Forum>): String {
        return moshi.adapter<List<Forum>>(
            Types.newParameterizedType(
                List::class.java,
                Forum::class.java
            )
        ).toJson(list)
    }

    @TypeConverter
    fun jsonToKaomojiList(value: String): List<Emoji> {
        return moshi.adapter<List<Emoji>>(
            Types.newParameterizedType(
                List::class.java,
                Emoji::class.java
            )
        ).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun kaomojiListToJson(list: List<Emoji>): String {
        return moshi.adapter<List<Emoji>>(
            Types.newParameterizedType(
                List::class.java,
                Emoji::class.java
            )
        ).toJson(list)
    }

//    @TypeConverter
//    fun jsonToWhiteList(value: String): LuweiNotice.WhiteList {
//        return moshi.adapter(LuweiNotice.WhiteList::class.java).fromJson(value)!!
//    }
//
//    @TypeConverter
//    fun whiteListToJson(whiteList: LuweiNotice.WhiteList): String {
//        return moshi.adapter(LuweiNotice.WhiteList::class.java).toJson(whiteList)
//    }


    @TypeConverter
    fun jsonToTrendList(value: String): List<Trend> {
        return moshi.adapter<List<Trend>>(
            Types.newParameterizedType(
                List::class.java,
                Trend::class.java
            )
        ).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun trendListToJson(list: List<Trend>): String {
        return moshi.adapter<List<Trend>>(
            Types.newParameterizedType(
                List::class.java,
                Trend::class.java
            )
        ).toJson(list)
    }

    @TypeConverter
    fun jsonToNoticeForumList(value: String): List<NoticeForum> {
        return moshi.adapter<List<NoticeForum>>(
            Types.newParameterizedType(
                List::class.java,
                NoticeForum::class.java
            )
        ).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun noticeForumListToJson(list: List<NoticeForum>): String {
        return moshi.adapter<List<NoticeForum>>(
            Types.newParameterizedType(
                List::class.java,
                NoticeForum::class.java
            )
        ).toJson(list)
    }

    @TypeConverter
    fun jsonToStringBooleanMap(value: String): Map<String, Boolean> {
        return moshi.adapter<Map<String, Boolean>>(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Boolean::class.javaObjectType
            )
        ).fromJson(value) ?: emptyMap()
    }

    @TypeConverter
    fun stringBooleanMapToJson(map: Map<String, Boolean>): String {
        return moshi.adapter<Map<String, Boolean>>(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Boolean::class.javaObjectType
            )
        ).toJson(map)
    }

    @TypeConverter
    fun jsonToStringList(value: String): List<String> {
        return moshi.adapter<List<String>>(
            Types.newParameterizedType(
                List::class.java,
                String::class.java
            )
        ).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun stringListToJson(list: List<String>): String {
        return moshi.adapter<List<String>>(
            Types.newParameterizedType(
                List::class.java,
                String::class.java
            )
        ).toJson(list)
    }

//    @TypeConverter
//    fun jsonToClientsInfoMap(value: String): Map<String, LuweiNotice.ClientInfo> {
//        return moshi.adapter<Map<String, LuweiNotice.ClientInfo>>(
//            Types.newParameterizedType(
//                Map::class.java,
//                String::class.java,
//                LuweiNotice.ClientInfo::class.java
//            )
//        ).fromJson(value) ?: emptyMap()
//    }
//
//    @TypeConverter
//    fun clientsInfoMapToJson(map: Map<String, LuweiNotice.ClientInfo>): String {
//        return moshi.adapter<Map<String, LuweiNotice.ClientInfo>>(
//            Types.newParameterizedType(
//                Map::class.java,
//                String::class.java,
//                LuweiNotice.ClientInfo::class.java
//            )
//        ).toJson(map)
//    }

    @TypeConverter
    fun integerSetToString(set: MutableSet<Int>): String {
        return set.toString().removeSurrounding("[", "]")
    }

    @TypeConverter
    fun stringToIntegerSet(s: String): MutableSet<Int> {
        return s.splitToSequence(",").map { it.trim().toInt() }.toMutableSet()
    }

    @TypeConverter
    fun longToDate(dateLong: Long?): Date? {
        return dateLong?.let { Date(it) }
    }

    @TypeConverter
    fun dateToLong(date: Date?): Long? {
        return date?.let { date.time }
    }

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @TypeConverter
    fun stringToDateTime(string: String): LocalDateTime {
        return LocalDateTime.parse(string, dateTimeFormatter)
    }

    @TypeConverter
    fun dateTimeToString(dateTime: LocalDateTime): String {
        return dateTime.format(dateTimeFormatter)
    }

}