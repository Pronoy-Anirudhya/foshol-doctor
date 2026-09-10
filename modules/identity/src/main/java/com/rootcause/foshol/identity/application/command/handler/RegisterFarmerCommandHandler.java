package com.rootcause.foshol.identity.application.command.handler;

import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.util.Uuid7;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.identity.application.command.RegisterFarmerCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerResult;
import com.rootcause.foshol.identity.application.port.DistrictPort;
import com.rootcause.foshol.identity.application.port.DistrictRef;
import com.rootcause.foshol.identity.application.port.FarmerSnapshot;
import com.rootcause.foshol.identity.application.port.FarmerStore;
import com.rootcause.foshol.identity.application.port.IdempotencySnapshot;
import com.rootcause.foshol.identity.application.port.OfficerSnapshot;
import com.rootcause.foshol.identity.application.port.OfficerStore;
import com.rootcause.foshol.identity.application.port.PhoneCipherPort;
import com.rootcause.foshol.identity.application.port.ProvisionIdempotencyStore;
import com.rootcause.foshol.identity.application.query.FarmerRecordAssembler;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.domain.RegistrationSource;
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

    private final FarmerStore farmers;
    private final OfficerStore officers;
    private final DistrictPort districts;
    private final ProvisionIdempotencyStore idempotency;
    private final PhoneCipherPort phoneCipher;
    private final FarmerRecordAssembler assembler;
    private final Clock clock;
    private final Duration idempotencyTtl;

    public RegisterFarmerCommandHandler(
            FarmerStore farmers,
            OfficerStore officers,
            DistrictPort districts,
            ProvisionIdempotencyStore idempotency,
            PhoneCipherPort phoneCipher,
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
        OfficerSnapshot officer = officers.findById(command.officerId())
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
            var existing = idempotency.findByKey(idempotencyKey);
            if (existing.isPresent()) {
                IdempotencySnapshot row = existing.get();
                if (!row.officerId().equals(command.officerId()) || !row.requestHash().equals(requestHash)) {
                    throw new IdentityException(
                            ErrorCodes.ERR_IDEMPOTENCY_KEY_CONFLICT, 409, "Idempotency key was reused with a different request.");
                }
                FarmerSnapshot replayed = farmers.findById(row.farmerId())
                        .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_FARMER_NOT_FOUND, 404, "Farmer not found."));
                return new RegisterFarmerResult(assembler.assemble(replayed), true);
            }
        }

        if (farmers.findByPhoneHash(phoneHash).isPresent()) {
            throw new IdentityException(ErrorCodes.ERR_FARMER_PHONE_EXISTS, 409, "A farmer with this phone is already registered.");
        }

        Instant now = clock.instant();
        UUID farmerId = Uuid7.create();
        FarmerSnapshot farmer = new FarmerSnapshot(
                farmerId,
                name,
                phoneHash,
                phoneCipher.encrypt(phone.e164()),
                command.districtCode().trim(),
                command.divisionCode().trim(),
                language,
                officer.id(),
                source,
                now);
        try {
            farmers.saveAndFlush(farmer);
            if (idempotencyKey != null) {
                idempotency.saveAndFlush(new IdempotencySnapshot(
                        idempotencyKey,
                        officer.id(),
                        requestHash,
                        farmerId,
                        now,
                        now.plus(idempotencyTtl)));
            }
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey != null) {
                var raced = idempotency.findByKey(idempotencyKey);
                if (raced.isPresent()
                        && raced.get().officerId().equals(command.officerId())
                        && raced.get().requestHash().equals(requestHash)) {
                    FarmerSnapshot replayed = farmers.findById(raced.get().farmerId())
                            .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_FARMER_NOT_FOUND, 404, "Farmer not found."));
                    return new RegisterFarmerResult(assembler.assemble(replayed), true);
                }
            }
            throw new IdentityException(ErrorCodes.ERR_FARMER_PHONE_EXISTS, 409, "A farmer with this phone is already registered.");
        }
        return new RegisterFarmerResult(assembler.assemble(farmer), false);
    }

    private void validateGeo(OfficerSnapshot officer, String divisionCode, String districtCode) {
        if (divisionCode == null || divisionCode.isBlank() || districtCode == null || districtCode.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_GEO_INVALID, 400, "Division and district are required.");
        }
        String division = divisionCode.trim();
        String district = districtCode.trim();
        DistrictRef geo = districts.findByCode(district)
                .orElseThrow(() -> new IdentityException(ErrorCodes.ERR_GEO_INVALID, 400, "Unknown division or district."));
        if (!geo.divisionCode().equals(division)) {
            throw new IdentityException(ErrorCodes.ERR_GEO_INVALID, 400, "Unknown division or district.");
        }
        if (!officer.districtCode().equals(district) || !officer.divisionCode().equals(division)) {
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
