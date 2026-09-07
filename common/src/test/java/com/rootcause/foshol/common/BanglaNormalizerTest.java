package com.rootcause.foshol.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BanglaNormalizerTest {

    @Test
    void forMatchingStripsZeroWidthAndFoldsDigits() {
        String input = "পাতা\u200c১২";
        assertThat(BanglaNormalizer.forMatching(input)).isEqualTo("পাতা12");
    }
}
