package com.rootcause.foshol.common.util;

import java.util.List;

/** COMMON-NFR-038: when English is missing, copy Bangla and flag fallback. */
public final class CatalogueLocale {

    private CatalogueLocale() {}

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isBlankList(List<String> values) {
        return values == null || values.isEmpty();
    }

    public static String enOrBn(String en, String bn) {
        return isBlank(en) ? bn : en;
    }

    public static boolean enFallback(String en) {
        return isBlank(en);
    }

    public static boolean enFallback(String en, String bn) {
        return isBlank(en) && !isBlank(bn);
    }

    public static List<String> enOrBn(List<String> en, List<String> bn) {
        List<String> bangla = bn == null ? List.of() : bn;
        return isBlankList(en) ? bangla : en;
    }

    public static boolean listFallback(List<String> en) {
        return isBlankList(en);
    }
}
