package com.example.a2faaccesssystem

import android.os.Bundle
import android.widget.*
import android.util.Base64
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.util.Log


class SignupActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val editId = findViewById<EditText>(R.id.editId)
        val editName = findViewById<EditText>(R.id.editName)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val editPasswordConfirm = findViewById<EditText>(R.id.editPasswordConfirm)
        val btnCheckId = findViewById<Button>(R.id.btnCheckId)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        var isIdChecked = false

        editId.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isIdChecked = false
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })


        btnCheckId.setOnClickListener {
            val id = editId.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "ID를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("idApp", id)
            }

            Log.d("NETWORK", "보낼 JSON (중복확인): $json")




            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            //ID 중복확인

            Log.d("NETWORK", "중복확인 요청 URL: ${BuildConfig.BASE_URL}/check-id")

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}/check-id")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val res = response.body?.string()
                    Log.d("NETWORK", "중복확인 응답 코드: ${response.code}")
                    Log.d("NETWORK", "중복확인 응답 바디: $res")
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@SignupActivity, "사용 가능한 ID입니다", Toast.LENGTH_SHORT).show()
                            isIdChecked = true
                        } else {
                            val message = JSONObject(res ?: "").optString("message", "중복 확인 실패")
                            Toast.makeText(this@SignupActivity, message, Toast.LENGTH_SHORT).show()
                            isIdChecked = false
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "서버 연결 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("NETWORK", "중복확인 서버 연결 실패: ${e.message}")
                    }
                }
            })
        }
        //회원 등록
        btnRegister.setOnClickListener {
            val id = editId.text.toString().trim()
            val name = editName.text.toString().trim()
            val pw = editPassword.text.toString()
            val pw2 = editPasswordConfirm.text.toString()

            if (!isIdChecked) {
                Toast.makeText(this, "ID 중복 확인을 먼저 해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pw.length < 8) {
                Toast.makeText(this, "비밀번호는 8자 이상이어야 합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pw != pw2) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 비밀번호 해시, 그리고 idApp을 제외한 항목들 RSA 암호화
            val hashedPw = HashUtil.hashpassword(pw)
            val encryptedName = RSAUtil.encryptRSA(this, name)
            val encryptedPassword = RSAUtil.encryptRSA(this, hashedPw)

            val encryptedUser = JSONObject().apply {
                put("idApp", id)
                put("name", encryptedName)
                put("password", encryptedPassword)
            }

            val userRequest = Request.Builder()
                .url("${BuildConfig.BASE_URL}/register-user")
                .post(encryptedUser.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(userRequest).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val resText = response.body?.string()
                    runOnUiThread {
                        if (response.isSuccessful) {
                            requestAESKey(id)
                        } else {
                            val message = JSONObject(resText ?: "").optString("message", "회원가입 실패")
                            Toast.makeText(this@SignupActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "회원가입 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }
    // 회원 등록 후 키 교환
    private fun requestAESKey(idApp: String) {
        //앱의 일회용 RSA 키페어 생성 -> 공개키 가져와서 서버에 전송
        RSAUtil.generateRSA()
        val publicKey = RSAUtil.getPublicKey()

        val keyReqJson = JSONObject().apply {
            put("idApp", idApp)
            put("publicKey", publicKey)
        }

        val keyRequest = Request.Builder()
            .url("${BuildConfig.BASE_URL}/issue-key")
            .post(keyReqJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(keyRequest).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val json = JSONObject(body ?: "")
                val encryptedKeyBase64 = json.optString("aesKey")

                if (encryptedKeyBase64.isNullOrBlank()) {
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "AES 키 수신 실패", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val encryptedKeyBytes = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
                val aesKeyBytes = RSAUtil.decryptRSA(encryptedKeyBytes)
                Log.d("AES_DEBUG", "복호화된 AES 키 길이: ${aesKeyBytes.size}")

                val aesKeyBase64 = Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP)
                AESUtil.storeAESKey(this@SignupActivity, aesKeyBase64)
                Log.d("AES_DEBUG", "AES 키 저장 완료: ${aesKeyBase64.take(10)}...")

                val loadedKey = AESUtil.loadKey(this@SignupActivity)
                if (loadedKey != null) {
                    val loadedKeyBase64 = Base64.encodeToString(loadedKey.encoded, Base64.NO_WRAP)
                    Log.d("AES_DEBUG", "저장 직후 로드된 AES 키: $loadedKeyBase64")
                } else {
                    Log.e("AES_DEBUG", " 저장 직후 AES 키를 불러올 수 없음")
                }


                runOnUiThread {
                    Toast.makeText(this@SignupActivity, "회원가입 및 키 등록 완료", Toast.LENGTH_SHORT).show()
                    RSAUtil.deleteRSA()

                    //로그인 페이지 이동
                    val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                    startActivity(intent)

                    finish()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SignupActivity, "AES 키 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}