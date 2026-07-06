package com.example.appbackend.util;

import com.example.appbackend.persistence.EncryptedStringConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveStringEncryptorTest {

    @Test
    void encryptsAndDecryptsSensitiveValues() {
        String raw = "20203090128";

        String encrypted = SensitiveStringEncryptor.encrypt(raw);

        assertNotEquals(raw, encrypted);
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertEquals(raw, SensitiveStringEncryptor.decrypt(encrypted));
    }

    @Test
    void decryptTreatsLegacyPlaintextAsReadableValue() {
        assertEquals("legacy-student-id", SensitiveStringEncryptor.decrypt("legacy-student-id"));
    }

    @Test
    void converterStoresCiphertextAndRestoresPlaintext() {
        EncryptedStringConverter converter = new EncryptedStringConverter();

        String databaseValue = converter.convertToDatabaseColumn("secret-password");

        assertNotEquals("secret-password", databaseValue);
        assertTrue(databaseValue.startsWith("enc:v1:"));
        assertEquals("secret-password", converter.convertToEntityAttribute(databaseValue));
    }
}
