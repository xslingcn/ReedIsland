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

package sh.xsl.reedisland.data.remote

import com.squareup.moshi.*
import org.json.JSONObject
import sh.xsl.reedisland.data.local.entity.*
import sh.xsl.reedisland.util.ReadableTime
import timber.log.Timber
import java.time.LocalDateTime


abstract class NMBJsonParser<T> {
    abstract fun parse(response: String): T

    companion object {
        private val moshi: Moshi = Moshi.Builder().add(LocalDateTimeAdapter()).build()
    }

    class ReleaseParser : NMBJsonParser<Release>() {
        override fun parse(response: String): Release {
            return JSONObject(response).run {
                Release(1, optString("tag_name"), optString("html_url"), optString("body"))
            }
        }
    }

    class ConfigParser : NMBJsonParser<Config>() {
        override fun parse(response: String): Config {
            return moshi.adapter(Config::class.java).fromJson(response)!!
        }
    }

    class NMBNoticeParser : NMBJsonParser<NMBNotice>() {
        override fun parse(response: String): NMBNotice {
            val notice: String = JSONObject(response).optString("siteNotify")
            return moshi.adapter(NMBNotice::class.java)
                .fromJson(JSONObject().put("content", notice).toString())!!
        }
    }

//    class LuweiNoticeParser : NMBJsonParser<LuweiNotice>() {
//        override fun parse(response: String): LuweiNotice {
//            return moshi.adapter(LuweiNotice::class.java).fromJson(response)!!
//        }
//    }

    class DawnNoticeParser : NMBJsonParser<DawnNotice>() {
        override fun parse(response: String): DawnNotice {
            return moshi.adapter(DawnNotice::class.java).fromJson(response)!!
        }
    }

    class CommunityParser : NMBJsonParser<List<Community>>() {
        override fun parse(response: String): List<Community> {
            val forumList = JSONObject(response).run { optJSONArray("forumListV1") }!!
            return moshi.adapter<List<Community>>(
                Types.newParameterizedType(List::class.java, Community::class.java)
            ).fromJson(forumList.toString())!!
        }
    }

    class PostParser : NMBJsonParser<List<Post>>() {
        override fun parse(response: String): List<Post> {
            return moshi.adapter<List<Post>>(
                Types.newParameterizedType(List::class.java, Post::class.java)
            ).fromJson(response)!!
        }
    }

    class FeedParser : NMBJsonParser<List<Feed.ServerFeed>>() {
        override fun parse(response: String): List<Feed.ServerFeed> {
            Timber.d(response)
            val feedList = JSONObject(response).run { optJSONArray("list") }!!
            return moshi.adapter<List<Feed.ServerFeed>>(
                Types.newParameterizedType(List::class.java, Feed.ServerFeed::class.java)
            ).fromJson(feedList.toString())!!
        }
    }

    class CommentParser : NMBJsonParser<Post>() {
        override fun parse(response: String): Post {
            return moshi.adapter(Post::class.java).fromJson(response)!!
        }
    }

    class QuoteParser : NMBJsonParser<Comment>() {
        override fun parse(response: String): Comment {
            return moshi.adapter(Comment::class.java).fromJson(response)!!
        }
    }

    class BrowsingHistoryParser : NMBJsonParser<List<Post>>() {
        override fun parse(response: String): List<Post> {
            TODO("Not yet implemented")
        }
    }

    class PostHistoryParser : NMBJsonParser<List<Comment>>() {
        override fun parse(response: String): List<Comment> {
            TODO("Not yet implemented")
        }
    }

    class SearchResultParser(val query: String, val page: Int) : NMBJsonParser<SearchResult>() {
        override fun parse(response: String): SearchResult {
            return JSONObject(response).run {
                val count = optInt("total")
                val hitsList = mutableListOf<Comment>()
                optJSONArray("threads")?.run {
                    for (i in 0 until length()) {
                        val hitObject = getJSONObject(i)
                        val hit =
                            moshi.adapter(Comment::class.java).fromJson(hitObject.toString())!!
                        hitsList.add(hit)
                    }
                }
                SearchResult(query, count, page, hitsList)
            }
        }
    }

    class ReedRandomPictureParser : NMBJsonParser<String>() {
        override fun parse(response: String): String {
            return response
        }
    }

    class LocalDateTimeAdapter {
        @ToJson
        fun toJson(dateTime: LocalDateTime): String {
            return dateTime.format(ReadableTime.SERVER_DATETIME_FORMAT)
        }

        @FromJson
        fun fromJson(str: String): LocalDateTime {
            return try {
                ReadableTime.serverTimeStringToServerLocalDateTime(str)
            } catch (e: Exception) {
                throw JsonDataException("Unknown DateTime String: $str")
            }
        }
    }
}