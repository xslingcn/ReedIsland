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

import android.util.SparseArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.data.local.dao.FeedDao
import sh.xsl.reedisland.data.local.entity.Feed
import sh.xsl.reedisland.data.local.entity.FeedAndPost
import sh.xsl.reedisland.data.local.entity.Post
import sh.xsl.reedisland.data.remote.APIMessageResponse
import sh.xsl.reedisland.data.remote.NMBServiceClient
import sh.xsl.reedisland.util.DataResource
import sh.xsl.reedisland.util.LoadingStatus
import sh.xsl.reedisland.util.getLocalListDataResource
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val webService: NMBServiceClient,
    private val feedDao: FeedDao
) {
    private val feedsMap = SparseArray<LiveData<DataResource<List<FeedAndPost>>>>()

    fun clearCache() {
        feedsMap.clear()
    }

    fun getLiveFeedPage(page: Int): LiveData<DataResource<List<FeedAndPost>>> {
        feedsMap.put(page, getCombinedFeedPage(page))
        return feedsMap[page]!!
    }

    // Remote only acts as a request status responder, actual data will be emitted by local cache
    private fun getCombinedFeedPage(page: Int): LiveData<DataResource<List<FeedAndPost>>> {
        val result = MediatorLiveData<DataResource<List<FeedAndPost>>>()
        val cache = getLocalData(page)
        val remote = getServerData(page)
        result.addSource(cache) {
            if (it.status == LoadingStatus.SUCCESS) {
                result.value = it
            }
        }
        result.addSource(remote) {
            if (it.status == LoadingStatus.NO_DATA || it.status == LoadingStatus.ERROR) {
                result.value = it
            }
        }
        return result
    }

    private fun getLocalData(page: Int): LiveData<DataResource<List<FeedAndPost>>> {
        Timber.d("Querying local Feed on $page")
        return getLocalListDataResource(feedDao.getDistinctFeedAndPostOnPage(page))
    }

    private fun getServerData(page: Int): LiveData<DataResource<List<FeedAndPost>>> {
        return liveData {
            Timber.d("Querying remote Feed on $page")
            val response =
                DataResource.create(webService.getFeeds(page))
            if (response.status == LoadingStatus.SUCCESS) {
                emit(
                    DataResource.create<List<FeedAndPost>>(
                        convertFeedData(response.data!!, page),
                        emptyList()
                    )
                )
            } else {
                emit(
                    DataResource.create<List<FeedAndPost>>(
                        response.status,
                        emptyList(),
                        "无法读取订阅...\n${response.message}"
                    )
                )
            }
        }
    }

    // Note only return request status
    private suspend fun convertFeedData(data: List<Feed.ServerFeed>, page: Int): LoadingStatus {
        if (data.isEmpty()) {
            return LoadingStatus.NO_DATA
        }
        val feeds = mutableListOf<Feed>()
        val posts = mutableListOf<Post>()
        val baseIndex = (page - 1) * 10 + 1
        val timestamp = LocalDateTime.now()
        data.mapIndexed { index, serverFeed ->
            feeds.add(
                Feed(
                    baseIndex + index,
                    page,
                    serverFeed.id,
                    DawnApp.currentDomain,
                    timestamp
                )
            )
            posts.add(serverFeed.toPost())
        }

        val cacheFeed = feedsMap[page]?.value?.data?.map { it.feed } ?: emptyList()
        if (cacheFeed != feeds) {
            coroutineScope {
                launch {
                    feedDao.insertAllFeed(feeds)
                    feedDao.insertAllPostIfNotExist(posts)
                }
            }
        }
        return LoadingStatus.SUCCESS
    }

    suspend fun deleteFeed(feed: Feed): String {
        Timber.d("Deleting Feed ${feed.postId}")
        return webService.delFeed(feed.postId).run {
            if (this is APIMessageResponse.Success) {
                coroutineScope { launch { feedDao.deleteFeedAndDecrementFeedIds(feed) } }
                message
            } else {
                Timber.e(message)
                "删除订阅失败"
            }
        }
    }
}