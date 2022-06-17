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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.data.local.entity.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class NMBServiceClient @Inject constructor(private val service: NMBService) {

    suspend fun getReedSession(): String {
        return withContext(Dispatchers.IO) {
            val response = service.getVersion().execute()
            if (response.isSuccessful) {
                response.headers().toMultimap()["set-cookie"]?.first { str ->
                    str.split(";")
                        .first { it.startsWith("REED_SESSION") && it.substringAfter("REED_SESSION=").length == 160 }
                        .isNotBlank()
                }?.split(";")?.first() ?: ""
            } else ""
        }
    }

    suspend fun getNMBSearch(
        query: String,
        page: Int = 1,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIDataResponse<SearchResult> {
        Timber.d("Getting search result for $query on Page $page...")
        return APIDataResponse.create(
            service.postNMBSearch(
                query.toRequestBody(),
                page.toString().toRequestBody(),
                if (userhash != null) reedSession.plus(";$userhash") else reedSession
            ),
            NMBJsonParser.SearchResultParser(query, page)
        )
    }

    suspend fun getPrivacyAgreement(): APIMessageResponse {
        return APIMessageResponse.create(service.getPrivacyAgreement())
    }

    suspend fun getChangeLog(): APIMessageResponse {
        return APIMessageResponse.create(service.getChangeLog())
    }

    suspend fun getRandomReedPicture(): APIDataResponse<String> {
        Timber.d("Getting Random Reed Picture...")
        return APIDataResponse.create(
            service.getRandomReedPicture(),
            NMBJsonParser.ReedRandomPictureParser()
        )
    }

    suspend fun getLatestRelease(): APIDataResponse<Release> {
        Timber.d("Checking Latest Version...")
        return APIDataResponse.create(service.getLatestRelease(), NMBJsonParser.ReleaseParser())
    }

    suspend fun getNMBNotice(): APIDataResponse<NMBNotice> {
        Timber.i("Downloading Notice...")
        return APIDataResponse.create(service.getConfig(), NMBJsonParser.NMBNoticeParser())
    }

    suspend fun getLuweiNotice(): APIDataResponse<LuweiNotice> {
        Timber.i("Downloading LuWeiNotice...")
        return APIDataResponse.create(service.getLuweiNotice(), NMBJsonParser.LuweiNoticeParser())
    }

    suspend fun getCommunities(): APIDataResponse<List<Community>> {
        Timber.i("Downloading Communities and Forums...")
        return APIDataResponse.create(service.getConfig(), NMBJsonParser.CommunityParser())
    }

    suspend fun getTimeLines(): APIDataResponse<List<Timeline>> {
        Timber.i("Downloading Timeline Forums...")
        return APIDataResponse.create(service.getConfig(), NMBJsonParser.TimelinesParser())
    }

    suspend fun getPosts(
        fid: String,
        page: Int,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIDataResponse<List<Post>> {
//        throw RuntimeException("Oh")
        Timber.i("Downloading Posts on Forum $fid...")
        val call = service.getNMBPosts(
            if (fid.startsWith("-")) fid.substringAfter("-")
            else fid, page,
            if (userhash != null) reedSession.plus(";$userhash")
            else reedSession
        )
        return APIDataResponse.create(call, NMBJsonParser.PostParser())
    }

    suspend fun getComments(
        id: String,
        page: Int,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIDataResponse<Post> {
        Timber.i("Downloading Comments on Post $id on Page $page...")
        return APIDataResponse.create(
            service.getNMBComments(
                if (userhash != null) reedSession.plus(";$userhash")
                else reedSession, id, page
            ),
            NMBJsonParser.CommentParser()
        )
    }

    suspend fun getFeeds(
        page: Int,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIDataResponse<List<Feed.ServerFeed>> {
        Timber.i("Downloading Feeds on Page $page...")
        return APIDataResponse.create(
            service.getNMBFeeds(
                page,
                if (userhash != null) reedSession.plus(";$userhash") else reedSession
            ), NMBJsonParser.FeedParser()
        )
    }


    // Returns BlankDataResponse(not Error) when comment is deleted
    suspend fun getQuote(
        id: String,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIDataResponse<Comment> {
        Timber.i("Downloading Quote $id...")
        return APIDataResponse.create(
            service.getNMBQuote(
                id,
                if (userhash != null) reedSession.plus(";$userhash") else reedSession
            ), NMBJsonParser.QuoteParser()
        )
    }

    suspend fun addFeed(
        tid: String,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIMessageResponse {
        Timber.i("Adding Feed $tid...")
        return APIMessageResponse.create(
            service.addNMBFeed(
                tid,
                if (userhash != null) reedSession.plus(";$userhash") else reedSession
            )
        )
    }

    suspend fun delFeed(
        tid: String,
        userhash: String? = DawnApp.applicationDataStore.firstCookieHash,
        reedSession: String = DawnApp.applicationDataStore.reedSession
    ): APIMessageResponse {
        Timber.i("Deleting Feed $tid...")
        return APIMessageResponse.create(
            service.delNMBFeed(
                tid,
                if (userhash != null) reedSession.plus(";$userhash") else reedSession
            )
        )
    }

    // Note: userhash should be already converted to header style beforehand
    // i.e. "userhash=v%C6%CB...."
    suspend fun sendPost(
        newPost: Boolean,
        targetId: String, name: String?,
        email: String?, title: String?,
        content: String?, water: String?,
        image: File?, userhash: String,
        reedSession: String = DawnApp.applicationDataStore.reedSession,
        report: Boolean? = null
    ): APIMessageResponse {
        return withContext(Dispatchers.IO) {
            Timber.d("Posting to $targetId...")
            var imagePart: MultipartBody.Part? = null
            image?.run {
                asRequestBody(("image/${image.extension}").toMediaTypeOrNull()).run {
                    imagePart = MultipartBody.Part.createFormData("image", image.name, this)
                }
            }
            val call = if (report == true) {
                service.postReport(
                    targetId.toRequestBody(),
                    content!!.toRequestBody(),
                    reedSession.plus(";$userhash")
                )
            } else if (newPost) {
                service.postNewPost(
                    targetId.toRequestBody(), name?.toRequestBody(),
                    email?.toRequestBody(), title?.toRequestBody(),
                    content?.toRequestBody(), water?.toRequestBody(),
                    imagePart,
                    reedSession.plus(";$userhash")
                )
            } else {
                service.postComment(
                    targetId.toRequestBody(), name?.toRequestBody(),
                    email?.toRequestBody(), title?.toRequestBody(),
                    content?.toRequestBody(), water?.toRequestBody(),
                    imagePart,
                    reedSession.plus(";$userhash")
                )
            }
            APIMessageResponse.create(call)
        }
    }


}