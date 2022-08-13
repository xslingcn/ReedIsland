package sh.xsl.reedisland.screens.widgets.spans

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class UserFunctionSpan(
    val code: String,
    private val clickListener: FunctionClickHandler? = null
) :
    ClickableSpan() {
    override fun onClick(widget: View) {
        clickListener?.handleFunction(code)
    }

    override fun updateDrawState(ds: TextPaint) {}

    interface FunctionClickHandler {
        fun handleFunction(code: String)
    }
}