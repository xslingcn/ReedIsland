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

package sh.xsl.reedisland.data.repository

import android.content.res.Resources
import android.util.ArrayMap
import android.util.SparseArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.data.local.dao.*
import sh.xsl.reedisland.data.local.entity.*
import sh.xsl.reedisland.data.remote.APIMessageResponse
import sh.xsl.reedisland.data.remote.NMBServiceClient
import sh.xsl.reedisland.util.*
import timber.log.Timber
import java.time.LocalDateTime
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.set

@Singleton
class CommentRepository @Inject constructor(
    private val webService: NMBServiceClient,
    private val commentDao: CommentDao,
    private val postDao: PostDao,
    private val readingPageDao: ReadingPageDao,
    private val browsingHistoryDao: BrowsingHistoryDao,
    private val feedDao: FeedDao,
    private val notificationDao: NotificationDao
) {

    /** remember all pages for last 10 posts, using threadId and page as index
     *  pop posts by fifo
     */
    private val cacheCap = 10
    private val postMap = ArrayMap<String, Post>(cacheCap)
    private val commentsMap =
        ArrayMap<String, SparseArray<LiveData<DataResource<List<Comment>>>>>(cacheCap)
    private val readingPageMap = ArrayMap<String, ReadingPage>(cacheCap)
    private val browsingHistoryMap = ArrayMap<String, BrowsingHistory>(cacheCap)
    private val fifoPostList = mutableListOf<String>()

    fun getPo(id: String) = postMap[id]?.userid ?: ""
    fun getMaxPage(id: String) = postMap[id]?.getMaxPage() ?: 1
    fun getFid(id: String) = postMap[id]?.fid ?: ""

    suspend fun setPost(id: String, fid: String) {
        clearCache()
        Timber.d("Setting new $fid Thread: $id")
        fifoPostList.add(id)
        if (commentsMap[id] == null) commentsMap[id] = SparseArray()
        if (postMap[id] == null) postMap[id] = postDao.findPostByIdSync(id)
        if (readingPageMap[id] == null) readingPageMap[id] = getReadingPageOnId(id)
        if (browsingHistoryMap[id] == null) browsingHistoryMap[id] = getBrowsingHistoryOnId(id, fid)
        notificationDao.readNotificationByIdSync(id)
    }

    private suspend fun getReadingPageOnId(id: String): ReadingPage {
        return readingPageDao.getReadingPageById(id) ?: ReadingPage(id, 1)
    }

    private fun getBrowsingHistoryOnId(id: String, fid: String): BrowsingHistory =
        BrowsingHistory(LocalDateTime.now(), id, fid, mutableSetOf())

    // get default page
    fun getLandingPage(id: String): Int {
        return if (DawnApp.applicationDataStore.getReadingProgressStatus()) {
            readingPageMap[id]?.page ?: 1
        } else 1
    }

    fun getHeaderPost(id: String): Comment? = postMap[id]?.toComment()

    suspend fun saveReadingProgress(id: String, progress: Int) {
        val readingProgress = readingPageMap[id] ?: ReadingPage(id, progress)
        readingProgress.page = progress
        readingPageDao.insertReadingPageWithTimeStamp(readingProgress)
    }

    fun clearCache() {
        for (i in 0 until (commentsMap.size - cacheCap)) {
            fifoPostList.first().run {
                Timber.d("Reached cache Cap. Clearing ${this}...")
                postMap.remove(this)
                readingPageMap.remove(this)
                browsingHistoryMap.remove(this)
                commentsMap.remove(this)
            }
            fifoPostList.removeAt(0)
        }
    }

    /**
     *  A page does not include the header post in comments
     *  w. cookie, a page of comments can have 20 reply w. ad, or 19 reply w/o ad
     *  w/o cookie, always have 20 reply w. ad
     *  *** here DB only store nonAd data
     *  **********************************************
     *  By default, a post is only stored in the post table, but not stored in the comment table.
     *  However when requesting references, all references are stored as comment in comment table.
     *  Therefore, the first page can have or not have the header post, if using local cache
     */
    fun checkFullPage(id: String, page: Int): Boolean =
        (commentsMap[id]?.get(page)?.value?.data?.filter { it.isNotAd() }?.size ?: 0) == 19

    fun getCommentsOnPage(
        id: String,
        page: Int,
        remoteDataOnly: Boolean,
        localDataOnly: Boolean
    ): LiveData<DataResource<List<Comment>>> {
        if (commentsMap[id] == null) {
            commentsMap[id] = SparseArray()
        }
        commentsMap[id]!!.let {
            if (it[page] == null || remoteDataOnly) {
                it.put(page, getLivePage(id, page, remoteDataOnly, localDataOnly))
            }
            return it[page]
        }
    }

    private fun getLivePage(
        id: String,
        page: Int,
        remoteDataOnly: Boolean,
        localDataOnly: Boolean
    ): LiveData<DataResource<List<Comment>>> {
        return when {
            localDataOnly -> {
                getLocalData(id, page, localDataOnly)
            }
            remoteDataOnly -> {
                getServerData(id, page)
            }
            else -> {
                getCombinedData(id, page)
            }
        }
    }

    private fun getCombinedData(id: String, page: Int): LiveData<DataResource<List<Comment>>> {
        val result = MediatorLiveData<DataResource<List<Comment>>>()
        val cache = getLocalData(id, page)
        val remote = getServerData(id, page)
        var hasRemote = false
        result.value = DataResource.create()
        result.addSource(cache) {
            if ((!hasRemote || remote.value?.status == LoadingStatus.NO_DATA)
                && cache.value?.status == LoadingStatus.SUCCESS && page != 1
            ) {
                result.value = it
            }
        }
        result.addSource(remote) {
            // probably post is deleted on server but I have cache, so show message but keep data
            if (cache.value?.status == LoadingStatus.SUCCESS && it.status == LoadingStatus.NO_DATA) {
                result.value =
                    DataResource.create(cache.value!!.status, cache.value!!.data, it.message)
            } else {
                hasRemote = true
                result.value = it
            }
        }
        return result
    }

    /** localDataOnly is true only if post is deleted from server,
     *  but local cache has data.
     *  *****************************************************
     *  Note: local data does not guarantee having all pages in db,
     *  hence when trying to load a page without actual cache, error is shown
     */
    private fun getLocalData(
        id: String,
        page: Int,
        localDataOnly: Boolean = false
    ): LiveData<DataResource<List<Comment>>> {
        Timber.d("Querying local data for Post $id on $page")
        if (localDataOnly) {
            browsingHistoryMap[id]?.let {
                it.pages.add(page)
                GlobalScope.launch { browsingHistoryDao.insertOrUpdateBrowsingHistory(it) }
            }
        }
        return getLocalListDataResource(commentDao.findDistinctPageByParentId(id, page))
    }

    private fun getServerData(id: String, page: Int): LiveData<DataResource<List<Comment>>> {
        return liveData {
            Timber.d("Querying remote data for Post $id on $page")
            val response = DataResource.create(webService.getComments(id, page))
            if (response.status == LoadingStatus.SUCCESS) {
                emit(convertServerData(id, response.data!!, page))
            } else {
                emit(
                    DataResource.create(
                        response.status,
                        emptyList(),
                        response.message
                    )
                )
            }
        }
    }

    private suspend fun convertServerData(
        id: String,
        data: Post,
        page: Int
    ): DataResource<List<Comment>> {
        // update current thread with latest info
        if (data.fid.isBlank() && postMap[id]?.fid?.isNotBlank() == true) {
            data.fid = postMap[id]?.fid.toString()
        }
        // update postFid for browse history(search jump does not have fid)
        browsingHistoryMap[id]?.run {
            if (postFid != data.fid) {
                postFid = data.fid
            }
            pages.add(page)
            browsingHistoryDao.insertOrUpdateBrowsingHistory(this)
        }

        if (data != postMap[id]) {
            postMap[id] = data.stripCopy()
            savePost(id, data)
        }

        data.comments.map {
            it.page = page
            it.parentId = id
        }
        /**
         *  On the first or last page, show NO DATA to indicate footer load end;
         *  When entering to a thread with cached data, refresh header is showing,
         *  set LoadingStatus to Success to hide the header
         */

        saveComments(id, page, data.comments)
        return DataResource.create(LoadingStatus.SUCCESS, data.comments)
    }

    // DO NOT SAVE ADS
    private suspend fun saveComments(id: String, page: Int, serverComments: List<Comment>) =
        coroutineScope {
            launch {
                val cacheNoAd =
                    commentsMap[id]?.get(page)?.value?.data?.filter { it.isNotAd() } ?: emptyList()
                val serverNoAd = serverComments.filter { it.isNotAd() }
                Timber.d("Got ${serverComments.size} comments and ${serverComments.size - serverNoAd.size} ad from server")
                if (!cacheNoAd.equalsWithServerComments(serverNoAd)) {
                    Timber.d("Updating ${serverNoAd.size} rows for $id on $page")
                    commentDao.insertAllWithTimeStamp(serverNoAd)
                }
            }
        }

    private suspend fun savePost(id: String, post: Post) {
        postMap[id] = post.stripCopy()
        postDao.insertWithTimeStamp(post)
    }

    suspend fun addFeed(id: String): SingleLiveEvent<String> {
        Timber.d("Adding Feed $id")
        val cachedFeed = feedDao.findFeedByPostId(id)
        if (cachedFeed != null) {
            return SingleLiveEvent.create("已经订阅过了哦\n取消订阅请长按按钮")
        }

        return webService.addFeed(id).run {
            if (this is APIMessageResponse.Success && messageType == APIMessageResponse.MessageType.String && message == "Ok") {
                coroutineScope {
                    launch {
                        val newFeed = Feed(1, 1, id, DawnApp.currentDomain, LocalDateTime.now())
                        feedDao.addFeedToTopAndIncrementFeedIds(newFeed)
                    }
                }
                SingleLiveEvent.create("订阅成功(ゝ∀･)")
            } else {
                Timber.e("Response type: ${this.javaClass.simpleName} $message")
                SingleLiveEvent.create("订阅失败...$message")
            }
        }
    }

    suspend fun deleteFeed(id: String): SingleLiveEvent<String> {
        Timber.d("Deleting Feed $id")
        return webService.delFeed(id).run {
            if (this is APIMessageResponse.Success && messageType == APIMessageResponse.MessageType.String && message == "Ok") {
                coroutineScope { launch { feedDao.deleteFeedAndDecrementFeedIdsById(id) } }
                SingleLiveEvent.create("取消订阅成功(ゝ∀･)")
            } else {
                Timber.e("Response type: ${this.javaClass.simpleName} $message")
                SingleLiveEvent.create("删除订阅失败...$message")
            }
        }
    }
}