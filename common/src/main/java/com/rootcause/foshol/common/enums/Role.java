package com.rootcause.foshol.common.enums;

public enum Role {
    FARMER,
    OFFICER,
    ADMIN;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public String toAuthority() {
        return AUTHORITY_PREFIX + name();
    }

    public static Role fromAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("authority is required");
        }
        String name = authority.startsWith(AUTHORITY_PREFIX)
                ? authority.substring(AUTHORITY_PREFIX.length())
                : authority;
        return valueOf(name);
    }
}
