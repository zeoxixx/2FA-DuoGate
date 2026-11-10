package com.example.a2faaccesssystem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class RegisterPinActivity : AppCompatActivity() {

    private lateinit var editPin1: EditText
    private lateinit var editPin2: EditText
    private lateinit var btnRegisterPin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_pin)

        editPin1 = findViewById(R.id.editPin1)
        editPin2 = findViewById(R.id.editPin2)
        btnRegisterPin = findViewById(R.id.btnRegisterPin)

        btnRegisterPin.setOnClickListener {
            val pin1 = editPin1.text.toString()
            val pin2 = editPin2.text.toString()

            if (pin1.length != 6 || pin2.length != 6) {
                Toast.makeText(this, "6자리 숫자를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pin1 != pin2) {
                Toast.makeText(this, "PIN이 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 저장 (EncryptedSharedPreferences로 안전하게)

            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                this,
                "secure_pin", // 파일 이름
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            prefs.edit().putString("simplePin", pin1).apply()

            Toast.makeText(this, "간편 로그인 등록 완료", Toast.LENGTH_SHORT).show()

            val serviceIntent = Intent(this, FirstAuth::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
