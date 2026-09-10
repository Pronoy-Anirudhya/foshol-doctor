package com.rootcause.foshol.identity.domain;

import com.rootcause.foshol.common.contract.ErrorCodes;
import java.util.regex.Pattern;

public final class PhoneNumber {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final String e164;

    private PhoneNumber(String e164) {
        this.e164 = e164;
    }

    public static PhoneNumber parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_PHONE_INVALID, 400, "Phone number is not valid.");
        }
        String digits = raw.trim().replaceAll("[\\s-]", "");
        String normalised;
        if (digits.startsWith("+")) {
            normalised = digits;
        } else if (digits.startsWith("880")) {
            normalised = "+" + digits;
        } else if (digits.startsWith("0")) {
            normalised = "+880" + digits.substring(1);
        } else {
            throw new IdentityException(ErrorCodes.ERR_PHONE_INVALID, 400, "Phone number is not valid.");
        }
        if (!E164.matcher(normalised).matches()) {
            throw new IdentityException(ErrorCodes.ERR_PHONE_INVALID, 400, "Phone number is not valid.");
        }
        return new PhoneNumber(normalised);
    }

    public String e164() {
        return e164;
    }
}
