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

package sh.xsl.reedisland.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.apache.commons.text.StringEscapeUtils
import org.json.JSONObject
import retrofit2.Call
import sh.xsl.reedisland.util.LoadingStatus
import timber.log.Timber
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class APIDataResponse<T>(
    val status: LoadingStatus,
    val data: T? = null,
    val message: String
) {

    /**
     * separate class for HTTP 204 responses so that we can make ApiSuccessResponse's body non-null.
     */
    class Empty<T> : APIDataResponse<T>(LoadingStatus.NO_DATA, null, "EmptyResponse")

    class BlankData<T>(message: String = "BlankDataResponse", data: T? = null) :
        APIDataResponse<T>(LoadingStatus.NO_DATA, data, message)

    class Error<T>(message: String, data: T? = null) :
        APIDataResponse<T>(LoadingStatus.ERROR, data, message)

    class Success<T>(message: String, data: T) :
        APIDataResponse<T>(LoadingStatus.SUCCESS, data, message)

    companion object {
        suspend fun <T> create(
            call: Call<ResponseBody>,
            parser: NMBJsonParser<T>
        ): APIDataResponse<T> {
            try {
                val response = withContext(Dispatchers.IO) { call.execute() }

                if (response.isSuccessful) {
                    val body = response.body()
                    body?.close()
                    if (body == null || response.code() == 204) {
                        return Empty()
                    }
                    val resBody = withContext(Dispatchers.IO) { body.string() }

                    return withContext(Dispatchers.Default) {
                        try {
                            Timber.d("Trying to parse response with supplied parser...")
                            Timber.d(call.request().url.toString())
                            Timber.d("Header: ${call.request().headers}")
                            Success(
                                "Parse success",
                                parser.parse(JSONObject(resBody).optString("result", resBody))
                            )
                        } catch (e: Exception) {
                            // server returns non json string
                            Timber.e("Parse failed: $e")
                            Timber.d("Response is non JSON data...")
//                            BlankData(StringEscapeUtils.unescapeJava(resBody.replace("\"", "")))
                            BlankData(StringEscapeUtils.unescapeJava(""))
                        }
                    }

                }

                return withContext(Dispatchers.IO) {
                    val msg = response.errorBody()?.string()
                    val errorMsg = if (!msg.isNullOrEmpty()) JSONObject(msg).optString(
                        "errmsg",
                        "未知错误"
                    ) else response.message()
                    Timber.e(errorMsg)
                    Error(errorMsg ?: "未知错误")
                }
            } catch (e: Exception) {
                Timber.e(e)
                return if (e is SSLException || e is UnknownHostException || e is SocketException)
                    Error("网络错误")
                else Error("未知错误 ${e.javaClass.simpleName}")
            }
        }
    }
}

