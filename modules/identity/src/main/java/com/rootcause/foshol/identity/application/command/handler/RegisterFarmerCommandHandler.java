package com.rootcause.foshol.identity.application.command.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.identity.application.command.RegisterFarmerCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerResult;
import com.rootcause.foshol.identity.application.FarmerRecordAssembler;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.domain.RegistrationSource;
import com.rootcause.foshol.identity.infrastructure.FarmerEntity;
import com.rootcause.foshol.identity.infrastructure.FarmerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FarmerProvisionIdempotencyEntity;
import com.rootcause.foshol.identity.infrastructure.FarmerProvisionIdempotencyJpaRepository;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerEntity;
import com.rootcause.foshol.identity.infrastructure.FieldOfficerJpaRepository;
import com.rootcause.foshol.identity.infrastructure.GeoDistrictEntity;
import com.rootcause.foshol.identity.infrastructure.GeoDistrictJpaRepository;
import com.rootcause.foshol.identity.infrastructure.PhoneCipher;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterFarmerCommandHandler implements CommandHandler<RegisterFarmerCommand, RegisterFarmerResult> {

    private final FarmerJpaRepository farmers;
    private final FieldOfficerJpaRepository officers;
    private final GeoDistrictJpaRepository districts;
    private final FarmerProvisionIdempotencyJpaRepository idempotency;
    private final PhoneCipher phoneCipher;
    private final FarmerRecordAssembler assembler;
    private final Clock clock;
    private final Duration idempotencyTtl;

    public RegisterFarmerCommandHandler(
            FarmerJpaRepository farmers,
            FieldOfficerJpaRepository officers,
            GeoDistrictJpaRepository districts,
            FarmerProvisionIdempotencyJpaRepository idempotency,
            PhoneCipher phoneCipher,
            FarmerRecordAssembler assembler,
            Clock clock,
            @Value("${" + ConfigKeys.IDENTITY_IDEMPOTENCY_TTL + "}") Duration idempotencyTtl) {
        this.farmers = farmers;
        this.officers = officers;
        this.districts = districts;
        this.idempotency = idempotency;
        this.phoneCipher = phoneCipher;
        this.assembler = assembler;
        this.clock = clock;
        this.idempotencyTtl = idempotencyTtl;
    }

    @Override
    public Class<RegisterFarmerCommand> commandType() {
        return RegisterFarmerCommand.class;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public RegisterFarmerResult handle(RegisterFarmerCommand command) {
        FieldOfficerEntity officer = officers.findById(command.officerId())
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_SUBJECT_NOT_FOUND, 404, "Subject not found."));
        String name = requireName(command.name());
        String language = requireLanguage(command.preferredLanguage());
        PhoneNumber phone = PhoneNumber.parse(command.phone());
        String phoneHash = PhoneHash.of(phone).hex();
        validateGeo(officer, command.divisionCode(), command.districtCode());
        String source = command.source() == null ? RegistrationSource.MANUAL : command.source();
        UUID idempotencyKey = parseIdempotencyKey(command.idempotencyKey(), source);
        String requestHash = fingerprint(command.officerId(), name, phoneHash, command.divisionCode(), command.districtCode(), language, source);

        if (idempotencyKey != null) {
            var existing = idempotency.findById(idempotencyKey);
            if (existing.isPresent()) {
                FarmerProvisionIdempotencyEntity row = existing.get();
                if (!row.getOfficerId().equals(command.officerId()) || !row.getRequestHash().equals(requestHash)) {
                    throw new IdentityException(
                            ErrorCodes.ERR_IDEMPOTENCY_KEY_CONFLICT, 409, "Idempotency key was reused with a different request.");
                }
                FarmerEntity replayed = farmers.findById(row.getFarmerId())
                        .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_FARMER_NOT_FOUND, 404, "Farmer not found."));
                return new RegisterFarmerResult(assembler.assemble(replayed), true);
            }
        }

        if (farmers.findByPhoneHash(phoneHash).isPresent()) {
            throw new IdentityException(ErrorCodes.ERR_FARMER_PHONE_EXISTS, 409, "A farmer with this phone is already registered.");
        }

        Instant now = clock.instant();
        UUID farmerId = Uuid7.create();
        FarmerEntity farmer = new FarmerEntity(
                farmerId,
                name,
                phoneHash,
                phoneCipher.encrypt(phone.e164()),
                command.districtCode().trim(),
                command.divisionCode().trim(),
                language,
                officer.getId(),
                source,
                now,
                now);
        try {
            farmers.saveAndFlush(farmer);
            if (idempotencyKey != null) {
                idempotency.saveAndFlush(new FarmerProvisionIdempotencyEntity(
                        idempotencyKey,
                        officer.getId(),
                        requestHash,
                        farmerId,
                        now,
                        now.plus(idempotencyTtl)));
            }
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey != null) {
                var raced = idempotency.findById(idempotencyKey);
                if (raced.isPresent()
                        && raced.get().getOfficerId().equals(command.officerId())
                        && raced.get().getRequestHash().equals(requestHash)) {
                    FarmerEntity replayed = farmers.findById(raced.get().getFarmerId())
                            .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_FARMER_NOT_FOUND, 404, "Farmer not found."));
                    return new RegisterFarmerResult(assembler.assemble(replayed), true);
                }
            }
            throw new IdentityException(ErrorCodes.ERR_FARMER_PHONE_EXISTS, 409, "A farmer with this phone is already registered.");
        }
        return new RegisterFarmerResult(assembler.assemble(farmer), false);
    }

    private void validateGeo(FieldOfficerEntity officer, String divisionCode, String districtCode) {
        if (divisionCode == null || divisionCode.isBlank() || districtCode == null || districtCode.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_GEO_INVALID, 400, "Division and district are required.");
        }
        String division = divisionCode.trim();
        String district = districtCode.trim();
        GeoDistrictEntity geo = districts.findById(district)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_GEO_INVALID, 400, "Unknown division or district."));
        if (!geo.getDivision().getCode().equals(division)) {
            throw new IdentityException(ErrorCodes.ERR_GEO_INVALID, 400, "Unknown division or district.");
        }
        if (!officer.getDistrictCode().equals(district) || !officer.getDivisionCode().equals(division)) {
            throw new IdentityException(
                    ErrorCodes.ERR_DISTRICT_SCOPE, 400, "Farmers can only be registered in your district.");
        }
    }

    private static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_BAD_REQUEST, 400, "Name is required.");
        }
        String name = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        if (name.isEmpty() || name.length() > 120) {
            throw new IdentityException(ErrorCodes.ERR_BAD_REQUEST, 400, "Name must be between 1 and 120 characters.");
        }
        return name;
    }

    private static String requireLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_BAD_REQUEST, 400, "Preferred language is required.");
        }
        String language = raw.trim();
        if (!"bn".equals(language) && !"en".equals(language)) {
            throw new IdentityException(ErrorCodes.ERR_BAD_REQUEST, 400, "Preferred language must be bn or en.");
        }
        return language;
    }

    private static String fingerprint(
            UUID officerId,
            String name,
            String phoneHash,
            String division,
            String district,
            String language,
            String source) {
        return PhoneHash.sha256Hex(
                officerId + "|" + name + "|" + phoneHash + "|" + division + "|" + district + "|" + language + "|" + source);
    }

    private static UUID parseIdempotencyKey(String raw, String source) {
        if (RegistrationSource.CSV.equals(source)) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_IDEMPOTENCY_KEY_MISSING, 400, "Idempotency-Key is required.");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IdentityException(ErrorCodes.ERR_IDEMPOTENCY_KEY_INVALID, 400, "Idempotency-Key must be a UUID.");
        }
    }
}
