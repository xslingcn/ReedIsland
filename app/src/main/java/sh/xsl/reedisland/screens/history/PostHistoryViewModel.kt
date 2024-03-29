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

package sh.xsl.reedisland.screens.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import sh.xsl.reedisland.data.local.dao.PostHistoryDao
import sh.xsl.reedisland.data.local.entity.PostHistory
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class PostHistoryViewModel @Inject constructor(private val postHistoryDao: PostHistoryDao) :
    ViewModel() {
    var endDate: LocalDateTime = LocalDate.now().atTime(23, 59)
    var startDate: LocalDateTime = LocalDate.now().atTime(0, 0).minusWeeks(1)
    private var currentList: LiveData<List<PostHistory>>? = null
    val postHistoryList = MediatorLiveData<List<PostHistory>>()

    init {
        searchByDate()
    }

    fun searchByDate() {
        if (currentList != null) postHistoryList.removeSource(currentList!!)
        currentList = getLiveHistoryInRange(startDate, endDate)
        postHistoryList.addSource(currentList!!) {
            postHistoryList.value = it
        }
    }

    private fun getLiveHistoryInRange(startDate: LocalDateTime, endDate: LocalDateTime) =
        postHistoryDao.getAllPostHistoryInDateRange(startDate, endDate)
}