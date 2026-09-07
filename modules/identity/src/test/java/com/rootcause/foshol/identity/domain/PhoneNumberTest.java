package com.rootcause.foshol.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.foshol.common.ErrorCodes;
import org.junit.jupiter.api.Test;

class PhoneNumberTest {

    @Test
    void normalisesLocalBangladeshiPrefixToE164() {
        PhoneNumber parsed = PhoneNumber.parse("01712345678");
        assertThat(parsed.e164()).isEqualTo("+8801712345678");
        assertThat(PhoneHash.of(parsed).hex()).isEqualTo(PhoneHash.of(PhoneNumber.parse("+8801712345678")).hex());
    }

    @Test
    void hashIsStableLowercaseHex() {
        String first = PhoneHash.of(PhoneNumber.parse("+8801712345678")).hex();
        String second = PhoneHash.of(PhoneNumber.parse("+8801712345678")).hex();
        assertThat(first).isEqualTo(second).hasSize(64).isLowerCase();
    }

    @Test
    void rejectsUnparseableInput() {
        assertThatThrownBy(() -> PhoneNumber.parse("not-a-phone"))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_PHONE_INVALID);
    }
}
