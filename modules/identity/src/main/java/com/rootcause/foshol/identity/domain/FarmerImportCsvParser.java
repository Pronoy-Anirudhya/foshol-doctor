package com.rootcause.foshol.identity.domain;

import com.rootcause.foshol.common.contract.ErrorCodes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FarmerImportCsvParser {

    public static final String HEADER = "name,phone,divisionCode,districtCode,preferredLanguage";

    public record CsvRow(
            int rowNumber, String name, String phone, String divisionCode, String districtCode, String preferredLanguage) {}

    private FarmerImportCsvParser() {}

    public static List<CsvRow> parse(byte[] bytes, int maxBytes, int maxRows) {
        if (bytes == null || bytes.length == 0) {
            throw new IdentityException(ErrorCodes.ERR_FARMER_IMPORT_INVALID, 400, "CSV file is empty.");
        }
        if (bytes.length > maxBytes) {
            throw new IdentityException(ErrorCodes.ERR_FARMER_IMPORT_INVALID, 400, "CSV file is too large.");
        }
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            offset = 3;
        }
        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n", -1);
        int first = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            throw new IdentityException(ErrorCodes.ERR_FARMER_IMPORT_INVALID, 400, "CSV file is empty.");
        }
        String header = lines[first].strip();
        if (header.charAt(0) == '\uFEFF') {
            header = header.substring(1);
        }
        if (!HEADER.equals(header)) {
            throw new IdentityException(
                    ErrorCodes.ERR_FARMER_IMPORT_INVALID,
                    400,
                    "CSV header must be name,phone,divisionCode,districtCode,preferredLanguage.");
        }
        List<CsvRow> rows = new ArrayList<>();
        for (int i = first + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            List<String> cols = splitCsvLine(line);
            if (cols.size() != 5) {
                throw new IdentityException(
                        ErrorCodes.ERR_FARMER_IMPORT_INVALID, 400, "Each CSV row must have exactly five columns.");
            }
            int rowNumber = i + 1;
            rows.add(new CsvRow(rowNumber, cols.get(0).trim(), cols.get(1).trim(), cols.get(2).trim(), cols.get(3).trim(), cols.get(4).trim()));
        }
        if (rows.size() > maxRows) {
            throw new IdentityException(ErrorCodes.ERR_BULK_TOO_LARGE, 400, "CSV has too many data rows.");
        }
        return rows;
    }

    static List<String> splitCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cols.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cols.add(current.toString());
        return cols;
    }
}
