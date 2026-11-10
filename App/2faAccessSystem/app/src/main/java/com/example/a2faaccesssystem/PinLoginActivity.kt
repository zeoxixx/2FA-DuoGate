package com.example.a2faaccesssystem

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.os.Build
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinLoginActivity : AppCompatActivity() {

    private lateinit var editPin: EditText
    private lateinit var btnConfirm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_login)

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
                // PIN 최초 로그인 성공 시에 백그라운드 작동 시작
                val serviceIntent = Intent(this, FirstAuth::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent) // 8.0이상일 때 강제 종료 방지
                } else {
                    startService(serviceIntent)
                }
                // 안내 메시지 후 백그라운드 작동을 위한 앱 종료
                Toast.makeText(this, "1차 인증 감지를 위한 백그라운드 서비스가 시작됩니다.", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("from_pin_login", true)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    finishAffinity()}, 3000) // 앱 스택 초기화
            } else {
                Toast.makeText(this, "PIN이 올바르지 않습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
