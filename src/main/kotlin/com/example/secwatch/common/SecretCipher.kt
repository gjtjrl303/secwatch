package com.example.secwatch.common

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 다시 꺼내 써야 하는 비밀 값(웹훅 주소, 사이트 점검 비밀번호)을 AES-GCM으로 암호화한다. */
@Component
class SecretCipher(@Value("\${secwatch.encryption-key:}") rawKey: String) {
    private val key = SecretKeySpec(decodeKey(rawKey), "AES")
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) throw IllegalStateException("저장된 비밀 값이 암호화되어 있지 않습니다.")
        return try {
            val bytes = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            }
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("저장된 비밀 값을 풀지 못했습니다. 암호화 키가 저장할 때와 같은지 확인해주세요.")
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("저장된 비밀 값을 풀지 못했습니다. 암호화 키가 저장할 때와 같은지 확인해주세요.")
        }
    }

    private companion object {
        const val PREFIX = "v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32
        const val KEY_GUIDE = "SECWATCH_ENCRYPTION_KEY에 32바이트 키를 Base64로 넣어주세요. 만드는 방법: openssl rand -base64 32"

        fun decodeKey(raw: String): ByteArray {
            val bytes = try {
                Base64.getDecoder().decode(raw.trim())
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("암호화 키를 읽지 못했습니다. $KEY_GUIDE")
            }
            if (bytes.size != KEY_BYTES) throw IllegalStateException("암호화 키가 없거나 길이가 맞지 않습니다. $KEY_GUIDE")
            return bytes
        }
    }
}
