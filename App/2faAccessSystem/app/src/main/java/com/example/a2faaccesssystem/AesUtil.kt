// AESUtil.kt - 서버로부터 받은 AES 키를 저장하고 암복호화까지 수행
package com.example.a2faaccesssystem

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import android.util.Log


object AESUtil {
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12 // bytes
    private const val GCM_TAG_LENGTH = 16 // bytes = 128 bits
    private const val PREF_NAME = "secure_prefs"
    private const val KEY_NAME = "aes_key"

    // Base64 문자열을 SecretKey로 디코딩
    private fun decodeKey(encodedKey: String): SecretKey {
        val decoded = Base64.decode(encodedKey, Base64.NO_WRAP)
        Log.d("AES_DEBUG", "decodeKey(): Base64 decode 결과 길이 = ${decoded.size}")
        return SecretKeySpec(decoded, 0, decoded.size, "AES")
    }

    // AES 키를 EncryptedSharedPreferences에 저장
    // -> Android Jetpack Security 라이브러리 보안 저장소 (SharedPreferences) 개념
    // 서버와 교환한 개인 AES 키를 AES-GCM 암호화 해 저장
    fun storeAESKey(context: Context, base64Key: String) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        prefs.edit().putString(KEY_NAME, base64Key).commit()
        Log.d("AES_DEBUG", "AES 키 저장 완료: $base64Key")
    }

    // SharedPreferences에 저장된 AES 키 로드
    fun loadKey(context: Context): SecretKey? {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val base64Key = prefs.getString(KEY_NAME, null) ?: return null
        return decodeKey(base64Key) // 사용할 수 있게 문자열을 decode
    }

    // 평문 암호화
    fun encryptAES(context: Context, plainText: String): String {
        val key = loadKey(context) ?: throw Exception("AES 키가 저장되어 있지 않습니다.")
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val encryptedWithTag = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val cipherText = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - GCM_TAG_LENGTH)
        val tag = encryptedWithTag.copyOfRange(encryptedWithTag.size - GCM_TAG_LENGTH, encryptedWithTag.size)

        val json = JSONObject()
        json.put("nonce", Base64.encodeToString(iv, Base64.NO_WRAP))
        json.put("tag", Base64.encodeToString(tag, Base64.NO_WRAP))
        json.put("ciphertext", Base64.encodeToString(cipherText, Base64.NO_WRAP))

        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    // 암호문 복호화
    fun decryptAES(context: Context, encodedPackage: String): String {
        val key = loadKey(context) ?: throw Exception("AES 키가 저장되어 있지 않습니다.")
        if (key == null) {
            Log.e("AES_DEBUG", "AES 키가 null입니다. 저장되지 않았습니다.")
            throw Exception("AES 키가 저장되어 있지 않습니다.")
        }
        Log.d("AES_DEBUG", "AES 키 로드 완료: ${key}...")

        val decodedJson = String(Base64.decode(encodedPackage, Base64.NO_WRAP), Charsets.UTF_8)
        val json = JSONObject(decodedJson)

        val iv = Base64.decode(json.getString("nonce"), Base64.NO_WRAP)
        val tag = Base64.decode(json.getString("tag"), Base64.NO_WRAP)
        val cipherText = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)

        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val cipherWithTag = cipherText + tag
        val plainBytes = cipher.doFinal(cipherWithTag)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun resetAESKey(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        prefs.edit().clear().apply()
        Log.d("AES_DEBUG", "AES 키 초기화 완료")
    }

}
