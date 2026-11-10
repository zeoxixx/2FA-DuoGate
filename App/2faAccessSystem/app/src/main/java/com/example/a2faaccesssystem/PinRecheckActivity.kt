package com.example.a2faaccesssystem

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class PinRecheckActivity : AppCompatActivity() {

    private lateinit var editPin: EditText
    private lateinit var btnConfirm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_recheck)

        // 안내 메시지 (1차 인증 완료)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@PinRecheckActivity, "1차 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show()
        }

        editPin = findViewById(R.id.editPin)
        btnConfirm = findViewById(R.id.btnConfirmPin)

        btnConfirm.setOnClickListener {
            val inputPin = editPin.text.toString()

            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val pinPrefs = EncryptedSharedPreferences.create(
                this,
                "secure_pin",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val storedPin = pinPrefs.getString("simplePin", null)

            if (storedPin != null && inputPin == storedPin) {
                // PIN 일치 시 OTP 요청 실행
                requestOtp()
            } else {
                Toast.makeText(this, "PIN이 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 서버에 OTP 요청을 보내는 함수
    private fun requestOtp() {
        val idApp = getSharedPreferences("user_session", MODE_PRIVATE).getString("idApp", null)
        val token = getSharedPreferences("auth_result", MODE_PRIVATE).getString("auth_token", null)

        if (idApp == null || token == null) {
            Toast.makeText(this, "인증 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val payloadJson = JSONObject().apply {
            put("token", token)
            put("request_type", "otp_request")
            put("timestamp", getCurrentKSTTime())
        }.toString()

        val key = AESUtil.loadKey(this)
        if (key == null) {
            Log.e("AES_DEBUG", "AES 키 로드 실패로 암호화 중단")
            Toast.makeText(this, "AES 키가 존재하지 않아 암호화 실패", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d("AES_DEBUG", "AES 키 정상 로드됨")

        val encryptedPayload = try {
            AESUtil.encryptAES(this, payloadJson)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AES_DEBUG", "AES 암호화 중 예외 발생", e)
            Toast.makeText(this, "AES 암호화 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        val requestJson = JSONObject().apply {
            put("idApp", idApp)
            put("payload", encryptedPayload)
        }

        Log.d("DEBUG", " requestOtp() 호출됨. token = $token")
        Log.d("DEBUG", " payload = $payloadJson")
        Log.d("DEBUG", " encryptedPayload = $encryptedPayload")

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}/request-otp")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@PinRecheckActivity, "서버 응답이 비어 있습니다", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                try {
                    val json = JSONObject(body)
                    val status = json.getString("status")

                    runOnUiThread {
                        when (status) {
                            "success" -> {
                                val otp = json.getString("otp")
                                val timestamp = json.getString("timestamp")
                                val intent = Intent(this@PinRecheckActivity, OtpVerifyActivity::class.java).apply {
                                    putExtra("otp", otp)
                                    putExtra("timestamp", timestamp)
                                }
                                startActivity(intent)
                                finish()
                            }
                            "fail" -> {
                                Toast.makeText(this@PinRecheckActivity, "OTP 요청 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                            }
                            "LOCKED" -> {
                                Toast.makeText(this@PinRecheckActivity, "계정이 잠겼습니다.", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@PinRecheckActivity, LockActivity::class.java))
                                finish()
                            }
                            "expired_token" -> {
                                Toast.makeText(this@PinRecheckActivity, "토큰이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@PinRecheckActivity, PinLoginActivity::class.java))
                                finish()
                            }
                            else -> {
                                Toast.makeText(this@PinRecheckActivity, "알 수 없는 오류: $status", Toast.LENGTH_SHORT).show()
                                finishAffinity()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@PinRecheckActivity, "서버 응답 파싱 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PinRecheckActivity, "서버 연결 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }


    private fun getCurrentKSTTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        //sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
        return sdf.format(Date())
    }
}