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

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.view.*
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemDragListener
import com.chad.library.adapter.base.listener.OnItemSwipeListener
import com.chad.library.adapter.base.module.DraggableModule
import com.chad.library.adapter.base.util.getItemView
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.card.MaterialCardView
import dagger.android.support.DaggerFragment
import sh.xsl.reedisland.MainNavDirections
import sh.xsl.reedisland.R
import sh.xsl.reedisland.data.local.entity.NotificationAndPost
import sh.xsl.reedisland.databinding.FragmentNotificationBinding
import sh.xsl.reedisland.screens.SharedViewModel
import sh.xsl.reedisland.screens.adapters.applyTextSizeAndLetterSpacing
import sh.xsl.reedisland.screens.posts.PostCardFactory
import sh.xsl.reedisland.screens.util.ContentTransformation
import sh.xsl.reedisland.screens.util.Layout
import sh.xsl.reedisland.screens.widgets.spans.RoundBackgroundColorSpan
import sh.xsl.reedisland.util.DawnConstants
import javax.inject.Inject

class NotificationFragment : DaggerFragment() {

    @Inject
    lateinit var viewModalFactory: ViewModelProvider.Factory

    private val sharedVM: SharedViewModel by activityViewModels { viewModalFactory }
    private val viewModel: NotificationViewModel by viewModels { viewModalFactory }

    private var binding: FragmentNotificationBinding? = null
    private var mAdapter: NotificationAdapter? = null
    private var cacheDomain: String = DawnConstants.AWEIDomain

    private val notificationObs = Observer<List<NotificationAndPost>> { list ->
        if (list.isNullOrEmpty()) mAdapter?.showNoData()
        else mAdapter?.setDiffNewData(list.toMutableList())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNotificationBinding.inflate(inflater, container, false)

        mAdapter = NotificationAdapter(R.layout.list_item_notification).apply {
            setOnItemClickListener { _, _, position ->
                mAdapter?.getItem(position)?.let {
                    viewModel.readNotification(it.notification)
                    val action = MainNavDirections.actionGlobalCommentsFragment(
                        it.notification.id,
                        it.notification.fid
                    )
                    findNavController().navigate(action)
                }
            }
            setDiffCallback(NotificationDiffer())
        }

        binding?.recyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mAdapter
            setHasFixedSize(true)
        }

        mAdapter?.setEmptyView(R.layout.view_no_data)
        viewModel.notificationAndPost.observe(viewLifecycleOwner, notificationObs)


//        sharedVM.currentDomain.observe(viewLifecycleOwner) {
//            if (it != cacheDomain) {
//                viewModel.refresh()
//                viewModel.notificationAndPost.observe(viewLifecycleOwner, notificationObs)
//                cacheDomain = it
//            }
//        }
        // Inflate the layout for this fragment
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_fragment_base_with_help, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                context?.let {
                    menu.findItem(R.id.help)?.icon?.setTint(
                        Layout.getThemeInverseColor(
                            it
                        )
                    )
                }
                super.onPrepareMenu(menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.help -> {
                        if (activity == null || !isAdded) return true
                        MaterialDialog(requireContext()).show {
                            lifecycleOwner(this@NotificationFragment)
                            title(R.string.feed_notification)
                            message(R.string.feed_notification_help)
                            positiveButton(R.string.acknowledge)
                        }
                        return true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private class NotificationDiffer : DiffUtil.ItemCallback<NotificationAndPost>() {
        override fun areItemsTheSame(
            oldItem: NotificationAndPost,
            newItem: NotificationAndPost
        ): Boolean {
            return oldItem.notification.id == newItem.notification.id
        }

        override fun areContentsTheSame(
            oldItem: NotificationAndPost,
            newItem: NotificationAndPost
        ): Boolean {
            return oldItem.notification == newItem.notification
        }
    }

    inner class NotificationAdapter(private val layoutResId: Int) :
        BaseQuickAdapter<NotificationAndPost, BaseViewHolder>(layoutResId),
        DraggableModule {

        override fun onCreateDefViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
            val view = parent.getItemView(layoutResId).applyTextSizeAndLetterSpacing()
            PostCardFactory.applySettings(view as MaterialCardView)
            return createBaseViewHolder(view)
        }

        private val dragListener: OnItemDragListener = object : OnItemDragListener {
            override fun onItemDragStart(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                val startColor: Int = android.R.color.transparent
                val endColor: Int = requireContext().resources.getColor(R.color.colorPrimary, null)
                ValueAnimator.ofArgb(startColor, endColor).apply {
                    addUpdateListener { animation ->
                        (viewHolder as BaseViewHolder).itemView.setBackgroundColor(
                            animation.animatedValue as Int
                        )
                    }
                    duration = 300
                }.start()
            }

            override fun onItemDragMoving(
                source: RecyclerView.ViewHolder,
                from: Int,
                target: RecyclerView.ViewHolder,
                to: Int
            ) {
            }

            override fun onItemDragEnd(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                val startColor: Int =
                    requireContext().resources.getColor(R.color.colorPrimary, null)
                val endColor: Int = android.R.color.transparent
                ValueAnimator.ofArgb(startColor, endColor).apply {
                    addUpdateListener { animation ->
                        (viewHolder as BaseViewHolder).itemView.setBackgroundColor(
                            animation.animatedValue as Int
                        )
                    }
                    duration = 300
                }.start()
            }
        }

        private val swipeListener: OnItemSwipeListener = object : OnItemSwipeListener {
            override fun onItemSwipeStart(viewHolder: RecyclerView.ViewHolder, pos: Int) {}

            override fun clearView(viewHolder: RecyclerView.ViewHolder, pos: Int) {}

            override fun onItemSwiped(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                viewModel.notificationAndPost.value?.get(pos)?.let {
                    viewModel.deleteNotification(it.notification)
                }
            }

            override fun onItemSwipeMoving(
                canvas: Canvas,
                viewHolder: RecyclerView.ViewHolder?,
                dX: Float,
                dY: Float,
                isCurrentlyActive: Boolean
            ) {
                canvas.drawColor(requireContext().getColor(R.color.lime_500))
            }
        }

        init {
            draggableModule.isSwipeEnabled = true
            draggableModule.isDragEnabled = true
            draggableModule.setOnItemDragListener(dragListener)
            draggableModule.setOnItemSwipeListener(swipeListener)
            draggableModule.itemTouchHelperCallback.setSwipeMoveFlags(ItemTouchHelper.START or ItemTouchHelper.END)

        }

        override fun convert(holder: BaseViewHolder, item: NotificationAndPost) {
            holder.setText(R.id.refId, "No. ${item.notification.id}")
            holder.setText(
                R.id.timestamp,
                "检查时间: ${ContentTransformation.transformTime(item.notification.lastUpdatedAt)}"
            )
            val content =
                if (item.notification.message.isBlank()) item.post?.content
                    ?: "" else item.notification.message
            holder.setText(R.id.content, ContentTransformation.transformContent(context, content))
            holder.setGone(R.id.newReplyCount, item.notification.read)
            val forumName =
                SpannableString(sharedVM.getForumOrTimelineDisplayName(item.notification.fid))
            forumName.setSpan(
                RoundBackgroundColorSpan(),
                0,
                forumName.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            holder.getView<TextView>(R.id.forumName).setText(
                forumName,
                TextView.BufferType.SPANNABLE
            )

            if (!item.notification.read) {
                val newReplyCount = SpannableString(item.notification.newReplyCount.toString())
                newReplyCount.setSpan(
                    RoundBackgroundColorSpan(
                        "#FB3E3E",
                        "#FF9393"
                    ), 0, newReplyCount.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                holder.getView<TextView>(R.id.newReplyCount).setText(
                    newReplyCount,
                    TextView.BufferType.SPANNABLE
                )
            }

        }

        fun showNoData() {
            if (!hasEmptyView()) setEmptyView(R.layout.view_no_data)
            setList(null)
        }
    }

}