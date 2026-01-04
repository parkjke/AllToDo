package kr.alltodo.services

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KakaoSearchService @Inject constructor() {
    private val client = OkHttpClient()
    // [WARNING] REST_API_KEY placeholder. Using native key as a fallback if possible, 
    // but REST API usually requires REST API KEY.
    private val REST_API_KEY = "622cc25924dcce684064c5794fbfe384"

    fun searchKeyword(
        query: String,
        latitude: Double?,
        longitude: Double?,
        onResult: (List<SearchResult>?, Int?) -> Unit
    ) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("dapi.kakao.com")
            .addPathSegment("v2")
            .addPathSegment("local")
            .addPathSegment("search")
            .addPathSegment("keyword.json")
            .addQueryParameter("query", query)
            .addQueryParameter("size", "10")

        if (latitude != null && longitude != null) {
            urlBuilder.addQueryParameter("x", longitude.toString())
            urlBuilder.addQueryParameter("y", latitude.toString())
            urlBuilder.addQueryParameter("radius", "20000")
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "KakaoAK $REST_API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null, -1)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(null, response.code)
                    return
                }

                val results = mutableListOf<SearchResult>()
                bodyString?.let { body ->
                    try {
                        val json = JSONObject(body)
                        val documents = json.getJSONArray("documents")
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            results.add(
                                SearchResult(
                                    name = doc.getString("place_name"),
                                    address = doc.getString("address_name"),
                                    latitude = doc.getDouble("y"),
                                    longitude = doc.getDouble("x"),
                                    distance = doc.optString("distance"),
                                    isAddress = false
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("KakaoSearch", "Parsing error: ${e.message}")
                    }
                }
                onResult(results, response.code)
            }
        })
    }

    fun searchAddress(
        query: String,
        onResult: (List<SearchResult>?, Int?) -> Unit
    ) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("dapi.kakao.com")
            .addPathSegment("v2")
            .addPathSegment("local")
            .addPathSegment("search")
            .addPathSegment("address.json")
            .addQueryParameter("query", query)
            .addQueryParameter("size", "10")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "KakaoAK $REST_API_KEY")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null, -1)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(null, response.code)
                    return
                }

                val results = mutableListOf<SearchResult>()
                bodyString?.let { body ->
                    try {
                        val json = JSONObject(body)
                        val documents = json.getJSONArray("documents")
                        for (i in 0 until documents.length()) {
                            val doc = documents.getJSONObject(i)
                            // address.json parsing
                            val addressName = doc.getString("address_name")
                            results.add(
                                SearchResult(
                                    name = addressName,
                                    address = addressName,
                                    latitude = doc.getDouble("y"),
                                    longitude = doc.getDouble("x"),
                                    isAddress = true
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("KakaoSearch", "Parsing error: ${e.message}")
                    }
                }
                onResult(results, response.code)
            }
        })
    }
}

data class SearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distance: String? = null,
    val isAddress: Boolean = false
)
