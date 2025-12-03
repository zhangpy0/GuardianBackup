package top.zhangpy.guardianbackup.core.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("api/backups/upload") // 假设这是您的服务器上传端点
    suspend fun uploadBackup(
        @Part("manifest") manifest: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Unit> // 假设成功后服务器返回 2xx 状态码，没有特定响应体

    @POST
    suspend fun backupList()
}

// 建议创建一个 Retrofit 单例
object RetrofitClient {
    private const val BASE_URL = ""

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}