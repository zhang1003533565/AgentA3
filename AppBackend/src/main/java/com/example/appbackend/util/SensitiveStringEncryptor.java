package com.example.appbackend.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class SensitiveStringEncryptor {
    private static final String PREFIX = "enc:v1:";
    private static final String KEY_ENV = "APP_CREDENTIAL_ENCRYPTION_KEY";
    private static final String KEY_PROPERTY = "app.credential.encryption-key";
    private static final String DEFAULT_KEY = "smart-campus-local-credential-encryption-key-change-me";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SensitiveStringEncryptor() {
    }

    public static String encrypt(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || isEncrypted(rawValue)) {
            return rawValue;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(rawValue.getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(iv.length + ciphertext.length);
            payload.put(iv);
            payload.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    public static String decrypt(String databaseValue) {
        if (databaseValue == null || databaseValue.isBlank() || !isEncrypted(databaseValue)) {
            return databaseValue;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(databaseValue.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("encrypted payload is too short");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("敏感字段解密失败，请检查 " + KEY_ENV + " 是否与保存时一致", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKeySpec keySpec() throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(resolveKeyMaterial().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private static String resolveKeyMaterial() {
        String propertyKey = System.getProperty(KEY_PROPERTY);
        if (propertyKey != null && !propertyKey.isBlank()) {
            return propertyKey;
        }
        String envKey = System.getenv(KEY_ENV);
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            return jwtSecret;
        }
        return DEFAULT_KEY;
    }
}
