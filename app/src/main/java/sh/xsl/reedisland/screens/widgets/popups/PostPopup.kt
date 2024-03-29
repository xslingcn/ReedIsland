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

package sh.xsl.reedisland.screens.widgets.popups

import android.Manifest.permission
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.core.BottomPopupView
import com.lxj.xpopup.interfaces.SimpleCallback
import com.lxj.xpopup.util.KeyboardUtils
import kotlinx.coroutines.launch
import sh.xsl.reedisland.DawnApp.Companion.applicationDataStore
import sh.xsl.reedisland.R
import sh.xsl.reedisland.data.local.entity.Cookie
import sh.xsl.reedisland.data.local.entity.Emoji
import sh.xsl.reedisland.screens.MainActivity
import sh.xsl.reedisland.screens.SharedViewModel
import sh.xsl.reedisland.screens.adapters.QuickAdapter
import sh.xsl.reedisland.screens.util.Layout
import sh.xsl.reedisland.util.DawnConstants
import sh.xsl.reedisland.util.ImageUtil
import sh.xsl.reedisland.util.openLinksWithOtherApps
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime


@SuppressLint("ViewConstructor")
class PostPopup(private val caller: MainActivity, private val sharedVM: SharedViewModel) :
    BottomPopupView(caller) {

    private var newPost = false
    private var targetId: String? = null
    var name = ""
    private var email = ""
    var title = ""
    var content = ""

    private var waterMark: String? = null
    private var imageFile: File? = null
    private var cookieHash = ""
    private var targetPage = 1
    private var targetFid = ""
    private var report: Boolean? = null

    private var selectedCookie: Cookie? = null
    private var postCookie: Button? = null
    private var postForum: Button? = null
    private var roll: Button? = null
    private var cheatTime: Long = 0

    private var summary: TextView? = null
    private var toggleContainers: ConstraintLayout? = null
    private var expansionContainer: LinearLayout? = null
    private var attachmentContainer: ConstraintLayout? = null
    private var buttonToggleGroup: MaterialButtonToggleGroup? = null
    private var emojiContainer: RecyclerView? = null
    private var luweiStickerContainer: ConstraintLayout? = null
    private var postContent: TextInputLayout? = null
    private var postImagePreview: ImageView? = null

    // keyboard height listener
    private var keyboardHolder: LinearLayout? = null

    private var mHandler: Handler? = null
    private var latestPost: Pair<String, LocalDateTime>? = null
    private var counterUpdateCallback: Runnable? = null

    private fun updateTitle(targetId: String?, newPost: Boolean) {
        findViewById<TextView>(R.id.postTitle).text =
            if (report == true) context.getString(R.string.report)
            else if (newPost) "${context.getString(R.string.new_post)} > ${getForumTitle(targetId!!)}"
            else "${context.getString(R.string.reply_comment)} > $targetId"
    }

    private fun getForumTitle(targetId: String): String {
        return if (targetId == DawnConstants.TIMELINE_FORUM_ID) ""
        else sharedVM.postForumNameMapping[targetId] ?: ""
    }

    private fun updateForumButton(targetId: String?, newPost: Boolean) {
        findViewById<Button>(R.id.postForum).apply {
            visibility = if (!newPost) View.GONE else View.VISIBLE
            text =
                if (newPost && targetId != null && targetId != DawnConstants.TIMELINE_FORUM_ID) {
                    getForumTitle(targetId)
                } else {
                    context.getString(R.string.choose_forum)
                }
        }
    }

    private fun updateRollButton(newPost: Boolean) {
        findViewById<Button>(R.id.roll).apply {
            visibility = if (newPost) View.GONE else View.VISIBLE
        }
    }

    private fun updateRollText(function: String?) {
        function?.let {
            postContent?.editText?.text?.insert(
                postContent!!.editText!!.selectionStart,
                String.format(context.resources.getString(R.string.inserted_function), it)
            )
        }
    }

    private fun updateCookies() {
        if (selectedCookie == null || applicationDataStore.cookies.isEmpty()) {
            findViewById<Button>(R.id.postCookie)?.run {
                text = if (applicationDataStore.cookies.isEmpty()) {
                    context.getString(R.string.missing_cookie)
                } else {
                    selectedCookie = applicationDataStore.cookies[0]
                    selectedCookie!!.cookieName
                }
            }
        }
    }

    fun updateView(targetId: String?, newPost: Boolean, quote: String?) {
        if (targetId != DawnConstants.TIMELINE_FORUM_ID) this.targetId =
            targetId // cannot post to timeline
        postContent?.editText?.hint = sharedVM.getForumTips(targetId)
        this.newPost = newPost

        updateTitle(targetId, newPost)
        updateCookies()
        updateForumButton(targetId, newPost)
        updateRollButton(newPost)

        quote?.run { postContent?.editText?.text?.insert(0, quote) }
        if (report == true) setReportButtons()
    }

    private fun setReportButtons() {
        findViewById<Button>(R.id.forumRule).visibility = View.INVISIBLE
        findViewById<Button>(R.id.postForum).visibility = View.GONE
        findViewById<Button>(R.id.postExpand).visibility = View.GONE
        findViewById<Button>(R.id.postLuwei).visibility = View.INVISIBLE
        findViewById<Button>(R.id.postCamera).visibility = View.INVISIBLE
        findViewById<Button>(R.id.postImage).visibility = View.INVISIBLE
        findViewById<Button>(R.id.postDoodle).visibility = View.INVISIBLE
        findViewById<Button>(R.id.postSave).visibility = View.INVISIBLE
    }

    fun setupAndShow(
        targetId: String?,
        targetFid: String,
        newPost: Boolean = false,
        targetPage: Int = 1,
        quote: String? = null,
        report: Boolean? = null
    ) {
        this.targetPage = targetPage
        this.targetFid = targetFid
        this.report = report
        XPopup.Builder(context)
            .setPopupCallback(object : SimpleCallback() {
                override fun beforeShow(popupView: BasePopupView?) {
                    super.beforeShow(popupView)
                    updateView(targetId, newPost, quote)
                }
            })
            .autoFocusEditText(false)
            .enableDrag(false)
            .moveUpToKeyboard(false)
            .asCustom(this)
            .show()
    }

    override fun getImplLayoutId(): Int {
        return R.layout.popup_post
    }

    override fun onShow() {
        super.onShow()
        KeyboardUtils.registerSoftInputChangedListener(caller.window, this) { height ->
            if (popupInfo == null) return@registerSoftInputChangedListener
            if (height > 0) {
                listOf(emojiContainer, luweiStickerContainer).map {
                    val lp = it?.layoutParams
                    lp?.height = height
                    it?.layoutParams = lp
                }
                buttonToggleGroup?.uncheck(R.id.postFace)
                buttonToggleGroup?.uncheck(R.id.postLuwei)
            }
            val lp = keyboardHolder?.layoutParams
            lp?.height = height
            keyboardHolder?.layoutParams = lp
        }
        /** On some system, EditText automatically grab focus then the keyboard is show,
         *  manually hiding the soft input on those systems. will cause a view flash...
         */
        KeyboardUtils.hideSoftInput(postContent)
    }

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()
        postContent = findViewById<TextInputLayout>(R.id.postContent).apply {
            setOnClickListener { view -> KeyboardUtils.showSoftInput(view) }
        }

        val emojiAdapter = QuickAdapter<Emoji>(R.layout.grid_item_emoji).apply {
            setOnItemClickListener { _, _, position ->
                postContent?.editText?.text?.insert(
                    postContent!!.editText!!.selectionStart,
                    data[position].value
                )
                sharedVM.setLastUsedEmoji(data[position])
            }
        }

        val luweiStickerAdapter = QuickAdapter<String>(R.layout.grid_item_luwei_sticker).apply {
            setOnItemClickListener { _, _, pos ->
                val emojiId = getItem(pos)
                val resourceId: Int = context.resources.getIdentifier(
                    "le$emojiId", "drawable",
                    context.packageName
                )
                imageFile = ImageUtil.getFileFromDrawable(caller, emojiId, resourceId)
                if (imageFile != null) {
                    postImagePreview!!.setImageResource(resourceId)
                    attachmentContainer!!.visibility = View.VISIBLE
                } else {
                    Toast.makeText(
                        context,
                        R.string.cannot_load_image_file,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        toggleContainers = findViewById<ConstraintLayout>(R.id.toggleContainers).also {
            expansionContainer = findViewById(R.id.expansionContainer)
            keyboardHolder = findViewById(R.id.keyboardHolder)
            emojiContainer = findViewById(R.id.emojiContainer)
            emojiContainer!!.layoutManager = GridLayoutManager(context, 3)

            emojiContainer!!.adapter = emojiAdapter

            // add luweiSticker
            luweiStickerContainer = findViewById(R.id.luweiStickerContainer)
            luweiStickerContainer.apply {
                findViewById<MaterialButtonToggleGroup>(R.id.luweiStickerToggle).addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (checkedId == R.id.luweiStickerWhite && isChecked) {
                        luweiStickerAdapter.setDiffNewData(
                            resources.getStringArray(R.array.LuweiStickersWhite).toMutableList()
                        )
                    } else if (checkedId == R.id.luweiStickerColor && isChecked) {
                        luweiStickerAdapter.setDiffNewData(
                            resources.getStringArray(R.array.LuweiStickersColor).toMutableList()
                        )
                    }
                }

                findViewById<Button>(R.id.luweiStickerColor).apply {
                    paint.shader = LinearGradient(
                        0f, 0f, paint.measureText(text.toString()), textSize, intArrayOf(
                            Color.parseColor("#F97C3C"),
                            Color.parseColor("#FDB54E"),
                            Color.parseColor("#64B678"),
                            Color.parseColor("#478AEA"),
                            Color.parseColor("#8446CC")
                        ), null, Shader.TileMode.CLAMP
                    )
                }
                findViewById<RecyclerView>(R.id.luweiStickerRecyclerView).apply {
                    layoutManager = GridLayoutManager(context, 3)
                    adapter = luweiStickerAdapter
                    luweiStickerAdapter.removeEmptyView()
                }
            }
        }

        attachmentContainer = findViewById<ConstraintLayout>(R.id.attachmentContainer).apply {
            postImagePreview = findViewById(R.id.postImagePreview)
        }

        summary = findViewById<TextView>(R.id.summary).apply { visibility = View.GONE }

        postForum = findViewById<Button>(R.id.postForum).apply {
            if (visibility == View.VISIBLE) {
                setOnClickListener {
                    KeyboardUtils.hideSoftInput(postContent!!)

                    MaterialDialog(context).show {
                        lifecycleOwner(caller)
                        title(R.string.select_target_forum)
                        val mapping = sharedVM.postForumNameMapping
                        listItemsSingleChoice(
                            items = mapping.values.toList()
                        ) { _, index, text ->
                            targetId = mapping.keys.toList()[index]
                            targetFid = targetId!!
                            postForum!!.text = text
                        }
                        // TODO 去除已停止回复的版块
                    }.onDismiss {
                        if (targetId == null) return@onDismiss
                        postContent?.editText?.hint = sharedVM.getForumTips(targetId!!)
                        updateTitle(targetId, newPost)
                    }
                }
            }
        }

        findViewById<Button>(R.id.roll).apply {
            roll = this

            setOnClickListener {
                if (newPost) {
                    postContent?.editText?.text?.insert(
                        postContent!!.editText!!.selectionStart,
                        context.resources.getString(R.string.inserted_roll)
                    )
                } else targetId?.let {
                    val postId: String = targetId!!
                    caller.lifecycleScope.launch {
                        val functions = sharedVM.getUserFunctionsByPostId(postId)
                        if (functions?.isEmpty() == true) {
                            postContent?.editText?.text?.insert(
                                postContent!!.editText!!.selectionStart,
                                context.resources.getString(R.string.inserted_roll)
                            )
                            return@launch
                        }
                        functions?.let {
                            KeyboardUtils.hideSoftInput(postContent!!)
                            MaterialDialog(context).show {
                                lifecycleOwner(caller)
                                title(R.string.select_user_function)
                                listItemsSingleChoice(
                                    items = functions
                                ) { _, _, text ->
                                    updateRollText(text.toString())
                                }
                            }
                        }
                    }
                }
            }
        }

        findViewById<Button>(R.id.postSend).setOnClickListener {
            KeyboardUtils.hideSoftInput(postContent!!)
            send()
        }

        findViewById<MaterialButtonToggleGroup>(R.id.toggleButtonGroup).apply {
            buttonToggleGroup = this
            addOnButtonCheckedListener { toggleGroup, checkedId, isChecked ->
                when (checkedId) {
                    R.id.postExpand -> {
                        expansionContainer!!.visibility = if (isChecked) View.VISIBLE else View.GONE
                        toggleGroup.findViewById<Button>(R.id.postExpand)?.run {
                            if (isChecked) {
                                animate().setDuration(200)
                                    .setInterpolator(DecelerateInterpolator())
                                    .rotation(180f)
                                    .start()
                            } else {
                                animate().setDuration(200)
                                    .setInterpolator(DecelerateInterpolator())
                                    .rotation(0f)
                                    .start()
                            }
                        }
                    }

                    R.id.postFace -> {
                        if (isChecked) {
                            KeyboardUtils.hideSoftInput(postContent!!)
                            if (emojiAdapter.data.isEmpty()) {
                                caller.lifecycleScope.launch {
                                    emojiAdapter.setDiffNewData(
                                        sharedVM.getAllEmoji().toMutableList()
                                    )
                                }
                            }
                        }
                        emojiContainer!!.visibility = if (isChecked) View.VISIBLE else View.GONE

                    }

                    R.id.postLuwei -> {
                        if (isChecked) {
                            KeyboardUtils.hideSoftInput(postContent!!)
                        }
                        luweiStickerContainer!!.visibility =
                            if (isChecked) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        findViewById<Button>(R.id.postDoodle).setOnClickListener {
            if (!caller.intentsHelper.checkAndRequestAllPermissions(
                    caller,
                    arrayOf(permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE)
                )
            ) {
                return@setOnClickListener
            }
            KeyboardUtils.hideSoftInput(postContent!!)
            caller.intentsHelper.drawNewDoodle(caller, this)
        }

        // TODO: save draft
        findViewById<Button>(R.id.postSave).apply {
            visibility = View.GONE
        }

        postCookie = findViewById<Button>(R.id.postCookie).apply {
            setOnClickListener {
                if (applicationDataStore.cookies.isNotEmpty()) {
                    KeyboardUtils.hideSoftInput(postContent!!)
                    MaterialDialog(context).show {
                        lifecycleOwner(caller)
                        title(R.string.select_cookie)
                        listItemsSingleChoice(items = applicationDataStore.cookies.map { c -> c.cookieDisplayName }) { _, ind, text ->
                            selectedCookie = applicationDataStore.cookies[ind]
                            postCookie!!.text = text
                        }
                    }
                } else {
                    Toast.makeText(caller, R.string.missing_cookie, Toast.LENGTH_SHORT).show()
                }
            }
        }


        // TODO: follow new storage permission policy(use MediaStore)
        findViewById<Button>(R.id.postImage).setOnClickListener {
            if (!caller.intentsHelper.checkAndRequestSinglePermission(
                    caller, permission.READ_EXTERNAL_STORAGE, true
                )
            ) {
                return@setOnClickListener
            }
            KeyboardUtils.hideSoftInput(postContent!!)
            caller.intentsHelper.getImageFromGallery(this)
        }

        findViewById<Button>(R.id.postImageDelete).setOnClickListener {
            imageFile = null
            postImagePreview?.setImageDrawable(null)
            attachmentContainer?.visibility = View.GONE
        }

        findViewById<Button>(R.id.postCamera).setOnClickListener {
            if (!caller.intentsHelper.checkAndRequestAllPermissions(caller)) {
                return@setOnClickListener
            }
            KeyboardUtils.hideSoftInput(postContent!!)
            caller.intentsHelper.getImageFromCamera(caller, this)
        }

        findViewById<Button>(R.id.forumRule).setOnClickListener {
            val fid = if (newPost && targetId != null) targetId!! else targetFid
            try {
                val biId = if (fid.toInt() > 0) fid.toInt() else 1
                MaterialDialog(context).show {
                    lifecycleOwner(caller)

                    val resourceId: Int =
                        context.resources.getIdentifier("bi_$biId", "drawable", context.packageName)
                    ContextCompat.getDrawable(context, resourceId)?.let {
                        it.setTint(Layout.getThemeInverseColor(context))
                        icon(drawable = it)
                    }
                    title(text = sharedVM.getForumOrTimelineDisplayName(fid))
                    message(text = sharedVM.getForumOrTimelineMsg(fid)) { html() }
                    positiveButton(R.string.acknowledge)
                }
            } catch (e: Exception) {
                Timber.d("Missing icon for fid $fid")
            }
        }

        findViewById<CheckBox>(R.id.postWater).setOnClickListener {
            waterMark = if ((it as CheckBox).isChecked) "true" else null
        }

        findViewById<Button>(R.id.postClose).setOnClickListener {
            KeyboardUtils.hideSoftInput(postContent!!)
            buttonToggleGroup?.clearChecked()
            dismiss()
        }

    }

    override fun onDismiss() {
        summary?.run { visibility = View.GONE }
        counterUpdateCallback?.let { mHandler?.removeCallbacks(it) }
        counterUpdateCallback = null
        mHandler = null
        super.onDismiss()
    }

    fun compressAndPreviewImage(uri: Uri?) {
        Timber.d("Picked a local image. Prepare to upload...")
        if (uri == null) {
            Toast.makeText(context, R.string.cannot_load_image_file, Toast.LENGTH_SHORT).show()
            return
        }
        val compressDialog = MaterialDialog(context).show {
            lifecycleOwner(caller)
            title(R.string.compressing_oversize_image)
            customView(R.layout.widget_loading)
            cancelable(false)
        }
        compressDialog.show()
        caller.lifecycleScope.launch {
            imageFile = ImageUtil.getCompressedImageFileFromUri(caller, uri)
            if (imageFile != null) {
                try {
                    ImageUtil.loadImageThumbnailToImageView(
                        caller,
                        uri,
                        150,
                        150,
                        postImagePreview!!
                    )
                    attachmentContainer!!.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Timber.e(e, "Cannot load thumbnail from image...")
                }
            } else {
                Toast.makeText(
                    context,
                    R.string.compressing_oversize_image_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
            compressDialog.dismiss()
        }
    }

    private fun clearEntries() {
        postContent?.editText?.text?.clear()
        findViewById<TextView>(R.id.formName).text = ""
        findViewById<TextView>(R.id.formEmail).text = ""
        findViewById<TextView>(R.id.formTitle).text = ""
        imageFile = null
        postImagePreview?.setImageDrawable(null)
        attachmentContainer?.visibility = View.GONE
        findViewById<MaterialButtonToggleGroup>(R.id.toggleButtonGroup).clearChecked()
        findViewById<MaterialButtonToggleGroup>(R.id.luweiStickerToggle).clearChecked()
    }

    private fun send() {
        if (!applicationDataStore.checkAcknowledgementPostingRule()) {
            MaterialDialog(context).show {
                lifecycleOwner(caller)
                title(R.string.please_comply_rules)
                message(R.string.acknowledge_post_rules)
                positiveButton(R.string.submit) {
                    applicationDataStore.acknowledgementPostingRule()
                    openLinksWithOtherApps(DawnConstants.AWEIHost, caller)
                }
                negativeButton(R.string.cancel)
            }
            return
        }

        if (selectedCookie == null) {
            Toast.makeText(caller, R.string.need_cookie_to_post, Toast.LENGTH_SHORT).show()
            return
        }
        if (targetId == null && newPost) {
            Toast.makeText(caller, R.string.please_select_target_forum, Toast.LENGTH_SHORT)
                .show()
            return
        }
        name = findViewById<TextView>(R.id.formName).text.toString()
        email = findViewById<TextView>(R.id.formEmail).text.toString()
        title = findViewById<TextView>(R.id.formTitle).text.toString()
        content = postContent!!.editText!!.text.toString()
        if (content.isBlank()) {
            if (imageFile != null) {
                postContent!!.editText!!.setText("分享图片")
                content = "分享图片"
            } else {
                Toast.makeText(caller, R.string.need_content_to_post, Toast.LENGTH_SHORT).show()
                return
            }
        }


        selectedCookie?.let { cookieHash = it.getApiHeaderCookieHash() }

        val postProgressDialog = MaterialDialog(context).show {
            lifecycleOwner(caller)
            title(R.string.sending)
            customView(R.layout.widget_loading)
            cancelable(false)
        }
        Timber.i("Posting...")
        caller.lifecycleScope.launch {
            sharedVM.sendPost(
                newPost,
                targetId!!,
                name,
                email,
                title,
                content,
                waterMark,
                imageFile,
                cookieHash,
                report
            ).let { message ->
                postProgressDialog.dismiss()
                Timber.d("Post successfully sent with response $message")
                dismissWith {
                    if (message == "Ok") {
                        sharedVM.searchAndSavePost(
                            newPost,
                            targetId!!,
                            targetFid,
                            targetPage,
                            selectedCookie?.cookieName ?: "",
                            content
                        )
                        clearEntries()
                        selectedCookie?.let { applicationDataStore.setLastUsedCookie(it) }
                        Toast.makeText(caller, "发送成功", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(caller, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}