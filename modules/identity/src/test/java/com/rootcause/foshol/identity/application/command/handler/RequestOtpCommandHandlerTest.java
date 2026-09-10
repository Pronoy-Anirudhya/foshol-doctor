package com.rootcause.foshol.identity.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.identity.application.command.RequestOtpCommand;
import com.rootcause.foshol.identity.application.command.RequestOtpResult;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.infrastructure.FarmerEntity;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeEntity;
import com.rootcause.foshol.identity.infrastructure.OtpChallengeJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestOtpCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final String PHONE = "+8801700000001";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Mock
    private FarmerJpaRepository farmers;

    @Mock
    private OtpChallengeJpaRepository challenges;

    private RequestOtpCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RequestOtpCommandHandler(
                farmers,
                challenges,
                Clock.fixed(NOW, ZoneOffset.UTC),
                true,
                TTL,
                "000000",
                3,
                WINDOW);
    }

    @Test
    void unknownPhoneIs404AndDoesNotCreateChallenge() {
        String hash = PhoneHash.of(PhoneNumber.parse(PHONE)).hex();
        when(challenges.countByPhoneHashAndCreatedAtAfter(hash, NOW.minus(WINDOW))).thenReturn(0L);
        when(farmers.findByPhoneHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RequestOtpCommand(PHONE)))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException identity = (IdentityException) ex;
                    assertThat(identity.errorCode()).isEqualTo(ErrorCodes.ERR_FARMER_NOT_FOUND);
                    assertThat(identity.status()).isEqualTo(404);
                    assertThat(identity.getMessage()).isEqualTo("Farmer was not found.");
                });
        verify(challenges, never()).save(any());
        verify(challenges, never()).consumeOpenChallenges(any(), any());
    }

    @Test
    void knownFarmerCreatesChallengeWithDevFixedMode() {
        String hash = PhoneHash.of(PhoneNumber.parse(PHONE)).hex();
        when(challenges.countByPhoneHashAndCreatedAtAfter(hash, NOW.minus(WINDOW))).thenReturn(0L);
        when(farmers.findByPhoneHash(hash))
                .thenReturn(Optional.of(new FarmerEntity(
                        UUID.fromString("01800000-0000-7000-8000-000000000201"),
                        "Rahim",
                        hash,
                        new byte[] {1},
                        "DHA",
                        "DHK",
                        "bn",
                        NOW,
                        NOW)));
        when(challenges.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RequestOtpResult result = handler.handle(new RequestOtpCommand(PHONE));

        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(result.otpDeliveryMode()).isEqualTo("DEV_FIXED");
        ArgumentCaptor<OtpChallengeEntity> saved = ArgumentCaptor.forClass(OtpChallengeEntity.class);
        verify(challenges).consumeOpenChallenges(hash, NOW);
        verify(challenges).save(saved.capture());
        assertThat(saved.getValue().getPhoneHash()).isEqualTo(hash);
        assertThat(saved.getValue().getAttempts()).isEqualTo((short) 0);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(saved.getValue().getConsumedAt()).isNull();
    }

    @Test
    void disabledOtpIs503() {
        handler = new RequestOtpCommandHandler(
                farmers,
                challenges,
                Clock.fixed(NOW, ZoneOffset.UTC),
                false,
                TTL,
                "000000",
                3,
                WINDOW);
        assertThatThrownBy(() -> handler.handle(new RequestOtpCommand(PHONE)))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException identity = (IdentityException) ex;
                    assertThat(identity.errorCode()).isEqualTo(ErrorCodes.ERR_OTP_DISABLED);
                    assertThat(identity.status()).isEqualTo(503);
                });
        verify(challenges, never()).save(any());
    }

    @Test
    void rateLimitedIs429() {
        String hash = PhoneHash.of(PhoneNumber.parse(PHONE)).hex();
        when(challenges.countByPhoneHashAndCreatedAtAfter(hash, NOW.minus(WINDOW))).thenReturn(3L);
        assertThatThrownBy(() -> handler.handle(new RequestOtpCommand(PHONE)))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException identity = (IdentityException) ex;
                    assertThat(identity.errorCode()).isEqualTo(ErrorCodes.ERR_OTP_RATE_LIMITED);
                    assertThat(identity.status()).isEqualTo(429);
                });
        verify(farmers, never()).findByPhoneHash(any());
        verify(challenges, never()).save(any());
    }
}
