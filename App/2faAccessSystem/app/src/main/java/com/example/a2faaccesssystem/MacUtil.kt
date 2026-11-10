package com.example.a2faaccesssystem

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object MacUtil {

    // MAC 등록 함수 -> ANDROID ID + BSSID로?
    fun registerMac(context: Context, idApp: String) {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bssid = wifiManager.connectionInfo.bssid ?: "UNKNOWN"


        //idApp 제외 모두 암호화하여 통신
        val jsonToEncrypt = JSONObject().apply {
            put("macAddr", androidId)
            put("ssid", bssid)
            put("timestamp", System.currentTimeMillis().toString())
        }

        val encryptedPayload = AESUtil.encryptAES(context, jsonToEncrypt.toString())

        val json = JSONObject().apply {
            put("idApp", idApp)
            put("payload", encryptedPayload)
        }

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}/register-mac")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Log.d("MAC", "등록 성공: ${response.code}")
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("MAC", "등록 실패: ${e.message}")
            }
        })
    }
}