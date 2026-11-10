package com.example.a2faaccesssystem

import java.security.MessageDigest
// 사용자가 설정한 비밀번호 -> 해쉬하여 전송을 위한 코드 (SHA-256으로 해싱 -> 16진수 문자열)

object HashUtil {
    fun hashpassword(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))

        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
