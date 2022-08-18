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

package sh.xsl.reedisland.screens.notification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import sh.xsl.reedisland.data.local.dao.NotificationDao
import sh.xsl.reedisland.data.local.entity.Notification
import sh.xsl.reedisland.data.local.entity.NotificationAndPost
import timber.log.Timber
import javax.inject.Inject

class NotificationViewModel @Inject constructor(private val notificationDao: NotificationDao) :
    ViewModel() {
    private var _notificationAndPost: LiveData<List<NotificationAndPost>>? = null
    val notificationAndPost = MediatorLiveData<List<NotificationAndPost>>()

    init {
        getLiveNotifications()
    }

    private fun getLiveNotifications() {
        Timber.d("Getting live notifications...")
        if (_notificationAndPost != null) notificationAndPost.removeSource(_notificationAndPost!!)
        _notificationAndPost = notificationDao.getLiveAllNotificationsAndPosts()
        notificationAndPost.addSource(_notificationAndPost!!) {
            notificationAndPost.value = it
        }
    }

    fun deleteNotification(notification: Notification) {
        viewModelScope.launch {
            notificationDao.deleteNotifications(notification)
        }
    }

    fun readNotification(notification: Notification) {
        viewModelScope.launch {
            notification.read = true
            notification.newReplyCount = 0
            notificationDao.insertNotification(notification)
        }
    }
}