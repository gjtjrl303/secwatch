package com.example.secwatch.common

import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("저장할 비밀 값 암호화")
class SecretCipherTest {
    private fun key(fill: Int) = Base64.getEncoder().encodeToString(ByteArray(32) { fill.toByte() })

    private val cipher = SecretCipher(key(1))

    @Test
    @DisplayName("암호화한 값을 같은 키로 풀면 원래 값이 나온다")
    fun round_trip() {
        val encrypted = cipher.encrypt("p@ss 비밀")

        assertThat(encrypted).startsWith("v1:").doesNotContain("p@ss")
        assertThat(cipher.decrypt(encrypted)).isEqualTo("p@ss 비밀")
    }

    @Test
    @DisplayName("같은 값도 암호화할 때마다 다른 글자가 된다")
    fun same_value_encrypts_differently() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"))
    }

    @Test
    @DisplayName("다른 키로는 풀리지 않는다")
    fun other_key_cannot_decrypt() {
        val encrypted = cipher.encrypt("secret")

        assertThatThrownBy { SecretCipher(key(2)).decrypt(encrypted) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("암호화 키")
            .hasMessageNotContaining("secret")
    }

    @Test
    @DisplayName("저장된 값이 바뀌었으면 풀리지 않는다")
    fun tampered_value_cannot_decrypt() {
        val encrypted = cipher.encrypt("secret")
        val bytes = Base64.getDecoder().decode(encrypted.removePrefix("v1:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()

        assertThatThrownBy { cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(bytes)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    @DisplayName("암호화하지 않은 값은 풀지 않고 오류로 알린다")
    fun plain_value_is_rejected() {
        assertThatThrownBy { cipher.decrypt("https://discord.com/api/webhooks/1/a") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageNotContaining("discord")
    }

    @Test
    @DisplayName("키가 없거나 32바이트가 아니면 만들지 않고, 키를 만드는 방법을 알려준다")
    fun rejects_missing_or_wrong_key() {
        listOf("", "  ", "not-base64!!", Base64.getEncoder().encodeToString(ByteArray(16))).forEach { raw ->
            assertThatThrownBy { SecretCipher(raw) }
                .`as`(raw)
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("SECWATCH_ENCRYPTION_KEY")
                .hasMessageContaining("openssl rand -base64 32")
        }
    }

    @Test
    @DisplayName("키가 틀렸다는 오류에 키 값은 나오지 않는다")
    fun error_never_shows_the_key() {
        val wrong = Base64.getEncoder().encodeToString(ByteArray(16) { 7 })

        assertThatThrownBy { SecretCipher(wrong) }.hasMessageNotContaining(wrong)
    }
}
