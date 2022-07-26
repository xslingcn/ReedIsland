package sh.xsl.reedisland.screens.widgets

import android.os.Handler
import android.os.Looper
import android.view.View

class SingleAndDoubleClickListener(
    private val doubleClickTimeLimitMills: Long = 200,
    private val callback: SingleAndDoubleClickCallBack,
) : View.OnClickListener {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var lastClicked: Long = -1L
    private var isSingleClick: Boolean = false
    private val runnable: Runnable = Runnable {
        if (isSingleClick) callback.singleClicked()
    }

    override fun onClick(v: View?) {
        if (isDoubleClicked()) {
            isSingleClick = false
            handler.removeCallbacks(runnable)
            callback.doubleClicked()
            return
        }
        isSingleClick = true
        handler.postDelayed(runnable, doubleClickTimeLimitMills)
        lastClicked = System.currentTimeMillis()
    }

    private fun getTimeDiff(from: Long, to: Long): Long = to - from

    private fun isDoubleClicked(): Boolean {
        return getTimeDiff(lastClicked, System.currentTimeMillis()) <= doubleClickTimeLimitMills
    }

    interface SingleAndDoubleClickCallBack {
        fun singleClicked()
        fun doubleClicked()
    }
}