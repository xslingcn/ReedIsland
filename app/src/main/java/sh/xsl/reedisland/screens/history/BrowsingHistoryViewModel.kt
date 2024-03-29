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
import sh.xsl.reedisland.data.local.dao.BrowsingHistoryDao
import sh.xsl.reedisland.data.local.entity.BrowsingHistoryAndPost
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class BrowsingHistoryViewModel @Inject constructor(private val browsingHistoryDao: BrowsingHistoryDao) :
    ViewModel() {

    // get a week's history by default
    var endDate: LocalDateTime = LocalDate.now().atTime(23, 59)
    var startDate: LocalDateTime = LocalDate.now().atTime(0, 0).minusWeeks(1)
    private var currentList: LiveData<List<BrowsingHistoryAndPost>>? = null
    val browsingHistoryList = MediatorLiveData<List<BrowsingHistoryAndPost>>()

    init {
        searchByDate()
    }

    fun searchByDate() {
        if (currentList != null) browsingHistoryList.removeSource(currentList!!)
        currentList = getLiveHistoryInRange(startDate, endDate)
        browsingHistoryList.addSource(currentList!!) {
            browsingHistoryList.value = it
        }
    }

    private fun getLiveHistoryInRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): LiveData<List<BrowsingHistoryAndPost>> =
        browsingHistoryDao.getAllBrowsingHistoryAndPostInDateRange(startDate, endDate)
}