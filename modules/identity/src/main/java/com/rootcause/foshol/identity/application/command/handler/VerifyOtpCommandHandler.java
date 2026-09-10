package com.rootcause.foshol.identity.application.command.handler;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.identity.application.command.AuthTokenResult;
import com.rootcause.foshol.identity.application.command.VerifyOtpCommand;
import com.rootcause.foshol.identity.application.port.FarmerSnapshot;
import com.rootcause.foshol.identity.application.port.FarmerStore;
import com.rootcause.foshol.identity.application.port.GeoLabelPort;
import com.rootcause.foshol.identity.application.port.GeoLabels;
import com.rootcause.foshol.identity.application.port.IssuedToken;
import com.rootcause.foshol.identity.application.port.OtpChallengeStore;
import com.rootcause.foshol.identity.application.port.TokenIssuer;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.OtpChallenge;
import com.rootcause.foshol.identity.domain.OtpCodeHash;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
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

    private final FarmerStore farmers;
    private final OtpChallengeStore challenges;
    private final TokenIssuer tokens;
    private final GeoLabelPort geoLabels;
    private final Clock clock;
    private final boolean otpEnabled;
    private final int maxAttempts;

    public VerifyOtpCommandHandler(
            FarmerStore farmers,
            OtpChallengeStore challenges,
            TokenIssuer tokens,
            GeoLabelPort geoLabels,
            Clock clock,
            @Value("${" + ConfigKeys.AUTH_OTP_ENABLED + "}") boolean otpEnabled,
            @Value("${" + ConfigKeys.AUTH_OTP_MAX_ATTEMPTS + "}") int maxAttempts) {
        this.farmers = farmers;
        this.challenges = challenges;
        this.tokens = tokens;
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
        OtpChallenge challenge = challenges
                .findLatestOpen(phoneHash)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid."));
        if (challenge.isExpired(now)) {
            throw new IdentityException(ErrorCodes.ERR_OTP_EXPIRED, 401, "The code has expired.");
        }
        if (!challenge.isVerifiable(now, maxAttempts)) {
            throw new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid.");
        }
        String expected = OtpCodeHash.compute(challenge.id(), phoneHash, command.code());
        if (!OtpCodeHash.equalsConstantTime(expected, challenge.codeHash())) {
            int attempts = challenge.incrementAttempts();
            if (attempts >= maxAttempts) {
                challenge.consume(now);
                challenges.save(challenge);
                throw new IdentityException(
                        ErrorCodes.ERR_OTP_ATTEMPTS_EXCEEDED, 401, "Too many incorrect attempts.");
            }
            challenges.save(challenge);
            throw new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid.");
        }
        FarmerSnapshot farmer = farmers.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_OTP_INVALID, 401, "The code is not valid."));
        challenge.consume(now);
        challenges.save(challenge);
        IssuedToken token = tokens.issue(farmer.id(), Role.FARMER, now);
        GeoLabels geo = geoLabels.forDistrict(farmer.districtCode(), farmer.divisionCode());
        return new AuthTokenResult(
                token.compact(),
                token.expiresAt(),
                Role.FARMER,
                farmer.id(),
                farmer.name(),
                farmer.districtCode(),
                farmer.divisionCode(),
                geo.districtNameBn(),
                geo.districtNameEn(),
                geo.divisionNameBn(),
                geo.divisionNameEn(),
                farmer.preferredLanguage(),
                null);
    }
}
