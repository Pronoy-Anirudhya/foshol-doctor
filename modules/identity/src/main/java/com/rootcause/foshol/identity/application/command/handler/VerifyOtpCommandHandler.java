package com.rootcause.foshol.identity.application.command.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.application.command.AuthTokenResult;
import com.rootcause.foshol.identity.application.command.VerifyOtpCommand;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.OtpChallenge;
import com.rootcause.foshol.identity.domain.OtpCodeHash;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.GeoLabelLookup;
import com.rootcause.foshol.identity.infrastructure.JwtService;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeEntity;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeJpaRepository;
import com.rootcause.foshol.common.cqrs.CommandHandler;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerifyOtpCommandHandler implements CommandHandler<VerifyOtpCommand, AuthTokenResult> {

    @Override
    public Class<VerifyOtpCommand> commandType() {
        return VerifyOtpCommand.class;
    }

    private final FarmerJpaRepository farmers;
    private final OtpChallengeJpaRepository challenges;
    private final JwtService jwtService;
    private final GeoLabelLookup geoLabels;
    private final Clock clock;
    private final boolean otpEnabled;
    private final int maxAttempts;

    public VerifyOtpCommandHandler(
            FarmerJpaRepository farmers,
            OtpChallengeJpaRepository challenges,
            JwtService jwtService,
            GeoLabelLookup geoLabels,
            Clock clock,
            @Value("${" + ConfigKeys.AUTH_OTP_ENABLED + "}") boolean otpEnabled,
            @Value("${" + ConfigKeys.AUTH_OTP_MAX_ATTEMPTS + "}") int maxAttempts) {
        this.farmers = farmers;
        this.challenges = challenges;
        this.jwtService = jwtService;
        this.geoLabels = geoLabels;
        this.clock = clock;
        this.otpEnabled = otpEnabled;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    @Override
    public AuthTokenResult handle(VerifyOtpCommand command) {
        if (!otpEnabled) {
            throw new IdentityException(ErrorCodes.ERR_OTP_DISABLED, 503, "One-time codes are disabled.");
        }
        PhoneNumber phone = PhoneNumber.parse(command.phone());
        String phoneHash = PhoneHash.of(phone).hex();
        Instant now = clock.instant();
        OtpChallengeEntity row = challenges
                .findFirstByPhoneHashAndConsumedAtIsNullOrderByCreatedAtDesc(phoneHash)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid."));
        OtpChallenge challenge = new OtpChallenge(
                row.getId(),
                row.getPhoneHash(),
                row.getCodeHash(),
                row.getAttempts(),
                row.getExpiresAt(),
                row.getConsumedAt(),
                row.getCreatedAt());
        if (challenge.isExpired(now)) {
            throw new IdentityException(ErrorCodes.ERR_OTP_EXPIRED, 401, "The code has expired.");
        }
        if (!challenge.isVerifiable(now, maxAttempts)) {
            throw new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid.");
        }
        String expected = OtpCodeHash.compute(challenge.id(), phoneHash, command.code());
        if (!OtpCodeHash.equalsConstantTime(expected, challenge.codeHash())) {
            int attempts = challenge.incrementAttempts();
            row.setAttempts((short) attempts);
            if (attempts >= maxAttempts) {
                row.setConsumedAt(now);
                throw new IdentityException(
                        ErrorCodes.ERR_OTP_ATTEMPTS_EXCEEDED, 401, "Too many incorrect attempts.");
            }
            throw new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid.");
        }
        var farmer = farmers.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid."));
        row.setConsumedAt(now);
        JwtService.IssuedToken token = jwtService.issue(farmer.getId(), Role.FARMER, now);
        GeoLabelLookup.Labels geo = geoLabels.forDistrict(farmer.getDistrictCode(), farmer.getDivisionCode());
        return new AuthTokenResult(
                token.compact(),
                token.expiresAt(),
                Role.FARMER,
                farmer.getId(),
                farmer.getName(),
                farmer.getDistrictCode(),
                farmer.getDivisionCode(),
                geo.districtNameBn(),
                geo.districtNameEn(),
                geo.divisionNameBn(),
                geo.divisionNameEn(),
                farmer.getPreferredLanguage(),
                null);
    }
}
