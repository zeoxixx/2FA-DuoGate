package com.example.a2faaccesssystem

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import java.util.TimeZone
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {


    private lateinit var btnMyPage: Button
    private lateinit var btnEnter: Button
    private lateinit var tvSuccess: TextView
    private lateinit var tvFail: TextView

    private var fromPin: Boolean = false //Pin 최초 로그인 시 보여줄 화면을 위함


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnEnter = findViewById(R.id.btnEnter)
        tvSuccess = findViewById(R.id.tvSuccess)
        tvFail = findViewById(R.id.tvFail)
        btnMyPage = findViewById(R.id.btnMyPage)

        fromPin = intent.getBooleanExtra("from_pin_login", false)

        btnEnter.setOnClickListener {
            val prefs = getSharedPreferences("auth_result", MODE_PRIVATE)
            val isPassed = prefs.getBoolean("first_auth_passed", false)
            val token = prefs.getString("auth_token", null)
            val expireToken = prefs.getString("expire_token", null)
            Log.d("TOKEN_CHECK", "expireToken = ${expireToken ?: "없음" }")
            Log.d("TOKEN_CHECK", "auth_token = ${token ?: "없음"}")

            val isTokenValid = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
                val expireDate = expireToken?.let { sdf.parse(it) }
                val now = Date()
                Log.d("TOKEN_CHECK", "Parsed expireDate = $expireDate, now = $now")
                expireDate?.after(now) ?: false
            }catch (e: Exception) {
                Log.e("TOKEN_CHECK", "토큰 파싱 중 예외 발생: ${e.message}")
                false
            }


            if(isPassed && !token.isNullOrEmpty() && isTokenValid == true) {
                val intent = Intent(this, PinRecheckActivity::class.java)
                startActivity(intent)

            } else { //토큰 만료로 1차인증 다시 시도
                prefs.edit().putBoolean("first_auth_passed", false).apply()

                if(!FirstAuth.isRunning) {
                    val intent = Intent(this, FirstAuth::class.java)
                    ContextCompat.startForegroundService(this, intent)
                    Toast.makeText(this, "1차 인증을 시작합니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "이미 1차인증 중입니다.", Toast.LENGTH_SHORT).show()
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    btnEnter.isEnabled = true
                    finish()
                }, 2000)
            }
        }

        // 마이페이지 이동
        btnMyPage.setOnClickListener {
            Toast.makeText(this, "마이페이지로 이동", Toast.LENGTH_SHORT).show()
            // startActivity(Intent(this, MyPageActivity::class.java))
        }

    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            val isPassed = getSharedPreferences("auth_result", MODE_PRIVATE)
                .getBoolean("first_auth_passed", false)
            updateUI(isPassed, fromPin)
            intent.removeExtra("from_pin_login")
        }, 300) //0.3초 지연
    }

    private fun updateUI(isPassed: Boolean, fromPin: Boolean) {
        if (isPassed) {
            tvSuccess.visibility = View.VISIBLE
            tvFail.visibility = View.GONE
            btnEnter.text = "출입하기"
        } else {
            if(fromPin) {
                tvFail.text = "1차 인증을 시작합니다."
                btnEnter.text = "1차 인증하기"
            } else {
                tvFail.text = "1차 인증이 되지 않았습니다.\n1차 인증을 시도하세요."
                btnEnter.text = "1차 인증하기"
            }
            tvSuccess.visibility = View.GONE
            tvFail.visibility = View.VISIBLE
        }
    }
}
