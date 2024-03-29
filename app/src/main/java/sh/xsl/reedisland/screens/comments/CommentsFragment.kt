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

package sh.xsl.reedisland.screens.comments


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.style.UnderlineSpan
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.text.toSpannable
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.bottomsheets.gridItems
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.google.android.material.textfield.TextInputLayout
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.interfaces.SimpleCallback
import dagger.android.support.DaggerFragment
import me.dkzwm.widget.srl.RefreshingListenerAdapter
import me.dkzwm.widget.srl.config.Constants
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.MainNavDirections
import sh.xsl.reedisland.R
import sh.xsl.reedisland.data.local.entity.Comment
import sh.xsl.reedisland.databinding.FragmentCommentBinding
import sh.xsl.reedisland.screens.MainActivity
import sh.xsl.reedisland.screens.SharedViewModel
import sh.xsl.reedisland.screens.adapters.QuickAdapter
import sh.xsl.reedisland.screens.util.Layout
import sh.xsl.reedisland.screens.util.Layout.toast
import sh.xsl.reedisland.screens.util.Layout.updateHeaderAndFooter
import sh.xsl.reedisland.screens.widgets.LinkifyTextView
import sh.xsl.reedisland.screens.widgets.SingleAndDoubleClickListener
import sh.xsl.reedisland.screens.widgets.popups.ImageViewerPopup
import sh.xsl.reedisland.screens.widgets.popups.PostPopup
import sh.xsl.reedisland.screens.widgets.spans.ReferenceSpan
import sh.xsl.reedisland.screens.widgets.spans.UserFunctionSpan
import sh.xsl.reedisland.util.DawnConstants
import sh.xsl.reedisland.util.lazyOnMainOnly
import timber.log.Timber
import java.util.regex.Pattern
import javax.inject.Inject


class CommentsFragment : DaggerFragment() {
    private val args: CommentsFragmentArgs by navArgs()

    private var binding: FragmentCommentBinding? = null

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private val viewModel: CommentsViewModel by viewModels { viewModelFactory }
    private val sharedVM: SharedViewModel by activityViewModels { viewModelFactory }

    private var mAdapter: QuickAdapter<Comment>? = null
    private var viewCaching = false
    private var cacheDomain = DawnConstants.AWEIDomain
    private var refreshing = false

    // last visible item indicates the current page, uses for remembering last read page
    private var currentPage = 0
    private var pageCounter: TextView? = null
    private var filterActivated: Boolean = false
    private var requireTitleUpdate: Boolean = false

    private var imagesList: List<Any> = listOf()

    // list to remember all currently displaying popups
    // need to dismiss all before jumping to new post, by lifo
    private val quotePopups: MutableList<QuotePopup> = mutableListOf()

    private val postPopup: PostPopup by lazyOnMainOnly {
        PostPopup(
            requireActivity() as MainActivity,
            sharedVM
        )
    }
    private var imageViewerPopup: ImageViewerPopup? = null

    enum class RVScrollState {
        UP,
        DOWN
    }

    private var currentState: RVScrollState? = null
    private var currentAnimatorSet: ViewPropertyAnimator? = null

    @SuppressLint("CheckResult")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (mAdapter == null) {
            mAdapter = QuickAdapter<Comment>(R.layout.list_item_comment, sharedVM).apply {
                setReferenceClickListener(object : ReferenceSpan.ReferenceClickHandler {
                    override fun handleReference(id: String) {
                        displayQuote(id)
                    }
                })

                setFunctionClickListener(object : UserFunctionSpan.FunctionClickHandler {
                    override fun handleFunction(code: String) {
                        displayFunction(code)
                    }
                })

                setOnItemClickListener { _, _, pos ->
                    if (activity == null || !isAdded) return@setOnItemClickListener
                    toggleCommentMenuOnPos(pos)
                }

                addChildClickViewIds(
                    R.id.attachedImage,
                    R.id.expandSummary,
                    R.id.comment,
                    R.id.content,
                    R.id.copyContent,
                    R.id.copyId,
                    R.id.report
                )

                setOnItemChildClickListener { _, view, position ->
                    if (activity == null || !isAdded) return@setOnItemChildClickListener
                    when (view.id) {
                        R.id.attachedImage -> {
                            val pos = imagesList.indexOf(getItem(position))
                            if (pos < 0) {
                                Timber.e("Did not find image in for comment #$position")
                                return@setOnItemChildClickListener
                            }
                            getImageViewerPopup().setSrcView(view as ImageView, pos)
                            XPopup.Builder(context)
                                .asCustom(getImageViewerPopup())
                                .show()
                        }
                        R.id.comment -> {
                            val content = ">>No.${getItem(position).id}\n"
                            postPopup.setupAndShow(
                                viewModel.currentPostId,
                                viewModel.currentPostFid,
                                targetPage = viewModel.maxPage,
                                quote = content
                            )
                        }
                        R.id.copyContent -> {
                            mAdapter?.getViewByPosition(position, R.id.content)?.let {
                                copyText("内容", (it as TextView).text.toString())
                            }
                        }
                        R.id.copyId -> {
                            mAdapter?.getViewByPosition(position, R.id.refId)?.let {
                                copyText("串号", ">>${(it as TextView).text}")
                            }
                        }
                        R.id.report -> {
                            MaterialDialog(requireContext()).show {
                                lifecycleOwner(this@CommentsFragment)
                                title(R.string.report_reasons)
                                listItemsSingleChoice(res = R.array.report_reasons) { _, _, text ->
                                    postPopup.setupAndShow(
                                        getItem(position).id,//举报的串号
                                        "0",
                                        newPost = true,
                                        quote = "${
                                            context.getString(
                                                R.string.report_reasons
                                            )
                                        }: $text\n",
                                        report = true
                                    )
                                }
                                cancelOnTouchOutside(false)
                            }
                        }
                        R.id.content -> {
                            val ltv = view as LinkifyTextView
                            // no span was clicked, simulate click events to parent
                            if (ltv.currentSpan == null) {
                                val metaState = 0
                                (view.parent as View).dispatchTouchEvent(
                                    MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_DOWN,
                                        0f,
                                        0f,
                                        metaState
                                    )
                                )
                                (view.parent as View).dispatchTouchEvent(
                                    MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_UP,
                                        0f,
                                        0f,
                                        metaState
                                    )
                                )
                            }
                        }
                        R.id.expandSummary -> {
                            data[position].visible = true
                            notifyItemChanged(position)
                        }
                    }
                }

                // load more
                loadMoreModule.setOnLoadMoreListener {
                    viewModel.getNextPage()
                }
            }
        }

        if (binding != null) {
            Timber.d("Fragment View Reusing!")
        } else {
            Timber.d("Fragment View Created")
            binding = FragmentCommentBinding.inflate(inflater, container, false)
            binding!!.srlAndRv.refreshLayout.apply {
                setOnRefreshListener(object : RefreshingListenerAdapter() {
                    override fun onRefreshing() {
                        if (binding == null || mAdapter == null) return
                        if (mAdapter?.data.isNullOrEmpty().not() && mAdapter?.getItem(
                                (binding!!.srlAndRv.recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                            )?.page == 1
                        ) {
                            toast("没有上一页了。。。")
                            refreshComplete(true, 100L)
                        } else {
                            viewModel.getPreviousPage()
                        }
                    }
                })
            }

            binding!!.srlAndRv.recyclerView.apply {
                val llm = LinearLayoutManager(context)
                layoutManager = llm
                adapter = mAdapter
                setHasFixedSize(true)
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (activity == null || !isAdded || binding == null) return
                        if (dy > 0) {
                            hideMenu()
                            if (llm.findLastVisibleItemPosition() + 4 >=
                                (mAdapter?.data?.size ?: Int.MAX_VALUE)
                                && !binding!!.srlAndRv.refreshLayout.isRefreshing
                                && currentPage < viewModel.maxPage
                            ) {
                                recyclerView.post {
                                    mAdapter?.loadMoreModule?.loadMoreToLoading()
                                }
                            }
                        } else if (dy < 0) {
                            showMenu()
                            if (llm.findFirstVisibleItemPosition() <= 2 && !binding!!.srlAndRv.refreshLayout.isRefreshing) {
                                viewModel.getPreviousPage()
                            }
                        }
                        updateCurrentPage()
                    }
                })
            }

            binding!!.copyAndShare.setOnClickListener {
                if (activity == null || !isAdded) return@setOnClickListener
                val items = listOf(
                    BasicGridItem(R.drawable.ic_share_black_48dp, "分享串"),
                    BasicGridItem(R.drawable.ic_public_black_48dp, "复制串地址"),
                    BasicGridItem(R.drawable.ic_content_copy_black_48dp, "复制串号")
                )
                MaterialDialog(requireContext(), BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    lifecycleOwner(this@CommentsFragment)
                    title(R.string.share)
                    gridItems(items) { _, index, _ ->
                        when (index) {
                            0 -> {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    val shareContent = resources.getString(
                                        R.string.external_share_content,
                                        viewModel.getExternalShareContent(),
                                        resources.getString(R.string.app_name)
                                    )
                                    putExtra(Intent.EXTRA_TEXT, shareContent)
                                    type = "text/html"
                                    putExtra(
                                        Intent.EXTRA_TITLE,
                                        "阿苇岛 · ${sharedVM.getForumOrTimelineDisplayName(viewModel.currentPostFid)} · ${viewModel.currentPostId}"
                                    )
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                startActivity(shareIntent)
                            }
                            1 -> copyText(
                                "串地址",
                                "${DawnConstants.AWEIHost}/t/${viewModel.currentPostId}"
                            )
                            2 -> copyText("串号", ">>No.${viewModel.currentPostId}")
                            else -> {
                            }
                        }
                    }
                }
            }

            binding!!.post.setOnClickListener {
                if (activity == null || !isAdded) return@setOnClickListener
                postPopup.setupAndShow(
                    viewModel.currentPostId,
                    viewModel.currentPostFid,
                    targetPage = viewModel.maxPage
                )
            }

            binding!!.jump.setOnClickListener {
                showJumpPageDialog()
            }

            binding!!.feed.apply {
                setOnClickListener {
                    if (activity == null || !isAdded) return@setOnClickListener
                    viewModel.addFeed(viewModel.currentPostId)
                }
                setOnLongClickListener {
                    if (activity != null && isAdded) {
                        viewModel.delFeed(viewModel.currentPostId)
                    }
                    true
                }
            }
        }
        subscribeUI()
        viewCaching = false
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_fragment_comment, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.pageCounter).actionView.apply {
                    pageCounter = findViewById(R.id.text)
                    setOnClickListener { showJumpPageDialog() }
                }
                context?.let {
                    menu.findItem(R.id.filter).icon.setTint(
                        Layout.getThemeInverseColor(
                            it
                        )
                    )
                }
                super.onPrepareMenu(menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.filter -> {
                        filterActivated = filterActivated.not()
                        if (!filterActivated) {
                            viewModel.clearFilter()
                            toast(R.string.comment_filter_off)
                        } else {
                            viewModel.onlyPo()
                            toast(R.string.comment_filter_on)
                        }
                        (binding?.srlAndRv?.recyclerView?.layoutManager as LinearLayoutManager?)?.run {
                            val startPos = findFirstVisibleItemPosition()
                            val itemCount = findLastVisibleItemPosition() - startPos
                            mAdapter?.notifyItemRangeChanged(
                                startPos + mAdapter!!.headerLayoutCount,
                                itemCount + initialPrefetchItemCount - mAdapter!!.footerLayoutCount
                            )
                        }
                        return true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        viewModel.setPost(args.id, args.fid, args.targetPage)
        requireTitleUpdate = args.fid.isBlank()
        updateTitle()
    }

    private fun subscribeUI() {
        viewModel.feedResponse.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let { message -> toast(message) }
        }

        viewModel.loadingStatus.observe(viewLifecycleOwner) {
            if (binding == null || mAdapter == null) return@observe
            it.getContentIfNotHandled()?.run {
                updateHeaderAndFooter(binding!!.srlAndRv.refreshLayout, mAdapter!!, this)
            }
        }

        viewModel.comments.observe(viewLifecycleOwner) {
            if (mAdapter == null) return@observe
            if (it.isNullOrEmpty()) {
                mAdapter?.showNoData()
                return@observe
            }
            val referenced = preProcessReference(it).toMutableList()
            updateCurrentPage()
            if (requireTitleUpdate) {
                updateTitle()
                requireTitleUpdate = false
            }
            if (refreshing) {
                binding?.srlAndRv?.recyclerView?.scrollToPosition(0)
                mAdapter?.setNewInstance(referenced)
            } else {
                mAdapter?.setDiffNewData(referenced)
            }
            refreshing = false
            updateCurrentlyAvailableImages(referenced)
            mAdapter?.setPo(viewModel.po)
            Timber.i("${this.javaClass.simpleName} Adapter will have ${mAdapter?.data?.size} comments")
        }

        sharedVM.savePostStatus.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                if (it && currentPage >= viewModel.maxPage - 1) {
                    mAdapter?.loadMoreModule?.loadMoreToLoading()
                }
            }
        }

        sharedVM.currentDomain.observe(viewLifecycleOwner) {
            if (it != cacheDomain) {
                viewModel.clearCache(true)
                cacheDomain = it
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding?.srlAndRv?.recyclerView?.stopScroll()
    }

    override fun onResume() {
        super.onResume()

        if (activity != null && isAdded) {
            (requireActivity() as MainActivity).run {
                setToolbarClickListener(object :
                    SingleAndDoubleClickListener.SingleAndDoubleClickCallBack {
                    override fun singleClicked() {
                        // do nothing
                    }

                    override fun doubleClicked() {
                        binding?.srlAndRv?.recyclerView?.layoutManager?.scrollToPosition(0)
                        if (binding != null) showMenu()
                    }

                })
                hideNav()
            }
        }
    }

    private fun preProcessReference(comments: List<Comment>): List<Comment> {
        val referencePattern = Pattern.compile("&gt;&gt;?(?:No.)?(\\d+)")
        val resList = ArrayList(comments.map { it.copy() })
        resList.forEach {
            it.content?.run {
                var lastLeading = this
                var lastTrailing = ""
                var m = referencePattern.matcher(this)
                while (m.find()) {
                    val leading =
                        if (lastTrailing.isBlank()) lastLeading.substring(0, m.start())
                        else lastLeading.plus(lastTrailing.substring(0, m.start()))
                    val trailing =
                        if (lastTrailing.isBlank())
                            lastLeading.substring(m.end(), lastLeading.length)
                        else lastTrailing.substring(m.end(), lastTrailing.length)
                    val quote = viewModel.getLocalQuote(
                        m.group(1)!!,
                        comments
                    )
                    quote?.apply {
                        val nlPattern = "(<br\\s*\\/?>)|(\n)"
                        content?.apply {
                            var quoteContent = this
                            val mn = referencePattern.matcher(quoteContent)
                            // remove other references
                            while (mn.find()) quoteContent = quoteContent.replace(mn.group(0)!!, "")
                            // remove new lines
                            quoteContent = quoteContent.replace(nlPattern.toRegex(), " ")
                            val builder = StringBuilder()
                            run countDoubleChar@{
                                val doubleChar = "[^\\x00-\\xff]+".toRegex()
                                var i = 0
                                val maxChar =
                                    Resources.getSystem().displayMetrics.widthPixels.div(30)
                                quoteContent.forEach { c ->
                                    i += if (doubleChar.containsMatchIn(it.toString())) 2 else 1
                                    if (i >= maxChar) return@countDoubleChar builder.append("...")
                                    builder.append(c)
                                }
                            }
                            lastLeading = leading.plus(
                                if (leading.isNotBlank() && !leading.endsWithNewLine()) "<br/>"
                                else ""
                            ).plus(m.group(0))
                                .plus("<font color=#808080><small><i> ")
                                .plus(builder.toString()).plus("</i></small></font>")
                        } ?: img?.apply {
                            lastLeading = leading.plus(
                                if (leading.isNotBlank() && !leading.endsWithNewLine()) "<br/>"
                                else ""
                            ).plus(m.group(0))
                                .plus("<font color=#808080><small><i> ")
                                .plus(getString(R.string.no_content_image))
                                .plus("</i></small></font>")
                        }
                        lastTrailing = trailing
                        m = referencePattern.matcher(lastTrailing)
                    }
                }
                if (!lastTrailing.startsWithNewLine() && lastTrailing.isNotBlank())
                    lastTrailing = "<br/>".plus(lastTrailing)
                it.content = lastLeading.plus(lastTrailing)
            }
        }
        return resList.toList()
    }

    private fun showJumpPageDialog() {
        if (binding == null || mAdapter == null || activity == null || !isAdded) return
        if (binding!!.srlAndRv.refreshLayout.isRefreshing || mAdapter?.loadMoreModule?.isLoading == true) {
            Timber.d("Loading data...Holding on jump...")
            return
        }
        val page = getCurrentPage()
        MaterialDialog(requireContext()).show {
            lifecycleOwner(this@CommentsFragment)
            val maxPage = viewModel.maxPage
            var targetPage = page
            var canJump = DawnApp.applicationDataStore.firstCookieHash != null || targetPage < 100
            customView(R.layout.popup_jump)
            val submitButton = getActionButton(WhichButton.POSITIVE)
            getCustomView().apply {
                val pageInput = findViewById<TextInputLayout>(R.id.pageInput)
                pageInput.editText!!.doOnTextChanged { text, _, _, _ ->
                    try {
                        submitButton.isEnabled =
                            !(text.isNullOrBlank() || text.length > maxPage.toString().length || text.toString()
                                .toInt() > maxPage)
                        if (submitButton.isEnabled) {
                            targetPage = pageInput.editText!!.text.toString().toInt()
                        }
                        canJump =
                            DawnApp.applicationDataStore.firstCookieHash != null || targetPage < 100
                        pageInput.error =
                            if (!canJump) context.resources.getString(R.string.need_cookie_to_read) else null
                        submitButton.isEnabled = canJump
                    } catch (e: Exception) {
                        submitButton.isEnabled = false
                        targetPage = page
                    }
                }
                pageInput.editText!!.setText(targetPage.toString())
                pageInput.error =
                    if (!canJump) context.resources.getString(R.string.need_cookie_to_read) else null

                findViewById<TextView>(R.id.currentPage).text = currentPage.toString()
                findViewById<TextView>(R.id.maxPage).text = maxPage.toString()
                findViewById<ImageButton>(R.id.firstPage).setOnClickListener {
                    targetPage = 1
                    pageInput.editText!!.setText(targetPage.toString())
                }

                findViewById<ImageButton>(R.id.lastPage).setOnClickListener {
                    targetPage = maxPage
                    pageInput.editText!!.setText(targetPage.toString())
                }
            }

            positiveButton(R.string.submit) {
                if (binding == null || mAdapter == null) dismiss()
                binding!!.srlAndRv.refreshLayout.autoRefresh(
                    Constants.ACTION_NOTHING,
                    false
                )
                refreshing = true
                Timber.i("Jumping to $targetPage...")
                viewModel.jumpTo(targetPage)
            }
            negativeButton(R.string.cancel)
        }
    }

    private fun copyText(label: String, text: String) {
        getSystemService(requireContext(), ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
        toast(resources.getString(R.string.content_copied, label))
    }

    private fun getCurrentPage(): Int {
        if (mAdapter == null || binding == null || mAdapter?.data.isNullOrEmpty()) return 1
        val pos = (binding!!.srlAndRv.recyclerView.layoutManager as LinearLayoutManager)
            .findLastVisibleItemPosition()
            .coerceAtLeast(0)
            .coerceAtMost(mAdapter!!.data.lastIndex)
        return mAdapter!!.getItem(pos).page
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissAllQuotes()
        if (!viewCaching) {
            mAdapter = null
            binding = null
        }
        imageViewerPopup?.clearLoaders()
        imageViewerPopup = null
        Timber.d("Fragment View Destroyed ${binding == null}")
    }

    fun hideMenu() {
        if (currentState == RVScrollState.DOWN) return
        if (currentAnimatorSet != null) {
            currentAnimatorSet!!.cancel()
        }
        currentState = RVScrollState.DOWN
        currentAnimatorSet = binding?.bottomToolbar?.animate()?.apply {
            alpha(0f)
            translationY(binding!!.bottomToolbar.height.toFloat())
            duration = 250
            interpolator = LinearInterpolator()
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    currentAnimatorSet = null
                    binding?.bottomToolbar?.visibility = View.GONE
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
        }
        currentAnimatorSet?.start()
    }

    fun showMenu() {
        if (currentState == RVScrollState.UP) return
        if (currentAnimatorSet != null) {
            currentAnimatorSet!!.cancel()
        }
        currentState = RVScrollState.UP
        binding?.bottomToolbar?.visibility = View.VISIBLE
        currentAnimatorSet = binding?.bottomToolbar?.animate()?.apply {
            alpha(1f)
            translationY(0f)
            duration = 250
            interpolator = LinearInterpolator()
            setListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    currentAnimatorSet = null
                }

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })
        }
        currentAnimatorSet?.start()
    }

    private fun updateTitle() {
        if (viewModel.currentPostFid.isNotBlank()) {
            (requireActivity() as MainActivity).setToolbarTitle(
                "${sharedVM.getSelectedPostForumName(viewModel.currentPostFid)} • ${viewModel.currentPostId}"
            )
        }
    }

    private fun updateCurrentPage() {
        if (mAdapter == null || binding == null) return
        val page = getCurrentPage()
        if (currentPage != 0 && page != currentPage) {
            viewModel.saveReadingProgress(page)
        }
        val newText = "$page / ${viewModel.maxPage}"
        if (pageCounter?.text != newText) {
            pageCounter?.text =
                newText.toSpannable().apply { setSpan(UnderlineSpan(), 0, length, 0) }
        }
        currentPage = page
    }

    private var menuPos = -1

    private fun showCommentMenuOnPos(pos: Int) {
        menuPos = pos
        mAdapter?.getViewByPosition(pos, R.id.commentMenu)?.apply {
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(150)
                .setListener(null)
        }
    }

    private fun hideCommentMenuOnPos(pos: Int) {
        if (menuPos < 0) return
        mAdapter?.getViewByPosition(pos, R.id.commentMenu)?.apply {
            animate()
                .alpha(0f)
                .setDuration(150)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = View.GONE
                    }
                })
        }
    }

    private fun toggleCommentMenuOnPos(pos: Int) {
        mAdapter?.getViewByPosition(pos, R.id.commentMenu)?.apply {
            if (this.isVisible) {
                hideCommentMenuOnPos(pos)
            } else {
                hideCommentMenuOnPos(menuPos)
                showCommentMenuOnPos(pos)
            }
        }
    }

    fun displayQuote(id: String) {
        if (activity == null || !isAdded) return
        val top = QuotePopup(this, viewModel.getQuote(id), viewModel.currentPostId, viewModel.po)
        quotePopups.add(top)
        XPopup.Builder(context)
            .setPopupCallback(object : SimpleCallback() {
                override fun beforeShow(popupView: BasePopupView?) {
                    super.beforeShow(popupView)
                    top.listenToLiveQuote(viewLifecycleOwner)
                }

                override fun onDismiss(popupView: BasePopupView?) {
                    quotePopups.remove(popupView)
                    super.onDismiss(popupView)
                }
            })
            .isDestroyOnDismiss(true)
            .asCustom(top)
            .show()
    }

    fun displayFunction(code: String) {
        if (activity == null || !isAdded) return
        XPopup.Builder(context)
            .isDestroyOnDismiss(true)
            .asCustom(FunctionPopup(this, code))
            .show()
    }

    private fun dismissAllQuotes() {
        for (q in quotePopups.reversed()) {
            q.smartDismiss()
        }
    }

    fun jumpToNewPost(id: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity == null || !isAdded) return@postDelayed
            viewCaching = DawnApp.applicationDataStore.getViewCaching()
            val navAction = MainNavDirections.actionGlobalCommentsFragment(id, "")
            findNavController().navigate(navAction)
        }, (XPopup.getAnimationDuration() + 50) * quotePopups.size + 100L)
        dismissAllQuotes()
    }

    private fun getImageViewerPopup(): ImageViewerPopup {
        if (imageViewerPopup == null) {
            imageViewerPopup = ImageViewerPopup(requireContext()).apply {
                setNextPageLoader { viewModel.getNextPage(forceUpdate = false) }
                setPreviousPageLoader { viewModel.getPreviousPage(forceUpdate = false) }
            }
        }
        return imageViewerPopup!!
    }

    private fun updateCurrentlyAvailableImages(newList: MutableList<Comment>) {
        imagesList = newList.filter { it.getImgUrl().isNotBlank() }
        getImageViewerPopup().setImageUrls(imagesList.toMutableList())
    }

    private fun String.endsWithNewLine() =
        this.endsWith("\n") || this.endsWith("<br/>", true) || this.endsWith("<br />", true)

    private fun String.startsWithNewLine() =
        this.startsWith("\n") || this.startsWith("<br/>", true) || this.startsWith("<br />", true)
}
