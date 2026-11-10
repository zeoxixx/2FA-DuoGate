package com.example.a2faaccesssystem

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys



import com.example.a2faaccesssystem.BuildConfig


class FirstAuth : Service() {

    companion object {
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val interval = 5000L // 5초마다 Wi-Fi 상태 확인

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkWifiAndSend(applicationContext)
            handler.postDelayed(this, interval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(1, createNotice())
        handler.post(checkRunnable)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotice(): Notification {
        val channelId = "wifi_monitor"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Wi-Fi Monitor", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("1차 인증 모니터링 중")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun checkWifiAndSend(context: Context) {
        val authResult = context.getSharedPreferences("auth_result", Context.MODE_PRIVATE)
        val isPassed = authResult.getBoolean("first_auth_passed", false)
        val expireToken = authResult.getString("expire_token", null)

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.getDefault())
        //sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")

        val isTokenValid = expireToken?.let{ // 토큰 유효기간이 현재보다 앞인지 검사
            try{
                val expireDate = try {
                    sdf.parse(expireToken).also {
                        Log.d("FirstAuth", "파싱 성공, expireDate = $it")
                    }
                }catch (e: Exception) {
                    Log.e("FirstAuth", "토큰 파싱 실패: ${e.message}")
                    null
                }
                val now = java.util.Date()
                Log.d("FirstAuth", "expire = $expireDate, now = $now")
                expireDate?.after(now) ?: false
            } catch (e:Exception) {
                Log.e("FirstAuth", "날짜 파싱 오류: ${e.message}")
                false
            }
        } ?: false

        // 1차 인증 및 서버에서 받은 토큰까지 유효함 -> 1차인증 완료
        if(isPassed && isTokenValid) {
            Log.d("FirstAuth", "인증 완료 + 토큰 유효함. 재요청 생략합니다.")
            stopSelf()
            return
        }
        else if (isPassed && !isTokenValid) {
            Log.d("FirstAuth", "토큰 만료됨. 인증 무효화 처리로 재요청합니다.")
            authResult.edit().putBoolean("first_auth_passed", false).apply()
        }

        val pref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val idApp = pref.getString("idApp", null) ?: return

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.replace("\"", "")

        Log.d("FirstAuth", "SSID = ${wifiInfo.ssid}, BSSID = ${wifiInfo.bssid}")


        if (ssid == BuildConfig.WIFI_SSID) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val isoTime = sdf.format(java.util.Date(System.currentTimeMillis()))

            // 평문 JSON 생성
            val payload = JSONObject().apply {
                put("macAddr", androidId)
                put("request_type", "First-auth")
                put("timestamp", isoTime)
                put("ssid", "pi")
            }.toString()

            val encryptedBody = AESUtil.encryptAES(context, payload)

            val fullbody = JSONObject().apply {
                put("idApp", idApp)
                put("payload", encryptedBody)
            }.toString()

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}/verify-mac")
                .post(fullbody.toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val encryptedResponse = response.body?.string()
                    if (!encryptedResponse.isNullOrBlank()) {
                        try {
                            Log.d("FirstAuth", "서버 응답 암호문 (Base64): $encryptedResponse")

                            val decryptedJson = AESUtil.decryptAES(context, encryptedResponse)
                            val json = JSONObject(decryptedJson)

                            val status = json.optString("status")

                            if (status == "success") {
                                val userId = json.optString("idUser")
                                val token = json.optString("token")
                                val expire = json.optString("expire_token")
                                Log.d("FirstAuth", "인증 성공 - ID: $userId, 토큰: $token")

                                context.getSharedPreferences("auth_result", Context.MODE_PRIVATE)
                                    .edit().apply {
                                        putBoolean("first_auth_passed", true)
                                        putString("auth_token", token)
                                        putString("expire_token", expire)
                                        putString("ssid", ssid)
                                        putLong("last_success_time", System.currentTimeMillis())
                                        Log.d("TOKEN_CHECK", "저장 완료: token=$token / expire=$expire")
                                        apply()
                                    }

                                // PIN 미등록 시 화면 이동 생략
                                val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                                val pinPrefs = EncryptedSharedPreferences.create(
                                    "secure_pin",
                                    masterKey,
                                    context,
                                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                )
                                val hasPin = pinPrefs.contains("simplePin")
                                if (!hasPin) {
                                    Log.w("FirstAuth", "PIN 미등록 상태 - PinRecheckActivity 진입 생략")
                                    stopSelf()
                                    return  // 화면 전환 없이 종료 (서비스는 자동 중단됨)
                                }

                                // 2차 인증 화면 이동
                                val intent = Intent(this@FirstAuth, PinRecheckActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                startActivity(intent)

                                Handler(Looper.getMainLooper()).postDelayed({
                                    handler.removeCallbacks(checkRunnable)
                                    stopSelf()
                                }, 500) // 0.5초 뒤에 종료
                            }
                            else {
                                context.getSharedPreferences("auth_result", Context.MODE_PRIVATE)
                                    .edit().putBoolean("first_auth_passed", false).apply()

                                Log.w("FirstAuth", "인증 실패 또는 매칭 안 됨")
                            }
                        } catch (e: Exception) {
                            Log.e("FirstAuth", "복호화 오류: ${e.message}")
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("FirstAuth", "전송 실패: ${e.message}")
                }
            })
        }
    }
}
