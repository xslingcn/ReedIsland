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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.zhpan.indicator.IndicatorView
import com.zhpan.indicator.enums.IndicatorSlideMode
import com.zhpan.indicator.enums.IndicatorStyle
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.R
import sh.xsl.reedisland.screens.util.Layout
import sh.xsl.reedisland.screens.widgets.BaseNavFragment
import sh.xsl.reedisland.screens.widgets.BasePagerFragment

class HistoryPagerFragment : BasePagerFragment() {
    private val browsingIndex = DawnApp.applicationDataStore.getHistoryPagerBrowsingIndex()
    override val pageTitleResIds = mutableMapOf<Int, Int>().apply {
        put(browsingIndex, R.string.browsing_history)
        put(1 - browsingIndex, R.string.post_history)
    }

    override val pageFragmentClass = mutableMapOf<Int, Class<out BaseNavFragment>>().apply {
        put(browsingIndex, BrowsingHistoryFragment::class.java)
        put(1 - browsingIndex, PostHistoryFragment::class.java)
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
                            lifecycleOwner(this@HistoryPagerFragment)
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
    }
}