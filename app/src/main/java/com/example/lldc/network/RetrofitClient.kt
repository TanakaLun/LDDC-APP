package com.example.lldc.network

import android.content.Context
import com.example.lldc.data.Song
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "http://127.0.0.1:8000/"
    private var retrofit: Retrofit? = null
    private var currentUrl: String? = null
    private val observers = mutableListOf<() -> Unit>()

    /**
     * 重置Retrofit客户端，清除缓存的实例和URL
     */
    fun reset() {
        retrofit = null
        currentUrl = null
        // URL已被重置，不需要立即通知观察者
        // 观察者会在getApiService被调用时收到通知
    }

    fun getApiService(context: Context): ApiService {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val baseUrl = sharedPrefs.getString("api_url", DEFAULT_BASE_URL)!!

        println("RetrofitClient: 当前请求的BaseURL: $baseUrl")
        println("RetrofitClient: 当前缓存的URL: $currentUrl")

        // 如果URL没有变化，并且Retrofit实例已存在，则重用它
        if (baseUrl == currentUrl && retrofit != null) {
            println("RetrofitClient: 重用现有的API服务实例")
            return retrofit!!.create(ApiService::class.java)
        }

        // 创建一个 Gson 实例，并注册自定义的 TypeAdapter
        val gson = GsonBuilder()
            .registerTypeAdapter(Song::class.java, SongDeserializer())
            .create()

        // 否则，创建一个新的Retrofit实例
        currentUrl = baseUrl
        println("RetrofitClient: 创建新的API服务实例，URL: $baseUrl")
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        // 通知所有观察者URL已更改
        println("RetrofitClient: 通知${observers.size}个观察者API服务已更新")
        notifyObservers()
        
        return retrofit!!.create(ApiService::class.java)
    }
    
    // 添加观察者
    fun addObserver(observer: () -> Unit) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    // 移除观察者
    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    // 通知所有观察者
    private fun notifyObservers() {
        observers.forEach { it() }
    }
}