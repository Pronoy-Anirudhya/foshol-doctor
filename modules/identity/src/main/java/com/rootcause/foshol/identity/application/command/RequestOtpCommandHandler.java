package com.rootcause.foshol.identity.application.command;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.OtpCodeHash;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeEntity;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestOtpCommandHandler {

    private final FarmerJpaRepository farmers;
    private final OtpChallengeJpaRepository challenges;
    private final Clock clock;
    private final boolean otpEnabled;
    private final Duration otpTtl;
    private final String devCode;
    private final int rateMax;
    private final Duration rateWindow;

    public RequestOtpCommandHandler(
            FarmerJpaRepository farmers,
            OtpChallengeJpaRepository challenges,
            Clock clock,
            @Value("${" + ConfigKeys.AUTH_OTP_ENABLED + "}") boolean otpEnabled,
            @Value("${" + ConfigKeys.AUTH_OTP_TTL + "}") Duration otpTtl,
            @Value("${" + ConfigKeys.AUTH_OTP_DEV_CODE + ":#{null}}") String devCode,
            @Value("${" + ConfigKeys.AUTH_OTP_RATE_LIMIT_MAX_REQUESTS + "}") int rateMax,
            @Value("${" + ConfigKeys.AUTH_OTP_RATE_LIMIT_WINDOW + "}") Duration rateWindow) {
        this.farmers = farmers;
        this.challenges = challenges;
        this.clock = clock;
        this.otpEnabled = otpEnabled;
        this.otpTtl = otpTtl;
        this.devCode = devCode;
        this.rateMax = rateMax;
        this.rateWindow = rateWindow;
    }

    @Transactional
    public RequestOtpResult handle(RequestOtpCommand command) {
        if (!otpEnabled) {
            throw new IdentityException(ErrorCodes.ERR_OTP_DISABLED, 503, "One-time codes are disabled.");
        }
        PhoneNumber phone = PhoneNumber.parse(command.phone());
        String phoneHash = PhoneHash.of(phone).hex();
        Instant now = clock.instant();
        long recent = challenges.countByPhoneHashAndCreatedAtAfter(phoneHash, now.minus(rateWindow));
        if (recent >= rateMax) {
            throw new IdentityException(
                    ErrorCodes.ERR_OTP_RATE_LIMITED, 429, "Too many one-time code requests. Try again later.");
        }
        String mode = (devCode == null || devCode.isBlank()) ? "SMS" : "DEV_FIXED";
        if (farmers.findByPhoneHash(phoneHash).isEmpty()) {
            return new RequestOtpResult((int) otpTtl.toSeconds(), mode);
        }
        challenges.consumeOpenChallenges(phoneHash, now);
        UUID id = Uuid7.create();
        String code = (devCode == null || devCode.isBlank()) ? "000000" : devCode;
        String codeHash = OtpCodeHash.compute(id, phoneHash, code);
        challenges.save(new OtpChallengeEntity(id, phoneHash, codeHash, (short) 0, now.plus(otpTtl), null, now));
        return new RequestOtpResult((int) otpTtl.toSeconds(), mode);
    }
}
