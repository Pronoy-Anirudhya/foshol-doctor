package com.rootcause.foshol.identity.application.command.handler;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.cqrs.CommandHandler;
import com.rootcause.foshol.identity.application.command.FarmerImportResult;
import com.rootcause.foshol.identity.application.command.FarmerImportResult.FarmerImportRowResult;
import com.rootcause.foshol.identity.application.command.ImportFarmersCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerResult;
import com.rootcause.foshol.identity.domain.FarmerImportCsvParser;
import com.rootcause.foshol.identity.domain.FarmerImportCsvParser.CsvRow;
import com.rootcause.foshol.identity.domain.IdentityException;
import com.rootcause.foshol.identity.domain.PhoneHash;
import com.rootcause.foshol.identity.domain.PhoneNumber;
import com.rootcause.foshol.identity.domain.RegistrationSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImportFarmersCommandHandler implements CommandHandler<ImportFarmersCommand, FarmerImportResult> {

    private final RegisterFarmerCommandHandler register;
    private final int maxRows;
    private final int maxBytes;

    public ImportFarmersCommandHandler(
            RegisterFarmerCommandHandler register,
            @Value("${" + ConfigKeys.IDENTITY_BULK_MAX_SIZE + "}") int maxRows,
            @Value("${" + ConfigKeys.IDENTITY_BULK_MAX_BYTES + "}") int maxBytes) {
        this.register = register;
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
    }

    @Override
    public Class<ImportFarmersCommand> commandType() {
        return ImportFarmersCommand.class;
    }

    @Override
    public FarmerImportResult handle(ImportFarmersCommand command) {
        if (command.contentType() != null && !isCsvContentType(command.contentType())) {
            throw new IdentityException(ErrorCodes.ERR_UNSUPPORTED_MEDIA_TYPE, 415, "Upload a CSV file.");
        }
        List<CsvRow> rows = FarmerImportCsvParser.parse(command.csvBytes(), maxBytes, maxRows);
        List<FarmerImportRowResult> results = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        int succeeded = 0;
        int failed = 0;
        for (CsvRow row : rows) {
            String duplicateHash = phoneHashOrNull(row.phone());
            if (duplicateHash != null && !seenHashes.add(duplicateHash)) {
                failed++;
                results.add(new FarmerImportRowResult(
                        row.rowNumber(),
                        "FAILED",
                        null,
                        blankToNull(row.name()),
                        ErrorCodes.ERR_BULK_DUPLICATE,
                        "This phone appears more than once in the file."));
                continue;
            }
            try {
                RegisterFarmerResult created = register.handle(new RegisterFarmerCommand(
                        command.officerId(),
                        row.name(),
                        row.phone(),
                        row.divisionCode(),
                        row.districtCode(),
                        row.preferredLanguage(),
                        RegistrationSource.CSV,
                        null));
                succeeded++;
                results.add(new FarmerImportRowResult(
                        row.rowNumber(), "OK", created.farmer().id(), created.farmer().name(), null, null));
            } catch (IdentityException ex) {
                failed++;
                results.add(new FarmerImportRowResult(
                        row.rowNumber(),
                        "FAILED",
                        null,
                        blankToNull(row.name()),
                        ex.errorCode(),
                        ex.getMessage()));
            }
        }
        return new FarmerImportResult(succeeded, failed, results);
    }

    private static String phoneHashOrNull(String phone) {
        try {
            return PhoneHash.of(PhoneNumber.parse(phone)).hex();
        } catch (IdentityException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static boolean isCsvContentType(String contentType) {
        String ct = contentType.toLowerCase();
        return ct.contains("csv")
                || ct.contains("excel")
                || ct.startsWith("text/plain")
                || ct.startsWith("application/octet-stream");
    }
}
