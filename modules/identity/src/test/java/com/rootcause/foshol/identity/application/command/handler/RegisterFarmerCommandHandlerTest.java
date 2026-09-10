package com.rootcause.foshol.identity.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.identity.application.command.RegisterFarmerCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerResult;
import com.rootcause.foshol.identity.application.port.DistrictPort;
import com.rootcause.foshol.identity.application.port.DistrictRef;
import com.rootcause.foshol.identity.application.port.FarmerSnapshot;
import com.rootcause.foshol.identity.application.port.FarmerStore;
import com.rootcause.foshol.identity.application.port.OfficerSnapshot;
import com.rootcause.foshol.identity.application.port.OfficerStore;
import com.rootcause.foshol.identity.application.port.PhoneCipherPort;
import com.rootcause.foshol.identity.application.port.ProvisionIdempotencyStore;
import com.rootcause.foshol.identity.application.query.FarmerRecord;
import com.rootcause.foshol.identity.application.query.FarmerRecordAssembler;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.domain.RegistrationSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterFarmerCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final UUID OFFICER = UUID.fromString("01800000-0000-7000-8000-000000000301");
    private static final UUID KEY = UUID.fromString("01800000-0000-7000-8000-000000000401");

    @Mock
    private FarmerStore farmers;

    @Mock
    private OfficerStore officers;

    @Mock
    private DistrictPort districts;

    @Mock
    private ProvisionIdempotencyStore idempotency;

    @Mock
    private PhoneCipherPort phoneCipher;

    @Mock
    private FarmerRecordAssembler assembler;

    private RegisterFarmerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegisterFarmerCommandHandler(
                farmers,
                officers,
                districts,
                idempotency,
                phoneCipher,
                assembler,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24));
        OfficerSnapshot officer = new OfficerSnapshot(
                OFFICER, "IT Officer", "it_officer", "hash", true, "OFFICER", "DHA", "DHK");
        when(officers.findById(OFFICER)).thenReturn(Optional.of(officer));
        when(districts.findByCode("DHA")).thenReturn(Optional.of(new DistrictRef("DHA", "DHK")));
    }

    @Test
    void insertsFarmerInCallerDistrict() {
        when(farmers.findByPhoneHash(any())).thenReturn(Optional.empty());
        when(phoneCipher.encrypt(any())).thenReturn(new byte[] {1, 2, 3});
        FarmerRecord assembled = new FarmerRecord(
                UUID.randomUUID(), "Rahim", "DHK", "DHA", null, null, null, null, "bn", NOW, OFFICER, "IT Officer", "MANUAL");
        when(assembler.assemble(any(FarmerSnapshot.class))).thenReturn(assembled);

        RegisterFarmerResult result = handler.handle(command("+8801700000099", "DHK", "DHA"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.farmer().name()).isEqualTo("Rahim");
        verify(farmers).saveAndFlush(any(FarmerSnapshot.class));
        verify(idempotency).saveAndFlush(any());
    }

    @Test
    void rejectsOtherDistrict() {
        when(districts.findByCode("CTG")).thenReturn(Optional.of(new DistrictRef("CTG", "CTG")));
        assertThatThrownBy(() -> handler.handle(command("+8801700000099", "CTG", "CTG")))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_DISTRICT_SCOPE);
    }

    @Test
    void rejectsDuplicatePhone() {
        String hash = PhoneHash.of(PhoneNumber.parse("+8801700000099")).hex();
        when(farmers.findByPhoneHash(hash)).thenReturn(Optional.of(new FarmerSnapshot(
                UUID.randomUUID(), "Existing", hash, new byte[0], "DHA", "DHK", "bn", null, "MIGRATION", NOW)));
        assertThatThrownBy(() -> handler.handle(command("+8801700000099", "DHK", "DHA")))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_FARMER_PHONE_EXISTS);
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> handler.handle(command("+8801700000099", "DHK", "DHA", null)))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_IDEMPOTENCY_KEY_MISSING);
    }

    @Test
    void invalidIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> handler.handle(command("+8801700000099", "DHK", "DHA", "not-a-uuid")))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_IDEMPOTENCY_KEY_INVALID);
    }

    private RegisterFarmerCommand command(String phone, String divisionCode, String districtCode) {
        return command(phone, divisionCode, districtCode, KEY.toString());
    }

    private RegisterFarmerCommand command(String phone, String divisionCode, String districtCode, String key) {
        return new RegisterFarmerCommand(
                OFFICER, "Rahim", phone, divisionCode, districtCode, "bn", RegistrationSource.MANUAL, key);
    }
}
