package com.example.a2faaccesssystem

// aes 키 교환시에 사용되는 RSA 일회용 키 생성 및 폐기 함수를 위한 파일
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.content.Context
import java.security.spec.X509EncodedKeySpec
import java.security.*
import android.util.Base64
import javax.crypto.Cipher

object RSAUtil {
    private const val KEY_ALIAS = "temp_rsa_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun generateRSA() { //일회용 rsa 키 생성
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGen = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            keyGen.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
                )
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build()
            )
            keyGen.generateKeyPair() // 공개키와 개인키 페어 생성 및 keystore에 저장
        }
    }

    //내장된 서버 공개키로 RSA 암호화
    fun encryptRSA(context: Context, plainText: String): String {
        val inputStream = context.resources.openRawResource(R.raw.public_key)
        val pemText = inputStream.bufferedReader().use { it.readText() }
        val cleanPem = pemText
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")

        val publicBytes = Base64.decode(cleanPem, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }


    fun getPublicKey(): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun decryptRSA(encrypted: ByteArray): ByteArray { //개인키로 복호화
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(encrypted)
    }

    fun deleteRSA() { //aes 키 교환 후 삭제
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}