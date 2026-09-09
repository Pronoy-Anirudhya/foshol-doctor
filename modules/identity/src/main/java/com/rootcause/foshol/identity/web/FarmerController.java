package com.rootcause.foshol.identity.web;

import com.rootcause.foshol.common.cqrs.CommandBus;
import com.rootcause.foshol.common.cqrs.QueryBus;
import com.rootcause.foshol.identity.application.FarmerRecord;
import com.rootcause.foshol.identity.application.command.FarmerImportResult;
import com.rootcause.foshol.identity.application.command.ImportFarmersCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerCommand;
import com.rootcause.foshol.identity.application.command.RegisterFarmerResult;
import com.rootcause.foshol.identity.application.query.FarmerRecordPage;
import com.rootcause.foshol.identity.application.query.GetFarmerQuery;
import com.rootcause.foshol.identity.application.query.ListFarmersQuery;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/farmers")
@PreAuthorize("hasAnyRole('OFFICER','ADMIN')")
public class FarmerController {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String CSV_HEADER = "name,phone,divisionCode,districtCode,preferredLanguage";
    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv; charset=utf-8");
    private static final String SOURCE_MANUAL = "MANUAL";

    private final CommandBus commands;
    private final QueryBus queries;

    public FarmerController(CommandBus commands, QueryBus queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    public ResponseEntity<FarmerRecord> register(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RegisterFarmerRequest request) {
        RegisterFarmerResult result = commands.handle(new RegisterFarmerCommand(
                UUID.fromString(authentication.getName()),
                request.name(),
                request.phone(),
                request.divisionCode(),
                request.districtCode(),
                request.preferredLanguage(),
                SOURCE_MANUAL,
                idempotencyKey));
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }
        return new ResponseEntity<>(result.farmer(), headers, HttpStatus.CREATED);
    }

    @GetMapping
    public FarmerRecordPage list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String phone) {
        return queries.handle(
                new ListFarmersQuery(UUID.fromString(authentication.getName()), page, size, q, phone));
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        byte[] header = (CSV_HEADER + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[BOM.length + header.length];
        System.arraycopy(BOM, 0, body, 0, BOM.length);
        System.arraycopy(header, 0, body, BOM.length, header.length);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_CSV);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("farmer-import-template.csv").build());
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FarmerImportResult importCsv(Authentication authentication, @RequestPart("file") MultipartFile file)
            throws IOException {
        byte[] bytes = file == null ? new byte[0] : file.getBytes();
        String contentType = file == null ? null : file.getContentType();
        return commands.handle(
                new ImportFarmersCommand(UUID.fromString(authentication.getName()), bytes, contentType));
    }

    @GetMapping("/{farmerId}")
    public FarmerRecord get(Authentication authentication, @PathVariable UUID farmerId) {
        return queries.handle(new GetFarmerQuery(UUID.fromString(authentication.getName()), farmerId));
    }
}
