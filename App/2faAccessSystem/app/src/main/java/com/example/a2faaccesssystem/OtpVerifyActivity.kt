package com.example.a2faaccesssystem

import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType


class OtpVerifyActivity : AppCompatActivity() {

    private lateinit var otpCodeText: TextView
    private lateinit var otpTimerText: TextView
    private lateinit var otpProgressBar: ProgressBar

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()

    private var timer: CountDownTimer? = null
    private val checkHandler = Handler(Looper.getMainLooper())
    private val checkInterval = 2000L // 1초마다 polling

    // 2초마다 서버에 OTP 인증 결과를 확인하는 Runnable
    private val resultCheckRunnable = object : Runnable {
        override fun run() {
            checkOtpResult()
            checkHandler.postDelayed(this, checkInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verify)

        otpCodeText = findViewById(R.id.textOtpCode)
        otpTimerText = findViewById(R.id.textOtpTimer)
        otpProgressBar = findViewById(R.id.progressOtpTimer)

        // 암호화된 OTP와 생성 시각(timestamp)을 intent로부터 전달받음
        val encryptedOtp = intent.getStringExtra("otp")
        val timestamp = intent.getStringExtra("timestamp")

        if (encryptedOtp == null || timestamp == null) {
            Toast.makeText(this, "OTP 정보가 누락되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 서버로부터 받은 암호화된 OTP 복호화
        val otpCode = AESUtil.decryptAES(this, encryptedOtp)
        otpCodeText.text = otpCode.chunked(3).joinToString(" ")

        // 서버 timestamp를 기준으로 남은 시간 계산
        val remainingMillis = calculateRemainingTime(timestamp)

        if (remainingMillis <= 0) {
            // 이미 만료된 경우 새 OTP 자동 요청
            requestNewOtp()
        } else {
            // 타이머 시작 및 결과 확인 polling 시작
            startTimer(remainingMillis)
            checkHandler.post(resultCheckRunnable) //풀링 지연시키려면 , 1000L <-추가 하면 됨
        }
    }

    // 타이머를 시작하는 함수 (남은 시간 기반)
    private fun startTimer(durationMillis: Long) {
        timer?.cancel()

        val totalSec = (durationMillis / 1000).toInt()
        otpProgressBar.max = 30
        otpProgressBar.progress = totalSec
        otpTimerText.text = totalSec.toString()

        timer = object : CountDownTimer(durationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                otpProgressBar.progress = seconds
                otpTimerText.text = seconds.toString()
            }

            override fun onFinish() {
                // 타이머 만료 시 새 OTP 자동 요청
                requestNewOtp()
            }
        }
        timer?.start()
    }

    // OTP 유효시간이 끝났을 때 새 OTP를 요청하는 함수
    private fun requestNewOtp() {
        val pref = getSharedPreferences("user_session", MODE_PRIVATE)
        val idApp = pref.getString("idApp", null)
        val token = getSharedPreferences("auth_result", MODE_PRIVATE)
            .getString("auth_token", null)

        if (idApp == null || token == null) {
            Toast.makeText(this, "인증 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 서버에 보낼 payload 생성
        val payloadJson = JSONObject().apply {
            put("token", token)
            put("request_type", "otp_request")
            put("timestamp", getCurrentKSTTime())
        }.toString()

        val encryptedPayload = AESUtil.encryptAES(this, payloadJson)

        val requestJson = JSONObject().apply {
            put("idApp", idApp)
            put("payload", encryptedPayload)
        }

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}/request-otp")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: return
                val json = JSONObject(result)
                val status = json.getString("status")
                Log.d("OTP_DEBUG", "checkOtpResult 호출됨")
                Log.d("OTP_DEBUG", "서버 응답: $result")

                runOnUiThread {
                    when (status) {
                        "success" -> {
                            // 응답으로 받은 새로운 OTP 표시 및 타이머 재시작
                            val otpEncrypted = json.getString("otp")
                            val timestamp = json.getString("timestamp")

                            val otpCode = AESUtil.decryptAES(this@OtpVerifyActivity, otpEncrypted)
                            otpCodeText.text = otpCode.chunked(3).joinToString(" ")

                            val remainingMillis = calculateRemainingTime(timestamp)
                            startTimer(remainingMillis)
                        }
                        "expired_token" -> {
                            // 토큰이 만료된 경우 로그인으로 이동
                            Toast.makeText(this@OtpVerifyActivity, "1차 인증이 만료되었습니다.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@OtpVerifyActivity, PinLoginActivity::class.java))
                            finish()
                        }
                        else -> {
                            Toast.makeText(this@OtpVerifyActivity, "OTP 요청 실패: $status", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@OtpVerifyActivity, "서버 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // 서버에 2초마다 결과를 확인하는 polling 함수
    private fun checkOtpResult() {
        val idApp = getSharedPreferences("user_session", MODE_PRIVATE)
            .getString("idApp", null) ?: return

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}/check-result?idApp=$idApp")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: return
                val json = JSONObject(result)
                val status = json.getString("status")

                runOnUiThread {
                    when (status) {
                        "success" -> {
                            cleanupAndGo(AccessSuccessActivity::class.java)
                        }
                        "fail" -> {
                            cleanupAndGo(AccessFailActivity::class.java)
                        }
                        "LOCKED" -> {
                            cleanupAndGo(LockActivity::class.java)
                        }
                        "expired_otp" -> {
                            cleanupAndGo(PinLoginActivity::class.java)
                            Toast.makeText(this@OtpVerifyActivity, "OTP가 만료되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("OTP_RESULT", "결과 확인 실패: ${e.message}")
            }
        })
    }

    // 타이머 및 polling 정리 후 화면 전환
    private fun cleanupAndGo(cls: Class<*>) {
        checkHandler.removeCallbacks(resultCheckRunnable)
        timer?.cancel()
        startActivity(Intent(this, cls))
        finish()
    }

    // 서버 timestamp 기준으로 남은 시간 계산 (단위: 밀리초)
    private fun calculateRemainingTime(serverTimestamp: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            //sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
            val serverTime = sdf.parse(serverTimestamp) ?: return 0
            val now = Date()

            Log.d("OTP_DEBUG", "serverTime: $serverTime, now: $now, diff: ${now.time - serverTime.time}")


            val diff = 30_000 - (now.time - serverTime.time)
            diff.coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    // 현재 UTC 시간 문자열 생성
    private fun getCurrentKSTTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return sdf.format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        checkHandler.removeCallbacks(resultCheckRunnable)
    }
}
