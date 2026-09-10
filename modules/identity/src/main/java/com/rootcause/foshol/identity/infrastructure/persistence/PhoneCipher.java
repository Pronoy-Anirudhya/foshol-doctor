package com.rootcause.foshol.identity.infrastructure.persistence;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.identity.application.port.PhoneCipherPort;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhoneCipher implements PhoneCipherPort {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public PhoneCipher(@Value("${" + ConfigKeys.CRYPTO_PHONE_KEY + "}") String encoded) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            decoded = new byte[0];
        }
        this.key = decoded;
    }

    @PostConstruct
    void validateKey() {
        if (key.length != 32) {
            throw new IllegalStateException(ErrorCodes.ERR_PHONE_KEY_INVALID);
        }
    }

    @Override
    public byte[] encrypt(String e164) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(e164.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
