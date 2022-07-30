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

import android.annotation.SuppressLint
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.CenterPopupView
import com.lxj.xpopup.util.XPopupUtils
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.R
import sh.xsl.reedisland.data.local.entity.Comment
import sh.xsl.reedisland.screens.util.ContentTransformation.transformContent
import sh.xsl.reedisland.screens.util.ContentTransformation.transformCookie
import sh.xsl.reedisland.screens.util.ContentTransformation.transformTime
import sh.xsl.reedisland.screens.widgets.popups.ImageViewerPopup
import sh.xsl.reedisland.screens.widgets.spans.ReferenceSpan
import sh.xsl.reedisland.util.DataResource
import sh.xsl.reedisland.util.LoadingStatus

@SuppressLint("ViewConstructor")
// uses caller fragment's context, should not live without fragment
class QuotePopup(
    caller: CommentsFragment,
    liveQuote: LiveData<DataResource<Comment>>,
    private val currentPostId: String,
    private val po: String
) : CenterPopupView(caller.requireContext()) {

    private var mCaller: CommentsFragment? = caller
    private var mLiveQuote: LiveData<DataResource<Comment>>? = liveQuote
    override fun getImplLayoutId(): Int = R.layout.popup_quote

    override fun getMaxWidth(): Int = (XPopupUtils.getAppWidth(context) * .9f).toInt()

    private val liveQuoteObs = Observer<DataResource<Comment>> {
        when (it.status) {
            LoadingStatus.SUCCESS -> {
                convertQuote(it.data!!, po)
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
                findViewById<ConstraintLayout>(R.id.quote).visibility = View.VISIBLE
            }
            LoadingStatus.ERROR -> {
                dismiss()
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
            // do nothing when loading or no data
            else -> {}
        }
    }

    fun listenToLiveQuote(lifecycleOwner: LifecycleOwner) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.quote).visibility = View.GONE
        mLiveQuote?.observe(lifecycleOwner, liveQuoteObs)
    }

    private fun convertQuote(quote: Comment, po: String) {
        mLiveQuote?.removeObserver(liveQuoteObs)

        findViewById<TextView>(R.id.userId).text = transformCookie(quote.userid, quote.admin, po)

        findViewById<ImageView>(R.id.OPHighlight).visibility =
            if (quote.userid == po) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.timestamp).text = transformTime(quote.now)

        findViewById<TextView>(R.id.refId).text =
            context.resources.getString(R.string.ref_id_formatted, quote.id)

        findViewById<TextView>(R.id.sage).run {
            visibility = if (quote.sage == "1") {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        val title = quote.getSimplifiedTitle()
        findViewById<TextView>(R.id.title).run {
            if (title.isNotBlank()) {
                text = title
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        val name = quote.getSimplifiedName()
        findViewById<TextView>(R.id.name).run {
            if (name.isNotBlank()) {
                text = name
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        // load image
        findViewById<ImageView>(R.id.attachedImage).run {
            visibility = if (quote.img != null) {
                sh.xsl.reedisland.util.GlideApp.with(context)
                    .load(DawnApp.currentThumbCDN + quote.img + quote.ext)
//                    .override(250, 250)
                    .fitCenter()
                    .into(this)
                View.VISIBLE
            } else {
                View.GONE
            }
            setOnClickListener { imageView ->
                val viewerPopup = ImageViewerPopup(context)
                viewerPopup.setSingleSrcView(imageView as ImageView?, quote)
                XPopup.Builder(context)
                    .asCustom(viewerPopup)
                    .show()
            }
        }

        val referenceClickListener = object : ReferenceSpan.ReferenceClickHandler {
            override fun handleReference(id: String) {
                if (isShow) {
                    mCaller?.displayQuote(id)
                }
            }
        }

        findViewById<TextView>(R.id.content).run {
            maxLines = 15
            movementMethod = LinkMovementMethod.getInstance()
            text = transformContent(
                context,
                quote.content ?: "",
                DawnApp.applicationDataStore.lineHeight,
                DawnApp.applicationDataStore.segGap, referenceClickListener
            )
            textSize = DawnApp.applicationDataStore.textSize
            letterSpacing = DawnApp.applicationDataStore.letterSpace
        }


        findViewById<Button>(R.id.jumpToQuotedPost).run {
            visibility = if (quote.parentId.equals("0")) View.VISIBLE else View.GONE
            setOnClickListener {
                if (isShow) {
                    mCaller?.jumpToNewPost(quote.id)
                }
            }
        }
    }

    override fun onDismiss() {
        mLiveQuote?.removeObserver(liveQuoteObs)
        mLiveQuote = null
        mCaller = null
        super.onDismiss()
    }
}