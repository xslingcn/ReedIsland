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

package sh.xsl.reedisland.screens.tasks

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.king.zxing.CaptureActivity
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager
import com.king.zxing.analyze.MultiFormatAnalyzer
import com.king.zxing.util.CodeUtils
import com.king.zxing.util.LogUtils
import sh.xsl.reedisland.R
import sh.xsl.reedisland.screens.util.ToolBar.themeStatusBar
import sh.xsl.reedisland.util.ImageUtil
import sh.xsl.reedisland.util.IntentsHelper.Companion.CAMERA_SCAN_RESULT
import timber.log.Timber

class QRCookieActivity : CaptureActivity() {

    override fun getLayoutId(): Int {
        return R.layout.activity_qrcookie
    }

    private val getCookieImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.run {
                try {
                    val file =
                        ImageUtil.getImageFileFromUri(this@QRCookieActivity, uri)
                            ?: return@registerForActivityResult
                    val res = CodeUtils.parseQRCode(file.path)
                    if (res != null) {
                        val intent = Intent()
                        intent.putExtra(CAMERA_SCAN_RESULT, res)
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@QRCookieActivity,
                            R.string.did_not_get_cookie_from_image,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    // Ignore
                    Timber.e(e)
                }
            }
        }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        findViewById<Button>(R.id.selectFromGallery).setOnClickListener {
            getCookieImage.launch("image/*")
        }

        LogUtils.setPriority(LogUtils.ERROR)

        themeStatusBar()
    }

    override fun initCameraScan() {
        super.initCameraScan()
        val decodeConfig: DecodeConfig = DecodeConfig()
            .setHints(DecodeFormatManager.QR_CODE_HINTS) //设置只识别二维码会提升速度

        cameraScan.setPlayBeep(false) //播放音效
            .setVibrate(true) //震动
            .setAnalyzer(MultiFormatAnalyzer(decodeConfig))
    }
}