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

package sh.xsl.reedisland.screens

import android.animation.Animator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.checkbox.isCheckPromptChecked
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.google.android.material.animation.AnimationUtils
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.enums.PopupPosition
import com.lxj.xpopup.interfaces.SimpleCallback
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.jessyan.retrofiturlmanager.RetrofitUrlManager
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.DawnApp.Companion.applicationDataStore
import sh.xsl.reedisland.MainNavDirections
import sh.xsl.reedisland.R
import sh.xsl.reedisland.databinding.ActivityMainBinding
import sh.xsl.reedisland.screens.util.ToolBar.immersiveToolbar
import sh.xsl.reedisland.screens.util.ToolBar.immersiveToolbarInitialization
import sh.xsl.reedisland.screens.widgets.DoubleClickListener
import sh.xsl.reedisland.screens.widgets.popups.ForumDrawerPopup
import sh.xsl.reedisland.util.DawnConstants
import sh.xsl.reedisland.util.IntentsHelper
import sh.xsl.reedisland.util.LoadingStatus
import sh.xsl.reedisland.util.SingleLiveEvent
import timber.log.Timber
import java.net.URL
import java.net.URLDecoder
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max

class MainActivity : DaggerAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var intentsHelper: IntentsHelper


    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val sharedVM: SharedViewModel by viewModels { viewModelFactory }

    // The BroadcastReceiver that tracks network connectivity changes.
    private var networkStateReceiver: NetworkReceiver? = null
    private var lastNetworkTestTime: Long = 0
    private var lastSuccessfulBaseCDN: String = ""
    private var lastSuccessfulRefCDN: String = ""


    private var doubleBackToExitPressedOnce = false
    private val mHandler = Handler(Looper.getMainLooper())
    private val mRunnable = Runnable { doubleBackToExitPressedOnce = false }
    private var reselectCDNRunnable: Runnable? = null

    enum class NavScrollSate {
        UP,
        DOWN
    }

    private var currentState: NavScrollSate? = null
    private var currentAnimatorSet: ViewPropertyAnimator? = null

    private var forumDrawer: ForumDrawerPopup? = null

    init {
        // load Resources
        lifecycleScope.launchWhenCreated {
            autoSelectCDNs()
            loadResources()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentFilterNavigation(intent)
    }

    // uses to display fab menu if it exists
    private var currentFragmentId: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_activity_main, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                when (item.itemId) {
                    android.R.id.home -> {
                        findNavController(R.id.navHostFragment).popBackStack()
                    }
                }
                return false
            }
        })

        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.toolbar.apply {
            immersiveToolbar()
            setSubtitle(R.string.toolbar_subtitle_adnmb)
        }
        immersiveToolbarInitialization()
        customToolbarBackground()
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        bindNavBarAndNavController()

        handleIntentFilterNavigation(intent)

        if (networkStateReceiver == null) {
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            networkStateReceiver = NetworkReceiver()
            registerReceiver(networkStateReceiver, filter)
        }

        intentsHelper = IntentsHelper(activityResultRegistry, this)
        lifecycle.addObserver(intentsHelper)

        sharedVM.communityList.observe(this) {
            if (it.status == LoadingStatus.ERROR) {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                return@observe
            }
            if (it.data.isNullOrEmpty()) return@observe
            if (DawnApp.currentDomain == DawnConstants.ADNMBDomain) forumDrawer?.setCommunities(it.data)
            sharedVM.setForumMappings(it.data)
            Timber.i("Loaded ${it.data.size} communities to Adapter")
        }

        sharedVM.timelineList.observe(this) {
            if (it.status == LoadingStatus.ERROR) {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                return@observe
            }
            if (it.data.isNullOrEmpty()) return@observe
            if (DawnApp.currentDomain == DawnConstants.ADNMBDomain) forumDrawer?.setTimelines(it.data)
            sharedVM.setTimelineMappings(it.data)
            Timber.i("Loaded ${it.data.size} timelines to Adapter")
        }

        sharedVM.reedPictureUrl.observe(this) { forumDrawer?.setReedPicture(it) }

        sharedVM.selectedForumId.observe(this) {
            if (currentFragmentId == R.id.postsFragment) {
                setToolbarTitle(sharedVM.getForumOrTimelineDisplayName(it))
            }
        }

        AppCenter.start(
            application, "51adb231-476b-4bb4-8b15-b3758243ab16",
            Analytics::class.java, Crashes::class.java
        )
    }

    private fun handleIntentFilterNavigation(intent: Intent?) {
        val action: String? = intent?.action
        val data: Uri? = intent?.data
        if (action == Intent.ACTION_VIEW && data != null) {
            val path = data.path
            if (path.isNullOrBlank()) return
            val count = path.filter { it == '/' }.count()
            val raw = data.toString().substringAfterLast("/")
            if (raw.isNotBlank()) {
                val id = if (raw.contains("?")) raw.substringBefore("?") else raw
                // TODO tnmb
                if ((count == 1 && data.host == "t") || (count == 2 && path[1] == 't')) {
                    val navAction = MainNavDirections.actionGlobalCommentsFragment(id, "")
                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (navHostFragment is NavHostFragment) {
                        navHostFragment.navController.navigate(navAction)
                    }

                } else if ((count == 2 && path[1] == 'f')) {
                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (navHostFragment is NavHostFragment) {
                        if (navHostFragment.navController.currentDestination?.id != R.id.postsFragment) {
                            navHostFragment.navController.popBackStack(R.id.postsFragment, false)
                        }
                    }
                    val fid = sharedVM.getForumIdByName(URLDecoder.decode(id, "UTF-8"))
                    sharedVM.setForumId(fid)
                } else if (count == 1 && data.host == "f") {
                    val navHostFragment =
                        supportFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (navHostFragment is NavHostFragment) {
                        if (navHostFragment.navController.currentDestination?.id != R.id.postsFragment) {
                            navHostFragment.navController.popBackStack(R.id.postsFragment, false)
                        }
                    }
                    sharedVM.setForumId(id)
                }
            }
        }
    }

    fun showDrawer() {
        if (forumDrawer == null) {
            forumDrawer = ForumDrawerPopup(this, sharedVM)
        }
        forumDrawer!!.let { drawer ->
            XPopup.Builder(this)
                .setPopupCallback(object : SimpleCallback() {
                    override fun beforeShow(popupView: BasePopupView?) {
                        super.beforeShow(popupView)
                        if (DawnApp.currentDomain == DawnConstants.ADNMBDomain) {
                            sharedVM.communityList.value?.data?.let { drawer.setCommunities(it) }
                            sharedVM.timelineList.value?.data?.let { drawer.setTimelines(it) }
                        } else {
                            sharedVM.beitaiForums.let { drawer.setCommunities(it) }
                            drawer.setTimelines(emptyList())
                        }
                        sharedVM.reedPictureUrl.value?.let { drawer.setReedPicture(it) }
                        drawer.loadReedPicture()
                    }

                    override fun onDismiss(popupView: BasePopupView?) {
                        super.onDismiss(popupView)
                        forumDrawer = null
                    }
                })
                .popupPosition(PopupPosition.Left)
                .isDestroyOnDismiss(true)
                .asCustom(drawer)
                .show()
        }
    }

    // initialize Global resources
    @SuppressLint("CheckResult")
    private suspend fun loadResources() {
        applicationDataStore.loadCookies()
        applicationDataStore.getLatestRelease()?.let { release ->
            if (this.isFinishing) return@let
            MaterialDialog(this).show {
                lifecycleOwner(this@MainActivity)
                title(R.string.download_latest_version)
                icon(R.mipmap.ic_launcher)
                message(text = release.message)
                listItemsSingleChoice(
                    R.array.download_options,
                    waitForPositiveButton = true
                ) { _, index, _ ->
                    val uri = when (index) {
                        0 -> Uri.parse(DawnConstants.DOWNLOAD_APP_CENTER)
                        1 -> Uri.parse(release.downloadUrl)
//                        2 -> Uri.parse(DawnConstants.DOWNLOAD_GOOGLE_PLAY)
                        else -> Uri.parse("https://github.com/xslingcn/ReedIsland")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                }
                positiveButton(R.string.submit)
                negativeButton(R.string.cancel)
            }
        }

        applicationDataStore.getReedSession()
        if (sharedVM.selectedForumId.value == null) sharedVM.setForumId(applicationDataStore.getDefaultForumId())

        applicationDataStore.getLatestNMBNotice()?.let { notice ->
            if (this.isFinishing) return@let
            MaterialDialog(this).show {
                lifecycleOwner(this@MainActivity)
                title(res = R.string.announcement)
                checkBoxPrompt(R.string.acknowledge) {}
                message(text = notice.content) { html() }
                positiveButton(R.string.close) {
                    notice.read = isCheckPromptChecked()
                    if (notice.read) lifecycleScope.launch {
                        applicationDataStore.readNMBNotice(notice)
                    }
                }
            }
        }

//        applicationDataStore.getLatestLuweiNotice()?.let { luweiNotice ->
//            sharedVM.setLuweiLoadingBible(luweiNotice.loadingMsgs)
//            sharedVM.setBeiTaiForums(luweiNotice.beitaiForums)
//        }

//        // first time app entry
//        applicationDataStore.getFirstTimeUse().let {
//            if (it) {
//                if (this.isFinishing) return@let
//                MaterialDialog(this).show {
//                    lifecycleOwner(this@MainActivity)
//                    title(res = R.string.announcement)
//                    checkBoxPrompt(R.string.acknowledge) {}
//                    cancelOnTouchOutside(false)
//                    message(R.string.entry_message)
//                    positiveButton(R.string.close) {
//                        if (isCheckPromptChecked()) {
//                            applicationDataStore.setFirstTimeUse()
//                        }
//                    }
//                }
//            }
//        }
    }

    private fun bindNavBarAndNavController() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        if (navHostFragment is NavHostFragment) {
            val navController = navHostFragment.navController
            navController.addOnDestinationChangedListener { _, destination, _ ->
                currentFragmentId = destination.id
                updateTitleAndBottomNav(destination)
            }
            binding.bottomNavBar.setOnNavigationItemReselectedListener { item: MenuItem ->
                if (item.itemId == R.id.postsFragment && currentFragmentId == R.id.postsFragment) showDrawer()
            }
            binding.bottomNavBar.setupWithNavController(navController)
            // up button
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.postsFragment,
                    R.id.subscriptionPagerFragment,
                    R.id.historyPagerFragment,
                    R.id.profileFragment
                ),
                null
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
        }
    }

    override fun onBackPressed() {
        if (!doubleBackToExitPressedOnce && findNavController(R.id.navHostFragment).previousBackStackEntry == null) {
            doubleBackToExitPressedOnce = true
            Toast.makeText(this, R.string.press_again_to_exit, Toast.LENGTH_SHORT).show()
            mHandler.postDelayed(mRunnable, 2000)
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        mHandler.removeCallbacks(mRunnable)
        reselectCDNRunnable?.let { mHandler.removeCallbacks(it) }
        networkStateReceiver?.let { unregisterReceiver(it) }
    }

    fun hideNav() {
        if (currentState == NavScrollSate.DOWN) return
        if (currentAnimatorSet != null) {
            currentAnimatorSet!!.cancel()
        }
        currentState = NavScrollSate.DOWN
        currentAnimatorSet = binding.bottomNavBar.animate().apply {
            alpha(0f)
            translationY(binding.bottomNavBar.height.toFloat())
            duration = 250
            interpolator = AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    currentAnimatorSet = null
                    binding.bottomNavBar.visibility = View.GONE
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
        }
        currentAnimatorSet!!.start()
    }

    fun showNav() {
        if (currentState == NavScrollSate.UP) return
        if (currentAnimatorSet != null) {
            currentAnimatorSet!!.cancel()
        }
        currentState = NavScrollSate.UP
        binding.bottomNavBar.visibility = View.VISIBLE
        currentAnimatorSet = binding.bottomNavBar.animate().apply {
            alpha(1f)
            translationY(0f)
            duration = 250
            interpolator = AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    currentAnimatorSet = null
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
        }
        currentAnimatorSet!!.start()
    }

    fun setToolbarClickListener(listener: () -> Unit) {
        binding.toolbar.setOnClickListener(
            DoubleClickListener(callback = object : DoubleClickListener.DoubleClickCallBack {
                override fun doubleClicked() {
                    listener.invoke()
                }
            })
        )
    }

    private var oldTitle = ""
    private var toolbarAnim: Animator? = null
    fun setToolbarTitle(newTitle: String) {
        if (oldTitle == newTitle) return
        toolbarAnim?.cancel()
        val animCharCount = max(oldTitle.length, newTitle.length)
        toolbarAnim = ValueAnimator.ofObject(
            ToolbarTitleEvaluator(animCharCount),
            StringBuilder(oldTitle),
            StringBuilder(newTitle)
        ).apply {
            duration = animCharCount.toLong() * 80
            addUpdateListener {
                binding.toolbar.title = it.animatedValue.toString()
                binding.toolbar.invalidate()
            }
        }
        oldTitle = newTitle
        toolbarAnim?.start()
    }

    fun setToolbarTitle(resId: Int) {
        val text = getText(resId).toString()
        setToolbarTitle(text)
    }

    private class ToolbarTitleEvaluator(private val animCharCount: Int) :
        TypeEvaluator<StringBuilder> {
        override fun evaluate(
            fraction: Float,
            startValue: StringBuilder,
            endValue: StringBuilder
        ): StringBuilder {
            val ind = (fraction * animCharCount).toInt()
            for (i in 0..ind) {
                val newChar = if (i >= endValue.length) ' ' else endValue[i]
                if (i < startValue.length) startValue.setCharAt(i, newChar)
                else startValue.append(newChar)
            }
            return startValue
        }
    }

    private fun updateTitleAndBottomNav(destination: NavDestination) {
        when (destination.id) {
            R.id.postsFragment -> {
                sharedVM.selectedForumId.value?.let {
                    setToolbarTitle(sharedVM.getForumOrTimelineDisplayName(it))
                }
                showNav()
            }
            R.id.historyPagerFragment, R.id.subscriptionPagerFragment -> {
                showNav()
            }
            R.id.searchFragment -> {
                setToolbarTitle(R.string.search)
                showNav()
            }
            R.id.commentsFragment -> {
                hideNav()
            }

            R.id.aboutFragment -> {
                setToolbarTitle(R.string.about)
                hideNav()
            }
            R.id.commonForumsFragment -> {
                setToolbarTitle(R.string.common_forum_setting)
                hideNav()
            }
            R.id.commonPostsFragment -> {
                setToolbarTitle(R.string.common_posts_setting)
                hideNav()
            }
            R.id.customSettingFragment -> {
                setToolbarTitle(R.string.custom_settings)
                hideNav()
            }
            R.id.displaySettingFragment -> {
                setToolbarTitle(R.string.display_settings)
                hideNav()
            }
            R.id.generalSettingFragment -> {
                setToolbarTitle(R.string.general_settings)
                hideNav()
            }
            R.id.profileFragment -> {
                setToolbarTitle(R.string.my_profile)
                showNav()
            }
            R.id.sizeCustomizationFragment -> {
                setToolbarTitle(R.string.layout_customization)
                hideNav()
            }
            R.id.notificationFragment -> {
                setToolbarTitle(R.string.feed_notification)
                hideNav()
            }
            R.id.emojiSettingFragment -> {
                setToolbarTitle(R.string.emoji_setting)
                hideNav()
            }
            else -> {
                Timber.e("Unhandled destination navigation $destination")
            }
        }
    }

    private fun customToolbarBackground() {
        if (applicationDataStore.getCustomToolbarImageStatus()) {
            try {
                val path = applicationDataStore.getCustomToolbarImagePath().toUri()
                binding.imageView.setImageURI(path)
                binding.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } catch (e: Exception) {
                binding.imageView.setImageResource(R.drawable.appbar)
                Toast.makeText(this, R.string.toolbar_customization_error, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun goToADNMB() {
        Timber.d("Switching to AD......")
        sharedVM.onADNMB()
        RetrofitUrlManager.getInstance().putDomain("host", DawnConstants.ADNMBHost)
        autoSelectCDNs()

        sharedVM.setForumId(applicationDataStore.getDefaultForumId(), true)

        binding.toolbar.setSubtitle(R.string.toolbar_subtitle_adnmb)
    }

    fun goToTNMB() {
        Timber.d("Switching to BT......")
        sharedVM.onTNMB()
        RetrofitUrlManager.getInstance().putDomain("host", DawnConstants.TNMBHost)
        RetrofitUrlManager.getInstance().putDomain("nmb", DawnConstants.TNMBHost)
        RetrofitUrlManager.getInstance().putDomain("nmb-ref", DawnConstants.TNMBHost)

        sharedVM.beitaiForums.firstOrNull()?.forums?.firstOrNull()?.id?.let {
            sharedVM.setForumId(
                it,
                true
            )
        }

        binding.toolbar.setSubtitle(R.string.toolbar_subtitle_tnmb)
    }


    private fun autoSelectCDNs() {
        if (DawnApp.currentDomain == DawnConstants.TNMBDomain) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - 20000 <= lastNetworkTestTime) {
            Timber.d("CDN was set less than 20 seconds ago, skipping...")
            if (lastSuccessfulBaseCDN.isNotBlank()) RetrofitUrlManager.getInstance()
                .putDomain("nmb", lastSuccessfulBaseCDN)
            if (lastSuccessfulRefCDN.isNotBlank()) RetrofitUrlManager.getInstance()
                .putDomain("nmb-ref", lastSuccessfulRefCDN)
            return
        }
        lastNetworkTestTime = currentTime
        // base CDN
        val base = applicationDataStore.getBaseCDN()
        // Reference CDN
        val ref = applicationDataStore.getRefCDN()
        if (ref != "auto") {
            Timber.d("Setting ref CDN to $ref")
            RetrofitUrlManager.getInstance().putDomain("nmb-ref", ref)
        }

        if (base != "auto") {
            Timber.i("Setting base CDN to $base...")
            RetrofitUrlManager.getInstance().putDomain("nmb", base)
        }
        if (ref != "auto" && base != "auto") return
        Timber.i("Auto selecting CDNs...")
        val availableConnections = sortedMapOf<Long, String>()
        val baseCDNs = resources.getStringArray(R.array.base_cdn_options).drop(1).dropLast(1)
        val refCDNs = resources.getStringArray(R.array.ref_cdn_options).drop(1).dropLast(1)
        for (url in if (base == "auto") baseCDNs else refCDNs) {
            lifecycleScope.launch(Dispatchers.IO) {
                var connection: HttpsURLConnection? = null
                try {
                    Timber.d("Testing $url...")
                    connection = (URL(url).openConnection() as? HttpsURLConnection)
                    connection?.run {
                        readTimeout = 3000
                        connectTimeout = 3000
                        requestMethod = "GET"
                        val startTime = System.currentTimeMillis()
                        connect()
                        if (responseCode == HttpsURLConnection.HTTP_OK
                            // fastmirror forbids base get but might works
                            || responseCode == HttpsURLConnection.HTTP_FORBIDDEN
                        ) {
                            val timeElapsed = System.currentTimeMillis() - startTime
                            availableConnections[timeElapsed] = url
                            Timber.d("Available CDNs: $availableConnections")
                            // Base
                            if (base == "auto" && availableConnections.firstKey() == timeElapsed) {
                                Timber.d("Using $url for Base")
                                lastSuccessfulBaseCDN = url
                                RetrofitUrlManager.getInstance()
                                    .putDomain("nmb", lastSuccessfulBaseCDN)
                            }
                            // Ref
                            if (ref == "auto") {
                                availableConnections.values.toList().firstOrNull { it in refCDNs }
                                    ?.let {
                                        Timber.d("Using $it for Ref")
                                        lastSuccessfulRefCDN = url
                                        RetrofitUrlManager.getInstance()
                                            .putDomain("nmb-ref", lastSuccessfulRefCDN)
                                    }
                            }
                            sharedVM.hostChange.postValue(SingleLiveEvent.create(true))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e("$url Host Test failed with $e")
                } finally {
                    connection?.disconnect()
                }
            }
        }
    }

    private class NetworkReceiver : BroadcastReceiver() {
        private var lastConnectionType: Int? = null
        override fun onReceive(context: Context, intent: Intent) {
            val info =
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
            if (info?.type != lastConnectionType && info?.isConnected == true) {
                (context as? MainActivity)?.run {
                    reselectCDNRunnable = Runnable {
                        Timber.d("Re-selecting CDNs after network changes")
                        autoSelectCDNs()
                    }
                    mHandler.postDelayed(reselectCDNRunnable!!, 3000)
                }
            }
            lastConnectionType = info?.type
        }
    }

}
