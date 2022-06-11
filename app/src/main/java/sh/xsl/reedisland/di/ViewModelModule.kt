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

package sh.xsl.reedisland.di

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import sh.xsl.reedisland.screens.MainActivity
import sh.xsl.reedisland.screens.SharedViewModel
import sh.xsl.reedisland.screens.comments.CommentsFragment
import sh.xsl.reedisland.screens.comments.CommentsViewModel
import sh.xsl.reedisland.screens.history.*
import sh.xsl.reedisland.screens.notification.NotificationFragment
import sh.xsl.reedisland.screens.notification.NotificationViewModel
import sh.xsl.reedisland.screens.posts.PostsFragment
import sh.xsl.reedisland.screens.posts.PostsViewModel
import sh.xsl.reedisland.screens.profile.*
import sh.xsl.reedisland.screens.search.SearchFragment
import sh.xsl.reedisland.screens.search.SearchViewModel
import sh.xsl.reedisland.screens.subscriptions.*

@Module
abstract class ViewModelModule {

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun mainActivity(): MainActivity

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun postsFragment(): PostsFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun feedsFragment(): FeedsFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun commentsFragment(): CommentsFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun trendsFragment(): TrendsFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun browsingHistoryFragment(): BrowsingHistoryFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun postHistoryFragment(): PostHistoryFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun feedPagerFragment(): SubscriptionPagerFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun historyPagerFragment(): HistoryPagerFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun profileFragment(): ProfileFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun searchFragment(): SearchFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun aboutFragment(): AboutFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun commonForumsFragment(): CommonForumsFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun commonPostsFragment(): CommonPostsFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun customSettingFragment(): CustomSettingFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun notificationFragment(): NotificationFragment

    @ContributesAndroidInjector(modules = [ViewModelBuilder::class])
    internal abstract fun emojiSettingFragment(): EmojiSettingFragment

    @Binds
    @IntoMap
    @ViewModelKey(PostsViewModel::class)
    abstract fun bindPostsViewModel(viewModel: PostsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FeedsViewModel::class)
    abstract fun bindFeedsViewModel(viewModel: FeedsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CommentsViewModel::class)
    abstract fun bindCommentsViewModel(viewModel: CommentsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TrendsViewModel::class)
    abstract fun bindTrendsViewModel(viewModel: TrendsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(BrowsingHistoryViewModel::class)
    abstract fun bindBrowsingHistoryViewModel(viewModel: BrowsingHistoryViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PostHistoryViewModel::class)
    abstract fun bindPostHistoryViewModel(viewModel: PostHistoryViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ProfileViewModel::class)
    abstract fun bindProfileViewModel(viewModel: ProfileViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SearchViewModel::class)
    abstract fun bindSearchViewModel(viewModel: SearchViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SharedViewModel::class)
    abstract fun bindSharedViewModel(viewModel: SharedViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CustomSettingViewModel::class)
    abstract fun bindCustomSettingViewModel(viewModel: CustomSettingViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(NotificationViewModel::class)
    abstract fun bindNotificationViewModel(viewModel: NotificationViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EmojiSettingViewModel::class)
    abstract fun bindEmojiSettingViewModel(viewModel: EmojiSettingViewModel): ViewModel
}