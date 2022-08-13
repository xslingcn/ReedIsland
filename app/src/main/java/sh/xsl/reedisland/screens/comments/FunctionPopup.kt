package sh.xsl.reedisland.screens.comments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lxj.xpopup.core.CenterPopupView
import com.lxj.xpopup.util.XPopupUtils
import io.github.kbiakov.codeview.CodeView
import io.github.kbiakov.codeview.extractLines
import io.github.kbiakov.codeview.highlight.CodeHighlighter
import io.github.kbiakov.codeview.highlight.ColorTheme
import sh.xsl.reedisland.DawnApp
import sh.xsl.reedisland.R
import sh.xsl.reedisland.screens.util.ContentTransformation.transformCode
import sh.xsl.reedisland.screens.util.Layout.toast


class FunctionPopup(
    caller: CommentsFragment,
    private val code: String
) : CenterPopupView(caller.requireContext()) {
    private val mCaller = caller
    private val maxLines = 15

    private var codeView: CodeView? = null
    private var mCode = code

    override fun getImplLayoutId(): Int = R.layout.popup_function

    override fun getMaxWidth(): Int = (XPopupUtils.getAppWidth(context) * .9f).toInt()

    override fun onCreate() {
        super.onCreate()
        mCode = CodeHighlighter.highlight(
            "js", code,
            when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> ColorTheme.MONOKAI.theme()
                else -> ColorTheme.SOLARIZED_LIGHT.theme()
            }
        )
        findViewById<TextView>(R.id.userId).visibility = View.GONE
        findViewById<ImageView>(R.id.OPHighlight).visibility = View.GONE
        findViewById<TextView>(R.id.timestamp).visibility = View.GONE
        findViewById<TextView>(R.id.refId).visibility = View.GONE
        findViewById<TextView>(R.id.sage).visibility = View.GONE
        findViewById<TextView>(R.id.title).visibility = View.GONE
        findViewById<TextView>(R.id.name).visibility = View.GONE
        findViewById<ImageView>(R.id.attachedImage).visibility = View.GONE
        findViewById<TextView>(R.id.content).run {
            maxLines = 15
            movementMethod = LinkMovementMethod.getInstance()
            text = transformCode(
                prepareCodeLines(),
                DawnApp.applicationDataStore.lineHeight,
                DawnApp.applicationDataStore.segGap
            )
            textSize = DawnApp.applicationDataStore.textSize
            letterSpacing = DawnApp.applicationDataStore.letterSpace
        }
        findViewById<Button>(R.id.copyCode).run {
            visibility = View.VISIBLE
            setOnClickListener {
                ContextCompat.getSystemService(
                    mCaller.requireContext(),
                    ClipboardManager::class.java
                )?.setPrimaryClip(ClipData.newPlainText("代码", code))
                mCaller.toast(resources.getString(R.string.content_copied, "代码"))
            }
        }
    }

    private fun prepareCodeLines(): String {
        return extractLines(mCode).joinToString("<br>")
    }


    override fun onDismiss() {
        codeView = null
        super.onDismiss()
    }

}