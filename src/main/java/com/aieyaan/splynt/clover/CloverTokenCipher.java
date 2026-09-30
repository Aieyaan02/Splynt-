package com.aieyaan.splynt.clover;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Application encryption key is separate from the database and JWT signing key. */
@Component
public class CloverTokenCipher {
    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();

    public CloverTokenCipher(@Value("${splynt.integrations.clover.encryption-key:}") String key) {
        this.configuredKey = key;
    }

    public void validateConfiguration() { key(); }

    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Clover connection encryption is not configured");
        }
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            return "v1:" + Base64.getEncoder().encodeToString(nonce) + ":"
                    + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to secure Clover connection", ex);
        }
    }

    public String decrypt(String value) {
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Clover credentials cannot be read; reconnect the store", ex);
        }
    }
}
