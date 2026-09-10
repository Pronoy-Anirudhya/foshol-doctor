package com.rootcause.foshol.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.contract.ErrorCodes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FarmerImportCsvParserTest {

    @Test
    void parsesUtf8RowsAndSkipsBlankLines() {
        String csv = FarmerImportCsvParser.HEADER + "\n"
                + "Rahim,+8801712345678,DHK,DHA,bn\n"
                + "\n"
                + "Karim,01712345679,DHK,DHA,en\n";
        List<FarmerImportCsvParser.CsvRow> rows = FarmerImportCsvParser.parse(csv.getBytes(StandardCharsets.UTF_8), 1024, 10);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
        assertThat(rows.get(0).name()).isEqualTo("Rahim");
        assertThat(rows.get(1).rowNumber()).isEqualTo(4);
        assertThat(rows.get(1).preferredLanguage()).isEqualTo("en");
    }

    @Test
    void acceptsBomAndRejectsWrongHeader() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] header = (FarmerImportCsvParser.HEADER + "\nA,+8801,DHK,DHA,bn\n").getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + header.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(header, 0, withBom, bom.length, header.length);
        assertThat(FarmerImportCsvParser.parse(withBom, 1024, 10)).hasSize(1);

        assertThatThrownBy(() -> FarmerImportCsvParser.parse("nope\n".getBytes(StandardCharsets.UTF_8), 1024, 10))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_FARMER_IMPORT_INVALID);
    }

    @Test
    void rejectsTooManyRows() {
        String csv = FarmerImportCsvParser.HEADER + "\nA,+8801700000001,DHK,DHA,bn\nB,+8801700000002,DHK,DHA,bn\n";
        assertThatThrownBy(() -> FarmerImportCsvParser.parse(csv.getBytes(StandardCharsets.UTF_8), 1024, 1))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_BULK_TOO_LARGE);
    }
}
