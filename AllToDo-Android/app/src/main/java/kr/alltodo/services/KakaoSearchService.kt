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
            .addQueryParameter("size", "5")

        if (latitude != null && longitude != null) {
            urlBuilder.addQueryParameter("x", longitude.toString())
            urlBuilder.addQueryParameter("y", latitude.toString())
            urlBuilder.addQueryParameter("radius", "20000") // 20km radius search
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "KakaoAK $REST_API_KEY")
            .build()
        
        println(">>> [KakaoSearchService] Request URL: ${request.url}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println(">>> [KakaoSearchService] onFailure: ${e.message}")
                Log.e("KakaoSearch", "Search failed: ${e.message}")
                onResult(null, -1) // -1 for network failure
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                println(">>> [KakaoSearchService] onResponse: code=${response.code}")
                
                if (!response.isSuccessful) {
                    println(">>> [KakaoSearchService] onResponse: FAILED with body=$bodyString")
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
                                    distance = doc.optString("distance")
                                )
                            )
                        }
                    } catch (e: Exception) {
                        println(">>> [KakaoSearchService] Parsing error: ${e.message}")
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
    val distance: String? = null
)
