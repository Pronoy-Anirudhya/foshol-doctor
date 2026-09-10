package com.rootcause.foshol.analysis.domain;

import com.rootcause.foshol.common.enums.CropQuantityUnit;
import com.rootcause.foshol.common.enums.FieldAreaUnit;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extraction of field area / crop quantity from an ASR transcript. No LLM and no
 * invented dosages — only number + known unit phrases (CONTENT-OWNERS C16).
 */
public final class FieldMetricsExtractor {

    private static final Pattern TOKEN = Pattern.compile(
            "(?<num>\\d+(?:[.,]\\d+)?)\\s*(?<unit>"
                    + "শতক|বিঘা|একর|হেক্টর|বর্গ\\s*মিটার|বর্গমিটার|বর্গ\\s*ফুট|বর্গফুট|"
                    + "decimal|decimals|acre|acres|hectare|hectares|sq\\.?\\s*m|sq\\.?\\s*ft|sqm|sqft|"
                    + "কেজি|কি\\.?\\s*গ্রা|টন|গাছ|চারা|kg|kgs|ton|tons|plants?"
                    + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private FieldMetricsExtractor() {}

    public static Extracted extract(String transcriptBn) {
        if (transcriptBn == null || transcriptBn.isBlank()) {
            return Extracted.empty();
        }
        String normalised = asciiDigits(transcriptBn);
        BigDecimal area = null;
        FieldAreaUnit areaUnit = null;
        BigDecimal quantity = null;
        CropQuantityUnit quantityUnit = null;
        Matcher matcher = TOKEN.matcher(normalised);
        while (matcher.find()) {
            BigDecimal value = parseNumber(matcher.group("num"));
            if (value == null || value.signum() <= 0) {
                continue;
            }
            String unitRaw = matcher.group("unit").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            FieldAreaUnit parsedArea = mapArea(unitRaw);
            if (parsedArea != null && area == null) {
                area = value;
                areaUnit = parsedArea;
                continue;
            }
            CropQuantityUnit parsedQty = mapQuantity(unitRaw);
            if (parsedQty != null && quantity == null) {
                quantity = value;
                quantityUnit = parsedQty;
            }
        }
        return new Extracted(area, areaUnit, quantity, quantityUnit);
    }

    private static String asciiDigits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u09E6' && c <= '\u09EF') {
                out.append((char) ('0' + (c - '\u09E6')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static BigDecimal parseNumber(String raw) {
        try {
            return new BigDecimal(raw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static FieldAreaUnit mapArea(String unit) {
        return switch (unit) {
            case "শতক", "decimal", "decimals", "বিঘা" -> FieldAreaUnit.DECIMAL;
            case "একর", "acre", "acres" -> FieldAreaUnit.ACRE;
            case "হেক্টর", "hectare", "hectares" -> FieldAreaUnit.HECTARE;
            case "বর্গমিটার", "sq.m", "sqm" -> FieldAreaUnit.SQ_M;
            case "বর্গফুট", "sq.ft", "sqft" -> FieldAreaUnit.SQ_FT;
            default -> null;
        };
    }

    private static CropQuantityUnit mapQuantity(String unit) {
        return switch (unit) {
            case "কেজি", "কি.গ্রা", "কিগ্রা", "kg", "kgs" -> CropQuantityUnit.KG;
            case "টন", "ton", "tons" -> CropQuantityUnit.TON;
            case "গাছ", "চারা", "plant", "plants" -> CropQuantityUnit.PLANTS;
            default -> null;
        };
    }

    public record Extracted(
            BigDecimal fieldArea,
            FieldAreaUnit fieldAreaUnit,
            BigDecimal cropQuantity,
            CropQuantityUnit cropQuantityUnit) {

        static Extracted empty() {
            return new Extracted(null, null, null, null);
        }

        public boolean isEmpty() {
            return fieldArea == null && cropQuantity == null;
        }
    }
}
