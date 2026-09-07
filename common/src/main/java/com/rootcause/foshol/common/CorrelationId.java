package com.rootcause.foshol.common;

import org.slf4j.MDC;
import java.util.UUID;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {}

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }

    public static String currentOrCreate() {
        String value = MDC.get(MDC_KEY);
        if (value == null || value.isBlank()) {
            value = Uuid7.create().toString();
            MDC.put(MDC_KEY, value);
        }
        return value;
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static void adoptOrGenerate(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            set(Uuid7.create().toString());
        } else {
            set(incoming.trim());
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    public static UUID asUuid() {
        return UUID.fromString(currentOrCreate());
    }
}
