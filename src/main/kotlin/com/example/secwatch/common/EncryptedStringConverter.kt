package com.example.secwatch.common

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Component
@Converter
class EncryptedStringConverter(private val secretCipher: SecretCipher) : AttributeConverter<String?, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let(secretCipher::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.let(secretCipher::decrypt)
}
