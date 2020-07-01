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

package com.laotoua.dawnislandk.screens

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.checkbox.isCheckPromptChecked
import com.google.android.material.animation.AnimationUtils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.laotoua.dawnislandk.DawnApp.Companion.applicationDataStore
import com.laotoua.dawnislandk.R
import com.laotoua.dawnislandk.data.local.entity.Community
import com.laotoua.dawnislandk.databinding.ActivityMainBinding
import com.laotoua.dawnislandk.di.DaggerViewModelFactory
import com.laotoua.dawnislandk.screens.comments.CommentsFragment
import com.laotoua.dawnislandk.screens.comments.QuotePopup
import com.laotoua.dawnislandk.screens.util.ToolBar.immersiveToolbarInitialization
import com.laotoua.dawnislandk.screens.widgets.popups.ForumDrawerPopup
import com.laotoua.dawnislandk.util.EventPayload
import com.laotoua.dawnislandk.util.LoadingStatus
import com.laotoua.dawnislandk.util.SingleLiveEvent
import com.laotoua.dawnislandk.util.lazyOnMainOnly
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.enums.PopupPosition
import com.lxj.xpopup.interfaces.SimpleCallback
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


class MainActivity : DaggerAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var viewModelFactory: DaggerViewModelFactory

    private val sharedVM: SharedViewModel by viewModels { viewModelFactory }

    private var doubleBackToExitPressedOnce = false
    private val mHandler = Handler()
    private val mRunnable = Runnable { doubleBackToExitPressedOnce = false }

    enum class NavScrollSate {
        UP,
        DOWN
    }

    private var currentState: NavScrollSate? = null
    private var currentAnimatorSet: AnimatorSet? = null


    private val forumDrawer by lazyOnMainOnly {
        ForumDrawerPopup(
            this,
            sharedVM
        )
    }

    init {
        // load Resources
        lifecycleScope.launch { loadResources() }
        applicationDataStore.initializeFeedId()
    }

    // uses to display fab menu if it exists
    private var currentFragmentId: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        immersiveToolbarInitialization()
        setContentView(binding.root)

        bindNavBarAndNavController()

        handleIntentFilterNavigation()

        sharedVM.communityList.observe(this, Observer<List<Community>> {
            if (it.isNullOrEmpty()) return@Observer
            forumDrawer.setData(it)
            sharedVM.setForumMappings(it)
            // TODO: set default forum
            if (sharedVM.selectedForumId.value == null) sharedVM.setForumId(it.first().forums.first().id)
            Timber.i("Loaded ${it.size} communities to Adapter")
        })
        sharedVM.reedPictureUrl.observe(this, Observer<String> {
            forumDrawer.setReedPicture(it)
        })
        sharedVM.communityListLoadingStatus.observe(
            this,
            Observer<SingleLiveEvent<EventPayload<Nothing>>> {
                if (it.getContentIfNotHandled()?.loadingStatus == LoadingStatus.FAILED) {
                    Toast.makeText(this, it.peekContent().message, Toast.LENGTH_LONG)
                        .show()
                }
            })
    }

    private fun handleIntentFilterNavigation() {
        val action: String? = intent?.action
        val data: Uri? = intent?.data
        if (action == Intent.ACTION_VIEW && data != null) {
            val path = data.path
            if (path.isNullOrBlank()) return
            val count = path.filter { it == '/' }.count()
            val raw = data.toString().substringAfterLast("/")
            if (raw.isNotBlank()) {
                val id = if (raw.contains("?")) raw.substringBefore("?") else raw
                if (count == 1) {
                    sharedVM.setForumId(id)
                }
                if (count == 2) {
                    sharedVM.setPost(id, "")
                    showComment()
                }
            }
        }
    }

    fun showDrawer() {
        XPopup.Builder(this)
            .setPopupCallback(object : SimpleCallback() {
                override fun beforeShow() {
                    super.beforeShow()
                    forumDrawer.loadReedPicture()
                }
            })
            .popupPosition(PopupPosition.Left)
            .asCustom(forumDrawer)
            .show()
    }

    // initialize Global resources
    private suspend fun loadResources() {
        applicationDataStore.getLatestRelease()?.let { release ->
            MaterialDialog(this).show {
                title(R.string.found_new_version)
                message(text = release.message) { html() }
                positiveButton(R.string.download_latest_version) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                }
                negativeButton(R.string.acknowledge) {
                    dismiss()
                }
            }
        }

        applicationDataStore.loadCookies()
        applicationDataStore.getLatestNMBNotice()?.let { notice ->
            MaterialDialog(this).show {
                title(res = R.string.announcement)
                checkBoxPrompt(R.string.acknowledge) {}
                message(text = notice.content) { html() }
                positiveButton(R.string.close) {
                    notice.read = isCheckPromptChecked()
                    if (notice.read) lifecycleScope.launch {
                        applicationDataStore.readNMBNotice(
                            notice
                        )
                    }
                }
            }
        }

        applicationDataStore.getLatestLuweiNotice()?.let { luweiNotice ->
            sharedVM.setLuweiLoadingBible(luweiNotice.loadingMsgs)
        }

        // first time app entry
        applicationDataStore.firstTimeUse.let { it ->
            if (!it) {
                MaterialDialog(this).show {
                    title(res = R.string.announcement)
                    checkBoxPrompt(R.string.acknowledge) {}
                    message(R.string.entry_message)
                    positiveButton(R.string.close) {
                        if (isCheckPromptChecked()) {
                            applicationDataStore.setFirstTimeUse()
                        }
                    }
                }
            }
        }
    }

    private fun bindNavBarAndNavController() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        if (navHostFragment is NavHostFragment) {
            val navController = navHostFragment.navController
            navController.addOnDestinationChangedListener { _, destination, _ ->
                currentFragmentId = destination.id
            }
            binding.bottomNavBar.setOnNavigationItemReselectedListener { item: MenuItem ->
                if (item.itemId == R.id.postsFragment) showDrawer()
            }
            binding.bottomNavBar.setupWithNavController(navController)
        }
    }

    override fun onBackPressed() {
        /**
         *  Catch for popup which failed to request focus
         */
        if (!QuotePopup.ensureQuotePopupDismissal()) return

        if (hideComment()) return

        if (!doubleBackToExitPressedOnce &&
            findNavController(R.id.navHostFragment).previousBackStackEntry == null
        ) {
            doubleBackToExitPressedOnce = true
            Toast.makeText(
                this,
                R.string.press_again_to_exit, Toast.LENGTH_SHORT
            ).show()
            mHandler.postDelayed(mRunnable, 2000)
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        mHandler.removeCallbacks(mRunnable)
    }

    fun showComment() {
        var commentFrag = supportFragmentManager.findFragmentByTag("comment")
        if (commentFrag == null) {
            commentFrag = CommentsFragment()
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right)
                .add(R.id.navHostFragment, commentFrag, "comment")
                .addToBackStack(null).commit()
        } else {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right)
                .show(commentFrag)
                .runOnCommit { commentFrag.onResume() }
                .commit()
        }
        hideNav()
    }

    fun hideComment(): Boolean {
        supportFragmentManager.findFragmentByTag("comment")?.let {
            if (!it.isHidden) {
                it.onPause()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right)
                    .hide(it)
                    .commit()
                showNav()
                if (currentFragmentId == R.id.postsFragment) {
                    findViewById<FloatingActionButton>(R.id.fabMenu).show()
                    findViewById<FloatingActionButton>(R.id.fabMenu).isClickable = true
                }
                return true
            }
        }
        return false
    }

    private val navSlideOutBottomAnimAnim by lazyOnMainOnly {
        ObjectAnimator.ofFloat(
            binding.bottomNavBar,
            "TranslationY",
            binding.bottomNavBar.height.toFloat()
        )
    }

    private val navAlphaOutAnim by lazyOnMainOnly {
        ObjectAnimator.ofFloat(binding.bottomNavBar, "alpha", 0f)
    }

    private val navSlideInBottomAnim by lazyOnMainOnly {
        ObjectAnimator.ofFloat(
            binding.bottomNavBar,
            "TranslationY",
            0f
        )
    }

    private val navAlphaInAnim by lazyOnMainOnly {
        ObjectAnimator.ofFloat(binding.bottomNavBar, "alpha", 1f)
    }

    fun hideNav() {
        if (currentState == NavScrollSate.DOWN) return
        if (currentAnimatorSet != null) {
            currentAnimatorSet!!.cancel()
        }
        currentState = NavScrollSate.DOWN
        currentAnimatorSet = AnimatorSet().apply {
            duration = 250
            interpolator = AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    currentAnimatorSet = null
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
            playTogether(navSlideOutBottomAnimAnim, navAlphaOutAnim)
            start()
        }
    }

    fun showNav() {
        if (currentState == NavScrollSate.UP) return
        if (currentAnimatorSet != null) {
            currentAnimatorSet!!.cancel()
        }
        currentState = NavScrollSate.UP
        currentAnimatorSet = AnimatorSet().apply {
            duration = 250
            interpolator = AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    currentAnimatorSet = null
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
            playTogether(navSlideInBottomAnim, navAlphaInAnim)
            start()
        }
    }
}
