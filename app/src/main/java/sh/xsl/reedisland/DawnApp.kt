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

package sh.xsl.reedisland

import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVHandler
import com.tencent.mmkv.MMKVLogLevel
import com.tencent.mmkv.MMKVRecoverStrategic
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import me.jessyan.retrofiturlmanager.RetrofitUrlManager
import sh.xsl.reedisland.data.local.ApplicationDataStore
import sh.xsl.reedisland.util.DawnConstants
import sh.xsl.reedisland.util.ReadableTime
import timber.log.Timber
import javax.inject.Inject

class DawnApp : DaggerApplication() {

    companion object {
        lateinit var applicationDataStore: ApplicationDataStore
        var currentDomain: String = DawnConstants.AWEIDomain
            private set

        fun onDomain(domain: String) {
            currentDomain = domain
        }

        val currentThumbCDN: String
            get() =
                when (currentDomain) {
                    DawnConstants.AWEIDomain -> "${DawnConstants.AWEI_IMG_CDN}thumb/"
                    DawnConstants.TNMBDomain -> "${DawnConstants.TNMB_IMG_CDN}thumb/"
                    else -> {
                        throw Exception("Unhandled Thumb CDN $currentDomain")
                    }
                }

        val currentImgCDN: String
            get() =
                when (currentDomain) {
                    DawnConstants.AWEIDomain -> "${DawnConstants.AWEI_IMG_CDN}image/"
                    DawnConstants.TNMBDomain -> "${DawnConstants.TNMB_IMG_CDN}image/"
                    else -> {
                        throw Exception("Unhandled Image CDN $currentDomain")
                    }
                }
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return sh.xsl.reedisland.di.DaggerDawnAppComponent.factory().create(applicationContext)
    }

    @Inject
    lateinit var mApplicationDataStore: ApplicationDataStore

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
            RetrofitUrlManager.getInstance().setDebug(true)
        } else {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        }

        applicationDataStore = mApplicationDataStore

        // domain

        // MMKV
        MMKV.initialize(this)
        MMKV.registerHandler(handler)
        setDefaultDayNightMode()
        // Time
        ReadableTime.initialize(this)
    }

    private fun setDefaultDayNightMode() {
        when (applicationDataStore.defaultTheme) {
            1 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            2 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
            }
        }
    }


    private val handler = object : MMKVHandler {
        override fun onMMKVCRCCheckFail(p0: String?): MMKVRecoverStrategic {
            throw Exception("onMMKVCRCCheckFail $p0")
        }

        override fun wantLogRedirecting(): Boolean {
            return true
        }

        override fun mmkvLog(
            level: MMKVLogLevel?,
            file: String?,
            line: Int,
            func: String?,
            message: String?
        ) {
            val log =
                "<" + file.toString() + ":" + line.toString() + "::" + func.toString() + "> " + message
            when (level) {
                MMKVLogLevel.LevelDebug -> {
                    Timber.d(log)
                }
                MMKVLogLevel.LevelInfo -> {
                }
                MMKVLogLevel.LevelWarning -> {
                }
                MMKVLogLevel.LevelError -> {
                    Timber.e(log)
                }
                MMKVLogLevel.LevelNone -> {
                }
                else -> {}
            }
        }

        override fun onMMKVFileLengthError(p0: String?): MMKVRecoverStrategic {
            throw Exception("onMMKVFileLengthError $p0")
        }
    }
}

