package com.rootcause.foshol.identity.infrastructure;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class IdentityStartupGuard {

    private static final Set<String> DEV_PROFILES = Set.of("local", "demo", "test");

    private final Environment environment;
    private final String devCode;
    private final String jwtSecret;
    private final String phoneKey;

    public IdentityStartupGuard(
            Environment environment,
            @Value("${" + ConfigKeys.AUTH_OTP_DEV_CODE + ":#{null}}") String devCode,
            @Value("${" + ConfigKeys.AUTH_JWT_SECRET + "}") String jwtSecret,
            @Value("${" + ConfigKeys.CRYPTO_PHONE_KEY + "}") String phoneKey) {
        this.environment = environment;
        this.devCode = devCode;
        this.jwtSecret = jwtSecret;
        this.phoneKey = phoneKey;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void validate() {
        StringBuilder errors = new StringBuilder();
        byte[] key;
        try {
            key = java.util.Base64.getDecoder().decode(phoneKey);
        } catch (IllegalArgumentException ex) {
            key = new byte[0];
        }
        if (key.length != 32) {
            errors.append(ErrorCodes.ERR_PHONE_KEY_INVALID).append(' ');
        }
        if (jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            errors.append(ErrorCodes.ERR_JWT_SECRET_TOO_SHORT).append(' ');
        }
        boolean devProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(DEV_PROFILES::contains)
                || Arrays.stream(environment.getDefaultProfiles()).anyMatch(DEV_PROFILES::contains);
        if (devCode != null && !devCode.isBlank() && !devProfile) {
            errors.append(ErrorCodes.ERR_DEV_OTP_IN_NON_DEV_PROFILE).append(' ');
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(errors.toString().trim());
        }
    }
}
