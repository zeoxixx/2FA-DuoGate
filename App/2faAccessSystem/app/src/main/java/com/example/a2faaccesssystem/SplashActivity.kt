package com.example.a2faaccesssystem

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import android.content.Context
import android.util.Log
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.View
import android.widget.ImageView


class SplashActivity : AppCompatActivity() {

    private val permissionRequestCode = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_anim)  // 애니메이션 레이아웃으로 연결

        //AESUtil.resetAESKey(this)
        //clearPlainPrefs(this)
        playSplashAnimation()

        // 인증 상태 고려 후 백그라운드 시도
        //수정1: PIN 등록 여부 확인 후 FirstAuth 실행
        val pinPrefs = EncryptedSharedPreferences.create(
            "secure_pin",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val hasPin = pinPrefs.contains("simplePin")

        if (!FirstAuth.isRunning && hasPin) {
            val serviceIntent = Intent(this, FirstAuth::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            Log.d("SplashActivity", "FirstAuth 실행 조건 불충족 (실행 중이거나 PIN 없음)")
        }

    }
    // 1. 애니메이션 실행 → 애니메이션 끝나면 checkLocationPermission() 호출
    private fun playSplashAnimation() {
        val doorImage = findViewById<ImageView>(R.id.doorImageView)
        val blackOverlay = findViewById<View>(R.id.blackOverlay)

        doorImage.setImageResource(R.drawable.door_lock)

        Handler(Looper.getMainLooper()).postDelayed({
            doorImage.setImageResource(R.drawable.door_lock_2)
        }, 500)

        Handler(Looper.getMainLooper()).postDelayed({
            doorImage.setImageResource(R.drawable.door_lock_3)
        }, 1000)

        Handler(Looper.getMainLooper()).postDelayed({
            doorImage.setImageResource(R.drawable.door_lock_4)
        }, 1500)

        Handler(Looper.getMainLooper()).postDelayed({
            doorImage.setImageResource(R.drawable.door_lock_5)
        }, 2000)

        Handler(Looper.getMainLooper()).postDelayed({
            val fadeToBlack = AnimationUtils.loadAnimation(this, R.anim.fade_in_black)
            blackOverlay.visibility = View.VISIBLE
            blackOverlay.startAnimation(fadeToBlack)

            fadeToBlack.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    checkLocationPermission()
                }
            })
        }, 2000)
    }
    // 2. 위치 권한 확인
    private fun checkLocationPermission() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )


        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), permissionRequestCode)
        } else {
            decideNextScreen()  // 권한 이미 있을 경우 다음 화면으로
        }

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                decideNextScreen()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    val prefsNames = listOf("user_session", "secure_pin", "auth_result")



    private fun decideNextScreen() {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val pinPrefs = EncryptedSharedPreferences.create(
            "secure_pin",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val authPrefs = getSharedPreferences("auth_result", MODE_PRIVATE)
        // 1차 인증의 결과 여부와 토큰은 저장하여 2차 인증을 위해 대기

        val idApp = prefs.getString("idApp", null)
        val hasPin = pinPrefs.contains("simplePin")
        val isfirstpassed = authPrefs.getBoolean("first_auth_passed", false)
        val authToken = authPrefs.getString("auth_token", null)
        val expireToken = authPrefs.getString("expire_token", null)

        val isTokenValid = expireToken?.let{
            try {
                val sdf = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    java.util.Locale.getDefault()
                )
                //sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
                val expireDate = sdf.parse(it)
                val now = java.util.Date()
                expireDate?.after(now) ?: false
            } catch(e: Exception) {
                false
            }
        } ?:false

        // 토큰 만료 -> 1차인증 재시도를 위한 코드 (10분)
        if(isfirstpassed && !isTokenValid) {
            authPrefs.edit().putBoolean("first_auth_passed", false).apply()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            when {
                // 앱 접속 시 idApp이 없다면 - 회원가입으로
                idApp == null -> startActivity(Intent(this, SignupActivity::class.java))
                // 간편 pin이 없다면 -> 로그인 후 간편 Pin 등록 화면으로
                !hasPin -> startActivity(Intent(this, LoginActivity::class.java))
                // 1차인증 완료 x or 토큰을 받지 못했다면 수동 1차 인증 하도록 -> Main
                !authPrefs.getBoolean("first_auth_passed", false) || authPrefs.getString("auth_token", null).isNullOrEmpty() ->
                    startActivity(Intent(this, MainActivity::class.java))
                //나머지(1차인증 완료 & pin 있음 -> pinrecheck, pin 없으면 로그인부터)
                else -> {
                    if (hasPin) {
                        startActivity(Intent(this, PinRecheckActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))  // 또는 PIN 등록 화면
                    }
                }
            }
            finish()
        }, 1000)
    }

    fun clearPlainPrefs(context: Context) {
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d("DEBUG", "SharedPreferences cleared")
    }
}
