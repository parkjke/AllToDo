package kr.alltodo.tp

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class TPApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Kakao SDK here for better stability
        KakaoMapSdk.init(this, "8f86ef8dfb6e5d168ccc72d0b19b6420")
    }
}
