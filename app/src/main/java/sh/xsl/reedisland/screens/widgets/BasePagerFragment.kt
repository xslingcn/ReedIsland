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

package sh.xsl.reedisland.screens.widgets

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.zhpan.indicator.IndicatorView
import com.zhpan.indicator.enums.IndicatorSlideMode
import com.zhpan.indicator.enums.IndicatorStyle
import dagger.android.support.DaggerFragment
import sh.xsl.reedisland.R
import sh.xsl.reedisland.databinding.FragmentBasePagerBinding
import sh.xsl.reedisland.screens.MainActivity
import sh.xsl.reedisland.screens.util.Layout
import timber.log.Timber

abstract class BasePagerFragment : DaggerFragment() {
    private var binding: FragmentBasePagerBinding? = null

    abstract val pageTitleResIds: Map<Int, Int>
    abstract val pageFragmentClass: Map<Int, Class<out BaseNavFragment>>

    private val titleUpdateCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            updateTitle(pageTitleResIds[position] ?: error("Missing title ResIds"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (pageFragmentClass.size != pageTitleResIds.size) {
            throw Exception("Page Assertion failed")
        }
        binding = FragmentBasePagerBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_fragment_base_pager, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.pageIndicator)?.actionView?.findViewById<IndicatorView>(R.id.pageIndicatorView)
                    ?.apply {
                        setSliderColor(
                            requireContext().getColor(R.color.lime_500),
                            requireContext().getColor(R.color.pure_light)
                        )
                        setSliderWidth(requireContext().resources.getDimension(R.dimen.dp_10))
                        setSliderHeight(requireContext().resources.getDimension(R.dimen.dp_10))
                        setSliderGap(requireContext().resources.getDimension(R.dimen.dp_8))
                        setSlideMode(IndicatorSlideMode.WORM)
                        setIndicatorStyle(IndicatorStyle.CIRCLE)
                        setupWithViewPager(binding!!.viewPager2)
                    }
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
                        MaterialDialog(requireContext()).show {
                            lifecycleOwner(this@BasePagerFragment)
                            title(R.string.help)
                            message(R.string.pager_usage_help)
                            positiveButton(R.string.acknowledge)
                        }
                        true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        /** workaround for https://issuetracker.google.com/issues/134912610
         *  programmatically remove over scroll edge effect
         */
        (binding!!.viewPager2.getChildAt(0) as RecyclerView).overScrollMode = View.OVER_SCROLL_NEVER

        binding!!.viewPager2.adapter =
            object : FragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) {
                override fun getItemCount(): Int = pageFragmentClass.size
                override fun createFragment(position: Int): Fragment {
                    return pageFragmentClass[position]?.newInstance()
                        ?: error("Missing Fragment Class")
                }
            }

        binding!!.viewPager2.registerOnPageChangeCallback(titleUpdateCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.viewPager2?.unregisterOnPageChangeCallback(titleUpdateCallback)
        binding = null
        Timber.d("Pager View Destroyed")
    }

    fun updateTitle(resId: Int) {
        (requireActivity() as MainActivity).setToolbarTitle(resId)
    }

}