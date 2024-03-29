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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.datetime.datePicker
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.chad.library.adapter.base.binder.QuickItemBinder
import com.chad.library.adapter.base.util.getItemView
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.card.MaterialCardView
import com.lxj.xpopup.XPopup
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.MainNavDirections
import sh.xsl.reedisland.R
import sh.xsl.reedisland.data.local.entity.Post
import sh.xsl.reedisland.databinding.FragmentHistoryBrowsingBinding
import sh.xsl.reedisland.screens.SharedViewModel
import sh.xsl.reedisland.screens.adapters.*
import sh.xsl.reedisland.screens.posts.PostCardFactory
import sh.xsl.reedisland.screens.util.Layout.toast
import sh.xsl.reedisland.screens.widgets.BaseNavFragment
import sh.xsl.reedisland.screens.widgets.popups.ImageViewerPopup
import sh.xsl.reedisland.util.DawnConstants
import sh.xsl.reedisland.util.ReadableTime
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class BrowsingHistoryFragment : BaseNavFragment() {

    companion object {
        fun newInstance() = BrowsingHistoryFragment()
    }

    private var binding: FragmentHistoryBrowsingBinding? = null

    private var mAdapter: QuickMultiBinder? = null

    private var viewCaching = false

    private val viewModel: BrowsingHistoryViewModel by viewModels { viewModelFactory }

    private var cacheDomain: String = DawnConstants.AWEIDomain

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (mAdapter == null) {
            mAdapter = QuickMultiBinder(sharedVM).apply {
                addItemBinder(DateStringBinder(), DateStringDiffer())
                addItemBinder(PostBinder(sharedVM).apply {
                    addChildClickViewIds(R.id.attachedImage)
                }, PostDiffer())
            }
        }
        if (binding != null) {
            Timber.d("Fragment View Reusing!")
        } else {
            Timber.d("Fragment View Created")
            binding = FragmentHistoryBrowsingBinding.inflate(inflater, container, false)

            binding!!.recyclerView.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                adapter = mAdapter
            }

            binding!!.startDate.text = ReadableTime.getDateString(viewModel.startDate)
            binding!!.endDate.text = ReadableTime.getDateString(viewModel.endDate)
            binding!!.startDate.setOnClickListener {
                if (activity == null || !isAdded) return@setOnClickListener
                MaterialDialog(requireContext()).show {
                    lifecycleOwner(this@BrowsingHistoryFragment)
                    datePicker(currentDate = ReadableTime.localDateTimeToCalendarDate(viewModel.startDate)) { _, date ->
                        setStartDate(date)
                    }
                }
            }

            binding!!.endDate.setOnClickListener {
                if (activity == null || !isAdded) return@setOnClickListener
                MaterialDialog(requireContext()).show {
                    lifecycleOwner(this@BrowsingHistoryFragment)
                    datePicker(currentDate = ReadableTime.localDateTimeToCalendarDate(viewModel.endDate)) { _, date ->
                        setEndDate(date)
                    }
                }
            }

            binding!!.confirmDate.setOnClickListener {
                if (activity == null || !isAdded) return@setOnClickListener
                if (viewModel.startDate.toLocalDate().isBefore(viewModel.endDate.toLocalDate())) {
                    viewModel.searchByDate()
                } else {
                    toast(R.string.data_range_selection_error)
                }
            }
        }

        viewModel.browsingHistoryList.observe(viewLifecycleOwner) { list ->
            if (mAdapter == null || binding == null) return@observe
            if (list.isEmpty()) {
                mAdapter?.showNoData()
                return@observe
            }
            var lastDate: String? = null
            val data: MutableList<Any> = ArrayList()
            list.forEach {
                val dateString = ReadableTime.getDateString(
                    it.browsingHistory.browsedDateTime
                )
                if (lastDate == null || dateString != lastDate) {
                    data.add(dateString)
                }
                if (it.post != null) {
                    data.add(it.post)
                    lastDate = dateString
                }
            }
            mAdapter?.setDiffNewData(data)
            mAdapter?.setFooterView(
                layoutInflater.inflate(
                    R.layout.view_no_more_data,
                    binding!!.recyclerView,
                    false
                )
            )
            Timber.i("${this.javaClass.simpleName} Adapter will have ${data.size} items")
        }

        sharedVM.currentDomain.observe(viewLifecycleOwner) {
            if (it != cacheDomain) {
                viewModel.searchByDate()
                cacheDomain = it
            }
        }

        viewCaching = false
        return binding!!.root
    }

    private fun setStartDate(date: Calendar) {
        viewModel.startDate = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
        binding?.startDate?.text = ReadableTime.getDateString(viewModel.startDate)
    }

    private fun setEndDate(date: Calendar) {
        viewModel.endDate = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
        binding?.endDate?.text = ReadableTime.getDateString(viewModel.endDate)
    }

    private class DateStringBinder : QuickItemBinder<String>() {
        override fun convert(holder: BaseViewHolder, data: String) {
            holder.setText(R.id.text, data)
        }

        override fun getLayoutId(): Int = R.layout.list_item_simple_text
    }

    inner class PostBinder(private val sharedViewModel: SharedViewModel) : QuickItemBinder<Post>() {
        override fun convert(holder: BaseViewHolder, data: Post) {
            holder.convertUserId(data.userid, data.admin)
                .convertTitleAndName(data.getSimplifiedTitle(), data.getSimplifiedName())
                .convertRefId(context, data.id)
                .convertTimeStamp(data.now)
                .convertForumAndReplyCount(
                    data.replyCount,
                    sharedViewModel.getForumOrTimelineDisplayName(data.fid)
                )
                .convertSage(data.sage, data.isStickyTopBanner())
                .convertImage(data.getImgUrl())
                .convertContent(context, data.content)
        }

        override fun getLayoutId(): Int = R.layout.list_item_post

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val view = parent.getItemView(getLayoutId()).applyTextSizeAndLetterSpacing()
            PostCardFactory.applySettings(view as MaterialCardView)
            return BaseViewHolder(view)
        }

        override fun onClick(holder: BaseViewHolder, view: View, data: Post, position: Int) {
            if (activity == null || !isAdded) return
            viewCaching = DawnApp.applicationDataStore.getViewCaching()
            val navAction = MainNavDirections.actionGlobalCommentsFragment(data.id, data.fid)
            findNavController().navigate(navAction)
        }

        override fun onChildClick(holder: BaseViewHolder, view: View, data: Post, position: Int) {
            if (activity == null || !isAdded) return
            if (view.id == R.id.attachedImage) {
                val viewerPopup = ImageViewerPopup(context)
                viewerPopup.setSingleSrcView(view as ImageView?, data)
                XPopup.Builder(context)
                    .isDestroyOnDismiss(true)
                    .asCustom(viewerPopup)
                    .show()
            }
        }
    }

    private class PostDiffer : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return true
        }
    }

    private class DateStringDiffer : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!viewCaching) {
            mAdapter = null
            binding = null
        }
        Timber.d("Fragment View Destroyed ${binding == null}")
    }
}