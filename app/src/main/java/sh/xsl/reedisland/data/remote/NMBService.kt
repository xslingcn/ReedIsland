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

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
import sh.xsl.reedisland.util.DawnConstants

interface NMBService {
    @GET("https://raw.githubusercontent.com/fishballzzz/DawnIslandK/master/CHANGELOG.md")
    fun getChangeLog(): Call<ResponseBody>

    @GET("https://raw.githubusercontent.com/fishballzzz/DawnIslandK/master/privacy_policy_CN.html")
    fun getPrivacyAgreement(): Call<ResponseBody>

    @GET("https://reed.mfweb.top/Functions/Pictures/GetRandomPicture")
    fun getRandomReedPicture(): Call<ResponseBody>

    @GET("https://api.github.com/repos/xslingcn/ReedIsland/releases/latest")
    fun getLatestRelease(): Call<ResponseBody>

    @GET("https://cover.acfunwiki.org/nmb-notice.json")
    fun getNMBNotice(): Call<ResponseBody>

    @GET("https://cover.acfunwiki.org/luwei.json")
    fun getLuweiNotice(): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v2/system/getConfig")
    fun getConfig(): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v2/system/getVersion")
    fun getVersion(): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v1/showf")
    fun getNMBPosts(
        @Query("id") fid: String,
        @Query("page") page: Int,
        @Header("Cookie") cookie: String
    ): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v1/feed")
    fun getNMBFeeds(@Query("page") page: Int, @Header("Cookie") cookie: String): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v1/addFeed")
    fun addNMBFeed(@Query("tid") tid: String, @Header("Cookie") cookie: String): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v1/delFeed")
    fun delNMBFeed(@Query("tid") tid: String, @Header("Cookie") cookie: String): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v1/thread")
    fun getNMBComments(
        @Header("Cookie") hash: String,
        @Query("id") id: String,
        @Query("page") page: Int
    ): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @GET("api/v1/ref")
    fun getNMBQuote(@Query("id") id: String, @Header("Cookie") cookie: String): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @Multipart
    @POST("api/v1/Home/Forum/doReplyThread.html")
    fun postComment(
        @Part("resto") resto: RequestBody, @Part("name") name: RequestBody?,
        @Part("email") email: RequestBody?, @Part("title") title: RequestBody?,
        @Part("content") content: RequestBody?, @Part("water") water: RequestBody?,
        @Part image: MultipartBody.Part?, @Header("Cookie") hash: String
    ): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @Multipart
    @POST("api/v1/Home/Forum/doPostThread.html")
    fun postNewPost(
        @Part("fid") fid: RequestBody, @Part("name") name: RequestBody?,
        @Part("email") email: RequestBody?, @Part("title") title: RequestBody?,
        @Part("content") content: RequestBody?, @Part("water") water: RequestBody?,
        @Part image: MultipartBody.Part?, @Header("Cookie") hash: String
    ): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @Multipart
    @POST("api/v2/dutyRoom/report")
    fun postReport(
        @Part("threadId") tid: RequestBody, @Part("reason") reason: RequestBody,
        @Header("Cookie") hash: String
    ): Call<ResponseBody>

    @Headers(DawnConstants.USER_AGENT)
    @Multipart
    @POST("api/v1/search")
    fun postNMBSearch(
        @Part("keyword") query: RequestBody, @Part("page") page: RequestBody,
        @Header("Cookie") cookie: String
    ): Call<ResponseBody>
}

