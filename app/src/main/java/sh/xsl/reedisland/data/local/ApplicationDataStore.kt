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

package sh.xsl.reedisland.data.local

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.jessyan.retrofiturlmanager.RetrofitUrlManager
import sh.xsl.reedisland.data.local.dao.*
import sh.xsl.reedisland.data.local.entity.*
import sh.xsl.reedisland.data.remote.APIDataResponse
import sh.xsl.reedisland.data.remote.NMBServiceClient
import sh.xsl.reedisland.util.DawnConstants
import sh.xsl.reedisland.util.lazyOnMainOnly
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationDataStore @Inject constructor(
    private val cookieDao: CookieDao,
    private val reedSessionDao: ReedSessionDao,
    private val commentDao: CommentDao,
    private val trendDao: DailyTrendDao,
    private val feedDao: FeedDao,
    private val NMBNoticeDao: NMBNoticeDao,
    private val dawnNoticeDao: DawnNoticeDao,
//    private val luweiNoticeDao: LuweiNoticeDao,
    private val releaseDao: ReleaseDao,
    private val blockedIdDao: BlockedIdDao,
    private val webService: NMBServiceClient
) {

    private var mCookies = mutableListOf<Cookie>()
    val cookies: List<Cookie> get() = mCookies
    val firstCookieHash get() = cookies.firstOrNull()?.getApiHeaderCookieHash()
    lateinit var reedSession: ReedSession
    val reedSessionCookie get() = reedSession.cookie

    fun setLastUsedCookie(cookie: Cookie) {
        if (cookie != cookies.firstOrNull()) {
            mCookies.remove(cookie)
            mCookies.add(0, cookie)
            GlobalScope.launch {
                cookieDao.setLastUsedCookie(cookie)
            }
        }
    }

//    var luweiNotice: LuweiNotice? = null
//        private set

    var nmbNotice: NMBNotice? = null
        private set

    var dawnNotice: DawnNotice? = null
        private set

    val mmkv: MMKV by lazyOnMainOnly { MMKV.defaultMMKV() }

    val defaultTheme: Int by lazyOnMainOnly {
        mmkv.getInt(DawnConstants.DEFAULT_THEME, 0)
    }

    fun setDefaultTheme(theme: Int) {
        mmkv.putInt(DawnConstants.DEFAULT_THEME, theme)
    }

    private var baseCDN: String? = null
    fun getBaseCDN(): String {
        if (baseCDN == null) {
            baseCDN = mmkv.getString(DawnConstants.DEFAULT_CDN, "auto")
        }
        return baseCDN!!
    }

    fun setBaseCDN(newHost: String) {
        baseCDN = newHost
        mmkv.putString(DawnConstants.DEFAULT_CDN, newHost)
        if (newHost != "auto") RetrofitUrlManager.getInstance().putDomain("nmb", baseCDN)
    }

    private var refCDN: String? = null
    fun getRefCDN(): String {
        if (refCDN == null) {
            refCDN = mmkv.getString(DawnConstants.REF_CDN, "auto")
        }
        return refCDN!!
    }

    fun setRefCDN(newHost: String) {
        refCDN = newHost
        mmkv.putString(DawnConstants.REF_CDN, newHost)
        if (newHost != "auto") RetrofitUrlManager.getInstance().putDomain("nmb-ref", refCDN)
    }

//    private var feedId: String? = null
//    fun getFeedId(): String {
//        if (feedId == null) {
//            feedId = mmkv.getString(DawnConstants.FEED_ID, "")
//        }
//        if (feedId.isNullOrBlank()) {
//            setFeedId(UUID.randomUUID().toString())
//        }
//        return feedId!!
//    }
//
//    fun setFeedId(value: String) {
//        if (feedId != value) {
//            GlobalScope.launch { feedDao.nukeTable() }
//        }
//        feedId = value
//        mmkv.putString(DawnConstants.FEED_ID, value)
//    }

    private var expandedCommunityIDs: Set<String>? = null

    fun getExpandedCommunityIDs(): Set<String> {
        if (expandedCommunityIDs == null) {
            expandedCommunityIDs =
                mmkv.getStringSet(DawnConstants.EXPANDED_COMMUNITY_IDS, setOf("6", "11"))
        }
        return expandedCommunityIDs!!
    }

    fun setExpandedCommunityIDs(set: Set<String>) {
        expandedCommunityIDs = set
        mmkv.putStringSet(DawnConstants.EXPANDED_COMMUNITY_IDS, set)
    }

    private var defaultForumId: String? = null

    fun getDefaultForumId(): String {
        if (defaultForumId == null) {
            defaultForumId =
                mmkv.getString(DawnConstants.DEFAULT_FORUM_ID, DawnConstants.TIMELINE_FORUM_ID)
        }
        return defaultForumId!!
    }

    fun setDefaultForumId(fid: String) {
        defaultForumId = fid
        mmkv.putString(DawnConstants.DEFAULT_FORUM_ID, fid)
    }

    private var firstTimeUse: Boolean? = null

    fun getFirstTimeUse(): Boolean {
        if (firstTimeUse == null) {
            firstTimeUse = mmkv.getBoolean(DawnConstants.USE_APP_FIRST_TIME, true)
        }
        return firstTimeUse!!
    }

    fun setFirstTimeUse() {
        firstTimeUse = false
        mmkv.putBoolean(DawnConstants.USE_APP_FIRST_TIME, false)
    }

    // View settings
    val letterSpace by lazyOnMainOnly { mmkv.getFloat(DawnConstants.LETTER_SPACE, 0f) }
    val lineHeight by lazyOnMainOnly { mmkv.getInt(DawnConstants.LINE_HEIGHT, 0) }
    val segGap by lazyOnMainOnly { mmkv.getInt(DawnConstants.SEG_GAP, 0) }
    val textSize by lazyOnMainOnly { mmkv.getFloat(DawnConstants.MAIN_TEXT_SIZE, 15f) }

    private var layoutCustomizationStatus: Boolean? = null
    fun getLayoutCustomizationStatus(): Boolean {
        if (layoutCustomizationStatus == null) {
            layoutCustomizationStatus = mmkv.getBoolean(DawnConstants.LAYOUT_CUSTOMIZATION, true)
        }
        return layoutCustomizationStatus!!
    }

    fun setLayoutCustomizationStatus(value: Boolean) {
        layoutCustomizationStatus = value
        mmkv.putBoolean(DawnConstants.LAYOUT_CUSTOMIZATION, value)
    }

    private var sortEmojiByLastUsedStatus: Boolean? = null
    fun getSortEmojiByLastUsedStatus(): Boolean {
        if (sortEmojiByLastUsedStatus == null) {
            sortEmojiByLastUsedStatus =
                mmkv.getBoolean(DawnConstants.SORT_EMOJI_BY_LAST_USED_AT, true)
        }
        return sortEmojiByLastUsedStatus!!
    }

    fun setSortEmojiByLastUsedStatus(value: Boolean) {
        sortEmojiByLastUsedStatus = value
        mmkv.putBoolean(DawnConstants.SORT_EMOJI_BY_LAST_USED_AT, value)
    }


    private var customToolbarImageStatus: Boolean? = null
    fun getCustomToolbarImageStatus(): Boolean {
        if (customToolbarImageStatus == null) {
            customToolbarImageStatus = mmkv.getBoolean(DawnConstants.CUSTOM_TOOLBAR_STATUS, true)
        }
        return customToolbarImageStatus!!
    }

    fun setCustomToolbarImageStatus(value: Boolean) {
        customToolbarImageStatus = value
        mmkv.putBoolean(DawnConstants.CUSTOM_TOOLBAR_STATUS, value)
    }

    private var customToolbarImagePath: String? = null
    fun getCustomToolbarImagePath(): String {
        if (customToolbarImagePath == null) {
            customToolbarImagePath = mmkv.getString(DawnConstants.TOOLBAR_IMAGE_PATH, "")
        }
        return customToolbarImagePath!!
    }

    fun setCustomToolbarImagePath(value: String) {
        setCustomToolbarImageStatus(true)
        customToolbarImagePath = value
        mmkv.putString(DawnConstants.TOOLBAR_IMAGE_PATH, value)
    }

    val displayTimeFormat: Int by lazyOnMainOnly {
        mmkv.getInt(
            DawnConstants.DISPLAY_TIME_FORMAT,
            DawnConstants.DEFAULT_TIME_FORMAT
        )
    }

    fun setDisplayTimeFormat(format: Int) {
        mmkv.putInt(DawnConstants.DISPLAY_TIME_FORMAT, format)
    }

    // adapter settings
    val animationOption by lazyOnMainOnly { mmkv.getInt(DawnConstants.ANIMATION_OPTION, 0) }
    fun setAnimationOption(option: Int) {
        mmkv.putInt(DawnConstants.ANIMATION_OPTION, option)
    }

    val animationFirstOnly by lazyOnMainOnly {
        mmkv.getBoolean(
            DawnConstants.ANIMATION_FIRST_ONLY,
            false
        )
    }

    fun setAnimationFirstOnly(status: Boolean) {
        mmkv.putBoolean(DawnConstants.ANIMATION_FIRST_ONLY, status)
    }

    // Reading settings
    private var readingProgressStatus: Boolean? = null
    fun getReadingProgressStatus(): Boolean {
        if (readingProgressStatus == null) {
            readingProgressStatus = mmkv.getBoolean(DawnConstants.READING_PROGRESS, true)
        }
        return readingProgressStatus!!
    }

    fun setReadingProgressStatus(value: Boolean) {
        readingProgressStatus = value
        mmkv.putBoolean(DawnConstants.READING_PROGRESS, value)
    }

    // view caching
    private var viewCaching: Boolean? = null
    fun getViewCaching(): Boolean {
        if (viewCaching == null) {
            viewCaching = mmkv.getBoolean(DawnConstants.VIEW_CACHING, false)
        }
        return viewCaching!!
    }

    fun setViewCaching(value: Boolean) {
        viewCaching = value
        mmkv.putBoolean(DawnConstants.VIEW_CACHING, value)
    }

    private var autoUpdateFeed: Boolean? = null
    fun getAutoUpdateFeed(): Boolean {
        if (autoUpdateFeed == null) {
            autoUpdateFeed = mmkv.getBoolean(DawnConstants.AUTO_UPDATE_FEED, false)
        }
        return autoUpdateFeed!!
    }

    fun setAutoUpdateFeed(value: Boolean) {
        autoUpdateFeed = value
        mmkv.putBoolean(DawnConstants.AUTO_UPDATE_FEED, value)
    }

    private var autoUpdateFeedDot: Boolean? = null
    fun getAutoUpdateFeedDot(): Boolean {
        if (autoUpdateFeedDot == null) {
            autoUpdateFeedDot = mmkv.getBoolean(DawnConstants.AUTO_UPDATE_FEED_DOT, true)
        }
        return autoUpdateFeedDot!!
    }

    fun setAutoUpdateFeedDot(value: Boolean) {
        autoUpdateFeedDot = value
        mmkv.putBoolean(DawnConstants.AUTO_UPDATE_FEED_DOT, value)
    }

    suspend fun loadCookies() {
        mCookies = cookieDao.getAll().toMutableList()
    }

    fun getCookieDisplayName(cookieName: String): String? =
        cookies.firstOrNull { it.cookieName == cookieName }?.cookieDisplayName

    suspend fun addCookie(cookie: Cookie) {
        cookies.firstOrNull { it.cookieHash == cookie.cookieHash }?.let {
            it.cookieDisplayName = cookie.cookieDisplayName
            cookieDao.updateCookie(it)
            return
        }
        mCookies.add(cookie)
        cookieDao.insert(cookie)
    }

    suspend fun deleteCookies(cookie: Cookie) {
        mCookies.remove(cookie)
        cookieDao.delete(cookie)
    }

    fun nukeCommentTable() {
        GlobalScope.launch { commentDao.nukeTable() }
    }

    fun nukeTrendTable() {
        GlobalScope.launch { trendDao.nukeTable() }
    }

    fun nukeBlockedPostTable() {
        GlobalScope.launch { blockedIdDao.nukeBlockedPostIds() }
    }

    suspend fun getLatestNMBNotice(): NMBNotice? {
        nmbNotice = NMBNoticeDao.getLatestNMBNotice()
        webService.getNMBNotice().run {
            if (this is APIDataResponse.Success) {
                if (nmbNotice == null || data!!.content != nmbNotice!!.content) {
                    coroutineScope { launch { NMBNoticeDao.insertNMBNoticeWithTimestamp(data!!) } }
                    nmbNotice = data
                }
            } else {
                Timber.e(message)
            }
        }
        if (nmbNotice?.read != true) {
            return nmbNotice
        }
        return null
    }

    suspend fun readNMBNotice(notice: NMBNotice) {
        NMBNoticeDao.updateNMBNoticeWithTimestamp(
            notice.content,
            notice.enable,
            notice.read
        )
    }

    suspend fun getReedSession() {
        reedSessionDao.get()?.let {
            val daysSinceUpdate =
                Duration.between(it.lastUpdatedAt, LocalDateTime.now()).toDays()
            if (it.cookie.substringAfter("=").length == 160 && daysSinceUpdate < 90)
                reedSession = it
            else updateReedSession(it)
        } ?: updateReedSession()
    }

    private suspend fun updateReedSession(oldSession: ReedSession? = null) {
        val session = ReedSession(webService.getReedSession())
        oldSession?.let { reedSessionDao.delete(it) }
        reedSessionDao.insert(session)
        reedSession = session
    }

//    suspend fun getLatestLuweiNotice(): LuweiNotice? {
//        luweiNotice = luweiNoticeDao.getLatestLuweiNotice()
//        webService.getLuweiNotice().run {
//            if (this is APIDataResponse.Success) {
//                if (luweiNotice != data) {
//                    luweiNotice = data
//                    coroutineScope { launch { luweiNoticeDao.insertNoticeWithTimestamp(data!!) } }
//                }
//            } else {
//                Timber.e(message)
//            }
//        }
//        return luweiNotice
//    }

    suspend fun getLatestDawnNotice(): DawnNotice? {
        dawnNotice = dawnNoticeDao.getLatestDawnNotice()
        webService.getDawnNotice().run {
            if (this is APIDataResponse.Success) {
                if (dawnNotice != data) dawnNotice = data
                coroutineScope { launch { dawnNoticeDao.insertNoticeWithTimestamp(data!!) } }
            } else {
                Timber.e(message)
            }
        }
        return dawnNotice
    }

    fun checkAcknowledgementPostingRule(): Boolean {
        return mmkv.getBoolean(DawnConstants.ACKNOWLEDGE_POSTING_RULES, false)
    }

    fun acknowledgementPostingRule() {
        mmkv.putBoolean(DawnConstants.ACKNOWLEDGE_POSTING_RULES, true)
    }

    suspend fun getLatestRelease(): Release? {
        // TODO: add update check frequency
//        val currentVersion = releaseDao.getLatestRelease()
//        if (currentVersion == null) {
//            val currentRelease = Release(1, BuildConfig.VERSION_NAME, "", "default entry",Date().time)
//            coroutineScope { launch { releaseDao.insertRelease(currentRelease) } }
//        }
        val currentVersionCode =
            sh.xsl.reedisland.BuildConfig.VERSION_NAME.filter { it.isDigit() }.toInt()
        val latest = webService.getLatestRelease().run {
            if (this is APIDataResponse.Success) data
            else {
                Timber.e(message)
                null
            }
        }
        Timber.d("latest release: $latest")
        Timber.d("current version: $currentVersionCode")
        if (latest != null && latest.versionCode > currentVersionCode) {
            coroutineScope { launch { releaseDao.insertRelease(latest) } }
            return latest
        }
        return null
    }

    private var subscriptionPagerFeedIndex: Int? = null
    fun getSubscriptionPagerFeedIndex(): Int {
        if (subscriptionPagerFeedIndex == null) {
            subscriptionPagerFeedIndex = mmkv.getInt(DawnConstants.SUBSCRIPTION_PAGER_FEED_INDEX, 0)
        }
        return subscriptionPagerFeedIndex!!
    }

    fun setSubscriptionPagerFeedIndex(feedPageIndex: Int) {
        subscriptionPagerFeedIndex = feedPageIndex
        mmkv.putInt(DawnConstants.SUBSCRIPTION_PAGER_FEED_INDEX, feedPageIndex)
    }

    private var historyPagerBrowsingIndex: Int? = null
    fun getHistoryPagerBrowsingIndex(): Int {
        if (historyPagerBrowsingIndex == null) {
            historyPagerBrowsingIndex = mmkv.getInt(DawnConstants.HISTORY_PAGER_BROWSING_INDEX, 0)
        }
        return historyPagerBrowsingIndex!!
    }

    fun setHistoryPagerBrowsingIndex(browseIndex: Int) {
        historyPagerBrowsingIndex = browseIndex
        mmkv.putInt(DawnConstants.HISTORY_PAGER_BROWSING_INDEX, browseIndex)
    }

}