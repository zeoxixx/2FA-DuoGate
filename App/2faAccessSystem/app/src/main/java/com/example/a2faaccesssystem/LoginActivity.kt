package com.example.a2faaccesssystem

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.content.Context
import android.util.Log

class LoginActivity : AppCompatActivity() {

    private lateinit var editId: EditText
    private lateinit var editPw: EditText
    private lateinit var btnLogin: Button
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editId = findViewById(R.id.editId)
        editPw = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val idApp = editId.text.toString().trim()
            val password = editPw.text.toString()

            if (idApp.isBlank() || password.isBlank()) {
                Toast.makeText(this, "ID와 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 비밀번호 해쉬 후 AES 암호화
            val hashedPw = HashUtil.hashpassword(password)
            val encryptedPw = try {
                AESUtil.encryptAES(this@LoginActivity, hashedPw)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "암호화 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("idApp", idApp)
                put("password", encryptedPw)
            }

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}/login")
                .post(json.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    val resJson = JSONObject(body ?: "")
                    val status = resJson.optString("status")

                    runOnUiThread {
                        if (status == "success") {
                            // 로그인 성공 시 idApp 저장
                            val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
                            prefs.edit().putString("idApp", idApp).apply()

                            // 로그인 성공 후 안드로이드 ID도 서버로 전송
                            MacUtil.registerMac(this@LoginActivity, idApp)
                            val key = AESUtil.loadKey(this@LoginActivity)
                            Log.d("AES_DEBUG", "🔍 현재 저장된 AES 키 객체: $key")


                            // PIN 등록 여부 확인
                            val pinPrefs = getSharedPreferences("secure_pin", MODE_PRIVATE)
                            val hasPin = pinPrefs.contains("simplePin")

                            //  PIN 등록 안 되어 있으면 PIN 등록 화면으로 이동
                            if (!hasPin) {
                                val intent = Intent(this@LoginActivity, RegisterPinActivity::class.java)
                                startActivity(intent)
                            } else {
                                // 이미 등록되어 있으면 MainActivity로 바로 이동
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                startActivity(intent)
                            }
                            finish() // LoginActivity 종료
                        }
                        else {
                            val msg = resJson.optString("message", "로그인 실패")
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "서버 연결 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }
}
