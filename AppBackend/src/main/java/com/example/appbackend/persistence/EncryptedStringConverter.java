package com.example.appbackend.persistence;

import com.example.appbackend.util.SensitiveStringEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return SensitiveStringEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return SensitiveStringEncryptor.decrypt(dbData);
    }
}
