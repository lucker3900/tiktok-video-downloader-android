// Android 抖音视频下载器 - MainActivity.kt
// 完整源码请查看仓库中的其他文件

package com.tiktokdownloader

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.net.URL
import okhttp3.Headers
import kotlinx.coroutines.delay
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.net.Uri

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TikTokDownloader"
        private const val REQUEST_TIMEOUT = 30000L
        
        // 增强的User-Agent池（模拟真实设备）
        private val USER_AGENTS = listOf(
            // 抖音官方App User-Agent格式
            "com.ss.android.ugc.aweme/230500 (Linux; U; Android 12; zh_CN; SM-G991B; Build/SP1A.210812.016; Cronet/TTNetVersion:b4c3f46c 2022-01-20 QuicVersion:0x00000001)",
            "com.ss.android.ugc.aweme/250300 (Linux; U; Android 13; zh_CN; Pixel 7; Build/TQ2A.230505.002; Cronet/TTNetVersion:d0b91bb2 2023-03-15 QuicVersion:0x00000001)",
            // 高版本浏览器User-Agent
            "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.4 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 15_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6 Mobile/15E148 Safari/604.1"
        )
        
        // 请求间隔控制（反频率限制）
        private var lastRequestTime = 0L
        private const val MIN_REQUEST_INTERVAL = 3000L // 增加到3秒间隔
        
        // 设备指纹模拟
        private val DEVICE_IDS = listOf(
            "MS4wLjABAAAATQhqSe_GjXqz6lfJQcyAilkOqq3JmofeIWEWDa0k7BU",
            "MS4wLjABAAAAZRoO_dI3sSVO3UsOmNSnt5TAeWa1GZ3NhrfNg-c-KpcH",
            "MS4wLjABAAAANF_mDAaGfbGJ_A1QMJ4oEJ_gGY8fG5rQ8X0WDaL5k7ZF",
            "MS4wLjABAAAAnkJiR5BdF2_J7Q8sK5mZdC8FT7xX3H9wU4nL2pQvE8Kx"
        )
        
        // Cookie模板
        private val COOKIE_TEMPLATES = listOf(
            "tt_webid=7123456789012345678; tt_webid_v2=7123456789012345678",
            "install_id=7234567890123456789; ttreq=1\$abc12345",
            "s_v_web_id=verify_123abc456def; ttwid=1%7Cabc123def456"
        )
    }

      private val client = OkHttpClient.Builder()
              .connectTimeout(30, TimeUnit.SECONDS)
                      .readTimeout(30, TimeUnit.SECONDS)
                              .build()

                                      private val requestPermissionLauncher = registerForActivityResult(
                                                ActivityResultContracts.RequestMultiplePermissions()
                                                    ) { permissions ->
                                                val allGranted = permissions.values.all { it }
                                                        if (allGranted) {
                                                                      downloadVideo()
                                                        } else {
                                                                      showMessage("需要存储权限才能保存视频")
                                                        }
                                      }

                                              override fun onCreate(savedInstanceState: Bundle?) {
                                                        super.onCreate(savedInstanceState)
                                                                setContentView(R.layout.activity_main)
                                                                        setupClickListeners()
                                              }

                                                      private fun setupClickListeners() {
                                                                findViewById<android.widget.Button>(R.id.btnDownload).setOnClickListener {
                                                                              val url = findViewById<android.widget.EditText>(R.id.etUrl).text.toString().trim()
                                                                                          if (url.isEmpty()) {
                                                                                                            showMessage("请输入抖音链接")
                                                                                                                            return@setOnClickListener
                                                                                          }
                                                                                                      
                                                                                                                  if (!isValidTikTokUrl(url)) {
                                                                                                                                    showMessage("无效的抖音链接")
                                                                                                                                                    return@setOnClickListener
                                                                                                                  }
                                                                                                                              
                                                                                                                                          checkPermissionsAndDownload()
                                                                }
                                                      }

                                                                    private fun isValidTikTokUrl(input: String): Boolean {
            // 先尝试从输入文本中提取URL
            val extractedUrl = extractUrlFromText(input)
            if (extractedUrl.isNullOrEmpty()) return false
            
            val patterns = listOf(
                        ".*douyin\\.com.*",
                        ".*tiktok\\.com.*",
                        ".*v\\.douyin\\.com.*",
                        ".*vm\\.tiktok\\.com.*"
                    )
                  return patterns.any { Pattern.matches(it, extractedUrl) }
      }
      
      private fun extractUrlFromText(text: String): String? {
            // 正则表达式匹配各种抖音URL格式 - 按优先级排序，优先匹配完整URL
            val urlPatterns = listOf(
                // 优先匹配完整的URL（包含所有参数）
                "https?://[^\\s]+douyin\\.com[^\\s]*",
                "https?://[^\\s]+tiktok\\.com[^\\s]*",
                // 然后匹配具体的URL格式
                "https?://www\\.douyin\\.com/video/[0-9]+[^\\s]*",
                "https?://www\\.iesdouyin\\.com/[^\\s]*",
                "https?://v\\.douyin\\.com/[a-zA-Z0-9]+[^\\s]*",
                "https?://vm\\.tiktok\\.com/[a-zA-Z0-9]+[^\\s]*",
                "https?://www\\.tiktok\\.com/@[^/]+/video/[0-9]+[^\\s]*"
            )
            
            for (pattern in urlPatterns) {
                Log.d(TAG, "尝试URL正则: $pattern")
                val regex = Pattern.compile(pattern)
                val matcher = regex.matcher(text)
                if (matcher.find()) {
                    val extractedUrl = matcher.group()
                    Log.d(TAG, "正则匹配成功，提取URL: $extractedUrl")
                    return extractedUrl
                }
            }
            
            Log.d(TAG, "所有URL正则都无法匹配文本: $text")
            return null
      }

                                                                      private fun checkPermissionsAndDownload() {
                                                                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                                                              arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                                                                                } else {
                                                                                              arrayOf(
                                                                                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                                                                                            )
                                                                                }

                                                                                                val allPermissionsGranted = permissions.all { permission ->
                                                                                                              ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                                                                                                }
                                                                                                        
                                                                                                                if (allPermissionsGranted) {
                                                                                                                              downloadVideo()
                                                                                                                } else {
                                                                                                                              requestPermissionLauncher.launch(permissions)
                                                                                                                }
                                                                      }

                                                                                    private fun downloadVideo() {
            val inputText = findViewById<android.widget.EditText>(R.id.etUrl).text.toString().trim()
            Log.d(TAG, "开始下载视频，输入文本: $inputText")
            
            // 从输入文本中提取URL
            val extractedUrl = extractUrlFromText(inputText)
            Log.d(TAG, "提取到的URL: $extractedUrl")
            if (extractedUrl.isNullOrEmpty()) {
                Log.e(TAG, "无法从文本中提取有效的抖音链接")
                showMessage("无法从文本中提取有效的抖音链接")
                return
            }
            
            showMessage("提取到链接: ${extractedUrl}")
            showLoading(true)
                                        
                                                lifecycleScope.launch {
                                                              try {
                                                                                val videoUrl = extractVideoUrl(extractedUrl)
                                                                                                if (videoUrl != null) {
                                                                                                                      val success = downloadAndSaveVideo(videoUrl)
                                                                                                                                          if (success) {
                                                                                                                                                                    showMessage("视频已保存到相册")
                                                                                                                                                                        } else {
                                                                                                                                                    showMessage("下载失败，请重试或检查网络连接")
                                                                                                                                                                        }
                                                                                                                                          } else {
                                                                                                                      showMessage("无法获取视频下载地址，可能链接已失效")
                                                                                                                                      }
                                                              } catch (e: Exception) {
                                                                                Log.e(TAG, "下载视频时发生异常", e)
                                                                                showMessage("网络错误: ${e.message ?: "未知错误"}")
                                                              } finally {
                                                                                showLoading(false)
                                                              }
                                                }
      }

                                                                                      // 更多方法实现请查看完整源码...

                                                                                                    private suspend fun extractVideoUrl(shareUrl: String): String? = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始提取视频URL，分享链接: $shareUrl")
                
                // 第一步：访问分享链接获取重定向后的真实URL - 使用更真实的浏览器头部
                enforceRequestRateLimit() // 请求频率控制
                val request = Request.Builder()
                    .url(shareUrl)
                    .headers(generateDynamicHeaders(isApiRequest = false))
                    .build()
                    
                Log.d(TAG, "发送HTTP请求获取重定向URL")
                val response = client.newCall(request).execute()
                val finalUrl = response.request.url.toString()
                Log.d(TAG, "重定向后的URL: $finalUrl")
                response.close()
                
                // 第二步：从最终URL中提取视频ID
                val videoId = extractVideoId(finalUrl)
                Log.d(TAG, "提取到的视频ID: $videoId")
                if (videoId == null) {
                    Log.e(TAG, "无法从URL中提取视频ID")
                    return@withContext null
                }
                
                // 优先：对标准视频页进行 WebView 嗅探（更易触发 /aweme/v1/play 请求）
                val standardVideoPage = "https://www.douyin.com/video/${'$'}videoId?previous_page=web_code_link"
                Log.d(TAG, "优先对标准视频页嗅探: ${'$'}standardVideoPage")
                val sniffedDirectUrl = sniffVideoUrlWithWebView(standardVideoPage)
                if (!sniffedDirectUrl.isNullOrEmpty()) {
                    Log.d(TAG, "WebView 嗅探得到直链: ${'$'}sniffedDirectUrl")
                    return@withContext sniffedDirectUrl
                }
                
                // 第三步：尝试第三方API服务获取视频信息（使用完整URL）
                Log.d(TAG, "尝试第三方API服务获取视频信息")
                val thirdPartyVideoUrl = tryThirdPartyApis(finalUrl, videoId!!)
                if (thirdPartyVideoUrl != null) {
                    Log.d(TAG, "从第三方API解析得到视频URL: $thirdPartyVideoUrl")
                    return@withContext thirdPartyVideoUrl
                }
                
                // 第四步：尝试移动端API获取视频信息（作为备用）
                Log.d(TAG, "第三方API失败，尝试移动端API获取视频信息")
                val mobileVideoUrl = tryMobileApi(videoId, finalUrl)
                if (mobileVideoUrl != null) {
                    Log.d(TAG, "从移动端API解析得到视频URL: $mobileVideoUrl")
                    return@withContext mobileVideoUrl
                }
                
                // 第四步：直接访问网页并解析HTML（作为备用方案）
                Log.d(TAG, "移动端API失败，尝试访问网页获取视频信息")
                val webPageRequest = Request.Builder()
                    .url(finalUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .addHeader("Accept-Encoding", "gzip, deflate, br")
                    .addHeader("DNT", "1")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Upgrade-Insecure-Requests", "1")
                    .addHeader("Referer", "https://www.douyin.com/")
                    .addHeader("Sec-Fetch-Dest", "document")
                    .addHeader("Sec-Fetch-Mode", "navigate")
                    .addHeader("Sec-Fetch-Site", "same-origin")
                    .addHeader("Sec-Fetch-User", "?1")
                    .addHeader("Cache-Control", "max-age=0")
                    .build()
                    
                val webPageResponse = client.newCall(webPageRequest).execute()
                val htmlContent = webPageResponse.body?.string()
                Log.d(TAG, "网页响应状态: ${webPageResponse.code}")
                Log.d(TAG, "网页响应长度: ${htmlContent?.length ?: 0}")
                webPageResponse.close()
                
                if (!htmlContent.isNullOrEmpty()) {
                    Log.d(TAG, "开始深度解析HTML内容")
                    
                    // 保存HTML内容到日志，便于分析视频URL
                    Log.d(TAG, "HTML内容前1000字符: ${htmlContent.take(1000)}")
                    Log.d(TAG, "HTML内容后1000字符: ${htmlContent.takeLast(1000)}")
                    
                    // 搜索可能包含视频URL的关键词
                    val videoKeywords = listOf("play_addr", "download_addr", "video_url", "mp4", "aweme", "playUrl", "videoUrl", "url_list", "src")
                    videoKeywords.forEach { keyword ->
                        if (htmlContent.contains(keyword, ignoreCase = true)) {
                            val index = htmlContent.indexOf(keyword, ignoreCase = true)
                            val context = htmlContent.substring(
                                maxOf(0, index - 200),
                                minOf(htmlContent.length, index + 200)
                            )
                            Log.d(TAG, "找到关键词 '$keyword' 上下文: $context")
                        }
                    }
                    
                    // 首先尝试解析页面中的JSON数据
                    val jsonVideoUrl = extractVideoFromPageJson(htmlContent)
                    if (jsonVideoUrl != null) {
                        Log.d(TAG, "从页面JSON数据解析得到视频URL: $jsonVideoUrl")
                        return@withContext jsonVideoUrl
                    }
                    
                    // 尝试WebView JavaScript执行
                    Log.d(TAG, "JSON解析失败，尝试WebView JavaScript执行")
                    val webViewUrl = tryWebViewExtraction(finalUrl)
                    if (webViewUrl != null) {
                        Log.d(TAG, "WebView JavaScript解析得到视频URL: $webViewUrl")
                        return@withContext webViewUrl
                    }
                    
                    // 从HTML中提取视频URL（备用）
                    Log.d(TAG, "WebView失败，尝试HTML正则解析")
                    val videoUrl = extractVideoUrlFromHtml(htmlContent)
                    if (videoUrl != null) {
                        Log.d(TAG, "从HTML中解析得到视频URL: $videoUrl")
                        return@withContext videoUrl
                    }
                }
                
                // 备用方案1：尝试直接构造视频URL
                Log.d(TAG, "HTML解析失败，尝试直接构造视频URL")
                val directVideoUrl = tryDirectVideoUrlConstruction(videoId)
                if (directVideoUrl != null) {
                    Log.d(TAG, "直接构造视频URL成功: $directVideoUrl")
                    return@withContext directVideoUrl
                }
                
                // 备用方案2：尝试API端点（增强参数）
                Log.d(TAG, "直接构造失败，尝试API端点")
                val msToken = generateMsToken()
                val aBogus = generateABogus(finalUrl, USER_AGENTS.random())
                
                val apiEndpoints = listOf(
                    "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$videoId&msToken=$msToken&a_bogus=$aBogus",
                    "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId&msToken=$msToken&a_bogus=$aBogus",
                    "https://m.douyin.com/web/api/v2/aweme/iteminfo/?item_ids=$videoId&msToken=$msToken"
                )
                
                var jsonData: String? = null
                var successfulEndpoint = ""
                
                for (apiUrl in apiEndpoints) {
                    try {
                        Log.d(TAG, "尝试API端点: $apiUrl")
                        val apiRequest = Request.Builder()
                            .url(apiUrl)
                            .headers(generateDynamicHeaders(finalUrl, isApiRequest = true))
                            .build()
                            
                        val apiResponse = client.newCall(apiRequest).execute()
                        Log.d(TAG, "API响应状态 ($apiUrl): ${apiResponse.code}")
                        
                        if (apiResponse.isSuccessful) {
                            val responseData = apiResponse.body?.string()
                            Log.d(TAG, "端点 $apiUrl 响应长度: ${responseData?.length ?: 0}")
                            
                            // 检查响应是否为空或无效
                            if (!responseData.isNullOrEmpty() && responseData.trim().isNotEmpty()) {
                                jsonData = responseData
                                successfulEndpoint = apiUrl
                                apiResponse.close()
                                break
                            } else {
                                Log.d(TAG, "端点 $apiUrl 返回空响应，尝试下一个端点")
                            }
                        } else {
                            Log.d(TAG, "端点 $apiUrl 请求失败，状态码: ${apiResponse.code}")
                        }
                        apiResponse.close()
                    } catch (e: Exception) {
                        Log.d(TAG, "API端点失败 ($apiUrl): ${e.message}")
                    }
                }
                
                if (jsonData != null) {
                    Log.d(TAG, "成功从端点获取数据: $successfulEndpoint")
                    Log.d(TAG, "API响应数据长度: ${jsonData.length}")
                    
                    // 完整打印API响应用于调试
                    Log.d(TAG, "=== 完整API响应开始 ===")
                    // 分块打印以避免日志截断
                    val chunkSize = 1000
                    for (i in jsonData.indices step chunkSize) {
                        val end = minOf(i + chunkSize, jsonData.length)
                        Log.d(TAG, "响应片段 ${i/chunkSize + 1}: ${jsonData.substring(i, end)}")
                    }
                    Log.d(TAG, "=== 完整API响应结束 ===")
                } else {
                    Log.e(TAG, "所有API端点都失败了")
                }
                
                if (jsonData != null) {
                    val videoUrl = parseVideoUrlFromJson(jsonData)
                    Log.d(TAG, "解析得到的视频URL: $videoUrl")
                    return@withContext videoUrl
                }
                
                Log.e(TAG, "API响应数据为空")
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "提取视频URL时发生异常", e)
                e.printStackTrace()
                return@withContext null
            }
      }
      
      // 动态第三方服务管理
      private data class ApiService(
          val name: String,
          val url: String,
          val type: String,
          var isAvailable: Boolean = true,
          var lastChecked: Long = 0,
          var failureCount: Int = 0,
          val maxFailures: Int = 3
      )
      
      private val dynamicApiServices = mutableListOf(
          // 添加新的可用服务
          ApiService("SnapInsta API", "https://snapinsta.app/api/ajaxSearch", "snapinsta"),
          ApiService("SaveFrom API", "https://savefrom.net/api/", "savefrom"),
          ApiService("YT5s API", "https://yt5s.com/api/ajaxSearch", "yt5s"),
          ApiService("Loader API", "https://loader.to/api/", "loader"),
          // 保留原有服务但降低优先级
          ApiService("TikWM API", "https://www.tikwm.com/api/", "tikwm"),
          ApiService("SnapTik API", "https://snaptik.app/abc", "snaptik"),
          ApiService("SaveTT API", "https://savett.cc/api", "savett"),
          ApiService("TikMate API", "https://tikmate.online/api", "tikmate"),
          ApiService("MusicallyDown API", "https://musicaldown.com/api", "musicallydown"),
          ApiService("DownloadGram API", "https://downloadgram.com/", "downloadgram"),
          ApiService("SSSTik API", "https://ssstik.io/abc", "ssstik")
      )
      
      private suspend fun checkServiceAvailability(service: ApiService): Boolean = withContext(Dispatchers.IO) {
          try {
              val currentTime = System.currentTimeMillis()
              // 每10分钟检查一次
              if (currentTime - service.lastChecked < 10 * 60 * 1000 && service.isAvailable) {
                  return@withContext true
              }
              
              val request = Request.Builder()
                  .url(service.url)
                  .head() // 只发送HEAD请求检查可用性
                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                  .build()
                  
              client.newCall(request).execute().use { response ->
                  service.lastChecked = currentTime
                  service.isAvailable = response.isSuccessful || response.code in 400..499 // 400-499也算可用，可能只是参数问题
                  if (service.isAvailable) {
                      service.failureCount = 0
                  }
                  Log.d(TAG, "服务${service.name}可用性检查: ${service.isAvailable}")
                  return@withContext service.isAvailable
              }
          } catch (e: Exception) {
              service.isAvailable = false
              service.failureCount++
              Log.w(TAG, "服务${service.name}检查失败: ${e.message}")
              return@withContext false
          }
      }
      
      private suspend fun tryThirdPartyApis(originalUrl: String, videoId: String): String? = withContext(Dispatchers.IO) {
          try {
              Log.d(TAG, "开始尝试第三方API服务")
              
              // 先检查所有服务的可用性
              for (service in dynamicApiServices) {
                  checkServiceAvailability(service)
              }
              
              // 按可用性和失败次数排序
              val availableServices = dynamicApiServices.filter { it.isAvailable && it.failureCount < it.maxFailures }
                  .sortedBy { it.failureCount }
              
              if (availableServices.isEmpty()) {
                  Log.w(TAG, "没有可用的第三方API服务")
                  return@withContext null
              }
              
              Log.d(TAG, "找到${availableServices.size}个可用服务")
              
              for (service in availableServices) {
                  try {
                      Log.d(TAG, "尝试第三方API: ${service.name}")
                      
                      val request = when (service.type) {
                          "snapinsta" -> {
                              // SnapInsta API - POST请求
                              val formBody = okhttp3.FormBody.Builder()
                                  .add("q", originalUrl)
                                  .add("t", "media")
                                  .add("lang", "en")
                                  .build()
                              Request.Builder()
                                  .url(service.url)
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                                  .addHeader("Origin", "https://snapinsta.app")
                                  .addHeader("Referer", "https://snapinsta.app/")
                                  .post(formBody)
                                  .build()
                          }
                          
                          "savefrom" -> {
                              // SaveFrom API - GET请求
                              Request.Builder()
                                  .url("${service.url}?url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}")
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                                  .get()
                                  .build()
                          }
                          
                          "yt5s" -> {
                              // YT5s API - POST请求
                              val formBody = okhttp3.FormBody.Builder()
                                  .add("query", originalUrl)
                                  .add("vt", "downloader")
                                  .build()
                              Request.Builder()
                                  .url(service.url)
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                                  .addHeader("Origin", "https://yt5s.com")
                                  .addHeader("Referer", "https://yt5s.com/")
                                  .post(formBody)
                                  .build()
                          }
                          
                          "loader" -> {
                              // Loader API - GET请求
                              Request.Builder()
                                  .url("${service.url}?url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}")
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                                  .get()
                                  .build()
                          }
                          
                          "tikwm" -> {
                              // TikWM API - GET请求，支持抖音和TikTok
                              Request.Builder()
                                  .url("${service.url}?url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}&hd=1")
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                                  .get()
                                  .build()
                          }
                          
                          "downloadgram" -> {
                              // DownloadGram - Form POST
                              val formBody = okhttp3.FormBody.Builder()
                                  .add("url", originalUrl)
                                  .add("submit", "")
                                  .build()
                              Request.Builder()
                                  .url(service.url)
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                                  .addHeader("Origin", "https://downloadgram.com")
                                  .addHeader("Referer", "https://downloadgram.com/")
                                  .post(formBody)
                                  .build()
                          }
                          
                          "ssstik" -> {
                              // SSSTik - Form POST
                              val formBody = okhttp3.FormBody.Builder()
                                  .add("id", originalUrl)
                                  .add("locale", "en")
                                  .add("tt", "")
                                  .build()
                              Request.Builder()
                                  .url(service.url)
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                                  .addHeader("Origin", "https://ssstik.io")
                                  .addHeader("Referer", "https://ssstik.io/")
                                  .post(formBody)
                                  .build()
                          }
                          
                          "cobalt" -> {
                              // Cobalt Tools API - JSON POST
                              val requestBody = """{"url":"$originalUrl","isAudioOnly":false,"aFormat":"mp3","filenamePattern":"classic"}"""
                              Request.Builder()
                                  .url(service.url)
                                  .addHeader("Content-Type", "application/json")
                                  .addHeader("Accept", "application/json")
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                                  .post(okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), requestBody))
                                  .build()
                          }
                          
                          "snaptik" -> {
                              // SnapTik API - Form POST
                              val formBody = okhttp3.FormBody.Builder()
                                  .add("url", originalUrl)
                                  .build()
                              Request.Builder()
                                  .url(service.url)
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                                  .addHeader("Origin", "https://snaptik.app")
                                  .addHeader("Referer", "https://snaptik.app/")
                                  .post(formBody)
                                  .build()
                          }
                          
                          "savett", "tikmate", "musicallydown", "ttdownloader" -> {
                              // 通用GET请求
                              Request.Builder()
                                  .url("${service.url}?url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}")
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36")
                                  .get()
                                  .build()
                          }
                          
                          else -> {
                              // 默认GET请求
                              Request.Builder()
                                  .url("${service.url}?url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}")
                                  .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                                  .get()
                                  .build()
                          }
                      }
                      
                      val response = client.newCall(request).execute()
                      Log.d(TAG, "第三方API响应状态 (${service.name}): ${response.code}")
                      
                      if (response.isSuccessful) {
                          val responseData = response.body?.string()
                          Log.d(TAG, "${service.name} 响应长度: ${responseData?.length ?: 0}")
                          
                          if (!responseData.isNullOrEmpty()) {
                              Log.d(TAG, "${service.name} 响应数据: ${responseData.take(500)}...")
                              val videoUrl = parseThirdPartyApiResponse(responseData, service.type)
                              if (videoUrl != null && videoUrl.startsWith("http")) {
                                  Log.d(TAG, "${service.name} 解析成功: $videoUrl")
                                  service.failureCount = 0 // 成功时重置失败计数
                                  response.close()
                                  return@withContext videoUrl
                              }
                          }
                      } else {
                          Log.d(TAG, "${service.name} 请求失败: ${response.code} - ${response.message}")
                          service.failureCount++
                      }
                      response.close()
                      
                  } catch (e: Exception) {
                      Log.d(TAG, "${service.name} 异常: ${e.message}")
                      service.failureCount++
                  }
              }
              
              Log.d(TAG, "所有第三方API都失败了")
              return@withContext null
              
          } catch (e: Exception) {
              Log.e(TAG, "第三方API异常", e)
              return@withContext null
          }
      }
      
      private fun parseThirdPartyApiResponse(responseData: String, type: String): String? {
          try {
              // 响应验证：检查是否为空或HTML
              if (responseData.isBlank()) {
                  Log.w(TAG, "第三方API响应为空，类型: $type")
                  return null
              }
              
              // 检查是否为HTML响应（反爬虫检测）
              if (responseData.trimStart().startsWith("<") || 
                  responseData.contains("<html") || 
                  responseData.contains("<div") ||
                  responseData.contains("<!DOCTYPE") ||
                  responseData.contains("error") && responseData.contains("panel")) {
                  Log.w(TAG, "第三方API返回HTML错误页面，类型: $type，可能被反爬虫拦截")
                  // 尝试提取错误信息
                  if (responseData.contains("error") || responseData.contains("blocked")) {
                      Log.w(TAG, "检测到反爬虫拦截信号")
                  }
                  return null
              }
              
              val jsonObject = JSONObject(responseData)
              Log.d(TAG, "解析第三方API响应，类型: $type")
              
              when (type) {
                  "snapinsta" -> {
                      // SnapInsta API响应格式
                      if (jsonObject.has("status") && jsonObject.getString("status") == "ok") {
                          if (jsonObject.has("data")) {
                              val data = jsonObject.getJSONObject("data")
                              if (data.has("url")) {
                                  return data.getString("url")
                              }
                          }
                      }
                  }
                  
                  "savefrom" -> {
                      // SaveFrom API响应格式
                      if (jsonObject.has("status") && jsonObject.getString("status") == "success") {
                          if (jsonObject.has("url")) {
                              return jsonObject.getString("url")
                          }
                      }
                  }
                  
                  "yt5s" -> {
                      // YT5s API响应格式
                      if (jsonObject.has("status") && jsonObject.getString("status") == "success") {
                          if (jsonObject.has("result")) {
                              val result = jsonObject.getJSONObject("result")
                              if (result.has("video_url")) {
                                  return result.getString("video_url")
                              }
                          }
                      }
                  }
                  
                  "loader" -> {
                      // Loader API响应格式
                      if (jsonObject.has("success") && jsonObject.getBoolean("success")) {
                          if (jsonObject.has("video")) {
                              return jsonObject.getString("video")
                          }
                      }
                  }
                  
                  "tikwm" -> {
                      // TikWM API响应格式
                      if (jsonObject.has("code") && jsonObject.getInt("code") == 0) {
                          if (jsonObject.has("data")) {
                              val data = jsonObject.getJSONObject("data")
                              // 优先使用高清版本
                              if (data.has("hdplay")) {
                                  return data.getString("hdplay")
                              }
                              if (data.has("play")) {
                                  return data.getString("play")
                              }
                          }
                      }
                  }
                  
                  "downloadgram" -> {
                      // DownloadGram通常返回HTML，先跳过
                      return null
                  }
                  
                  "ssstik" -> {
                      // SSSTik通常返回HTML，先跳过
                      return null
                  }
                  
                  "snaptik" -> {
                      // SnapTik API响应格式
                      if (jsonObject.has("success") && jsonObject.getBoolean("success")) {
                          if (jsonObject.has("data") && jsonObject.getJSONObject("data").has("url")) {
                              return jsonObject.getJSONObject("data").getString("url")
                          }
                      }
                  }
                  
                  "savett", "tikmate", "musicallydown", "ttdownloader" -> {
                      // 通用JSON响应格式尝试
                      val possibleKeys = listOf("url", "video_url", "downloadUrl", "download_url", "mp4", "video")
                      for (key in possibleKeys) {
                          if (jsonObject.has(key)) {
                              val value = jsonObject.getString(key)
                              if (value.startsWith("http")) {
                                  return value
                              }
                          }
                      }
                      
                      // 尝试嵌套data对象
                      if (jsonObject.has("data")) {
                          val dataObj = jsonObject.getJSONObject("data")
                          for (key in possibleKeys) {
                              if (dataObj.has(key)) {
                                  val value = dataObj.getString(key)
                                  if (value.startsWith("http")) {
                                      return value
                                  }
                              }
                          }
                      }
                  }
                  
                  "cobalt" -> {
                      // Cobalt Tools API响应
                      if (jsonObject.has("status") && jsonObject.getString("status") == "redirect") {
                          if (jsonObject.has("url")) {
                              return jsonObject.getString("url")
                          }
                      }
                      
                      if (jsonObject.has("status") && jsonObject.getString("status") == "stream") {
                          if (jsonObject.has("url")) {
                              return jsonObject.getString("url")
                          }
                      }
                  }
              }
              
              // 通用解析：查找任何包含video或mp4的字段
              val keys = jsonObject.keys()
              while (keys.hasNext()) {
                  val key = keys.next()
                  try {
                      val value = jsonObject.getString(key)
                      if (value.contains(".mp4") && value.startsWith("http")) {
                          Log.d(TAG, "通用解析找到视频URL: $value")
                          return value
                      }
                  } catch (e: Exception) {
                      // 忽略非字符串字段
                  }
              }
              
              return null
          } catch (e: JSONException) {
              Log.e(TAG, "JSON解析异常，类型: $type，响应前2KB: ${responseData.take(2048)}", e)
              return null
          } catch (e: Exception) {
              Log.e(TAG, "第三方API响应解析失败，类型: $type，响应前1KB: ${responseData.take(1024)}", e)
              return null
          }
      }
      
      private suspend fun tryMobileApi(videoId: String, refererUrl: String): String? = withContext(Dispatchers.IO) {
          try {
              // 尝试移动端API接口
              val mobileApiEndpoints = listOf(
                  "https://aweme.snssdk.com/aweme/v1/aweme/detail/?aweme_id=$videoId&aid=1128&version_name=23.5.0&device_platform=android",
                  "https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId&aid=1128&version_name=23.5.0&device_platform=android", 
                  "https://aweme-hl.snssdk.com/aweme/v1/play/?video_id=$videoId&line=0&ratio=540p&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH",
                  "https://api-va.tiktokv.com/aweme/v1/aweme/detail/?aweme_id=$videoId"
              )
              
              for (apiUrl in mobileApiEndpoints) {
                  try {
                      Log.d(TAG, "尝试移动端API: $apiUrl")
                      val apiRequest = Request.Builder()
                          .url(apiUrl)
                          .headers(generateDynamicHeaders(refererUrl, isApiRequest = true))
                          .build()
                          
                      val response = client.newCall(apiRequest).execute()
                      Log.d(TAG, "移动端API响应状态 ($apiUrl): ${response.code}")
                      
                      if (response.isSuccessful) {
                          val responseData = response.body?.string()
                          Log.d(TAG, "移动端API响应长度: ${responseData?.length ?: 0}")
                          
                          if (!responseData.isNullOrEmpty() && responseData.trim().isNotEmpty()) {
                              Log.d(TAG, "移动端API响应数据: ${responseData.take(500)}...")
                              val videoUrl = parseMobileApiResponse(responseData)
                              if (videoUrl != null) {
                                  response.close()
                                  return@withContext videoUrl
                              }
                          }
                      }
                      response.close()
                  } catch (e: Exception) {
                      Log.d(TAG, "移动端API失败 ($apiUrl): ${e.message}")
                  }
              }
              
              Log.d(TAG, "所有移动端API都失败了")
              return@withContext null
          } catch (e: Exception) {
              Log.e(TAG, "移动端API异常", e)
              return@withContext null
          }
      }
      
      private fun generateXArgus(): String {
          // 简单的X-Argus生成（实际应用中需要更复杂的算法）
          return "X-Argus-" + System.currentTimeMillis().toString(36)
      }
      
      private fun generateXLadon(): String {
          // 简单的X-Ladon生成
          return "Ladon-" + (System.currentTimeMillis() / 1000).toString(16)
      }
      
      private fun parseMobileApiResponse(responseData: String): String? {
          try {
              val jsonObject = JSONObject(responseData)
              
              // 移动端API的常见响应结构
              val possiblePaths = listOf(
                  { obj: JSONObject ->
                      val awemeDetail = obj.getJSONObject("aweme_detail")
                      val video = awemeDetail.getJSONObject("video")
                      val playAddr = video.getJSONObject("play_addr")
                      val urlList = playAddr.getJSONArray("url_list")
                      if (urlList.length() > 0) urlList.getString(0) else null
                  },
                  { obj: JSONObject ->
                      val data = obj.getJSONObject("data")
                      val video = data.getJSONObject("video")
                      val downloadAddr = video.getJSONObject("download_addr")
                      val urlList = downloadAddr.getJSONArray("url_list")
                      if (urlList.length() > 0) urlList.getString(0) else null
                  },
                  { obj: JSONObject ->
                      // 直接从根对象获取视频URL
                      if (obj.has("video_url")) obj.getString("video_url") else null
                  }
              )
              
              for (pathFunction in possiblePaths) {
                  try {
                      val result = pathFunction(jsonObject)
                      if (result != null && result.isNotEmpty()) {
                          Log.d(TAG, "移动端API解析成功: $result")
                          return result
                      }
                  } catch (e: Exception) {
                      // 继续尝试下一个路径
                  }
              }
              
              return null
          } catch (e: Exception) {
              Log.e(TAG, "移动端API响应解析失败", e)
              return null
          }
      }
      
      private suspend fun tryWebViewExtraction(url: String): String? = withContext(Dispatchers.Main) {
          try {
              Log.d(TAG, "开始WebView JavaScript执行: $url")
              
              // 创建WebView实例
              val webView = WebView(this@MainActivity)
              webView.settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  loadWithOverviewMode = true
                  useWideViewPort = true
                  userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
              }
              
              var extractedUrl: String? = null
              var isFinished = false
              
              webView.webViewClient = object : WebViewClient() {
                  override fun onPageFinished(view: WebView?, url: String?) {
                      Log.d(TAG, "WebView页面加载完成")
                      
                      // 等待页面完全加载后再执行JavaScript
                      view?.postDelayed({
                          // JavaScript代码来提取视频URL
                          val jsCode = """
                              (function() {
                                  try {
                                      // 方法1: 查找video标签
                                      var videos = document.querySelectorAll('video');
                                      if (videos.length > 0) {
                                          for (var i = 0; i < videos.length; i++) {
                                              if (videos[i].src && videos[i].src.includes('.mp4')) {
                                                  return videos[i].src;
                                              }
                                          }
                                      }
                                      
                                      // 方法2: 查找全局变量
                                      if (window.__INITIAL_STATE__) {
                                          var state = JSON.stringify(window.__INITIAL_STATE__);
                                          var mp4Match = state.match(/(https:\/\/[^"]+\.mp4[^"]*)/);
                                          if (mp4Match) return mp4Match[1];
                                      }
                                      
                                      // 方法3: 查找页面中所有.mp4链接
                                      var pageSource = document.documentElement.outerHTML;
                                      var mp4Matches = pageSource.match(/(https:\/\/[^"'\s]+\.mp4[^"'\s]*)/g);
                                      if (mp4Matches && mp4Matches.length > 0) {
                                          return mp4Matches[0];
                                      }
                                      
                                      // 方法4: 查找aweme相关链接
                                      var awemeMatches = pageSource.match(/(https:\/\/[^"'\s]*aweme[^"'\s]*\.mp4[^"'\s]*)/g);
                                      if (awemeMatches && awemeMatches.length > 0) {
                                          return awemeMatches[0];
                                      }
                                      
                                      return 'NO_VIDEO_FOUND';
                                  } catch (e) {
                                      return 'JS_ERROR: ' + e.message;
                                  }
                              })()
                          """
                          
                          view.evaluateJavascript(jsCode) { result ->
                              Log.d(TAG, "JavaScript执行结果: $result")
                              
                              val cleanResult = result?.replace("\"", "")?.trim()
                              if (!cleanResult.isNullOrEmpty() && 
                                  !cleanResult.equals("null", true) && 
                                  !cleanResult.equals("NO_VIDEO_FOUND", true) &&
                                  !cleanResult.startsWith("JS_ERROR")) {
                                  extractedUrl = cleanResult
                              }
                              isFinished = true
                          }
                      }, 3000) // 等待3秒确保JavaScript完全执行
                  }
                  
                  override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                      Log.e(TAG, "WebView加载错误: $description")
                      isFinished = true
                  }
              }
              
              // 加载页面
              webView.loadUrl(url)
              
              // 等待结果或超时
              var waitTime = 0
              while (!isFinished && waitTime < 15000) { // 15秒超时
                  kotlinx.coroutines.delay(500)
                  waitTime += 500
              }
              
              Log.d(TAG, "WebView执行完成，提取到URL: $extractedUrl")
              return@withContext extractedUrl
              
          } catch (e: Exception) {
              Log.e(TAG, "WebView执行异常", e)
              return@withContext null
          }
      }
      
      private fun extractVideoId(url: String): String? {
            Log.d(TAG, "尝试从URL中提取视频ID: $url")
            // 从不同格式的URL中提取视频ID
            val patterns = listOf(
                ".*douyin\\.com/video/([0-9]+)",                    // 普通格式
                ".*douyin\\.com.*modal_id=([0-9]+)",                // modal参数格式  
                ".*v\\.douyin\\.com/([a-zA-Z0-9]+)",                // 短链接格式
                ".*iesdouyin\\.com/share/video/([0-9]+)",           // 新格式：分享页面
                ".*douyin\\.com/share/video/([0-9]+)",              // 分享页面变体
                ".*aweme.*video.*id[=/]([0-9]+)",                   // aweme格式
                ".*video[/_]([0-9]{15,})"                           // 通用视频ID格式（15位以上数字）
            )
            
            for (pattern in patterns) {
                Log.d(TAG, "尝试正则表达式: $pattern")
                val regex = Pattern.compile(pattern)
                val matcher = regex.matcher(url)
                if (matcher.find()) {
                    val videoId = matcher.group(1)
                    Log.d(TAG, "匹配成功！视频ID: $videoId")
                    return videoId
                }
            }
            Log.e(TAG, "所有正则表达式都无法匹配URL")
            return null
      }
      
      /**
       * 从页面JSON数据中提取视频URL（新方法）
       */
      private fun extractVideoFromPageJson(htmlContent: String): String? {
          try {
              Log.d(TAG, "开始从页面JSON数据中提取视频URL")
              
              // 查找页面中的JSON数据块
              val jsonPatterns = listOf(
                  // window.__INITIAL_STATE__ 数据
                  "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});",
                  // RENDER_DATA 数据
                  "window\\._ROUTER_DATA\\s*=\\s*(\\{.*?\\});",
                  "window\\.__data\\s*=\\s*(\\{.*?\\});",
                  // 内嵌script标签中的JSON
                  "<script[^>]*>\\s*var\\s+(?:data|videoData|itemData)\\s*=\\s*(\\{.*?\\});</script>",
                  // React应用数据
                  "self\\.__pace_data__\\s*=\\s*(\\{.*?\\});",
                  // 抖音特有的数据结构
                  "\\\\${'$'}render_data\\s*=\\s*(\\{.*?\\})",
                  "window\\.SIGI_STATE\\s*=\\s*(\\{.*?\\});"
              )
              
              for (pattern in jsonPatterns) {
                  try {
                      val regex = Pattern.compile(pattern, Pattern.DOTALL)
                      val matcher = regex.matcher(htmlContent)
                      if (matcher.find()) {
                          val jsonStr = matcher.group(1)
                          Log.d(TAG, "找到JSON数据块，长度: ${jsonStr?.length}")
                          if (jsonStr != null) {
                              val videoUrl = parseJsonForVideoUrl(jsonStr)
                              if (videoUrl != null) {
                                  return videoUrl
                              }
                          }
                      }
                  } catch (e: Exception) {
                      Log.d(TAG, "JSON模式匹配失败: $pattern, 错误: ${e.message}")
                  }
              }
              
              // 尝试直接查找JSON对象中的视频链接
              Log.d(TAG, "尝试直接从HTML中查找JSON对象")
              val directJsonPattern = "\\{[^{}]*(?:video|play|download)[^{}]*mp4[^{}]*\\}"
              val regex = Pattern.compile(directJsonPattern)
              val matcher = regex.matcher(htmlContent)
              while (matcher.find()) {
                  val jsonStr = matcher.group()
                  try {
                      val videoUrl = parseJsonForVideoUrl(jsonStr)
                      if (videoUrl != null) {
                          return videoUrl
                      }
                  } catch (e: Exception) {
                      Log.d(TAG, "直接JSON解析失败: ${e.message}")
                  }
              }
              
              Log.d(TAG, "未找到有效的JSON视频数据")
              return null
              
          } catch (e: Exception) {
              Log.e(TAG, "页面JSON解析异常", e)
              return null
          }
      }
      
      /**
       * 解析JSON字符串中的视频URL
       */
      private fun parseJsonForVideoUrl(jsonStr: String): String? {
          try {
              // 清理JSON字符串
              val cleanJson = jsonStr
                  .replace("\\u002F", "/")
                  .replace("\\/", "/")
                  .trim()
              
              val jsonObject = JSONObject(cleanJson)
              Log.d(TAG, "成功解析JSON对象")
              
              // 尝试多种可能的视频URL路径
              val videoPaths = listOf(
                  // 标准路径
                  listOf("video", "play_addr", "url_list", "0"),
                  listOf("video", "download_addr", "url_list", "0"),
                  listOf("aweme_detail", "video", "play_addr", "url_list", "0"),
                  
                  // 直接字段
                  listOf("video_url"),
                  listOf("play_url"),
                  listOf("download_url"),
                  listOf("src"),
                  
                  // 嵌套路径
                  listOf("data", "video", "play_addr", "url_list", "0"),
                  listOf("item_list", "0", "video", "play_addr", "url_list", "0"),
                  listOf("aweme_list", "0", "video", "play_addr", "url_list", "0")
              )
              
              for (path in videoPaths) {
                  try {
                      var current: Any? = jsonObject
                      for (key in path) {
                          current = when (current) {
                              is JSONObject -> {
                                  if (current.has(key)) {
                                      current.get(key)
                                  } else null
                              }
                              is JSONArray -> {
                                  val index = key.toIntOrNull()
                                  if (index != null && index < current.length()) {
                                      current.getJSONObject(index)
                                  } else null
                              }
                              else -> null
                          }
                          if (current == null) break
                      }
                      
                      if (current is String && current.startsWith("http") && current.contains(".mp4")) {
                          Log.d(TAG, "通过路径 ${path.joinToString(".")} 找到视频URL: $current")
                          return current
                      }
                  } catch (e: Exception) {
                      Log.d(TAG, "路径 ${path.joinToString(".")} 解析失败: ${e.message}")
                  }
              }
              
              // 遍历所有字段查找视频URL
              val keys = jsonObject.keys()
              while (keys.hasNext()) {
                  val key = keys.next()
                  try {
                      val value = jsonObject.get(key).toString()
                      if (value.contains(".mp4") && value.startsWith("http")) {
                          Log.d(TAG, "通过遍历找到视频URL: $value")
                          return value
                      }
                  } catch (e: Exception) {
                      Log.d(TAG, "遍历字段 $key 失败: ${e.message}")
                  }
              }
              
              return null
              
          } catch (e: Exception) {
              Log.d(TAG, "JSON解析失败: ${e.message}")
              return null
          }
      }

      private fun extractVideoUrlFromHtml(htmlContent: String): String? {
            try {
                Log.d(TAG, "开始从HTML中解析视频URL")
                
                // 更全面的视频URL模式，包括JavaScript和动态内容
                val videoUrlPatterns = listOf(
                    // JavaScript中的视频URL
                    "(?s)<script[^>]*>.*?videoUrl[\"']?:\\s*[\"']([^\"']+)[\"'].*?</script>",
                    "(?s)<script[^>]*>.*?playUrl[\"']?:\\s*[\"']([^\"']+)[\"'].*?</script>",
                    "(?s)window\\.__INITIAL_STATE__.*?videoUrl[\"']?:\\s*[\"']([^\"']+)[\"']",
                    
                    // JSON数据中的视频URL
                    "\"playApi\":\\s*\"([^\"]+)\"",
                    "\"play_url\":\\s*\"([^\"]+)\"",  
                    "\"downloadAddr\":\\s*\"([^\"]+)\"",
                    "\"download_addr\".*?\"url_list\":\\s*\\[\\s*\"([^\"]+)\"",
                    "\"play_addr\".*?\"url_list\":\\s*\\[\\s*\"([^\"]+)\"",
                    
                    // Base64编码的视频URL
                    "data:video/mp4;base64,([A-Za-z0-9+/=]+)",
                    
                    // 直接的MP4链接
                    "src\\s*=\\s*[\"']([^\"']*\\.mp4[^\"']*)[\"']",
                    "\"src\":\\s*\"([^\"]*\\.mp4[^\"]*?)\"",
                    "(https://[^\"\\s<>]*aweme[^\"\\s<>]*\\.mp4[^\"\\s<>]*)",
                    "(https://[^\"\\s<>]*douyin[^\"\\s<>]*\\.mp4[^\"\\s<>]*)",
                    "(https://[^\"\\s<>]*tiktok[^\"\\s<>]*\\.mp4[^\"\\s<>]*)",
                    
                    // 其他可能的视频格式
                    "(https://[^\"\\s<>]*\\.(m3u8|flv|avi|mov)[^\"\\s<>]*)",
                    
                    // URL编码的视频链接
                    "https%3A%2F%2F[^\"\\s<>]*%2E(mp4|m3u8)[^\"\\s<>]*",
                    
                    // 抖音特有的视频服务器模式
                    "(https://[^\"\\s<>]*v\\d+[^\"\\s<>]*douyin[^\"\\s<>]*)",
                    "(https://[^\"\\s<>]*aweme[^\"\\s<>]*v\\d+[^\"\\s<>]*)"
                )
                
                for ((index, pattern) in videoUrlPatterns.withIndex()) {
                    try {
                        Log.d(TAG, "尝试HTML视频正则 ${index + 1}: $pattern")
                        val regex = Pattern.compile(pattern)
                        val matcher = regex.matcher(htmlContent)
                        if (matcher.find()) {
                            val videoUrl = matcher.group(1) ?: matcher.group(0)
                            Log.d(TAG, "HTML正则 ${index + 1} 匹配成功: $videoUrl")
                            // 清理和验证URL
                            val cleanUrl = cleanVideoUrl(videoUrl)
                            if (cleanUrl != null && isValidVideoUrl(cleanUrl)) {
                                Log.d(TAG, "验证成功的视频URL: $cleanUrl")
                                return cleanUrl
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "HTML正则 ${index + 1} 失败: ${e.message}")
                    }
                }
                
                Log.d(TAG, "HTML中未找到有效的视频URL")
                return null
                
            } catch (e: Exception) {
                Log.e(TAG, "HTML解析异常", e)
                return null
            }
      }
      
      private fun cleanVideoUrl(url: String): String? {
            try {
                // 移除转义字符和多余的参数
                val cleanedUrl = url
                    .replace("\\u002F", "/")
                    .replace("\\/", "/")  
                    .replace("\\", "")
                    .trim()
                
                // 确保URL格式正确
                return if (cleanedUrl.startsWith("http")) cleanedUrl else null
            } catch (e: Exception) {
                return null
            }
      }
      
      private fun isValidVideoUrl(url: String): Boolean {
            return url.startsWith("http") && 
                   (url.contains(".mp4") || url.contains("video") || url.contains("aweme"))
      }
      
      private fun parseVideoUrlFromJson(jsonData: String): String? {
            try {
                Log.d(TAG, "开始解析JSON数据")
                val jsonObject = JSONObject(jsonData)
                Log.d(TAG, "JSON根对象解析成功")
                
                // 打印JSON的主要键和结构
                val keys = jsonObject.keys()
                val keysList = mutableListOf<String>()
                while (keys.hasNext()) {
                    keysList.add(keys.next())
                }
                Log.d(TAG, "JSON主要键: ${keysList.joinToString(", ")}")
                
                // 打印每个主要键的类型和结构
                for (key in keysList) {
                    try {
                        val value = jsonObject.get(key)
                        Log.d(TAG, "键 '$key' 的类型: ${value?.javaClass?.simpleName}, 值预览: ${value.toString().take(100)}")
                    } catch (e: Exception) {
                        Log.d(TAG, "无法解析键 '$key': ${e.message}")
                    }
                }
                
                // 尝试多种解析路径
                val possiblePaths = listOf(
                    // 路径1: item_list -> video -> play_addr -> url_list
                    { obj: JSONObject ->
                        Log.d(TAG, "尝试路径1: item_list -> video -> play_addr -> url_list")
                        val itemList = obj.getJSONArray("item_list")
                        if (itemList.length() > 0) {
                            val item = itemList.getJSONObject(0)
                            Log.d(TAG, "item对象键: ${item.keys().asSequence().toList()}")
                            val video = item.getJSONObject("video")
                            Log.d(TAG, "video对象键: ${video.keys().asSequence().toList()}")
                            val playAddr = video.getJSONObject("play_addr")
                            val urlList = playAddr.getJSONArray("url_list")
                            if (urlList.length() > 0) urlList.getString(0) else null
                        } else null
                    },
                    // 路径2: item_list -> video -> download_addr -> url_list
                    { obj: JSONObject ->
                        Log.d(TAG, "尝试路径2: item_list -> video -> download_addr -> url_list")
                        val itemList = obj.getJSONArray("item_list")
                        if (itemList.length() > 0) {
                            val item = itemList.getJSONObject(0)
                            val video = item.getJSONObject("video")
                            if (video.has("download_addr")) {
                                val downloadAddr = video.getJSONObject("download_addr")
                                val urlList = downloadAddr.getJSONArray("url_list")
                                if (urlList.length() > 0) urlList.getString(0) else null
                            } else null
                        } else null
                    },
                    // 路径3: aweme_list -> video -> play_addr -> url_list
                    { obj: JSONObject ->
                        Log.d(TAG, "尝试路径3: aweme_list -> video -> play_addr -> url_list")
                        if (obj.has("aweme_list")) {
                            val awemeList = obj.getJSONArray("aweme_list")
                            if (awemeList.length() > 0) {
                                val aweme = awemeList.getJSONObject(0)
                                val video = aweme.getJSONObject("video")
                                val playAddr = video.getJSONObject("play_addr")
                                val urlList = playAddr.getJSONArray("url_list")
                                if (urlList.length() > 0) urlList.getString(0) else null
                            } else null
                        } else null
                    },
                    // 路径4: data -> item_list -> video -> play_addr
                    { obj: JSONObject ->
                        Log.d(TAG, "尝试路径4: data -> item_list -> video -> play_addr")
                        if (obj.has("data")) {
                            val data = obj.getJSONObject("data")
                            if (data.has("item_list")) {
                                val itemList = data.getJSONArray("item_list")
                                if (itemList.length() > 0) {
                                    val item = itemList.getJSONObject(0)
                                    val video = item.getJSONObject("video")
                                    val playAddr = video.getJSONObject("play_addr")
                                    val urlList = playAddr.getJSONArray("url_list")
                                    if (urlList.length() > 0) urlList.getString(0) else null
                                } else null
                            } else null
                        } else null
                    },
                    // 路径5: 直接从video获取 (如果是单个视频对象)
                    { obj: JSONObject ->
                        Log.d(TAG, "尝试路径5: 直接从video获取")
                        if (obj.has("video")) {
                            val video = obj.getJSONObject("video")
                            if (video.has("play_addr")) {
                                val playAddr = video.getJSONObject("play_addr")
                                val urlList = playAddr.getJSONArray("url_list")
                                if (urlList.length() > 0) urlList.getString(0) else null
                            } else if (video.has("download_addr")) {
                                val downloadAddr = video.getJSONObject("download_addr")
                                val urlList = downloadAddr.getJSONArray("url_list")
                                if (urlList.length() > 0) urlList.getString(0) else null
                            } else null
                        } else null
                    }
                )
                
                for ((index, pathFunction) in possiblePaths.withIndex()) {
                    try {
                        Log.d(TAG, "尝试解析路径 ${index + 1}")
                        val result = pathFunction(jsonObject)
                        if (result != null && result.isNotEmpty()) {
                            Log.d(TAG, "路径 ${index + 1} 解析成功: $result")
                            return result
                        } else {
                            Log.d(TAG, "路径 ${index + 1} 返回空值")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "路径 ${index + 1} 解析失败: ${e.message}")
                    }
                }
                
                Log.e(TAG, "所有解析路径都失败了")
                return null
                
            } catch (e: Exception) {
                Log.e(TAG, "JSON解析异常", e)
                e.printStackTrace()
            }
            return null
      }

                                                                                                            private suspend fun downloadAndSaveVideo(videoUrl: String): Boolean = withContext(Dispatchers.IO) {
            try {
                fun buildReq(u: String, extra: Map<String, String> = emptyMap()): Request {
                    val b = Request.Builder()
                        .url(u)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                        .addHeader("Referer", "https://www.douyin.com/")
                        .addHeader("Accept", "video/mp4,video/*,*/*")
                        .addHeader("Accept-Encoding", "identity")
                        .addHeader("Connection", "keep-alive")
                    latestCookie?.let { b.addHeader("Cookie", it) }
                    extra.forEach { (k, v) -> b.addHeader(k, v) }
                    return b.build()
                }

                fun follow(u: String, max: Int = 5): okhttp3.Response {
                    var url = u
                    var times = 0
                    while (times < max) {
                        val r = client.newCall(buildReq(url)).execute()
                        val cl = r.header("Content-Length")
                        Log.d(TAG, "下载阶段响应: code=${r.code}, content-length=${cl ?: "<none>"}, url=$url")
                        if (r.isRedirect) {
                            val loc = r.header("Location")
                            Log.d(TAG, "发现重定向 -> $loc")
                            r.close()
                            if (loc.isNullOrBlank()) break
                            url = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
                            times++
                            continue
                        }
                        return r
                    }
                    return client.newCall(buildReq(url)).execute()
                }

                var response = follow(videoUrl)
                if (!response.isSuccessful || response.body == null) {
                    response.close()
                    Log.w(TAG, "直下失败，尝试 Range 方式")
                    response = client.newCall(buildReq(videoUrl, mapOf("Range" to "bytes=0-"))).execute()
                    val cl2 = response.header("Content-Length")
                    Log.d(TAG, "Range 响应: code=${response.code}, content-length=${cl2 ?: "<none>"}")
                    if (!response.isSuccessful || response.body == null) {
                        response.close()
                        return@withContext false
                    }
                }

                val fileName = "douyin_video_${System.currentTimeMillis()}.mp4"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DouYin")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: run {
                    response.close(); return@withContext false
                }

                var bytes = 0L
                contentResolver.openOutputStream(uri)?.use { os ->
                    java.io.BufferedInputStream(response.body!!.byteStream()).use { ins ->
                        java.io.BufferedOutputStream(os).use { bos ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                bos.write(buf, 0, n)
                                bytes += n
                                if (bytes % (1024 * 512) == 0L) {
                                    Log.d(TAG, "已写入: ${bytes} bytes")
                                }
                            }
                            bos.flush()
                        }
                    }
                }
                response.close()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    contentResolver.update(uri, done, null, null)
                }

                if (bytes <= 0) {
                    contentResolver.delete(uri, null, null)
                    Log.e(TAG, "下载写入字节为0，已删除无效文件")
                    return@withContext false
                }

                Log.d(TAG, "视频保存成功，大小: $bytes bytes")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "下载保存异常", e)
                return@withContext false
            }
      }
                                                                                                          
                                                                                                              private fun showLoading(show: Boolean) {
                                                                                                                        findViewById<android.widget.ProgressBar>(R.id.progressBar).visibility = if (show) View.VISIBLE else View.GONE
                                                                                                                        findViewById<android.widget.Button>(R.id.btnDownload).isEnabled = !show
                                                                                                              }
                                                                                                                  
    private fun showMessage(message: String) {
        findViewById<android.widget.TextView>(R.id.tvStatus).text = message
        findViewById<android.widget.TextView>(R.id.tvStatus).visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 生成动态请求头（增强反爬虫策略）
     */
    private fun generateDynamicHeaders(refererUrl: String? = null, isApiRequest: Boolean = false): Headers {
        val userAgent = USER_AGENTS.random()
        val builder = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .add("Accept-Encoding", "gzip, deflate, br")
            .add("Connection", "keep-alive")
            .add("Cache-Control", "no-cache")
            .add("Pragma", "no-cache")
        
        // 根据请求类型设置不同的Accept头
        if (isApiRequest) {
            builder.add("Accept", "application/json, text/javascript, */*; q=0.01")
            builder.add("Content-Type", "application/json")
            builder.add("X-Requested-With", "XMLHttpRequest")
        } else {
            builder.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
            builder.add("Upgrade-Insecure-Requests", "1")
        }
        
        // 添加关键的抖音反爬虫参数
        builder.add("X-SS-REQ-TICKET", System.currentTimeMillis().toString())
        builder.add("X-tt-trace-id", generateTraceId())
        builder.add("X-Argus", generateXArgus())
        builder.add("X-Ladon", generateXLadon())
        
        // 添加设备相关Headers
        val deviceId = DEVICE_IDS.random()
        builder.add("X-SS-DID", deviceId)
        builder.add("X-tt-store-region", "cn")
        builder.add("X-tt-store-region-src", "uid")
        
        // 添加Cookie
        val cookie = COOKIE_TEMPLATES.random() + generateDynamicCookieParams()
        builder.add("Cookie", cookie)
        
        // 添加Referer（反爬虫检测）
        refererUrl?.let {
            builder.add("Referer", it)
        } ?: run {
            builder.add("Referer", "https://www.douyin.com/")
        }
        
        // 添加Sec-Fetch系列headers（模拟浏览器）
        builder.add("Sec-Fetch-Dest", if (isApiRequest) "empty" else "document")
        builder.add("Sec-Fetch-Mode", if (isApiRequest) "cors" else "navigate")
        builder.add("Sec-Fetch-Site", "same-origin")
        if (!isApiRequest) {
            builder.add("Sec-Fetch-User", "?1")
        }
        
        return builder.build()
    }
    
    /**
     * 生成追踪ID
     */
    private fun generateTraceId(): String {
        return buildString {
            repeat(8) { append((0..9).random()) }
            append("-")
            repeat(4) { append((0..9).random()) }
            append("-")
            repeat(4) { append((0..9).random()) }
            append("-")
            repeat(4) { append((0..9).random()) }
            append("-")
            repeat(12) { append((0..9).random()) }
        }
    }
    
    /**
     * 生成msToken（消息令牌）
     */
    private fun generateMsToken(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString {
            repeat(107) { append(chars.random()) }
        }
    }
    
    /**
     * 生成ttwid（TikTok Web ID）
     */
    private fun generateTtwid(): String {
        val timestamp = System.currentTimeMillis()
        val random = (100000..999999).random()
        return "1%7C${timestamp}%7C${random}"
    }
    
    /**
     * 生成动态Cookie参数
     */
    private fun generateDynamicCookieParams(): String {
        val msToken = generateMsToken()
        val ttwid = generateTtwid()
        val timestamp = System.currentTimeMillis()
        
        return "; msToken=${msToken}; ttwid=${ttwid}; __ac_nonce=0${timestamp}001; __ac_signature=_02B4Z6wo00f01"
    }
    
    /**
     * 生成a_bogus参数（增强版本）
     */
    private fun generateABogus(url: String, userAgent: String): String {
        val timestamp = System.currentTimeMillis()
        
        // 基于URL和时间戳的简单哈希
        val urlHash = url.hashCode().toString().takeLast(8)
        val uaHash = userAgent.hashCode().toString().takeLast(6)
        
        // 生成更复杂的参数
        val part1 = (timestamp % 1000000).toString().padStart(6, '0')
        val part2 = urlHash.padStart(8, '0')
        val part3 = uaHash.padStart(6, '0')
        val part4 = (1000..9999).random().toString()
        
        return "DFSzswVhMwTBDFP7${part1}${part2}${part3}${part4}=="
    }
    
    /**
     * 生成x_bogus参数（简化版本）
     */
    private fun generateXBogus(): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(32) { append(chars.random()) }
        }
    }
    
    /**
     * 请求频率控制（反频率限制）
     */
    private suspend fun enforceRequestRateLimit() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
            val delayTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest
            Log.d(TAG, "请求频率控制：延迟 ${delayTime}ms")
            delay(delayTime)
        }
        
        lastRequestTime = System.currentTimeMillis()
    }
    
    /**
     * URL预处理和长度检查（避免414错误）
     */
    private fun preprocessUrl(url: String): String {
        var processedUrl = url.trim()
        
        // 检查URL长度（避免414 Request-URI Too Large）
        if (processedUrl.length > 2000) {
            Log.w(TAG, "URL过长 (${processedUrl.length}字符)，进行截断处理")
            // 保留核心部分，截断参数
            val urlParts = processedUrl.split("?")
            if (urlParts.size > 1) {
                processedUrl = urlParts[0] + "?" + urlParts[1].take(1000)
            }
        }
        
        // URL编码检查
        try {
            java.net.URL(processedUrl)
        } catch (e: Exception) {
            Log.w(TAG, "URL格式异常，尝试编码: $processedUrl")
            processedUrl = java.net.URLEncoder.encode(processedUrl, "UTF-8")
        }
        
        return processedUrl
    }
    
    /**
     * 尝试直接构造视频URL（基于视频ID）
     */
    private suspend fun tryDirectVideoUrlConstruction(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "尝试直接构造视频URL，视频ID: $videoId")
            
            // 常见的抖音视频URL模式
            val possibleUrls = listOf(
                "https://aweme-hl.snssdk.com/aweme/v1/play/?video_id=$videoId&line=0&ratio=720p&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH",
                "https://aweme-hl.snssdk.com/aweme/v1/play/?video_id=$videoId&line=0&ratio=540p&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH",
                "https://aweme-hl.snssdk.com/aweme/v1/play/?video_id=$videoId&line=0&ratio=480p&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH",
                "https://aweme-hl.snssdk.com/aweme/v1/play/?video_id=$videoId&line=0&ratio=360p&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH",
                "https://aweme-hl.snssdk.com/aweme/v1/play/?video_id=$videoId&line=0&ratio=1080p&media=mp4&vr_type=0&improve_bitrate=0&is_play_url=1&source=PackSourceEnum_PUBLISH"
            )
            
            for (url in possibleUrls) {
                try {
                    Log.d(TAG, "测试直接构造的URL: $url")
                    val request = Request.Builder()
                        .url(url)
                        .headers(generateDynamicHeaders())
                        .head() // 只检查HEAD请求
                        .build()
                    
                    val response = client.newCall(request).execute()
                    Log.d(TAG, "直接构造URL响应状态: ${response.code}")
                    
                    if (response.code == 200) {
                        Log.d(TAG, "找到可用的直接视频URL: $url")
                        response.close()
                        return@withContext url
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.d(TAG, "直接构造URL测试失败: ${e.message}")
                }
            }
            
            Log.d(TAG, "所有直接构造的URL都不可用")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "直接构造视频URL异常", e)
            return@withContext null
        }
    }

    private var latestCookie: String? = null

    private suspend fun sniffVideoUrlWithWebView(pageUrl: String): String? = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "开始 WebView 嗅探: $pageUrl")
            val webView = WebView(this@MainActivity)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                @Suppress("DEPRECATION")
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            try { CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true) } catch (_: Exception) {}
            try {
                val swc = android.webkit.ServiceWorkerController.getInstance()
                swc.setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        val u = request.url.toString()
                        if (u.contains("/aweme/v1/play/") || u.endsWith(".mp4") || u.contains("video_id=")) {
                            Log.d(TAG, "[SW] 拦截到直链候选: ${'$'}u")
                        }
                        return null
                    }
                })
            } catch (_: Throwable) {}
            var captured: String? = null
            var finished = false
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    try {
                        val u = request?.url?.toString().orEmpty()
                        if (u.contains("/aweme/v1/play/") || u.endsWith(".mp4") || u.contains("video_id=")) {
                            if (captured.isNullOrEmpty()) {
                                captured = u
                                Log.d(TAG, "拦截到直链候选: $u")
                            }
                        }
                    } catch (_: Exception) {}
                    return super.shouldInterceptRequest(view, request)
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "WebView 页面完成: $url")
                    try {
                        val host = try { Uri.parse(url ?: pageUrl).host ?: "www.douyin.com" } catch (_: Exception) { "www.douyin.com" }
                        val cm = CookieManager.getInstance()
                        val c = cm.getCookie(host)
                        latestCookie = c
                        Log.d(TAG, "同步到 Cookie(${host}): ${if (c.isNullOrBlank()) "<empty>" else c.take(120)}")
                    } catch (e: Exception) {
                        Log.w(TAG, "读取 Cookie 失败: ${e.message}")
                    }
                    view?.postDelayed({ finished = true }, 6000)
                }
            }
            webView.loadUrl(pageUrl)
            var waited = 0
            while (!finished && captured.isNullOrEmpty() && waited < 15000) {
                kotlinx.coroutines.delay(500)
                waited += 500
            }
            Log.d(TAG, "WebView 嗅探结束，结果: $captured")
            return@withContext captured
        } catch (e: Exception) {
            Log.e(TAG, "WebView 嗅探异常", e)
            return@withContext null
        }
    }
}
